/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package jetbrains.buildServer.clouds.google.connector

import com.google.api.client.googleapis.util.Utils
import com.google.api.client.json.GenericJson
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.ServiceOptions
import com.google.cloud.compute.v1.*
import com.google.cloud.resourcemanager.ResourceManagerOptions
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.google.GoogleCloudImage
import jetbrains.buildServer.clouds.google.GoogleCloudImageType
import jetbrains.buildServer.clouds.google.GoogleCloudInstance
import jetbrains.buildServer.clouds.google.GoogleConstants
import jetbrains.buildServer.clouds.google.utils.AlphaNumericStringComparator
import jetbrains.buildServer.util.StringUtil
import kotlinx.coroutines.*

class GoogleApiConnectorImpl : GoogleApiConnector {

    private val comparator = AlphaNumericStringComparator()
    private var myServerId: String? = null
    private var myProfileId: String? = null
    private val myCredentials: GoogleCredentials
    private var myProjectId: String? = null
    private var myAccessKey: String? = null

    constructor(accessKey: String) {
        myAccessKey = accessKey
        myCredentials = accessKey.trim().byteInputStream().use {
            val factory = Utils.getDefaultJsonFactory()
            val parser = factory.createJsonParser(it)
            val json = parser.parse(GenericJson::class.java)
            (json["project_id"] as String?)?.let { projectId ->
                myProjectId = projectId
            }

            it.reset()
            GoogleCredentials.fromStream(it)
        }
    }

    constructor() {
        myCredentials = GoogleCredentials.getApplicationDefault()
        myProjectId = ServiceOptions.getDefaultProjectId()
    }

    override fun test() {
        val resourceManager = ResourceManagerOptions.newBuilder()
                .setCredentials(myCredentials)
                .build()
                .service

        val missingPermissions = mutableListOf<String>()
        resourceManager.testPermissions(myProjectId, REQUIRED_PERMISSIONS).forEachIndexed { i, exists ->
            if (!exists) missingPermissions.add(REQUIRED_PERMISSIONS[i])
        }

        if (missingPermissions.isNotEmpty()) {
            throw CloudException(missingPermissions.joinToString(", ", "Missing required permissions: "))
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun createImageInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val details = instance.image.imageDetails
        val zone = details.zone

        val machineType = if (details.machineCustom) {
            "custom-${details.machineCores}-${details.machineMemory}${if (details.machineMemoryExt) "-ext" else ""}"
        } else details.machineType!!

        val metadata = mutableMapOf(
                GoogleConstants.TAG_SERVER to myServerId,
                GoogleConstants.TAG_DATA to userData.serialize(),
                GoogleConstants.TAG_PROFILE to myProfileId,
                GoogleConstants.TAG_SOURCE to details.sourceId
        ).apply {
            details.metadata?.let {
                if (it.isBlank()) {
                    return@let
                }
                val factory = Utils.getDefaultJsonFactory()
                val parser = factory.createJsonParser(it)
                val json = try {
                    parser.parse(GenericJson::class.java)
                } catch (e: Exception) {
                    LOG.warn("Invalid JSON metadata $it", e)
                    return@let
                }
                json.forEach { key, value ->
                    if (value is String) {
                        this[key] = value
                    } else {
                        LOG.warn("Invalid value for metadata key $key")
                    }
                }
            }
        }


        val instanceBootImageProject = if (details.sourceProject.isNullOrBlank()) myProjectId else details.sourceProject

        @Suppress("IMPLICIT_CAST_TO_ANY")
        val instanceBootImage = when(details.imageType) {
            GoogleCloudImageType.Image -> ProjectGlobalImageName.format(details.sourceImage, instanceBootImageProject)
            GoogleCloudImageType.ImageFamily -> ProjectGlobalImageFamilyName.format(details.sourceImageFamily, instanceBootImageProject)
            else -> LOG.warn("Invalid imageType: ${details.imageType}")
        }

        LOG.info("Creating instance from ${details.imageType}, using source: $instanceBootImage")

        val networkId = details.network ?: "default"
        val network = findNetwork(networkId)
        val networkName = network?.selfLink ?: ProjectGlobalNetworkName.format(networkId, myProjectId)

        val networkInterface = NetworkInterface.newBuilder()
            .setName(networkName)
            .apply {
                if (!details.subnet.isNullOrBlank()) {
                    val region = zone.substring(0, zone.length - 2)
                    subnetwork = network?.subnetworksList?.find{ it.endsWith(details.subnet)} ?:
                         ProjectRegionSubnetworkName.format(myProjectId, region, details.subnet)
                }
                if (details.externalIP) {
                    addAccessConfigs(
                        AccessConfig.newBuilder()
                            .setName("external-nat")
                            .setType("ONE_TO_ONE_NAT")
                            .build()
                    )
                }
            }
            .build()

        val instanceInfo = getInstanceBuilder(instance)
                .setMachineType(ProjectZoneMachineTypeName.format(machineType, myProjectId, zone))
                .addDisks(AttachedDisk.newBuilder()
                        .setInitializeParams(AttachedDiskInitializeParams.newBuilder()
                                .setSourceImage(instanceBootImage as String?)
                                .apply {
                                    if (!details.diskType.isNullOrBlank()) {
                                        diskType = ProjectZoneDiskTypeName.format(details.diskType, myProjectId, zone)
                                    }
                                }
                                .apply {
                                    if (!details.diskSizeGb.isNullOrBlank()) {
                                        diskSizeGb = details.diskSizeGb
                                    }
                                }
                                .build())
                        .setBoot(true)
                        .setAutoDelete(true)
                        .setType("PERSISTENT")
                        .build())
                .addNetworkInterfaces(networkInterface)
                .setMetadata(getMetadata(metadata))
                .apply {
                    if (!details.serviceAccount.isNullOrBlank()) {
                        val scopes = StringUtil.split(details.scopes
                                ?: "https://www.googleapis.com/auth/cloud-platform")
                        addServiceAccounts(ServiceAccount.newBuilder()
                                .setEmail(details.serviceAccount)
                                .addAllScopes(scopes)
                                .build())
                    }
                }
                .build()

        instanceClient.insertInstanceCallable().futureCall(InsertInstanceHttpRequest.newBuilder()
                .setZone(ProjectZoneName.format(myProjectId, zone))
                .setInstanceResource(instanceInfo)
                .build())
                .await()
        Unit
    }

    override suspend fun createTemplateInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val details = instance.image.imageDetails
        val zone = details.zone

        LOG.info("Fetching Google Instance Template")
        val instanceTemplateBuilder = getInstanceTemplate(instance).toBuilder()
        LOG.info("Google Instance Template properties captured from GCP")
        val instanceTemplateMetadataBuilder = instanceTemplateBuilder
                .getProperties()
                .toBuilder()
                .getMetadata()
                .toBuilder()
                .addAllItems(getMetadata(mutableMapOf(
                        GoogleConstants.TAG_SERVER to myServerId,
                        GoogleConstants.TAG_DATA to userData.serialize(),
                        GoogleConstants.TAG_PROFILE to myProfileId,
                        GoogleConstants.TAG_SOURCE to details.sourceId
                ))?.getItemsList())

        LOG.info("Creating an instance builder from merged metadata")
        val instanceInfo = getInstanceBuilder(instance, true)
                .setMetadata(instanceTemplateMetadataBuilder.build())
                .build()

        LOG.info("Creating instance from Instance Template: ${instanceTemplateBuilder.getName()}")
        instanceClient.insertInstanceCallable().futureCall(InsertInstanceHttpRequest.newBuilder()
                .setSourceInstanceTemplate(ProjectGlobalInstanceTemplateName.format(details.instanceTemplate, myProjectId))
                .setZone(ProjectZoneName.format(myProjectId, zone))
                .setInstanceResource(instanceInfo)
                .build())
                .await()

        Unit
    }

    private fun getInstanceBuilder(instance: GoogleCloudInstance, fromTemplate: Boolean): Instance.Builder {

        if (fromTemplate)
            return Instance.newBuilder()
                    .setName(instance.instanceId)

        return getInstanceBuilder(instance)
    }

    private fun getInstanceBuilder(instance: GoogleCloudInstance): Instance.Builder {
        return Instance.newBuilder()
                .setName(instance.instanceId)
                .setScheduling(Scheduling.newBuilder()
                        .setAutomaticRestart(false)
                        .setOnHostMaintenance("TERMINATE")
                        .setPreemptible(instance.image.imageDetails.preemptible)
                        .build())
    }

    private fun getInstanceTemplate(instance: GoogleCloudInstance): InstanceTemplate {
        LOG.info("getInstanceTemplate template name: ${instance.image.imageDetails.instanceTemplate}")
        LOG.info("getInstanceTemplate GCP project ID: $myProjectId")
        val instanceTemplateName = ProjectGlobalInstanceTemplateName.format(
                instance.image.imageDetails.instanceTemplate,
                myProjectId
        )

        LOG.info("GCP get Instance Template: $instanceTemplateName")
        return instanceTemplateClient.getInstanceTemplate(instanceTemplateName.toString())
    }

    private fun getMetadata(metadata: Map<String, String?>): Metadata? {
        return Metadata.newBuilder()
                .addAllItems(metadata.map {
                    Items.newBuilder()
                            .setKey(it.key)
                            .setValue(it.value)
                            .build()
                }).build()
    }

    override suspend fun startVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.startInstanceCallable()
                .futureCall(StartInstanceHttpRequest.newBuilder()
                        .setInstance(getInstance(instance))
                        .build())
                .await()
        Unit
    }

    override suspend fun restartVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.resetInstanceCallable()
                .futureCall(ResetInstanceHttpRequest.newBuilder()
                        .setInstance(getInstance(instance))
                        .build())
                .await()
        Unit
    }

    override suspend fun stopVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.stopInstanceCallable()
                .futureCall(StopInstanceHttpRequest.newBuilder()
                        .setInstance(getInstance(instance))
                        .build())
                .await()
        Unit
    }

    override suspend fun deleteVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.deleteInstanceCallable()
                .futureCall(DeleteInstanceHttpRequest.newBuilder()
                        .setInstance(getInstance(instance))
                        .build())
                .await()
        Unit
    }

    private fun getInstance(instance: GoogleCloudInstance): String {
        return ProjectZoneInstanceName.format(instance.id, myProjectId, instance.zone)
    }

    override fun checkImage(image: GoogleCloudImage) = runBlocking {
        val errors = image.handler.checkImage(image)
        errors.map { TypedCloudErrorInfo.fromException(it) }.toTypedArray()
    }

    override fun checkInstance(instance: GoogleCloudInstance): Array<TypedCloudErrorInfo> = emptyArray()

    override suspend fun getImages(project: String?) = coroutineScope {
        val projectName = if (project.isNullOrBlank()) myProjectId else project
        val images = imageClient.listImagesPagedCallable()
                .futureCall(ListImagesHttpRequest.newBuilder()
                        .setProject(ProjectName.format(projectName))
                        .build())
                .await()

        images.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override suspend fun getImageFamilies(project: String?) = coroutineScope {
        val projectName = if (project.isNullOrBlank()) myProjectId else project
        val images = imageClient.listImagesPagedCallable()
                .futureCall(ListImagesHttpRequest.newBuilder()
                        .setProject(ProjectName.format(projectName))
                        .build())
                .await()

        images.iterateAll()
                .filter { it.family != null }
                .map { it.family }
                .distinct()
                .sorted()
    }

    override suspend fun getTemplates() = coroutineScope {
        val templates = instanceTemplateClient.listInstanceTemplatesPagedCallable()
                .futureCall(ListInstanceTemplatesHttpRequest.newBuilder()
                        .setProject(ProjectName.format(myProjectId))
                        .build())
                .await()

        templates.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override suspend fun getZones() = coroutineScope {
        val zones = zoneClient.listZonesPagedCallable()
                .futureCall(ListZonesHttpRequest.newBuilder()
                        .setProject(ProjectName.format(myProjectId))
                        .build())
                .await()

        zones.iterateAll()
                .map { zone ->
                    val region = ProjectRegionName.parse(zone.region).region
                    zone.name to listOf(formattedName(zone.name, zone.description), region)
                }
                .sortedWith(compareBy(comparator) { it.second.first() })
                .associate { it.first to it.second }
    }

    override suspend fun getMachineTypes(zone: String) = coroutineScope {
        val machineTypes = machineTypeClient.listMachineTypesPagedCallable()
                .futureCall(ListMachineTypesHttpRequest.newBuilder()
                        .setZone(ProjectZoneName.format(myProjectId, zone))
                        .build())
                .await()

        machineTypes.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    private suspend fun <T> withVpcProjects(block: suspend(String) -> List<T>): List<T> {
        val items = mutableListOf<T>()
        myProjectId?.let { items.addAll(block(it)) }

        getVpcHostProjects().forEach { items.addAll(block(it.name)) }
        return items
    }

    private suspend fun getVpcHostProjects(): List<Project> {
        return projectClient.listXpnHostsProjectsCallable()
            .futureCall(
                ListXpnHostsProjectsHttpRequest.newBuilder()
                    .setProject(ProjectName.format(myProjectId))
                    .build()
            ).await().itemsList ?: listOf()
    }

    override suspend fun getNetworks() = coroutineScope {
        withVpcProjects { getNetworksForProject(it) }
            .map { it.name to formattedName(it.name, it.description) }
            .sortedWith(compareBy(comparator) { it.second })
            .associate { it.first to it.second }
    }

    private suspend fun getNetworksForProject(project: String): List<Network> {
        val networks = networkClient.listNetworksPagedCallable()
            .futureCall(
                ListNetworksHttpRequest.newBuilder()
                    .setProject(ProjectName.format(project))
                    .build()
            )
            .await()

        return networks.iterateAll().toList()
    }

    private suspend fun findNetwork(name: String): Network? = withVpcProjects { getNetworksForProject(it) }.find { it.name == name }

    override suspend fun getSubnets(region: String) = coroutineScope {
        withVpcProjects { getSubnetsForProject(it, region) }
            .map { subNetwork ->
                val network = ProjectGlobalNetworkName.parse(subNetwork.network).network
                subNetwork.name to listOf(formattedName(subNetwork.name, subNetwork.description), network)
            }
            .sortedWith(compareBy(comparator) { it.second.first() })
            .associate { it.first to it.second }
    }

    private suspend fun getSubnetsForProject(project: String?, region: String): List<Subnetwork> {
        val subNetworks = subNetworkClient.listSubnetworksPagedCallable()
            .futureCall(
                ListSubnetworksHttpRequest.newBuilder()
                    .setRegion(ProjectRegionName.format(project, region))
                    .build()
            ).await()

        return subNetworks.iterateAll().toList()
    }

    override suspend fun getDiskTypes(zone: String) = coroutineScope {
        val diskTypes = diskTypeClient.listDiskTypesPagedCallable()
                .futureCall(ListDiskTypesHttpRequest.newBuilder()
                        .setZone(ProjectZoneName.format(myProjectId, zone))
                        .build())
                .await()

        diskTypes.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override fun <R : AbstractInstance?> fetchInstances(image: GoogleCloudImage): MutableMap<String, R> {
        val instances = fetchInstances<R>(arrayListOf(image))
        return instances[image] as MutableMap<String, R>
    }

    override fun <R : AbstractInstance?> fetchInstances(images: MutableCollection<GoogleCloudImage>)
            : MutableMap<GoogleCloudImage, MutableMap<String, R>> {
        val map = mutableMapOf<String, MutableList<Instance>>()
        instanceClient.aggregatedListInstances(ProjectName.of(myProjectId))
                .iterateAll()
                .flatMap { it.instancesList ?: emptyList() }
                .forEach {
                    val metadata = it.metadata.itemsList?.associateBy(
                            { items -> items.key },
                            { items -> items.value })
                            ?: emptyMap()

                    if (metadata[GoogleConstants.TAG_SERVER] != myServerId) return@forEach
                    if (metadata[GoogleConstants.TAG_PROFILE] != myProfileId) return@forEach
                    if (metadata[GoogleConstants.TAG_DATA].isNullOrEmpty()) return@forEach
                    metadata[GoogleConstants.TAG_SOURCE]?.let { sourceId ->
                        val list = map.getOrPut(sourceId) { mutableListOf() }
                        list.add(it)
                    }
                }

        val result = hashMapOf<GoogleCloudImage, MutableMap<String, R>>()

        for (image in images) {
            val instances = hashMapOf<String, R>()
            map[image.imageDetails.sourceId]?.let { foundInstances ->
                foundInstances.forEach {
                    val name = it.name
                    val zone = ProjectZoneName.parse(it.zone).zone
                    @Suppress("UNCHECKED_CAST")
                    instances[name] = GoogleInstance(it, zone) as R

                    if ("TERMINATED" == it.status) {
                        GlobalScope.launch(image.coroutineContext) {
                            try {
                                LOG.info("Removing terminated instance $name")
                                deleteVm(GoogleCloudInstance(image, it.name, zone))
                            } catch (e: Exception) {
                                LOG.infoAndDebugDetails("Failed to remove instance $name", e)
                            }
                        }
                    }
                }
            }
            result[image] = instances
        }

        return result
    }

    fun setServerId(serverId: String?) {
        myServerId = serverId
    }

    fun setProfileId(profileId: String) {
        myProfileId = profileId
    }

    private val projectClient: ProjectClient by lazy {
        ProjectClient.create(ProjectSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { ProjectSettings.getDefaultServiceScopes() })
                .build())
    }

    private val instanceClient: InstanceClient by lazy {
        InstanceClient.create(InstanceSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { InstanceSettings.getDefaultServiceScopes() })
                .build())
    }

    private val imageClient: ImageClient by lazy {
        ImageClient.create(ImageSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { ImageSettings.getDefaultServiceScopes() })
                .build())
    }

    private val zoneClient: ZoneClient by lazy {
        ZoneClient.create(ZoneSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { ZoneSettings.getDefaultServiceScopes() })
                .build())
    }

    private val machineTypeClient: MachineTypeClient by lazy {
        MachineTypeClient.create(MachineTypeSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { MachineTypeSettings.getDefaultServiceScopes() })
                .build())
    }

    private val networkClient: NetworkClient by lazy {
        NetworkClient.create(NetworkSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { NetworkSettings.getDefaultServiceScopes() })
                .build())
    }

    private val subNetworkClient: SubnetworkClient by lazy {
        SubnetworkClient.create(SubnetworkSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { SubnetworkSettings.getDefaultServiceScopes() })
                .build())
    }

    private val diskTypeClient: DiskTypeClient by lazy {
        DiskTypeClient.create(DiskTypeSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { DiskTypeSettings.getDefaultServiceScopes() })
                .build())
    }

    private val instanceTemplateClient: InstanceTemplateClient by lazy {
        InstanceTemplateClient.create(InstanceTemplateSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { InstanceTemplateSettings.getDefaultServiceScopes() })
                .build())
    }

    private fun getCredentialsProvider(getScopes: () -> List<String>): CredentialsProvider {
        val credentials = if (myCredentials.createScopedRequired()) {
            myCredentials.createScoped(getScopes())
        } else {
            myCredentials
        }
        return FixedCredentialsProvider.create(credentials)
    }

    companion object {
        private val LOG = Logger.getInstance(GoogleApiConnectorImpl::class.java.name)

        val REQUIRED_PERMISSIONS = listOf(
                "compute.images.list",
                "compute.instances.create",
                "compute.instances.list",
                "compute.instances.setMetadata",
                "compute.machineTypes.list",
                "compute.diskTypes.list",
                "compute.networks.list",
                "compute.subnetworks.list",
                "compute.zones.list")

        private fun formattedName(name: String, description: String?): String {
            return if (description.isNullOrBlank())
                name
            else
                String.format("%s (%s)", name, description)
        }
    }
}