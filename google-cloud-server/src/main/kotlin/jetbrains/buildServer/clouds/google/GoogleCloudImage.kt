/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.google

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.QuotaException
import jetbrains.buildServer.clouds.base.AbstractCloudImage
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking

/**
 * Google cloud image.
 */
class GoogleCloudImage constructor(private val myImageDetails: GoogleCloudImageDetails,
                                   private val myApiConnector: GoogleApiConnector)
    : AbstractCloudImage<GoogleCloudInstance, GoogleCloudImageDetails>(myImageDetails.sourceId, myImageDetails.sourceId) {

    override fun getImageDetails(): GoogleCloudImageDetails {
        return myImageDetails
    }

    override fun createInstanceFromReal(realInstance: AbstractInstance): GoogleCloudInstance {
        return GoogleCloudInstance(this, realInstance.name).apply {
            properties = realInstance.properties
        }
    }

    override fun canStartNewInstance(): Boolean {
        return activeInstances.size < myImageDetails.maxInstances
    }

    override fun startNewInstance(userData: CloudInstanceUserData): GoogleCloudInstance = runBlocking {
        if (!canStartNewInstance()) {
            throw QuotaException("Unable to start more instances. Limit has reached")
        }

        val instance = tryToStartStoppedInstanceAsync().await()
        instance ?: createInstance(userData)
    }

    /**
     * Creates a new virtual machine.
     *
     * @param userData info about server.
     * @return created instance.
     */
    private fun createInstance(userData: CloudInstanceUserData): GoogleCloudInstance {
        val name = getInstanceName()
        val instance = GoogleCloudInstance(this, name)
        instance.status = InstanceStatus.SCHEDULED_TO_START
        val data = GoogleUtils.setVmNameForTag(userData, name)

        async(CommonPool) {
            try {
                LOG.info("Creating new virtual machine ${instance.name}")
                myApiConnector.createVmAsync(instance, data).await()
                instance.status = InstanceStatus.RUNNING
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)

                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))

                LOG.info("Removing allocated resources for virtual machine ${instance.name}")
                try {
                    myApiConnector.deleteVmAsync(instance).await()
                    LOG.info("Allocated resources for virtual machine ${instance.name} have been removed")
                    removeInstance(instance.instanceId)
                } catch (e: Throwable) {
                    val message = "Failed to delete allocated resources for virtual machine ${instance.name}: ${e.message}"
                    LOG.warnAndDebugDetails(message, e)
                }
            }
        }

        addInstance(instance)

        return instance
    }

    /**
     * Tries to find and start stopped instance.
     *
     * @return instance if it found.
     */
    private fun tryToStartStoppedInstanceAsync() = async(CommonPool) {
        val instances = stoppedInstances
        if (instances.isNotEmpty()) {
            val validInstances = if (myImageDetails.behaviour.isDeleteAfterStop) {
                LOG.info("Will remove all virtual machines due to cloud image settings")
                emptyList()
            } else instances

            val invalidInstances = instances - validInstances
            val instance = validInstances.firstOrNull()

            instance?.status = InstanceStatus.SCHEDULED_TO_START

            async(CommonPool) {
                invalidInstances.forEach {
                    try {
                        LOG.info("Removing virtual machine ${it.name}")
                        myApiConnector.deleteVmAsync(it).await()
                        removeInstance(it.instanceId)
                    } catch (e: Throwable) {
                        LOG.warnAndDebugDetails(e.message, e)
                        it.status = InstanceStatus.ERROR
                        it.updateErrors(TypedCloudErrorInfo.fromException(e))
                    }
                }

                instance?.let {
                    try {
                        LOG.info("Starting stopped virtual machine ${it.name}")
                        myApiConnector.startVmAsync(it).await()
                    } catch (e: Throwable) {
                        LOG.warnAndDebugDetails(e.message, e)
                        it.status = InstanceStatus.ERROR
                        it.updateErrors(TypedCloudErrorInfo.fromException(e))
                    }
                }
            }

            return@async instance
        }

        null
    }

    override fun restartInstance(instance: GoogleCloudInstance) {
        instance.status = InstanceStatus.RESTARTING

        async(CommonPool) {
            try {
                LOG.info("Restarting virtual machine ${instance.name}")
                myApiConnector.restartVmAsync(instance).await()
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun terminateInstance(instance: GoogleCloudInstance) {
        instance.status = InstanceStatus.SCHEDULED_TO_STOP

        async(CommonPool) {
            try {
                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image settings")
                    myApiConnector.deleteVmAsync(instance).await()
                    removeInstance(instance.instanceId)
                } else {
                    LOG.info("Stopping virtual machine ${instance.name}")
                    myApiConnector.stopVmAsync(instance).await()
                    instance.status = InstanceStatus.STOPPED
                }

                LOG.info("Virtual machine ${instance.name} has been successfully terminated")
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun getAgentPoolId(): Int? {
        return myImageDetails.agentPoolId
    }

    private fun getInstanceName(): String {
        val keys = instances.map { it.instanceId.toLowerCase() }
        val sourceName = myImageDetails.sourceId.toLowerCase()
        var i: Int = 1

        while (keys.contains(sourceName + i)) i++

        return sourceName + i
    }

    /**
     * Returns active instances.
     *
     * @return instances.
     */
    private val activeInstances: List<GoogleCloudInstance>
        get() = instances.filter { instance -> instance.status.isStartingOrStarted }

    /**
     * Returns stopped instances.
     *
     * @return instances.
     */
    private val stoppedInstances: List<GoogleCloudInstance>
        get() = instances.filter { instance -> instance.status == InstanceStatus.STOPPED }

    companion object {
        private val LOG = Logger.getInstance(GoogleCloudImage::class.java.name)
    }
}
