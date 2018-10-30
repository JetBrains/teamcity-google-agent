package jetbrains.buildServer.clouds.google.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.errors.CheckedCloudException
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.coroutineScope
import java.util.*

class GoogleTemplateHandler(private val connector: GoogleApiConnector) : GoogleHandler {

    override suspend fun checkImage(image: GoogleCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        val details = image.imageDetails

        if (details.instanceTemplate.isNullOrEmpty()) {
            exceptions.add(CheckedCloudException("Image template should not be empty"))
        } else {
            if (!connector.getTemplates().containsKey(details.instanceTemplate)) {
                exceptions.add(CheckedCloudException("Image template does not exist"))
            }
        }

        exceptions
    }

    override suspend fun createInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) =
        connector.createTemplateInstance(instance, userData)
}