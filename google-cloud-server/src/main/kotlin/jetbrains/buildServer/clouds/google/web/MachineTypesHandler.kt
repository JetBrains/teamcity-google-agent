

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.GoogleConstants
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import org.jdom.Element

/**
 * Handles machine types request.
 */
internal class MachineTypesHandler : GoogleResourceHandler() {
    override suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>) = coroutineScope {
        val zone = parameters[GoogleConstants.ZONE]!!
        val machineTypes = connector.getMachineTypes(zone)
        val machineTypesElement = Element("machineTypes")

        for ((id, displayName) in machineTypes) {
            machineTypesElement.addContent(Element("machineType").apply {
                setAttribute("id", id)
                text = displayName
            })
        }

        machineTypesElement
    }
}