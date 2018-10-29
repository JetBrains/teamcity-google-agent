package jetbrains.buildServer.clouds.google.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.coroutineScope
import java.util.*

class GoogleImageHandler(private val connector: GoogleApiConnector) : GoogleHandler {

    override suspend fun checkImage(image: GoogleCloudImage) = coroutineScope {
        val exceptions = ArrayList<Throwable>()
        exceptions
    }

    override suspend fun createInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) =
        connector.createImageInstance(instance, userData)
}