package com.michatec.radio.plugins

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

@Keep
data class PluginManifest(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("version") val version: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("license") val license: String = "",
    @SerializedName("main") val main: String = "main.lua",
    @SerializedName("icon") val icon: String = "",
    @SerializedName("minAppVersion") val minAppVersion: String = "1.0.0",
    @SerializedName("dependencies") val dependencies: List<String> = emptyList(),
    @SerializedName("permissions") val permissions: List<String> = emptyList(),
    @SerializedName("entrypoints") val entrypoints: Map<String, String> = mapOf("load" to "on_load", "unload" to "on_unload")
) {
    companion object {
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private val pluginIdPattern = Regex("^[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*$")
        private val entrypointPattern = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        private val knownPermissions = setOf(
            "radio.read",
            "audio.read",
            "audio.control",
            "network.http",
            "events.listen",
            "ui.notify",
            "config.read",
            "config.write",
            "storage.local",
            "plugins.read",
            "log"
        )

        fun fromJson(jsonString: String): PluginManifest {
            require(jsonString.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST_BYTES) {
                "Plugin manifest exceeds the size limit"
            }
            val manifest = try {
                Gson().fromJson(jsonString, PluginManifest::class.java)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid plugin manifest JSON", e)
            } ?: throw IllegalArgumentException("Plugin manifest cannot be null")

            val normalized = manifest.copy(
                schemaVersion = manifest.schemaVersion.takeIf { it != 0 } ?: 1,
                description = manifest.description,
                author = manifest.author,
                license = manifest.license,
                main = manifest.main.ifBlank { "main.lua" },
                icon = manifest.icon,
                minAppVersion = manifest.minAppVersion.ifBlank { "1.0.0" },
                dependencies = manifest.dependencies,
                permissions = manifest.permissions,
                entrypoints = manifest.entrypoints.ifEmpty {
                    mapOf("load" to "on_load", "unload" to "on_unload")
                }
            )
            val errors = validate(normalized)
            if (errors.isNotEmpty()) {
                throw IllegalArgumentException("Invalid plugin manifest: ${errors.joinToString(", ")}")
            }
            return normalized
        }

        fun validate(manifest: PluginManifest?): List<String> {
            val errors = mutableListOf<String>()
            if (manifest == null) {
                errors.add("Manifest cannot be null")
                return errors
            }
            if (manifest.id.isBlank() || !manifest.id.matches(pluginIdPattern)) {
                errors.add("Invalid or missing plugin ID: '${manifest.id}'")
            }
            if (manifest.name.isBlank() || manifest.name.length > 128) {
                errors.add("Plugin name must contain 1-128 characters")
            }
            if (manifest.version.isBlank() ||
                !manifest.version.matches(Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$"))
            ) {
                errors.add("Invalid semantic version: '${manifest.version}'")
            }
            if (!isSafeLuaPath(manifest.main)) {
                errors.add("Main script must be a relative .lua path within the plugin")
            }
            if (manifest.schemaVersion < 1) {
                errors.add("Schema version must be >= 1")
            }
            if (manifest.description.length > 4_096 || manifest.author.length > 128 ||
                manifest.license.length > 128 || manifest.icon.length > 256 ||
                manifest.minAppVersion.length > 64
            ) {
                errors.add("Plugin metadata exceeds the allowed length")
            }
            val dependencies = manifest.dependencies
            if (dependencies.size > 128 ||
                dependencies.any { !it.matches(pluginIdPattern) } ||
                dependencies.toSet().size != dependencies.size
            ) {
                errors.add("Plugin dependencies contain invalid or duplicate IDs")
            }
            val permissions = manifest.permissions
            if (permissions.size > knownPermissions.size ||
                permissions.toSet().size != permissions.size ||
                permissions.any { it !in knownPermissions }
            ) {
                errors.add("Plugin permissions contain unknown or duplicate values")
            }
            val entrypoints = manifest.entrypoints
            if (entrypoints.keys.any { it != "load" && it != "unload" && it != "settings" } ||
                entrypoints.values.any { !it.matches(entrypointPattern) }
            ) {
                errors.add("Plugin entrypoints contain invalid names")
            }
            return errors
        }

        fun isSafeLuaPath(path: String): Boolean {
            if (path.isBlank() || path.length > 256 || '\\' in path || path.startsWith('/')) {
                return false
            }
            val segments = path.split('/')
            return path.endsWith(".lua") &&
                segments.all { it.isNotBlank() && it != "." && it != ".." }
        }
    }
}
