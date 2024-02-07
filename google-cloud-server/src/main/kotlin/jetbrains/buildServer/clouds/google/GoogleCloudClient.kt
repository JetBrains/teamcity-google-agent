

package jetbrains.buildServer.clouds.google

import jetbrains.buildServer.clouds.CloudClientParameters
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import jetbrains.buildServer.clouds.google.utils.FileIdProvider
import java.io.File

/**
 * Google cloud client.
 */
class GoogleCloudClient(params: CloudClientParameters,
                        apiConnector: CloudApiConnector<GoogleCloudImage, GoogleCloudInstance>,
                        imagesHolder: GoogleCloudImagesHolder,
                        private val googleIdxStorage: File)
    : GoogleCloudClientBase<GoogleCloudInstance, GoogleCloudImage, GoogleCloudImageDetails>(params, apiConnector, imagesHolder) {

    override fun createImage(imageDetails: GoogleCloudImageDetails): GoogleCloudImage {
        val idProvider = FileIdProvider(File(googleIdxStorage, imageDetails.sourceId + ".idx"))
        return GoogleCloudImage(imageDetails, myApiConnector as GoogleApiConnector, idProvider)
    }

    override fun dispose() {
        super.dispose()
        images.forEach { image ->
            image.dispose()
        }
    }
}