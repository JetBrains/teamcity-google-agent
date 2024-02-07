

package jetbrains.buildServer.clouds.google

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.agent.AgentLifeCycleAdapter
import jetbrains.buildServer.agent.AgentLifeCycleListener
import jetbrains.buildServer.agent.BuildAgent
import jetbrains.buildServer.agent.BuildAgentConfigurationEx
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.util.EventDispatcher
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import java.net.SocketTimeoutException

class GoogleMetadataReader(events: EventDispatcher<AgentLifeCycleListener>,
                           private val configuration: BuildAgentConfigurationEx,
                           private val idleShutdown: IdleShutdown) {

    init {
        LOG.info("Google plugin initializing...")

        events.addListener(object : AgentLifeCycleAdapter() {
            override fun afterAgentConfigurationLoaded(agent: BuildAgent) {
                fetchConfiguration()
            }
        })
    }

    private fun fetchConfiguration() {
        val requestConfig = RequestConfig.custom()
                .setConnectTimeout(COMPUTE_PING_CONNECTION_TIMEOUT_MS)
                .build()

        HttpClients.custom()
                .useSystemProperties()
                .setDefaultRequestConfig(requestConfig)
                .build().use { client ->
                    for (i in 1..MAX_COMPUTE_PING_TRIES) {
                        val response = try {
                            client.execute(HttpGet(instanceMetadataUrl).apply {
                                addHeader(GCE_METADATA_HEADER, "Google")
                            })
                        } catch (ignored: SocketTimeoutException) {
                            // Ignore logging timeouts which is the expected failure mode in non GCE environments.
                            continue
                        } catch (e: Exception) {
                            LOG.info(ERROR_GCE_UNAVAILABLE + "Failed to connect to $metadataServerUrl: ${e.message}")
                            return@use
                        }

                        val statusCode = response.statusLine.statusCode
                        if (statusCode != 200) {
                            LOG.info(ERROR_GCE_UNAVAILABLE + "Failed to connect to $metadataServerUrl: HTTP $statusCode")
                            return@use
                        }

                        if (response.getHeaders(GCE_METADATA_HEADER).any { it.value == "Google" }) {
                            updateConfiguration(EntityUtils.toString(response.entity))
                        } else {
                            LOG.info(ERROR_GCE_UNAVAILABLE + "Invalid $GCE_METADATA_HEADER header")
                        }
                    }
                }
    }

    private fun updateConfiguration(json: String) {
        val metadata = deserializeMetadata(json)
        if (metadata == null) {
            LOG.info("Google Compute integration is not available: Invalid instance metadata")
            LOG.debug(json)
            return
        }

        val data = CloudInstanceUserData.deserialize(metadata.attributes?.teamcityData ?: "")
        if (data == null) {
            LOG.info("Google Compute integration is not available: No TeamCity metadata")
            LOG.debug(json)
            return
        }

        LOG.info("Google Compute integration is available, will register agent \"${metadata.name}\" on server URL \"${data.serverAddress}\"")
        configuration.name = metadata.name
        configuration.serverUrl = data.serverAddress

        metadata.networkInterfaces.firstOrNull()?.let { network ->
            network.accessConfigs.firstOrNull()?.let {
                LOG.info("Setting external IP address: ${it.externalIp}")
                configuration.addAlternativeAgentAddress(it.externalIp)
            }
        }

        configuration.addConfigurationParameter(GoogleAgentProperties.INSTANCE_NAME, metadata.name)
        data.customAgentConfigurationParameters.entries.forEach {
            configuration.addConfigurationParameter(it.key, it.value)
            LOG.info("Added config parameter: ${it.key} => ${it.value}")
        }

        data.idleTimeout?.let {
            idleShutdown.setIdleTime(it)
        }
    }

    data class Metadata(
            val attributes: MetadataAttributes?,
            val name: String,
            val networkInterfaces: List<NetworkInterface>
    )

    data class MetadataAttributes(
            val teamcityData: String?
    )

    data class NetworkInterface(
            val accessConfigs: List<AccessConfig>
    )

    data class AccessConfig(
            val externalIp: String
    )

    companion object {
        private val LOG = Logger.getInstance(GoogleMetadataReader::class.java.name)
        private val GSON = Gson()

        // Note: the explicit IP address is used to avoid name server resolution issues.
        private const val DEFAULT_METADATA_SERVER_URL = "http://169.254.169.254"
        private const val METADATA_API_URL = "/computeMetadata/v1/instance/?recursive=true"
        private const val GCE_METADATA_HOST_ENV_VAR = "GCE_METADATA_HOST"
        private const val GCE_METADATA_HEADER = "Metadata-Flavor"
        private const val ERROR_GCE_UNAVAILABLE = "Google Compute integration is not available: "

        // Note: the explicit `timeout` and `tries` below is a workaround. The underlying
        // issue is that resolving an unknown host on some networks will take
        // 20-30 seconds; making this timeout short fixes the issue, but
        // could lead to false negatives in the event that we are on GCE, but
        // the metadata resolution was particularly slow. The latter case is
        // "unlikely" since the expected 4-nines time is about 0.5 seconds.
        // This allows us to limit the total ping maximum timeout to 1.5 seconds
        // for developer desktop scenarios.
        private const val MAX_COMPUTE_PING_TRIES = 3
        private const val COMPUTE_PING_CONNECTION_TIMEOUT_MS = 500

        fun deserializeMetadata(json: String) = try {
            GSON.fromJson<Metadata>(json, Metadata::class.java)
        } catch (e: Exception) {
            LOG.debug("Failed to deserialize JSON data ${e.message}", e)
            null
        }

        private val metadataServerUrl: String
            get() {
                return System.getenv(GCE_METADATA_HOST_ENV_VAR)?.let {
                    return@let "http://$it"
                } ?: DEFAULT_METADATA_SERVER_URL
            }

        private val instanceMetadataUrl: String
            get() = metadataServerUrl + METADATA_API_URL
    }
}