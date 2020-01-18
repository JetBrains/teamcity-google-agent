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

package jetbrains.buildServer.clouds.google.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import java.util.*

class GoogleImageHandler(private val connector: GoogleApiConnector) : GoogleHandler {

    override suspend fun checkImage(image: GoogleCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        if (details.sourceImage.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image should not be empty"))
        } else {
            if (!connector.getImages().containsKey(details.sourceImage)) {
                exceptions.add(CheckedCloudException("Image does not exist"))
            }
        }

        if (details.machineCustom) {
            if (details.machineCores.isNullOrEmpty()) {
                exceptions.add(CheckedCloudException("Number of cores should not be empty"))
            }
            if (details.machineMemory.isNullOrEmpty()) {
                exceptions.add(CheckedCloudException("Machine memory should not be empty"))
            }
        } else {
            if (details.machineType.isNullOrEmpty()) {
                exceptions.add(CheckedCloudException("Machine type should not be empty"))
            }
        }

        if (details.network.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Network should not be empty"))
        }

        exceptions
    }

    override suspend fun createInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) =
        connector.createImageInstance(instance, userData)
}