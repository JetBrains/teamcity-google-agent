/*
 * Copyright 2000-2020 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.google

import jetbrains.buildServer.clouds.*
import jetbrains.buildServer.clouds.base.AbstractCloudClientFactory
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnectorImpl
import jetbrains.buildServer.serverSide.AgentDescription
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.ServerPaths
import jetbrains.buildServer.serverSide.ServerSettings
import jetbrains.buildServer.web.openapi.PluginDescriptor
import java.io.File
import java.util.*

/**
 * Constructs Google cloud clients.
 */
class GoogleCloudClientFactory(cloudRegistrar: CloudRegistrar,
                               serverPaths: ServerPaths,
                               private val myPluginDescriptor: PluginDescriptor,
                               private val mySettings: ServerSettings,
                               private val myImagesHolder: GoogleCloudImagesHolder)
    : AbstractCloudClientFactory<GoogleCloudImageDetails, GoogleCloudClient>(cloudRegistrar) {

    private val myGoogleStorage: File = File(serverPaths.pluginDataDirectory, "googleIdx")

    init {
        if (!myGoogleStorage.exists()) {
            myGoogleStorage.mkdirs()
        }
    }

    override fun createNewClient(state: CloudState,
                                 images: Collection<GoogleCloudImageDetails>,
                                 params: CloudClientParameters): GoogleCloudClient {
        return createNewClient(state, params, emptyArray())
    }

    override fun createNewClient(state: CloudState,
                                 params: CloudClientParameters,
                                 errors: Array<TypedCloudErrorInfo>): GoogleCloudClient {
        val credentialsType = params.getParameter(GoogleConstants.CREDENTIALS_TYPE)
        val apiConnector = if (credentialsType != GoogleConstants.CREDENTIALS_ENVIRONMENT) {
            val accessKey = getParameter(params, GoogleConstants.ACCESS_KEY)
            GoogleApiConnectorImpl(accessKey)
        } else {
            GoogleApiConnectorImpl()
        }

        apiConnector.setServerId(mySettings.serverUUID)
        apiConnector.setProfileId(state.profileId)

        val cloudClient = GoogleCloudClient(params, apiConnector, myImagesHolder, myGoogleStorage)
        cloudClient.updateErrors(*errors)

        return cloudClient
    }

    private fun getParameter(params: CloudClientParameters, parameter: String): String {
        return params.getParameter(parameter) ?: throw RuntimeException("$parameter must not be empty")
    }

    override fun parseImageData(params: CloudClientParameters): Collection<GoogleCloudImageDetails> {
        if (!params.getParameter(CloudImageParameters.SOURCE_IMAGES_JSON).isNullOrEmpty()) {
            return GoogleUtils.parseImageData(GoogleCloudImageDetails::class.java, params)
        }

        return params.cloudImages.map {
            GoogleCloudImageDetails(
                    it.id!!,
                    it.getParameter(GoogleConstants.IMAGE_TYPE)?.let { type ->
                        GoogleCloudImageType.valueOf(type)
                    },
                    it.getParameter(GoogleConstants.SOURCE_IMAGE),
                    it.getParameter(GoogleConstants.SOURCE_IMAGE_FAMILY),
                    it.getParameter(GoogleConstants.INSTANCE_TEMPLATE),
                    it.getParameter(GoogleConstants.ZONE)!!,
                    it.getParameter(GoogleConstants.NETWORK_ID),
                    it.getParameter(GoogleConstants.SUBNET_ID),
                    (it.getParameter(GoogleConstants.MACHINE_CUSTOM) ?: "").toBoolean(),
                    it.getParameter(GoogleConstants.MACHINE_TYPE),
                    it.getParameter(GoogleConstants.MACHINE_CORES),
                    it.getParameter(GoogleConstants.MACHINE_MEMORY),
                    (it.getParameter(GoogleConstants.MACHINE_MEMORY_EXT) ?: "").toBoolean(),
                    (it.getParameter(GoogleConstants.MAX_INSTANCES_COUNT) ?: "1").toInt(),
                    it.agentPoolId,
                    it.getParameter(GoogleConstants.PROFILE_ID)!!,
                    (it.getParameter(GoogleConstants.PREEMPTIBLE) ?: "").toBoolean(),
                    it.getParameter(GoogleConstants.DISK_TYPE),
                    it.getParameter(GoogleConstants.METADATA),
                    (it.getParameter(GoogleConstants.GROWING_ID) ?: "").toBoolean(),
                    it.getParameter(GoogleConstants.SERVICE_ACCOUNT),
                    it.getParameter(GoogleConstants.SCOPES)
            )
        }
    }

    override fun checkClientParams(params: CloudClientParameters): Array<TypedCloudErrorInfo>? {
        return emptyArray()
    }

    override fun getCloudCode(): String {
        return "google"
    }

    override fun getDisplayName(): String {
        return "Google Compute Engine"
    }

    override fun getEditProfileUrl(): String? {
        return myPluginDescriptor.getPluginResourcesPath("settings.html")
    }

    override fun getInitialParameterValues(): Map<String, String> {
        return mapOf(GoogleConstants.CREDENTIALS_TYPE to GoogleConstants.CREDENTIALS_ENVIRONMENT)
    }

    override fun getPropertiesProcessor(): PropertiesProcessor {
        return PropertiesProcessor { properties ->
            properties.keys
                    .filter { SKIP_PARAMETERS.contains(it) }
                    .forEach { properties.remove(it) }

            emptyList()
        }
    }

    override fun canBeAgentOfType(description: AgentDescription): Boolean {
        return description.configurationParameters.containsKey(GoogleAgentProperties.INSTANCE_NAME)
    }

    companion object {
        private val SKIP_PARAMETERS = Arrays.asList(CloudImageParameters.SOURCE_ID_FIELD)
    }
}
