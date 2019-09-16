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

import jetbrains.buildServer.agent.Constants
import jetbrains.buildServer.clouds.CloudImageParameters

/**
 * Google cloud constants.
 */
class GoogleConstants {

    val credentialsType: String
        get() = CREDENTIALS_TYPE

    val credentialsEnvironment: String
        get() = CREDENTIALS_ENVIRONMENT

    val credentialsKey: String
        get() = CREDENTIALS_KEY

    val accessKey: String
        get() = ACCESS_KEY

    val imageType: String
        get() = IMAGE_TYPE

    val sourceImage: String
        get() = SOURCE_IMAGE

    val instanceTemplate: String
        get() = INSTANCE_TEMPLATE

    val zone: String
        get() = ZONE

    val minInstancesCount: String
        get() = MIN_INSTANCES_COUNT

    val maxInstancesCount: String
        get() = MAX_INSTANCES_COUNT

    val network: String
        get() = NETWORK_ID

    val subnet: String
        get() = SUBNET_ID

    val machineCustom: String
        get() = MACHINE_CUSTOM

    val machineType: String
        get() = MACHINE_TYPE

    val machineCores: String
        get() = MACHINE_CORES

    val machineMemory: String
        get() = MACHINE_MEMORY

    val machineMemoryExt: String
        get() = MACHINE_MEMORY_EXT

    val diskType: String
        get() = DISK_TYPE

    val vmNamePrefix: String
        get() = CloudImageParameters.SOURCE_ID_FIELD

    val imagesData: String
        get() = CloudImageParameters.SOURCE_IMAGES_JSON

    val agentPoolId: String
        get() = CloudImageParameters.AGENT_POOL_ID_FIELD

    val preemptible: String
        get() = PREEMPTIBLE

    val metadata: String
        get() = METADATA

    val growingId: String
        get() = GROWING_ID

    val serviceAccount: String
        get() = SERVICE_ACCOUNT

    val scopes: String
        get() = SCOPES

    companion object {
        const val CREDENTIALS_TYPE = "credentialsType"
        const val CREDENTIALS_ENVIRONMENT = "environment"
        const val CREDENTIALS_KEY = "key"
        const val ACCESS_KEY = Constants.SECURE_PROPERTY_PREFIX + "accessKey"
        const val IMAGE_TYPE = "imageType"
        const val SOURCE_IMAGE = "sourceImage"
        const val INSTANCE_TEMPLATE = "instanceTemplate"
        const val ZONE = "zone"
        const val REGION = "region"
        const val NETWORK_ID = "network"
        const val SUBNET_ID = "subnet"
        const val MIN_INSTANCES_COUNT = "minInstances"
        const val MAX_INSTANCES_COUNT = "maxInstances"
        const val MACHINE_CUSTOM = "machineCustom"
        const val MACHINE_TYPE = "machineType"
        const val MACHINE_CORES = "machineCores"
        const val MACHINE_MEMORY = "machineMemory"
        const val MACHINE_MEMORY_EXT = "machineMemoryExt"
        const val DISK_TYPE = "diskType"
        const val TAG_SERVER = "teamcityServer"
        const val TAG_DATA = "teamcityData"
        const val TAG_PROFILE = "teamcityProfile"
        const val TAG_SOURCE = "teamcitySource"
        const val PROFILE_ID = "profileId"
        const val PREEMPTIBLE = "preemptible"
        const val METADATA = "metadata"
        const val GROWING_ID = "growingId"
        const val SERVICE_ACCOUNT = "serviceAccount"
        const val SCOPES = "scopes"
    }
}
