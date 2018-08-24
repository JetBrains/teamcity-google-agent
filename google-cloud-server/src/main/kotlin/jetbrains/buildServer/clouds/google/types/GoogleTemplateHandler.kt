package jetbrains.buildServer.clouds.google.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import jetbrains.buildServer.clouds.google.connector.GoogleApiConnector
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.async
import java.util.ArrayList

class GoogleTemplateHandler(private val connector: GoogleApiConnector) : GoogleHandler {

    override fun checkImageAsync(image: GoogleCloudImage) = async(CommonPool, CoroutineStart.LAZY) {
        val exceptions = ArrayList<Throwable>()
        exceptions
    }

    override fun createInstanceAsync(instance: GoogleCloudInstance, userData: CloudInstanceUserData) =
        connector.createTemplateInstanceAsync(instance, userData)
}