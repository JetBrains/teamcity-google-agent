package jetbrains.buildServer.clouds.google.utils

import com.google.cloud.compute.v1.*

val ProjectGlobalImageName.value: String
    get() = ProjectGlobalImageName.SERVICE_ADDRESS + toString()

val ProjectZoneMachineTypeName.value: String
    get() = ProjectZoneMachineTypeName.SERVICE_ADDRESS + toString()

val ProjectGlobalNetworkName.value: String
    get() = ProjectGlobalNetworkName.SERVICE_ADDRESS + toString()

val ProjectZoneName.value: String
    get() = ProjectZoneName.SERVICE_ADDRESS + toString()

val ProjectGlobalInstanceTemplateName.value: String
    get() = ProjectGlobalInstanceTemplateName.SERVICE_ADDRESS + toString()

val ProjectZoneInstanceName.value: String
    get() = ProjectZoneInstanceName.SERVICE_ADDRESS + toString()

val ProjectName.value: String
    get() = ProjectName.SERVICE_ADDRESS + toString()

val ProjectRegionName.value: String
    get() = ProjectRegionName.SERVICE_ADDRESS + toString()

val ProjectRegionSubnetworkName.value: String
    get() = ProjectRegionSubnetworkName.SERVICE_ADDRESS + toString()

val ProjectZoneDiskTypeName.value: String
    get() = ProjectZoneDiskTypeName.SERVICE_ADDRESS + toString()
