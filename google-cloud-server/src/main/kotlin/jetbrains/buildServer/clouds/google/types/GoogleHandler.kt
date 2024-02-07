

package jetbrains.buildServer.clouds.google.types

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance

interface GoogleHandler {
    suspend fun checkImage(image: GoogleCloudImage): List<Throwable>
    suspend fun createInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData)
}