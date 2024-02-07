

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles networks request.
 */
internal class ImageFamiliesHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val imageFamilies = connector.getImageFamilies(parameters.getOrDefault("sourceProject", ""))
        val imageFamiliesElement = Element("imageFamilies")

        for (family in imageFamilies) {
            imageFamiliesElement.addContent(Element("imageFamily").apply {
                setAttribute("id", family)
                text = family
            })
        }

        imageFamiliesElement
    }
}