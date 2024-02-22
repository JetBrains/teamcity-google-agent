

package jetbrains.buildServer.clouds.google.utils

import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.util.FileUtil

import java.io.File
import java.io.IOException

/**
 * File-based number provider.
 */
class FileIdProvider(private val myStorageFile: File) : IdProvider {

    init {
        if (!myStorageFile.exists()) {
            try {
                FileUtil.writeFileAndReportErrors(myStorageFile, "$DEFAULT_ID")
            } catch (e: IOException) {
                LOG.warn("Unable to write idx file '${myStorageFile.absolutePath}': $e")
            }
        }
    }

    override val nextId: Int
        get() = try {
            Integer.parseInt(FileUtil.readText(myStorageFile)).apply {
                FileUtil.writeFileAndReportErrors(myStorageFile, (this + 1).toString())
            }
        } catch (e: Exception) {
            LOG.warn("Unable to read idx file: $e")
            DEFAULT_ID
        }

    companion object {
        private val LOG = Logger.getInstance(FileIdProvider::class.java.name)
        private const val DEFAULT_ID = 1
    }
}