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