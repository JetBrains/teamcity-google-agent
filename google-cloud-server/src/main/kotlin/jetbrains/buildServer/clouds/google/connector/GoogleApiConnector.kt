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

package jetbrains.buildServer.clouds.google.connector

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import kotlinx.coroutines.experimental.Deferred

/**
 * Google API connector.
 */
interface GoogleApiConnector : CloudApiConnector<GoogleCloudImage, GoogleCloudInstance> {
    fun createVmAsync(instance: GoogleCloudInstance, userData: CloudInstanceUserData): Deferred<*>

    fun deleteVmAsync(instance: GoogleCloudInstance): Deferred<*>

    fun restartVmAsync(instance: GoogleCloudInstance): Deferred<*>

    fun startVmAsync(instance: GoogleCloudInstance): Deferred<*>

    fun stopVmAsync(instance: GoogleCloudInstance): Deferred<*>

    fun getImagesAsync(): Deferred<Map<String, String>>

    fun getZonesAsync(): Deferred<Map<String, String>>

    fun getMachineTypesAsync(): Deferred<Map<String, String>>

    fun getNetworksAsync(): Deferred<Map<String, String>>
}
