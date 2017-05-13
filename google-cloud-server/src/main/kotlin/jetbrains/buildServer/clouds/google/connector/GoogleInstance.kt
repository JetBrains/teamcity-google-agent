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

package jetbrains.buildServer.clouds.google.connector

import com.google.cloud.compute.Instance
import com.google.cloud.compute.InstanceInfo
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.base.connector.AbstractInstance

import java.util.Date

/**
 * Google cloud instance.
 */
class GoogleInstance internal constructor(private val instance: Instance) : AbstractInstance() {
    override fun getName(): String {
        return instance.instanceId.instance
    }

    override fun isInitialized(): Boolean {
        return true
    }

    override fun getStartDate(): Date? {
        return Date(instance.creationTimestamp)
    }

    override fun getIpAddress(): String? {
        val interfaces = instance.networkInterfaces
        if (interfaces.isEmpty()) return null
        return interfaces[0].networkIp
    }

    override fun getInstanceStatus(): InstanceStatus {
        STATES[instance.status]?.let {
            return it
        }

        return InstanceStatus.UNKNOWN
    }

    override fun getProperty(name: String): String? {
        return instance.metadata.values[name]
    }

    override fun getProperties(): MutableMap<String, String> = instance.metadata.values

    companion object {
        private var STATES = HashMap<InstanceInfo.Status, InstanceStatus>()

        init {
            STATES[InstanceInfo.Status.PROVISIONING] = InstanceStatus.SCHEDULED_TO_START
            STATES[InstanceInfo.Status.RUNNING] = InstanceStatus.RUNNING
            STATES[InstanceInfo.Status.STAGING] = InstanceStatus.SCHEDULED_TO_STOP
            STATES[InstanceInfo.Status.STOPPING] = InstanceStatus.STOPPING
            STATES[InstanceInfo.Status.TERMINATED] = InstanceStatus.STOPPED
        }
    }
}
