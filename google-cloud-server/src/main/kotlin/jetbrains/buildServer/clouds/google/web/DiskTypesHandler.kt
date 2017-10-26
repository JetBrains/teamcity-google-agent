package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.jdom.Element

/**
 * Handles disk types request.
 */
internal class DiskTypesHandler : GoogleResourceHandler() {
    override fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = async(CommonPool) {
        val diskTypes = connector.getDiskTypesAsync().await()
        val diskTypesElement = Element("diskTypes")

        for ((id, displayName) in diskTypes) {
            diskTypesElement.addContent(Element("diskType").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        diskTypesElement
    }
}