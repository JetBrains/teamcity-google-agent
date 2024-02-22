

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles networks request.
 */
internal class TemplatesHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val templates = connector.getTemplates()
        val templatesElement = Element("templates")

        for ((id, displayName) in templates) {
            templatesElement.addContent(Element("template").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        templatesElement
    }
}