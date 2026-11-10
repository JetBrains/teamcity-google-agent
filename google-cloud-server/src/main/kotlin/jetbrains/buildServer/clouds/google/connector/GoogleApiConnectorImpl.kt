

package jetbrains.buildServer.clouds.google.connector

import com.google.api.client.googleapis.util.Utils
import com.google.api.client.json.GenericJson
import com.google.api.gax.core.CredentialsProvider
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.api.gax.rpc.PermissionDeniedException
import com.google.api.gax.rpc.StatusCode
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.ServiceOptions
import com.google.cloud.compute.v1.*
import com.google.cloud.resourcemanager.ResourceManagerOptions
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.clouds.CloudException
import jetbrains.buildServer.clouds.CloudInstanceUserData
import jetbrains.buildServer.clouds.base.connector.AbstractInstance
import jetbrains.buildServer.clouds.base.errors.TypedCloudErrorInfo
import jetbrains.buildServer.clouds.google.*
import jetbrains.buildServer.clouds.google.utils.AlphaNumericStringComparator
import jetbrains.buildServer.util.StringUtil
import kotlinx.coroutines.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

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

    override suspend fun createImageInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData): Operation = coroutineScope {
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
            GoogleCloudImageType.Image -> "projects/$instanceBootImageProject/global/images/${details.sourceImage}"
            GoogleCloudImageType.ImageFamily -> "projects/$instanceBootImageProject/global/imageFamilies/${details.sourceImageFamily}"
            else -> LOG.warn("Invalid imageType: ${details.imageType}")
        }

        LOG.info("Creating instance from ${details.imageType}, using source: $instanceBootImage")

        val networkId = details.network ?: "default"
        val network = findNetwork(networkId)
        val networkName = network?.selfLink ?: "projects/$myProjectId/global/networks/$networkId"

        val networkInterface = NetworkInterface.newBuilder()
            .setName(networkName)
            .apply {
                if (details.subnetManually && !details.subnetInput.isNullOrBlank()) {
                    subnetwork = parseSubnetFromURL(details.subnetInput)
                } else if (!details.subnet.isNullOrBlank()) {
                    val region = zone.substring(0, zone.length - 2)
                    subnetwork = network?.subnetworksList?.find{ it.endsWith(details.subnet)} ?:
                            "projects/$myProjectId/regions/$region/subnetworks/${details.subnet}"
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
                .setMachineType("projects/$myProjectId/zones/$zone/machineTypes/$machineType")
                .addDisks(AttachedDisk.newBuilder()
                        .setInitializeParams(AttachedDiskInitializeParams.newBuilder()
                                .setSourceImage(instanceBootImage as String?)
                                .apply {
                                    if (!details.diskType.isNullOrBlank()) {
                                        diskType = "projects/$myProjectId/zones/$zone/diskTypes/${details.diskType}"
                                    }
                                }
                                .apply {
                                    if (!details.diskSizeGb.isNullOrBlank()) {
                                        diskSizeGb = details.diskSizeGb.toLong()
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

        instanceClient.insertCallable().futureCall(InsertInstanceRequest.newBuilder()
                .setProject(myProjectId)
                .setZone(zone)
                .setInstanceResource(instanceInfo)
                .build())
                .await()
    }

    override suspend fun createTemplateInstance(instance: GoogleCloudInstance, userData: CloudInstanceUserData): Operation = coroutineScope {
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
        instanceClient.insertCallable().futureCall(InsertInstanceRequest.newBuilder()
                .setSourceInstanceTemplate("projects/$myProjectId/global/instanceTemplates/${details.instanceTemplate}")
                .setProject(myProjectId)
                .setZone(zone)
                .setInstanceResource(instanceInfo)
                .build())
                .await()
    }

    private fun parseSubnetFromURL(subnetURL: String): String {
        // Subnet URL: projects/[project ID]/regions/[region]/subnetworks/[subnet]
        val (projectID, region, subnet) = subnetURL
            .split("/")
            .slice(1..5 step 2)

        return "projects/$projectID/regions/$region/subnetworks/$subnet"
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

        LOG.info("GCP get Instance Template: projects/$myProjectId/global/instanceTemplates/${instance.image.imageDetails.instanceTemplate}")
        return instanceTemplateClient.get(myProjectId, instance.image.imageDetails.instanceTemplate)
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
        instanceClient.startCallable()
                .futureCall(StartInstanceRequest.newBuilder()
                        .setProject(myProjectId)
                        .setZone(instance.zone)
                        .setInstance(instance.id)
                        .build())
                .await()
        Unit
    }

    override suspend fun restartVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.resetCallable()
                .futureCall(ResetInstanceRequest.newBuilder()
                        .setProject(myProjectId)
                        .setZone(instance.zone)
                        .setInstance(instance.id)
                        .build())
                .await()
        Unit
    }

    override suspend fun stopVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.stopCallable()
                .futureCall(StopInstanceRequest.newBuilder()
                        .setProject(myProjectId)
                        .setZone(instance.zone)
                        .setInstance(instance.id)
                        .build())
                .await()
        Unit
    }

    override suspend fun deleteVm(instance: GoogleCloudInstance) = coroutineScope {
        instanceClient.deleteCallable()
                .futureCall(DeleteInstanceRequest.newBuilder()
                        .setProject(myProjectId)
                        .setZone(instance.zone)
                        .setInstance(instance.id)
                        .build())
                .await()
        Unit
    }

    override fun checkImage(image: GoogleCloudImage) = runBlocking {
        val errors = image.handler.checkImage(image)
        errors.map { TypedCloudErrorInfo.fromException(it) }.toTypedArray()
    }

    override fun checkInstance(instance: GoogleCloudInstance): Array<TypedCloudErrorInfo> = emptyArray()

    override fun checkStartOperation(instance: GoogleCloudInstance): Array<TypedCloudErrorInfo> = runBlocking {
        if (instance.startOperationId == null || myProjectId == null) {
            return@runBlocking emptyArray()
        }

        val res = getOperation(instance.startOperationId!!, myProjectId!!, instance.zone) ?: return@runBlocking emptyArray()

        if (res.httpErrorMessage != null || res.httpErrorStatusCode > 0) {
            res.error?.errorsList?.map { e ->
                TypedCloudErrorInfo(
                    res.operationType,
                    generalDescriptionFromError(e)
                )
            }?.toTypedArray() ?: arrayOf(
                TypedCloudErrorInfo("Failed to retrieve the errors, check Google Cloud Operations for more info")
            )
        } else {
            emptyArray()
        }
    }

    override suspend fun getImages(project: String?) = coroutineScope {
        val projectName = if (project.isNullOrBlank()) myProjectId else project
        val images = imageClient.listPagedCallable()
                .futureCall(ListImagesRequest.newBuilder()
                        .setProject(projectName)
                        .build())
                .await()

        images.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override suspend fun getImageFamilies(project: String?) = coroutineScope {
        val projectName = if (project.isNullOrBlank()) myProjectId else project
        val images = imageClient.listPagedCallable()
                .futureCall(ListImagesRequest.newBuilder()
                        .setProject(projectName)
                        .build())
                .await()

        images.iterateAll()
                .filter { it.family != null }
                .map { it.family }
                .distinct()
                .sorted()
    }

    override suspend fun getTemplates() = coroutineScope {
        val templates = instanceTemplateClient.listPagedCallable()
                .futureCall(ListInstanceTemplatesRequest.newBuilder()
                        .setProject(myProjectId)
                        .build())
                .await()

        templates.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    override suspend fun getZones() = coroutineScope {
        val zones = zoneClient.listPagedCallable()
                .futureCall(ListZonesRequest.newBuilder()
                        .setProject(myProjectId)
                        .build())
                .await()

        zones.iterateAll()
                .map { zone ->
                    val region = zone.region.substringAfterLast('/')
                    zone.name to listOf(formattedName(zone.name, zone.description), region)
                }
                .sortedWith(compareBy(comparator) { it.second.first() })
                .associate { it.first to it.second }
    }

    override suspend fun getMachineTypes(zone: String) = coroutineScope {
        val machineTypes = machineTypeClient.listPagedCallable()
                .futureCall(ListMachineTypesRequest.newBuilder()
                        .setProject(myProjectId)
                        .setZone(zone)
                        .build())
                .await()

        machineTypes.iterateAll()
                .map { it.name to formattedName(it.name, it.description) }
                .sortedWith(compareBy(comparator) { it.second })
                .associate { it.first to it.second }
    }

    private suspend fun <T> withVpcProjects(block: suspend (String) -> List<T>): List<T> {
        val items = mutableListOf<T>()

        myProjectId?.let { items.addAll(block(it)) }
        getVpcHostProjects()?.let { items.addAll(block(it.name)) }

        return items
    }

    private suspend fun getVpcHostProjects(): Project? {
        return projectClient.getXpnHostCallable().futureCall(
            GetXpnHostProjectRequest.newBuilder()
                .setProject(myProjectId)
                .build()
        ).await()
    }

    override suspend fun getNetworks() = coroutineScope {
        withVpcProjects { getNetworksForProject(it) }
            .map { it.name to formattedName(it.name, it.description) }
            .sortedWith(compareBy(comparator) { it.second })
            .associate { it.first to it.second }
    }

    private suspend fun getNetworksForProject(project: String): List<Network> {
        try {
            val networks = networkClient.listPagedCallable()
                .futureCall(
                    ListNetworksRequest.newBuilder()
                        .setProject(project)
                        .build()
                )
                .await()

            return networks.iterateAll().toList()
        } catch (e: PermissionDeniedException) {
            if (e.statusCode.code == StatusCode.Code.PERMISSION_DENIED) {
                return emptyList()
            } else {
                throw RuntimeException(e.cause)
            }
        }
    }

    private suspend fun findNetwork(name: String): Network? = withVpcProjects { getNetworksForProject(it) }.find { it.name == name }

    override suspend fun getSubnets(region: String) = coroutineScope {
        withVpcProjects { getSubnetsForProject(it, region) }
            .map { subNetwork ->
                val network = subNetwork.network.substringAfterLast('/')
                subNetwork.name to listOf(formattedName(subNetwork.name, subNetwork.description), network)
            }
            .sortedWith(compareBy(comparator) { it.second.first() })
            .associate { it.first to it.second }
    }

    private suspend fun getSubnetsForProject(project: String?, region: String): List<Subnetwork> {
        try {
            val subNetworks = subNetworkClient.listPagedCallable()
                .futureCall(
                    ListSubnetworksRequest.newBuilder()
                        .setProject(project)
                        .setRegion(region)
                        .build()
                ).await()

            return subNetworks.iterateAll().toList()
        } catch (e: PermissionDeniedException) {
            if (e.statusCode.code == StatusCode.Code.PERMISSION_DENIED) {
                return emptyList()
            } else {
                throw RuntimeException(e.cause)
            }
        }
    }

    override suspend fun getDiskTypes(zone: String) = coroutineScope {
        val diskTypes = diskTypeClient.listPagedCallable()
                .futureCall(ListDiskTypesRequest.newBuilder()
                        .setProject(myProjectId)
                        .setZone(zone)
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
        var request = AggregatedListInstancesRequest.newBuilder()
                .setProject(myProjectId)
                .build()

        while (true) {
            val response = try {
                instanceClient.aggregatedListCallable()
                        .futureCall(request)
                        .get(GOOGLE_API_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                throw CloudException("$GOOGLE_API_TIMEOUT_SECONDS seconds timeout exceeded while listing instances in Google Cloud", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw CloudException("Interrupted while listing instances in Google Cloud", e)
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }

            response.itemsMap?.values.orEmpty()
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

            if (response.nextPageToken.isNullOrEmpty()) break
            request = request.toBuilder().setPageToken(response.nextPageToken).build()
        }

        val result = hashMapOf<GoogleCloudImage, MutableMap<String, R>>()

        for (image in images) {
            val instances = hashMapOf<String, R>()
            map[image.imageDetails.sourceId]?.let { foundInstances ->
                foundInstances.forEach {
                    val name = it.name
                    val zone = it.zone.substringAfterLast('/')
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

    private fun generalDescriptionFromError(err: Errors?): String {
        return if (err != null) {
            "${err.code}: ${err.message}"
        } else {
            "Error description unavailable"
        }
    }

    private fun getOperation(operationId: String, projectId: String, zone: String): Operation? {
        return operationsClient.get(projectId, zone, operationId)
    }

    fun setServerId(serverId: String?) {
        myServerId = serverId
    }

    fun setProfileId(profileId: String) {
        myProfileId = profileId
    }

    private val operationsClient: ZoneOperationsClient by lazy {
        ZoneOperationsClient.create(ZoneOperationsSettings.newBuilder()
            .setCredentialsProvider(getCredentialsProvider { ZoneOperationsSettings.getDefaultServiceScopes() })
            .build())
    }

    private val projectClient: ProjectsClient by lazy {
        ProjectsClient.create(ProjectsSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { ProjectsSettings.getDefaultServiceScopes() })
                .build())
    }

    private val instanceClient: InstancesClient by lazy {
        InstancesClient.create(InstancesSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { InstancesSettings.getDefaultServiceScopes() })
                .build())
    }

    private val imageClient: ImagesClient by lazy {
        ImagesClient.create(ImagesSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { ImagesSettings.getDefaultServiceScopes() })
                .build())
    }

    private val zoneClient: ZonesClient by lazy {
        ZonesClient.create(ZonesSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { ZonesSettings.getDefaultServiceScopes() })
                .build())
    }

    private val machineTypeClient: MachineTypesClient by lazy {
        MachineTypesClient.create(MachineTypesSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { MachineTypesSettings.getDefaultServiceScopes() })
                .build())
    }

    private val networkClient: NetworksClient by lazy {
        NetworksClient.create(NetworksSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { NetworksSettings.getDefaultServiceScopes() })
                .build())
    }

    private val subNetworkClient: SubnetworksClient by lazy {
        SubnetworksClient.create(SubnetworksSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { SubnetworksSettings.getDefaultServiceScopes() })
                .build())
    }

    private val diskTypeClient: DiskTypesClient by lazy {
        DiskTypesClient.create(DiskTypesSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { DiskTypesSettings.getDefaultServiceScopes() })
                .build())
    }

    private val instanceTemplateClient: InstanceTemplatesClient by lazy {
        InstanceTemplatesClient.create(InstanceTemplatesSettings.newBuilder()
                .setCredentialsProvider(getCredentialsProvider { InstanceTemplatesSettings.getDefaultServiceScopes() })
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

        private const val GOOGLE_API_TIMEOUT_SECONDS = 120L

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