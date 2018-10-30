package jetbrains.buildServer.clouds.google.connector

import com.google.api.client.googleapis.util.Utils
import com.google.api.client.json.GenericJson
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.ClientSettings
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

    override suspend fun createImageInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val details = instance.image.imageDetails
        val zone = details.zone
        val network = details.network ?: "default"
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

        val instanceInfo = getInstanceBuilder(instance)
                .setMachineType(ProjectZoneMachineTypeName.of(machineType, myProjectId, zone).toString())
                .addDisks(AttachedDisk.newBuilder()
                        .setInitializeParams(AttachedDiskInitializeParams.newBuilder()
                                .setSourceImage(ProjectGlobalImageName.of(details.sourceImage, myProjectId).toString())
                                .build())
                        .setBoot(true)
                        .setAutoDelete(true)
                        .setType("PERSISTENT")
                        .apply {
                            if (!details.diskType.isNullOrBlank()) {
                                type = details.diskType
                            }
                        }
                        .build())
                .addNetworkInterfaces(NetworkInterface.newBuilder()
                        .setName(ProjectGlobalNetworkName.of(network, myProjectId).toString())
                        .apply {
                            if (!details.subnet.isNullOrBlank()) {
                                subnetwork = details.subnet
                            }
                            addAccessConfigs(AccessConfig.newBuilder()
                                    .setName("external-nat")
                                    .setType("ONE_TO_ONE_NAT")
                                    .build())
                        }
                        .build())
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
                .setZone(ProjectZoneName.of(myProjectId, zone).toString())
                .setInstanceResource(instanceInfo)
                .build())
                .await()
        Unit
    }

    override suspend fun createTemplateInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData) = coroutineScope {
        val details = instance.image.imageDetails
        val zone = details.zone

        val instanceInfo = getInstanceBuilder(instance)
                .setMetadata(getMetadata(mutableMapOf(
                        GoogleConstants.TAG_SERVER to myServerId,
                        GoogleConstants.TAG_DATA to userData.serialize(),
                        GoogleConstants.TAG_PROFILE to myProfileId,
                        GoogleConstants.TAG_SOURCE to details.sourceId
                )))
                .build()

        instanceClient.insertInstanceCallable().futureCall(InsertInstanceHttpRequest.newBuilder()
                .setSourceInstanceTemplate(ProjectGlobalInstanceTemplateName.of(details.instanceTemplate, myProjectId).toString())
                .setZone(ProjectZoneName.of(myProjectId, zone).toString())
                .setInstanceResource(instanceInfo)
                .build())
                .await()

        Unit
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
        return ProjectZoneInstanceName.of(instance.id, myProjectId, instance.zone).toString()
    }

    override fun checkImage(image: GoogleCloudImage) = runBlocking {
        val errors = image.handler.checkImage(image)
        errors.map { TypedCloudErrorInfo.fromException(it) }.toTypedArray()
    }

    override fun checkInstance(instance: GoogleCloudInstance): Array<TypedCloudErrorInfo> = emptyArray()

    override suspend fun getImages() = coroutineScope {
        val images = imageClient.listImagesPagedCallable()
                .futureCall(ListImagesHttpRequest.newBuilder()
                        .setProject(ProjectName.of(myProjectId).toString())
                        .build())
                .await()

        images.iterateAll()
                .map { it.name to nonEmpty(it.description, it.name) }
                .sortedWith(compareBy(comparator) { it -> it.second })
                .associate { it -> it.first to it.second }
    }

    override suspend fun getTemplates() = coroutineScope {
        val templates = instanceTemplateClient.listInstanceTemplatesPagedCallable()
                .futureCall(ListInstanceTemplatesHttpRequest.newBuilder()
                        .setProject(ProjectName.of(myProjectId).toString())
                        .build())
                .await()

        templates.iterateAll()
                .map { it.name to nonEmpty(it.description, it.name) }
                .sortedWith(compareBy(comparator) { it -> it.second })
                .associate { it -> it.first to it.second }
    }

    override suspend fun getZones() = coroutineScope {
        val zones = zoneClient.listZonesPagedCallable()
                .futureCall(ListZonesHttpRequest.newBuilder()
                        .setProject(ProjectName.of(myProjectId).toString())
                        .build())
                .await()

        zones.iterateAll()
                .map { zone ->
                    val regionId = getResourceId(zone.region, zoneClient.settings)
                    val region = ProjectRegionName.parse(regionId).region
                    zone.name to listOf(nonEmpty(zone.description, zone.name), region)
                }
                .sortedWith(compareBy(comparator) { it -> it.second.first() })
                .associate { it -> it.first to it.second }
    }

    override suspend fun getMachineTypes(zone: String) = coroutineScope {
        val machineTypes = machineTypeClient.listMachineTypesPagedCallable()
                .futureCall(ListMachineTypesHttpRequest.newBuilder()
                        .setZone(ProjectZoneName.of(myProjectId, zone).toString())
                        .build())
                .await()

        machineTypes.iterateAll()
                .map { it -> it.name to nonEmpty(it.description, it.name) }
                .sortedWith(compareBy(comparator) { it -> it.second })
                .associate { it -> it.first to it.second }
    }

    override suspend fun getNetworks() = coroutineScope {
        val networks = networkClient.listNetworksPagedCallable()
                .futureCall(ListNetworksHttpRequest.newBuilder()
                        .setProject(ProjectName.of(myProjectId).toString())
                        .build())
                .await()

        networks.iterateAll()
                .map { it -> it.name to nonEmpty(it.description, it.name) }
                .sortedWith(compareBy(comparator) { it -> it.second })
                .associate { it -> it.first to it.second }
    }

    override suspend fun getSubnets(region: String) = coroutineScope {
        val subNetworks = subNetworkClient.listSubnetworksPagedCallable()
                .futureCall(ListSubnetworksHttpRequest.newBuilder()
                        .setRegion(ProjectRegionName.of(myProjectId, region).toString())
                        .build())
                .await()

        subNetworks.iterateAll()
                .map { subNetwork ->
                    val networkId = getResourceId(subNetwork.network, networkClient.settings)
                    val network = ProjectGlobalNetworkName.parse(networkId).network
                    subNetwork.name to listOf(nonEmpty(subNetwork.description, subNetwork.name), network)
                }
                .sortedWith(compareBy(comparator) { it -> it.second.first() })
                .associate { it -> it.first to it.second }
    }

    override suspend fun getDiskTypes(zone: String) = coroutineScope {
        val diskTypes = diskTypeClient.listDiskTypesPagedCallable()
                .futureCall(ListDiskTypesHttpRequest.newBuilder()
                        .setZone(ProjectZoneName.of(myProjectId, zone).toString())
                        .build())
                .await()

        diskTypes.iterateAll()
                .map { it -> it.name to nonEmpty(it.description, it.name) }
                .sortedWith(compareBy(comparator) { it -> it.second })
                .associate { it -> it.first to it.second }
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
                    val zoneId = getResourceId(it.zone, instanceClient.settings)
                    val zone = ProjectZoneName.parse(zoneId).zone
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

    private fun <T : ClientSettings<T>> getResourceId(value: String, settings: T): String {
        return "projects/${value.removePrefix(settings.endpoint)}"
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

        private fun nonEmpty(string: String?, defaultValue: String): String {
            string?.let {
                if (it.isNotBlank()) return it
            }
            return defaultValue
        }
    }
}