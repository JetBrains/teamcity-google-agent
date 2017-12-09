package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.GoogleConstants
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.jdom.Element

/**
 * Handles disk types request.
 */
internal class DiskTypesHandler : GoogleResourceHandler() {
    override fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = async(CommonPool) {
        val zone = parameters[GoogleConstants.ZONE]!!
        val diskTypes = connector.getDiskTypesAsync(zone).await()
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