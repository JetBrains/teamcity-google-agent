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

package jetbrains.buildServer.clouds.google

import jetbrains.buildServer.util.FileUtil
import org.testng.Assert
import org.testng.annotations.Test
import java.io.File

@Test
class GoogleMetadataReaderTest {

    fun deserializeMetadata() {
        val file = File("src/test/resources/metadata1.json")
        val json = FileUtil.readText(file)
        val metadata = GoogleMetadataReader.deserializeMetadata(json)
        Assert.assertNotNull(metadata)
        Assert.assertEquals(metadata?.name, "agent1")
        Assert.assertNotNull(metadata?.attributes)
        Assert.assertEquals(metadata?.attributes?.teamcityData, "data")
        Assert.assertNotNull(metadata?.networkInterfaces)
        Assert.assertEquals(metadata?.networkInterfaces?.size, 1)
        val accessConfigs = metadata!!.networkInterfaces[0].accessConfigs
        Assert.assertNotNull(accessConfigs)
        Assert.assertEquals(accessConfigs.size, 1)
        Assert.assertEquals(accessConfigs[0].externalIp, "1.1.1.1")
    }
}