/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.google.connector

import com.google.cloud.compute.v1.Operation
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance

/**
 * Google API connector.
 */
interface GoogleApiConnector : CloudApiConnector<GoogleCloudImage, GoogleCloudInstance> {
    suspend fun createImageInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData): Operation

    suspend fun createTemplateInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData): Operation

    suspend fun deleteVm(instance: GoogleCloudInstance)

    suspend fun restartVm(instance: GoogleCloudInstance)

    suspend fun startVm(instance: GoogleCloudInstance)

    suspend fun stopVm(instance: GoogleCloudInstance)

    suspend fun getImages(project: String?): Map<String, String>

    suspend fun getImageFamilies(project: String?): List<String>

    suspend fun getTemplates(): Map<String, String>

    suspend fun getZones(): Map<String, List<String>>

    suspend fun getMachineTypes(zone: String): Map<String, String>

    suspend fun getNetworks(): Map<String, String>

    suspend fun getSubnets(region: String): Map<String, List<String>>

    suspend fun getDiskTypes(zone: String): Map<String, String>
}
