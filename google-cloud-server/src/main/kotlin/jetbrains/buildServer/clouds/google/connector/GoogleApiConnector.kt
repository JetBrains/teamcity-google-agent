

package jetbrains.buildServer.clouds.google.connector

import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.CloudApiConnector
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudInstance

/**
 * Google API connector.
 */
interface GoogleApiConnector : CloudApiConnector<GoogleCloudImage, GoogleCloudInstance> {
    suspend fun createImageInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData)

    suspend fun createTemplateInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData)

    suspend fun deleteVm(instance: GoogleCloudInstance)

    suspend fun restartVm(instance: GoogleCloudInstance)

    suspend fun startVm(instance: GoogleCloudInstance)

    suspend fun stopVm(instance: GoogleCloudInstance)

    suspend fun getImages(project: String?): Map<String, String>

    suspend fun getImageFamilies(project: String?): List<String>

    suspend fun getTemplates(): Map<String, String>

    suspend fun getZones(): Map<String, List<String>>

    suspend fun getMachineTypes(zone: String): Map<String, String>

    suspend fun getNetworks(): Map<String, String>

    suspend fun getSubnets(region: String): Map<String, List<String>>

    suspend fun getDiskTypes(zone: String): Map<String, String>
}