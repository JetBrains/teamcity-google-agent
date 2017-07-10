package jetbrains.buildServer.clouds.google.web

import com.google.cloud.resourcemanager.ResourceManagerException
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.jdom.Element

/**
 * Handles permissions request.
 */
internal class PermissionsHandler : GoogleResourceHandler() {
    override fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = async(CommonPool) {
        val permissions = Element("permissions")
        try {
            connector.test()
        } catch (e: ResourceManagerException) {
            e.message?.let {
                if (it.contains("Google Cloud Resource Manager API has not been used in project")) {
                    LOG.info(it)
                    return@async permissions
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