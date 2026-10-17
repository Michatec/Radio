package com.michatec.radio.plugins

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object RdsContainer {
    private const val TAG = "RdsContainer"

    fun unpack(rdsFile: File, targetDir: File): PluginManifest {
        if (!rdsFile.exists()) {
            throw IOException("RDS file does not exist: ${rdsFile.absolutePath}")
        }
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            throw IllegalArgumentException("Target directory is not empty")
        }
        targetDir.mkdirs()

        val canonicalTargetDir = targetDir.canonicalFile
        val entries = mutableListOf<Triple<String, Boolean, ByteArray>>()

        ZipInputStream(rdsFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val isDir = entry.isDirectory
                val bytes = if (!isDir) zis.readBytes() else ByteArray(0)
                entries.add(Triple(name, isDir, bytes))
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (entries.isEmpty()) {
            throw IllegalArgumentException("RDS archive is empty")
        }

        // Detect if manifest.json is wrapped in a root folder (e.g. MyPlugin/manifest.json)
        val hasRootManifest = entries.any { it.first == "manifest.json" }
        val prefix = if (!hasRootManifest) {
            val manifestEntry = entries.find { it.first.endsWith("manifest.json") }
            if (manifestEntry != null) {
                val idx = manifestEntry.first.indexOf("manifest.json")
                if (idx > 0) manifestEntry.first.substring(0, idx) else ""
            } else ""
        } else ""

        var manifest: PluginManifest? = null
        val seenPaths = mutableSetOf<String>()

        for ((name, isDir, bytes) in entries) {
            val relativeName = if (prefix.isNotEmpty() && name.startsWith(prefix)) {
                name.removePrefix(prefix)
            } else {
                name
            }
            if (relativeName.isBlank() || relativeName == "/") continue

            if (!seenPaths.add(relativeName)) {
                throw SecurityException("Duplicate entry path in RDS archive: $relativeName")
            }

            val targetFile = File(canonicalTargetDir, relativeName)
            val canonicalTargetFile = targetFile.canonicalFile
            if (!canonicalTargetFile.path.startsWith(canonicalTargetDir.path + File.separator) &&
                canonicalTargetFile.path != canonicalTargetDir.path) {
                throw SecurityException("Path traversal vulnerability detected in ZIP entry: $relativeName")
            }

            if (isDir) {
                canonicalTargetFile.mkdirs()
            } else {
                canonicalTargetFile.parentFile?.mkdirs()
                canonicalTargetFile.writeBytes(bytes)
            }

            if (relativeName == "manifest.json") {
                val jsonContent = canonicalTargetFile.readText(Charsets.UTF_8)
                manifest = PluginManifest.fromJson(jsonContent)
            }
        }

        if (manifest == null) {
            throw IllegalArgumentException("Missing manifest.json in RDS archive")
        }

        val mainScriptFile = File(canonicalTargetDir, manifest.main)
        if (!mainScriptFile.exists()) {
            throw IllegalArgumentException("Missing main script specified in manifest: ${manifest.main}")
        }

        return manifest
    }

    fun pack(sourceDir: File, outputFile: File) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            throw IllegalArgumentException("Source directory is invalid: ${sourceDir.absolutePath}")
        }
        val manifestFile = File(sourceDir, "manifest.json")
        if (!manifestFile.exists()) {
            throw IllegalArgumentException("Source directory missing manifest.json")
        }

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val canonicalSourceDir = sourceDir.canonicalFile
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val canonicalFile = file.canonicalFile
                    val relativePath = canonicalSourceDir.toURI().relativize(canonicalFile.toURI()).path
                    zos.putNextEntry(ZipEntry(relativePath))
                    file.inputStream().use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
        Log.i(TAG, "Successfully packed ${sourceDir.name} into ${outputFile.absolutePath}")
    }
}
