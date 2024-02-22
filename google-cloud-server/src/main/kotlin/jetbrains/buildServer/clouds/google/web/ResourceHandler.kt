

package jetbrains.buildServer.clouds.google.web

import org.jdom.Content

/**
 * Request handler.
 */
internal interface ResourceHandler {
    suspend fun handle(parameters: Map<String, String>): Content
}