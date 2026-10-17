package com.michatec.radio.plugins

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.michatec.radio.BuildConfig
import com.michatec.radio.NotificationSys
import org.luaj.vm2.LuaValue
import java.io.File
import java.util.UUID
import androidx.core.content.edit

class PluginManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PluginManager"
        private const val PREFS_NAME = "radio_plugins_prefs"
        private const val PREF_ENABLED_PREFIX = "plugin_enabled_"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: PluginManager? = null

        fun getInstance(context: Context): PluginManager {
            return instance ?: synchronized(this) {
                instance ?: PluginManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val pluginsDir = File(context.filesDir, "plugins")
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val loadedPlugins = mutableMapOf<String, PluginInstance>()

    @Volatile
    var customEventDispatcher: PluginApi.PluginEventDispatcher? = null
    private val currentDispatcher: PluginApi.PluginEventDispatcher
        get() = customEventDispatcher ?: eventDispatcher

    private val eventDispatcher = object : PluginApi.PluginEventDispatcher {
        override fun registerEventListener(eventName: String, callback: LuaValue) {
            // Handled per instance
        }
        override fun unregisterEventListener(eventName: String) {}

        override fun notifyUser(title: String, message: String, type: String) {
            if (type == "toast" || type == "snackbar") {
                NotificationSys.showSnackbar(context, "$title: $message")
            } else {
                NotificationSys.showNotification(context, title, message)
            }
        }

        override fun getCurrentStationInfo(): Map<String, Any> {
            return mapOf(
                "name" to "Active Radio Station",
                "uri" to ""
            )
        }

        override fun isPlaying(): Boolean {
            return true
        }

        override fun getVolume(): Int {
            return 80
        }

        override fun setVolume(volume: Int) {
            Log.i(TAG, "Plugin requested volume set to: $volume")
        }
    }

    init {
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }
        check(pluginsDir.isDirectory) { "Unable to create plugins directory" }
    }

    @Synchronized
    fun loadAllEnabledPlugins() {
        pluginsDir.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            if (dir.isDirectory) {
                val manifestFile = File(dir, "manifest.json")
                if (manifestFile.isFile) {
                    try {
                        val manifest = readManifest(manifestFile)
                        if (dir.name == manifest.id &&
                            isPluginEnabled(manifest.id) &&
                            !loadedPlugins.containsKey(manifest.id)
                        ) {
                            val instance = PluginInstance(context, manifest, dir, currentDispatcher)
                            try {
                                instance.load()
                                loadedPlugins[manifest.id] = instance
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to load enabled plugin '${manifest.id}'", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading manifest in ${dir.name}", e)
                    }
                }
            }
        }
    }

    @Synchronized
    fun unloadAllPlugins() {
        loadedPlugins.values.toList().forEach { it.unload() }
        loadedPlugins.clear()
    }

    @Synchronized
    fun installPlugin(rdsFile: File): PluginManifest {
        if (!rdsFile.name.endsWith(".rds", ignoreCase = true)) {
            throw IllegalArgumentException("Only .rds files are allowed for installation.")
        }
        require(rdsFile.isFile) { "Plugin archive must be a regular file" }
        val tempDir = java.nio.file.Files.createTempDirectory(
            context.cacheDir.toPath(),
            "plugin_temp_"
        ).toFile()
        var stagingDir: File? = null
        var backupDir: File? = null
        var previousInstance: PluginInstance? = null
        var previousEnabled = true
        var previousPreferencePresent = false
        var pluginId: String? = null
        var targetDir: File? = null
        var installationComplete = false
        var installationStarted = false
        var targetActivated = false
        try {
            val manifest = RdsContainer.unpack(rdsFile, tempDir)
            if (!isVersionAtLeast(BuildConfig.VERSION_NAME, manifest.minAppVersion)) {
                throw IllegalArgumentException("Plugin requires app version >= ${manifest.minAppVersion}, but current app version is ${BuildConfig.VERSION_NAME}")
            }
            pluginId = manifest.id
            val pluginTargetDir = File(pluginsDir, manifest.id)
            targetDir = pluginTargetDir
            val preferenceKey = PREF_ENABLED_PREFIX + manifest.id
            previousPreferencePresent = prefs.contains(preferenceKey)
            previousEnabled = prefs.getBoolean(preferenceKey, true)
            val staging = File(pluginsDir, ".install_${UUID.randomUUID()}")
            stagingDir = staging
            require(tempDir.copyRecursively(staging, overwrite = false)) {
                "Unable to stage plugin installation"
            }
            preservePluginData(pluginTargetDir, staging)

            val backup = File(pluginsDir, ".backup_${UUID.randomUUID()}")
            backupDir = backup
            installationStarted = true
            previousInstance = loadedPlugins.remove(manifest.id)
            previousInstance?.unload()
            if (pluginTargetDir.exists()) {
                require(pluginTargetDir.renameTo(backup)) {
                    "Unable to back up existing plugin '${manifest.id}'"
                }
            }
            require(staging.renameTo(pluginTargetDir)) {
                "Unable to activate plugin installation"
            }
            targetActivated = true
            val instance = PluginInstance(context, manifest, pluginTargetDir, currentDispatcher)
            instance.load()
            loadedPlugins[manifest.id] = instance
            prefs.edit { putBoolean(PREF_ENABLED_PREFIX + manifest.id, true) }
            installationComplete = true

            Log.i(TAG, "Successfully installed and loaded plugin: ${manifest.id}")
            return manifest
        } catch (e: Exception) {
            if (installationStarted) pluginId?.let { id ->
                loadedPlugins.remove(id)
                if (targetActivated) targetDir?.deleteRecursively()
                val backup = backupDir
                val target = targetDir
                if (backup != null && backup.exists() && (target == null || !backup.renameTo(target))) {
                    e.addSuppressed(IllegalStateException("Could not restore the previous plugin directory"))
                }
                previousInstance?.let { old ->
                    try {
                        old.load()
                        loadedPlugins[id] = old
                    } catch (restoreError: Exception) {
                        e.addSuppressed(restoreError)
                    }
                }
                prefs.edit {
                    if (previousPreferencePresent) {
                        putBoolean(PREF_ENABLED_PREFIX + id, previousEnabled)
                    } else {
                        remove(PREF_ENABLED_PREFIX + id)
                    }
                }
            }
            Log.e(TAG, "Plugin installation failed. Aborting installation.", e)
            throw e
        } finally {
            stagingDir?.deleteRecursively()
            if (installationComplete && backupDir?.exists() == true && !backupDir.deleteRecursively()) {
                Log.w(TAG, "Could not remove previous plugin backup")
            }
            tempDir.deleteRecursively()
        }
    }

    @Synchronized
    fun uninstallPlugin(pluginId: String) {
        requireValidPluginId(pluginId)
        loadedPlugins[pluginId]?.let {
            it.unload()
            loadedPlugins.remove(pluginId)
        }
        val targetDir = File(pluginsDir, pluginId)
        if (targetDir.exists()) {
            check(targetDir.deleteRecursively()) { "Unable to remove plugin '$pluginId'" }
        }
        setPluginEnabled(pluginId, false)
        Log.i(TAG, "Uninstalled plugin: $pluginId")
    }

    @Synchronized
    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        requireValidPluginId(pluginId)
        if (enabled) {
            if (!loadedPlugins.containsKey(pluginId)) {
                val dir = File(pluginsDir, pluginId)
                val manifestFile = File(dir, "manifest.json")
                require(manifestFile.isFile) { "Plugin '$pluginId' is not installed" }
                val manifest = readManifest(manifestFile)
                require(manifest.id == pluginId) { "Plugin directory and manifest ID do not match" }
                val instance = PluginInstance(context, manifest, dir, currentDispatcher)
                instance.load()
                loadedPlugins[pluginId] = instance
            }
            prefs.edit { putBoolean(PREF_ENABLED_PREFIX + pluginId, true) }
        } else {
            loadedPlugins.remove(pluginId)?.unload()
            prefs.edit { putBoolean(PREF_ENABLED_PREFIX + pluginId, false) }
        }
    }

    @Synchronized
    fun isPluginEnabled(pluginId: String): Boolean {
        requireValidPluginId(pluginId)
        return prefs.getBoolean(PREF_ENABLED_PREFIX + pluginId, true)
    }

    @Synchronized
    fun hasSettings(pluginId: String): Boolean {
        requireValidPluginId(pluginId)
        val dir = File(pluginsDir, pluginId)
        val manifestFile = File(dir, "manifest.json")
        if (manifestFile.isFile) {
            try {
                val manifest = readManifest(manifestFile)
                if (manifest.entrypoints.containsKey("settings")) {
                    return true
                }
            } catch (_: Exception) {}
        }
        return loadedPlugins[pluginId]?.hasSettings() == true
    }

    fun openSettings(activity: Activity, pluginId: String) {
        requireValidPluginId(pluginId)
        var instance = loadedPlugins[pluginId]
        if (instance == null || !instance.isLoaded) {
            val dir = File(pluginsDir, pluginId)
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.isFile) {
                try {
                    val manifest = readManifest(manifestFile)
                    instance = PluginInstance(context, manifest, dir, currentDispatcher)
                    instance.load()
                    loadedPlugins[pluginId] = instance
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load plugin '$pluginId' for settings", e)
                }
            }
        }
        instance?.openSettings(activity)
    }

    @Synchronized
    fun getInstalledPluginManifests(): List<PluginManifest> {
        val list = mutableListOf<PluginManifest>()
        pluginsDir.listFiles()?.sortedBy { it.name }?.forEach { dir ->
            if (dir.isDirectory) {
                val manifestFile = File(dir, "manifest.json")
                if (manifestFile.isFile) {
                    try {
                        val manifest = readManifest(manifestFile)
                        if (dir.name == manifest.id) list.add(manifest)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading manifest in ${dir.name}", e)
                    }
                }
            }
        }
        return list
    }

    fun dispatchEvent(eventName: String, vararg args: Any?) {
        val instances = synchronized(this) { loadedPlugins.values.toList() }
        instances.forEach { instance ->
            instance.dispatchEvent(eventName, *args)
        }
    }

    private fun requireValidPluginId(pluginId: String) {
        require(pluginId.matches(Regex("^[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*$"))) {
            "Invalid plugin ID"
        }
    }

    private fun readManifest(file: File): PluginManifest {
        require(file.length() <= 64L * 1024) { "Plugin manifest exceeds the size limit" }
        return PluginManifest.fromJson(file.readText(Charsets.UTF_8))
    }

    private fun preservePluginData(previousDir: File, stagingDir: File) {
        if (!previousDir.isDirectory) return
        listOf("config.json", "storage.json").forEach { fileName ->
            val previousFile = File(previousDir, fileName)
            if (!previousFile.exists()) return@forEach
            require(previousFile.isFile && previousFile.canonicalFile.parentFile == previousDir.canonicalFile) {
                "Invalid plugin data file: $fileName"
            }
            require(previousFile.length() <= 1_048_576) {
                "Plugin data file exceeds the size limit: $fileName"
            }
            previousFile.inputStream().use { input ->
                File(stagingDir, fileName).outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun isVersionAtLeast(currentVersion: String, requiredVersion: String): Boolean {
        try {
            val currParts = currentVersion.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val reqParts = requiredVersion.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(currParts.size, reqParts.size)
            for (i in 0 until maxLen) {
                val curr = currParts.getOrNull(i) ?: 0
                val req = reqParts.getOrNull(i) ?: 0
                if (curr > req) return true
                if (curr < req) return false
            }
            return true
        } catch (_: Exception) {
            return true
        }
    }
}
