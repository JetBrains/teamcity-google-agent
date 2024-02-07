

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.GoogleConstants
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles sub networks request.
 */
internal class SubnetsHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val region = parameters[GoogleConstants.REGION]!!
        val subnets = connector.getSubnets(region)
        val subnetsElement = Element("subnets")

        for ((id, props) in subnets) {
            subnetsElement.addContent(Element("subnet").apply {
                setAttribute("id", id)
                setAttribute("network", props[1])
                text = props[0]
            })
        }

        subnetsElement
    }
}