/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
