

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles zones request.
 */
internal class ZonesHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val zones = connector.getZones()
        val zonesElement = Element("zones")

        for ((id, props) in zones) {
            zonesElement.addContent(Element("zone").apply {
                setAttribute("id", id)
                setAttribute("region", props[1])
                text = props[0]
            })
        }

        zonesElement
    }
}