

package jetbrains.buildServer.clouds.google.web

import com.google.cloud.resourcemanager.ResourceManagerException
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles permissions request.
 */
internal class PermissionsHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val permissions = Element("permissions")
        try {
            connector.test()
        } catch (e: ResourceManagerException) {
            e.message?.let {
                if (it.contains("Google Cloud Resource Manager API has not been used in project")) {
                    LOG.info(it)
                    return@coroutineScope permissions
                }
            }
            throw e
        }
        permissions
    }

    companion object {
        private val LOG = Logger.getInstance(PermissionsHandler::class.java.name)
    }
}