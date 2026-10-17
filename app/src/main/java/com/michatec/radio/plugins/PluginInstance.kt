package com.michatec.radio.plugins

import android.app.Activity
import android.content.Context
import android.util.Log
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import java.io.File
import java.io.FileInputStream

class PluginInstance(
    private val context: Context,
    val manifest: PluginManifest,
    val pluginDir: File,
    private val eventDispatcher: PluginApi.PluginEventDispatcher
) {
    private companion object {
        const val MAX_EVENT_LISTENERS = 128
    }

    private val TAG = "PluginInstance[${manifest.id}]"
    private lateinit var sandbox: PluginSandbox.Runtime
    private val globals: Globals
        get() = sandbox.globals
    private var pluginTable: LuaValue = LuaValue.NIL
    private val eventListeners = mutableMapOf<String, LuaValue>()
    var isLoaded: Boolean = false
        private set

    var currentActivity: Activity? = null

    @Synchronized
    fun load() {
        if (isLoaded) return
        try {
            val validationErrors = PluginManifest.validate(manifest)
            require(validationErrors.isEmpty()) {
                "Invalid plugin manifest: ${validationErrors.joinToString(", ")}"
            }
            sandbox = PluginSandbox.createSandboxGlobals(pluginDir)
            val api = PluginApi(context, manifest, pluginDir, object : PluginApi.PluginEventDispatcher by eventDispatcher {
                override fun registerEventListener(eventName: String, callback: LuaValue) {
                    require(eventName in eventListeners || eventListeners.size < MAX_EVENT_LISTENERS) {
                        "Plugin event listener limit exceeded"
                    }
                    eventListeners[eventName] = callback
                }
                override fun unregisterEventListener(eventName: String) {
                    eventListeners.remove(eventName)
                }
            }, { currentActivity })

            val apiTable = api.createApiTable()
            globals.set("api", apiTable)

            val mainScript = File(pluginDir, manifest.main)
            val pluginRoot = pluginDir.canonicalFile.toPath()
            val canonicalScript = mainScript.canonicalFile
            if (!canonicalScript.toPath().startsWith(pluginRoot) || !canonicalScript.isFile) {
                throw IllegalStateException("Main script not found: ${manifest.main}")
            }
            require(canonicalScript.length() <= 5L * 1024 * 1024) {
                "Main script exceeds the size limit"
            }

            val chunk = FileInputStream(canonicalScript).use {
                globals.load(it, "@${manifest.main}", "t", globals)
            }
            executeWithBudget { pluginTable = chunk.call() }

            // Call load entrypoint if present
            val loadFnName = manifest.entrypoints["load"] ?: "on_load"
            val onLoadFn = if (pluginTable.istable()) pluginTable.get(loadFnName) else globals.get(loadFnName)
            if (onLoadFn.isfunction()) {
                executeWithBudget { onLoadFn.call(apiTable) }
                Log.i(TAG, "Plugin '${manifest.id}' loaded successfully via $loadFnName")
            } else {
                Log.w(TAG, "Entrypoint function '$loadFnName' not found or not a function in plugin '${manifest.id}'")
            }

            isLoaded = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load plugin '${manifest.id}'", e)
            eventListeners.clear()
            pluginTable = LuaValue.NIL
            isLoaded = false
            throw e
        }
    }

    @Synchronized
    fun unload() {
        if (!isLoaded) return
        try {
            val unloadFnName = manifest.entrypoints["unload"] ?: "on_unload"
            val onUnloadFn = if (pluginTable.istable()) pluginTable.get(unloadFnName) else globals.get(unloadFnName)
            if (onUnloadFn.isfunction()) {
                executeWithBudget { onUnloadFn.call() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing unload function for '${manifest.id}'", e)
        } finally {
            eventListeners.clear()
            pluginTable = LuaValue.NIL
            isLoaded = false
            Log.i(TAG, "Plugin '${manifest.id}' unloaded")
        }
    }

    fun hasSettings(): Boolean {
        if (!isLoaded) return false
        val settingsFnName = manifest.entrypoints["settings"] ?: "on_settings"
        val fn = if (pluginTable.istable()) pluginTable.get(settingsFnName) else globals.get(settingsFnName)
        return fn.isfunction()
    }

    fun openSettings(activity: Activity) {
        if (!isLoaded) return
        currentActivity = activity
        try {
            val settingsFnName = manifest.entrypoints["settings"] ?: "on_settings"
            val fn = if (pluginTable.istable()) pluginTable.get(settingsFnName) else globals.get(settingsFnName)
            if (fn.isfunction()) {
                val apiTable = globals.get("api")
                executeWithBudget { fn.call(apiTable) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening settings for plugin '${manifest.id}'", e)
        } finally {
            currentActivity = null
        }
    }

    @Synchronized
    fun dispatchEvent(eventName: String, vararg args: Any?) {
        if (!isLoaded) return
        val luaArgs = args.map { toLuaValue(it) }.toTypedArray()
        val varargs = LuaValue.varargsOf(luaArgs)
        val listener = eventListeners[eventName]
        if (listener != null && listener.isfunction()) {
            runCallback("event listener '$eventName'") {
                listener.invoke(varargs)
            }
        }

        if (pluginTable.istable()) {
            val fn = pluginTable.get("on_$eventName")
            if (fn.isfunction()) {
                runCallback("event handler '$eventName'") {
                    fn.invoke(varargs)
                }
            }
        }
    }

    private fun runCallback(label: String, callback: () -> Unit) {
        try {
            executeWithBudget(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $label for plugin '${manifest.id}'", e)
        }
    }

    private fun executeWithBudget(callback: () -> Unit) {
        sandbox.resetInstructionBudget()
        callback()
    }

    private fun toLuaValue(obj: Any?, depth: Int = 0): LuaValue {
        require(depth <= 32) { "Event data exceeds the maximum nesting depth" }
        return when (obj) {
            null -> LuaValue.NIL
            is Boolean -> LuaValue.valueOf(obj)
            is Number -> LuaValue.valueOf(obj.toDouble())
            is String -> LuaValue.valueOf(obj)
            is Map<*, *> -> {
                require(obj.size <= 10_000) { "Event object exceeds the size limit" }
                val t = LuaTable()
                obj.forEach { (k, v) ->
                    if (k is String) t.set(k, toLuaValue(v, depth + 1))
                }
                t
            }
            is List<*> -> {
                require(obj.size <= 10_000) { "Event list exceeds the size limit" }
                LuaTable().apply {
                    obj.forEachIndexed { index, value ->
                        set(index + 1, toLuaValue(value, depth + 1))
                    }
                }
            }
            else -> LuaValue.valueOf(obj.toString())
        }
    }
}
