

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles networks request.
 */
internal class ImagesHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val images = connector.getImages(parameters.getOrDefault("sourceProject", ""))
        val imagesElement = Element("images")

        for ((id, displayName) in images) {
            imagesElement.addContent(Element("image").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        imagesElement
    }
}