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

package jetbrains.buildServer.clouds.google

import jetbrains.buildServer.clouds.base.AbstractCloudInstance
import jetbrains.buildServer.serverSide.AgentDescription

/**
 * Cloud instance.
 */
class GoogleCloudInstance internal constructor(image: GoogleCloudImage, val id: String, val zone: String)
    : AbstractCloudInstance<GoogleCloudImage>(image, id, id) {

    override fun containsAgent(agent: AgentDescription): Boolean {
        val agentInstanceName = agent.configurationParameters[GoogleAgentProperties.INSTANCE_NAME]
        return name.equals(agentInstanceName, ignoreCase = true)
    }
}
