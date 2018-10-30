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