

package jetbrains.buildServer.clouds.google.connector

import com.google.cloud.compute.v1.Instance
import jetbrains.buildServer.clouds.InstanceStatus
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.google.GoogleConstants
import java.text.SimpleDateFormat
import java.util.*

/**
 * Google cloud instance.
 */
class GoogleInstance internal constructor(private val instance: Instance, zone: String) : AbstractInstance() {

    private val properties: Map<String, String>

    init {
        properties = instance.metadata.itemsList
                .associateBy({ items -> items.key }, { items -> items.value })
                .toMutableMap()
        properties[GoogleConstants.ZONE] = zone
    }

    override fun getName(): String {
        return instance.name
    }

    override fun isInitialized(): Boolean {
        return true
    }

    override fun getStartDate(): Date? {
        return DATE_FORMAT.parse(instance.creationTimestamp)
    }

    override fun getIpAddress(): String? {
        val interfaces = instance.networkInterfacesList
        if (interfaces.isEmpty()) return null
        return interfaces[0].networkIP
    }

    override fun getInstanceStatus(): InstanceStatus {
        STATES[instance.status]?.let {
            return it
        }

        return InstanceStatus.UNKNOWN
    }

    override fun getProperty(name: String): String? {
        return properties[name]
    }

    override fun getProperties() = properties

    companion object {
        private var STATES = TreeMap<String, InstanceStatus>(String.CASE_INSENSITIVE_ORDER)
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        init {
            STATES["PROVISIONING"] = InstanceStatus.SCHEDULED_TO_START
            STATES["RUNNING"] = InstanceStatus.RUNNING
            STATES["STAGING"] = InstanceStatus.SCHEDULED_TO_STOP
            STATES["STOPPING"] = InstanceStatus.STOPPING
            STATES["TERMINATED"] = InstanceStatus.STOPPED
        }
    }
}