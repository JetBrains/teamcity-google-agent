/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
        private val DEFAULT_ID = 1
    }
}
