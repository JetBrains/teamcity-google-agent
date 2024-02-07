

package jetbrains.buildServer.clouds.google.web

import jetbrains.buildServer.clouds.google.GoogleConstants
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnectorImpl
import jetbrains.buildServer.clouds.google.utils.PluginPropertiesUtil
import jetbrains.buildServer.controllers.BasePropertiesBean
import org.jdom.Content

/**
 * Google resource handler.
 */
internal abstract class GoogleResourceHandler : ResourceHandler {
    override suspend fun handle(parameters: Map<String, String>): Content {
        val propsBean = BasePropertiesBean(null)
        PluginPropertiesUtil.bindPropertiesFromRequest(parameters, propsBean, true)

        val props = propsBean.properties
        val credentialsType = props[GoogleConstants.CREDENTIALS_TYPE]
        val apiConnector = if (credentialsType != GoogleConstants.CREDENTIALS_ENVIRONMENT) {
            val accessKey = props[GoogleConstants.ACCESS_KEY]!!
            GoogleApiConnectorImpl(accessKey)
        } else {
            GoogleApiConnectorImpl()
        }

        return handle(apiConnector, parameters)
    }

    protected abstract suspend fun handle(connector: GoogleApiConnector, parameters: Map<String, String>): Content
}