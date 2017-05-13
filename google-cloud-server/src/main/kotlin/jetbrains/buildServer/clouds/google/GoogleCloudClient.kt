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

import jetbrains.buildServer.clouds.CloudClientParameters
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector

/**
 * Google cloud client.
 */
class GoogleCloudClient(params: CloudClientParameters,
                        apiConnector: CloudApiConnector<GoogleCloudImage, GoogleCloudInstance>,
                        imagesHolder: GoogleCloudImagesHolder)
    : GoogleCloudClientBase<GoogleCloudInstance, GoogleCloudImage, GoogleCloudImageDetails>(params, apiConnector, imagesHolder) {

    override fun createImage(imageDetails: GoogleCloudImageDetails): GoogleCloudImage {
        return GoogleCloudImage(imageDetails, myApiConnector as GoogleApiConnector)
    }
}
