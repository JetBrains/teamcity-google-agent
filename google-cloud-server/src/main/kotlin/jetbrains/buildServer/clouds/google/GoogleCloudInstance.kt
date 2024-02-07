

package jetbrains.buildServer.clouds.google

import jetbrains.buildServer.clouds.base.AbstractCloudInstance
import jetbrains.buildServer.serverSide.AgentDescription

/**
 * Cloud instance.
 */
class GoogleCloudInstance internal constructor(image: GoogleCloudImage, val id: String, val zone: String)
    : AbstractCloudInstance<GoogleCloudImage>(image, id, id) {

    var properties: MutableMap<String, String> = HashMap()

    override fun containsAgent(agent: AgentDescription): Boolean {
        val agentInstanceName = agent.configurationParameters[GoogleAgentProperties.INSTANCE_NAME]
        return name.equals(agentInstanceName, ignoreCase = true)
    }
}