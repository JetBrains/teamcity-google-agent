/*
 * Copyright 2000-2020 JetBrains s.r.o.
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
