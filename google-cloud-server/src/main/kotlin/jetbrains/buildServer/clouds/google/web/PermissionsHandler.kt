package jetbrains.buildServer.clouds.google.web

import com.google.cloud.resourcemanager.ResourceManagerException
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.jdom.Element

/**
 * Handles permissions request.
 */
internal class PermissionsHandler : GoogleResourceHandler() {
    override fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = async(CommonPool) {
        try {
            connector.test()
        } catch (ignored: ResourceManagerException) {
        }

        Element("permissions")
    }
}