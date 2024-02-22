

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.GoogleConstants
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles disk types request.
 */
internal class DiskTypesHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val zone = parameters[GoogleConstants.ZONE]!!
        val diskTypes = connector.getDiskTypes(zone)
        val diskTypesElement = Element("diskTypes")

        for ((id, displayName) in diskTypes) {
            // Show only persistent disk types
            if (!id.startsWith("pd-")) continue

            diskTypesElement.addContent(Element("diskType").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        diskTypesElement
    }
}