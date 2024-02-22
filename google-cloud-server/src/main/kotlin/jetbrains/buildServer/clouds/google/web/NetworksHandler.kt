

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles networks request.
 */
internal class NetworksHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val networks = connector.getNetworks()
        val networksElement = Element("networks")

        for ((id, displayName) in networks) {
            networksElement.addContent(Element("network").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        networksElement
    }
}