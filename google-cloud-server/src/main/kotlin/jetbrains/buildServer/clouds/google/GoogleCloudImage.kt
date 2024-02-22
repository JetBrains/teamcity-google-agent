

package jetbrains.buildServer.clouds.google

import com.google.api.gax.rpc.InvalidArgumentException
import com.google.cloud.compute.v1.Operation
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.QuotaException
import jetbrains.buildServer.clouds.base.AbstractCloudImage
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import jetbrains.buildServer.clouds.google.types.GoogleHandler
import jetbrains.buildServer.clouds.google.types.GoogleImageFamilyHandler
import jetbrains.buildServer.clouds.google.types.GoogleImageHandler
import jetbrains.buildServer.clouds.google.types.GoogleTemplateHandler
import jetbrains.buildServer.clouds.google.utils.IdProvider
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Google cloud image.
 */
class GoogleCloudImage constructor(private val myImageDetails: GoogleCloudImageDetails,
                                   private val myApiConnector: GoogleApiConnector,
                                   private val myIdProvider: IdProvider)
    : AbstractCloudImage<GoogleCloudInstance, GoogleCloudImageDetails>(myImageDetails.sourceId, myImageDetails.sourceId),
DisposableHandle, CoroutineScope {

    private val job = Job()

    override fun dispose() {
        job.cancel()
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default + job

    private val myImageHandlers = mapOf(
            GoogleCloudImageType.Image to GoogleImageHandler(myApiConnector),
            GoogleCloudImageType.ImageFamily to GoogleImageFamilyHandler(myApiConnector),
            GoogleCloudImageType.Template to GoogleTemplateHandler(myApiConnector)
    )

    override fun getImageDetails(): GoogleCloudImageDetails {
        return myImageDetails
    }

    override fun createInstanceFromReal(realInstance: AbstractInstance): GoogleCloudInstance {
        val zone = realInstance.properties[GoogleConstants.ZONE]!!
        return GoogleCloudInstance(this, realInstance.name, zone).apply {
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

        createInstance(userData)
    }

    /**
     * Creates a new virtual machine.
     *
     * @param userData info about server.
     * @return created instance.
     */
    private fun createInstance(userData: CloudInstanceUserData): GoogleCloudInstance {
        val name = getInstanceName()
        val instance = GoogleCloudInstance(this, name, imageDetails.zone)
        instance.status = InstanceStatus.SCHEDULED_TO_START
        val data = GoogleUtils.setVmNameForTag(userData, name)

        launch {
            try {
                LOG.info("Creating new virtual machine ${instance.name}")
                val operation = handler.createInstance(instance, data)
                processOperationResult(instance, operation)
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)

                instance.status = InstanceStatus.ERROR
                val errInfo = TypedCloudErrorInfo.fromException(e)
                instance.updateErrors(errInfo)
                if (e is InvalidArgumentException) {
                    updateErrors(errInfo)
                }

                LOG.info("Removing allocated resources for virtual machine ${instance.name}")
                try {
                    myApiConnector.deleteVm(instance)
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

    private fun processOperationResult(instance: GoogleCloudInstance, operation: Operation) {
        instance.startOperationId = operation.id
        instance.status = InstanceStatus.RUNNING
    }

    override fun restartInstance(instance: GoogleCloudInstance) {
        instance.status = InstanceStatus.RESTARTING

        launch {
            try {
                LOG.info("Restarting virtual machine ${instance.name}")
                myApiConnector.restartVm(instance)
            } catch (e: Throwable) {
                LOG.warnAndDebugDetails(e.message, e)
                instance.status = InstanceStatus.ERROR
                instance.updateErrors(TypedCloudErrorInfo.fromException(e))
            }
        }
    }

    override fun terminateInstance(instance: GoogleCloudInstance) {
        instance.status = InstanceStatus.SCHEDULED_TO_STOP

        launch {
            try {
                if (myImageDetails.behaviour.isDeleteAfterStop) {
                    LOG.info("Removing virtual machine ${instance.name} due to cloud image settings")
                    myApiConnector.deleteVm(instance)
                } else {
                    LOG.info("Stopping virtual machine ${instance.name}")
                    myApiConnector.stopVm(instance)
                }
                instance.status = InstanceStatus.STOPPED
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

    val handler: GoogleHandler
        get() = myImageHandlers[imageDetails.type]!!

    private fun getInstanceName(): String {
        val sourceName = myImageDetails.sourceId.toLowerCase()

        val id = if (imageDetails.growingId) {
            myIdProvider.nextId
        } else {
            val keys = instances.map { it.instanceId.toLowerCase() }
            var i = 1
            while (keys.contains(sourceName + i)) i++
            i
        }

        return sourceName + id
    }

    /**
     * Returns active instances.
     *
     * @return instances.
     */
    private val activeInstances: List<GoogleCloudInstance>
        get() = instances.filter { instance -> instance.status.isStartingOrStarted }

    companion object {
        private val LOG = Logger.getInstance(GoogleCloudImage::class.java.name)
    }
}