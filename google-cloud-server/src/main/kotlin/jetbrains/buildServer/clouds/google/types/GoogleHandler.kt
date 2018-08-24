package jetbrains.buildServer.clouds.google.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import kotlinx.coroutines.experimental.Deferred

interface GoogleHandler {
    fun checkImageAsync(image: GoogleCloudImage): Deferred<List<Throwable>>
    fun createInstanceAsync(instance: GoogleCloudInstance, userData: CloudInstanceUserData): Deferred<*>
}
