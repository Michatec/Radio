package com.michatec.radio.plugins

import android.app.Activity
import android.content.Context
import android.util.AtomicFile
import android.util.Log
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.michatec.radio.R
import com.michatec.radio.helpers.FileHelper
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.ZeroArgFunction
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PluginApi(
    private val context: Context,
    private val manifest: PluginManifest,
    pluginDir: File,
    private val eventDispatcher: PluginEventDispatcher,
    private val activityProvider: () -> Activity? = { null }
) {

    interface PluginEventDispatcher {
        fun registerEventListener(eventName: String, callback: LuaValue)
        fun unregisterEventListener(eventName: String)
        fun notifyUser(title: String, message: String, type: String)
        fun getCurrentStationInfo(): Map<String, Any>
        fun isPlaying(): Boolean
        fun getVolume(): Int
        fun setVolume(volume: Int)
    }

    private val TAG = "PluginApi[${manifest.id}]"
    private val gson = Gson()
    private val configFile = File(pluginDir, "config.json")
    private val storageFile = File(pluginDir, "storage.json")

    private fun checkPermission(permission: String) {
        if (!manifest.permissions.contains(permission)) {
            throw SecurityException("Plugin '${manifest.id}' lacks required permission: '$permission'")
        }
    }

    private companion object {
        const val MAX_JSON_DEPTH = 32
        const val MAX_TABLE_ENTRIES = 10_000
        const val MAX_JSON_BYTES = 1_048_576
        const val MAX_STORAGE_KEY_LENGTH = 256
        val eventNamePattern = Regex("^[A-Za-z][A-Za-z0-9_.:-]{0,127}$")
        val notificationTypes = setOf("notification", "toast", "snackbar")
    }

    fun createApiTable(): LuaTable {
        val api = LuaTable()

        // api.log
        val logTable = LuaTable().apply {
            set("info", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    Log.i(TAG, boundedLogMessage(arg))
                    return NIL
                }
            })
            set("warn", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    Log.w(TAG, boundedLogMessage(arg))
                    return NIL
                }
            })
            set("error", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    Log.e(TAG, boundedLogMessage(arg))
                    return NIL
                }
            })
        }
        api.set("log", logTable)

        // api.radio
        val radioTable = LuaTable().apply {
            set("get_current_station", object : ZeroArgFunction() {
                override fun call(): LuaValue {
                    checkPermission("radio.read")
                    val info = eventDispatcher.getCurrentStationInfo()
                    val table = LuaTable()
                    table.set("uuid", toLuaValue(info["uuid"] ?: ""))
                    table.set("name", toLuaValue(info["name"] ?: "Unknown"))
                    table.set("uri", toLuaValue(info["uri"] ?: ""))
                    return table
                }
            })
            set("get_stations", object : ZeroArgFunction() {
                override fun call(): LuaValue {
                    checkPermission("radio.read")
                    val collection = FileHelper.readCollection(context)
                    require(collection.stations.size <= MAX_TABLE_ENTRIES) {
                        "Station collection exceeds the API size limit"
                    }
                    val table = LuaTable()
                    collection.stations.forEachIndexed { idx, station ->
                        val st = LuaTable().apply {
                            set("uuid", toLuaValue(station.uuid))
                            set("name", toLuaValue(station.name))
                            set("uri", toLuaValue(station.getStreamUri()))
                        }
                        table.set(idx + 1, st)
                    }
                    return table
                }
            })
            set("is_playing", object : ZeroArgFunction() {
                override fun call(): LuaValue {
                    checkPermission("audio.read")
                    return valueOf(eventDispatcher.isPlaying())
                }
            })
            set("get_volume", object : ZeroArgFunction() {
                override fun call(): LuaValue {
                    checkPermission("audio.read")
                    return valueOf(eventDispatcher.getVolume())
                }
            })
            set("set_volume", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    checkPermission("audio.control")
                    require(arg.isnumber()) { "Volume must be a number" }

                    val volume = arg.checkint()
                    require(volume in 0..100) {
                        "Volume must be between 0 and 100"
                    }

                    eventDispatcher.setVolume(volume)
                    return NIL
                }
            })
        }
        api.set("radio", radioTable)

        // api.network
        val networkTable = LuaTable().apply {
            set("get", object : OneArgFunction() {
                override fun call(urlArg: LuaValue): LuaValue {
                    checkPermission("network.http")
                    val urlString = urlArg.checkjstring()
                    require(urlString.length <= 2_048) { "URL is too long" }
                    var connection: HttpURLConnection? = null

                    return try {
                        val url = URL(urlString)
                        require(
                            (url.protocol.equals("http", ignoreCase = true) ||
                                url.protocol.equals("https", ignoreCase = true)) &&
                                url.host.isNotBlank() &&
                                url.userInfo == null
                        ) {
                            "Only HTTP and HTTPS URLs are allowed"
                        }

                        connection = url.openConnection() as HttpURLConnection
                        connection.requestMethod = "GET"
                        connection.connectTimeout = 5_000
                        connection.readTimeout = 5_000
                        connection.instanceFollowRedirects = false
                        connection.useCaches = false

                        require(connection.responseCode in 200..299) {
                            "HTTP request failed: ${connection.responseCode}"
                        }

                        val maxBytes = 1_048_576
                        val declaredLength = connection.getHeaderFieldLong("Content-Length", -1L)
                        require(declaredLength < 0 || declaredLength <= maxBytes) {
                            "HTTP response is too large"
                        }

                        val output = java.io.ByteArrayOutputStream()
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            var total = 0

                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break

                                total += count
                                require(total <= maxBytes) { "HTTP response is too large" }
                                output.write(buffer, 0, count)
                            }
                        }

                        valueOf(output.toString("UTF-8"))
                    } catch (e: Exception) {
                        Log.e(TAG, "HTTP GET failed", e)
                        throw LuaError("HTTP GET failed")
                    } finally {
                        connection?.disconnect()
                    }
                }
            })
        }
        api.set("network", networkTable)

        // api.events
        val eventsTable = LuaTable().apply {
            set("on", object : TwoArgFunction() {
                override fun call(
                    eventArg: LuaValue,
                    callbackArg: LuaValue
                ): LuaValue {
                    checkPermission("events.listen")

                    val eventName = eventArg.checkjstring()

                    require(eventName.isNotBlank()) {
                        "Event name cannot be empty"
                    }
                    require(eventName.matches(eventNamePattern)) {
                        "Invalid event name"
                    }
                    require(callbackArg.isfunction()) {
                        "Event callback must be a function"
                    }

                    eventDispatcher.registerEventListener(
                        eventName,
                        callbackArg
                    )

                    Log.d(TAG, "Lua listener registered: $eventName")
                    return NIL
                }
            })

            set("off", object : OneArgFunction() {
                override fun call(eventArg: LuaValue): LuaValue {
                    checkPermission("events.listen")

                    val eventName = eventArg.checkjstring()

                    require(eventName.isNotBlank()) {
                        "Event name cannot be empty"
                    }
                    require(eventName.length <= 128) { "Event name is too long" }

                    eventDispatcher.unregisterEventListener(eventName)

                    Log.d(TAG, "Lua listener removed: $eventName")
                    return NIL
                }
            })
        }
        api.set("events", eventsTable)

        // api.ui
        val uiTable = LuaTable().apply {
            set("notify", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    checkPermission("ui.notify")
                    var title = "Radio Plugin"
                    var message: String
                    var type = "notification"
                    if (arg.istable()) {
                        title = arg.get("title").optjstring(title)
                        message = arg.get("message").optjstring("")
                        type = arg.get("type").optjstring(type)
                    } else {
                        message = arg.tojstring()
                    }
                    require(title.length <= 256 && message.length <= 4_096) {
                        "Notification text exceeds the size limit"
                    }
                    require(type in notificationTypes) {
                        "Unsupported notification type"
                    }
                    eventDispatcher.notifyUser(title, message, type)
                    return NIL
                }
            })
        }
        api.set("ui", uiTable)

        // api.config
        val configTable = LuaTable().apply {
            set("get", object : TwoArgFunction() {
                override fun call(keyArg: LuaValue, defaultArg: LuaValue): LuaValue {
                    checkPermission("config.read")
                    val map = readJsonMap(configFile)
                    val value = map[checkStorageKey(keyArg)] ?: return defaultArg
                    return toLuaValue(value)
                }
            })
            set("set", object : TwoArgFunction() {
                override fun call(keyArg: LuaValue, valueArg: LuaValue): LuaValue {
                    checkPermission("config.write")
                    val map = readJsonMap(configFile)
                    map[checkStorageKey(keyArg)] = fromLuaValue(valueArg)
                    writeJsonMap(configFile, map)
                    return NIL
                }
            })
        }
        api.set("config", configTable)

        // api.storage
        val storageTable = LuaTable().apply {
            set("get", object : OneArgFunction() {
                override fun call(keyArg: LuaValue): LuaValue {
                    checkPermission("storage.local")
                    val map = readJsonMap(storageFile)
                    val value = map[checkStorageKey(keyArg)] ?: return NIL
                    return toLuaValue(value)
                }
            })
            set("set", object : TwoArgFunction() {
                override fun call(keyArg: LuaValue, valueArg: LuaValue): LuaValue {
                    checkPermission("storage.local")
                    val map = readJsonMap(storageFile)
                    map[checkStorageKey(keyArg)] = fromLuaValue(valueArg)
                    writeJsonMap(storageFile, map)
                    return NIL
                }
            })
        }
        api.set("storage", storageTable)

        // api.settings
        val settingsTable = LuaTable().apply {
            set("dialog", object : OneArgFunction() {
                override fun call(arg: LuaValue): LuaValue {
                    checkPermission("config.write")
                    if (!arg.istable()) return NIL
                    val title = arg.get("title").optjstring("Plugin Settings")
                    val itemsTable = arg.get("items")
                    val onSaveFn = arg.get("on_save")

                    val activity = activityProvider() ?: (context as? Activity) ?: return NIL
                    activity.runOnUiThread {
                        showSettingsDialog(activity, title, itemsTable, onSaveFn)
                    }
                    return NIL
                }
            })
        }
        api.set("settings", settingsTable)

        // api.plugins
        val pluginsTable = LuaTable().apply {
            set("get_installed", object : ZeroArgFunction() {
                override fun call(): LuaValue {
                    checkPermission("plugins.read")
                    val list = PluginManager.getInstance(context).getInstalledPluginManifests()
                    val t = LuaTable()
                    list.forEachIndexed { idx, m ->
                        val pt = LuaTable().apply {
                            set("id", m.id)
                            set("name", m.name)
                            set("version", m.version)
                        }
                        t.set(idx + 1, pt)
                    }
                    return t
                }
            })
        }
        api.set("plugins", pluginsTable)

        return api
    }

    private fun readJsonMap(file: File): MutableMap<String, Any?> {
        if (!file.exists()) return mutableMapOf()
        require(file.length() <= MAX_JSON_BYTES) {
            "${file.name} exceeds the storage size limit"
        }

        return try {
            val text = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<MutableMap<String, Any?>>() {}.type

            gson.fromJson<MutableMap<String, Any?>>(text, type)
                ?: mutableMapOf()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON file ${file.name}", e)
            throw IllegalStateException("Could not read plugin data file ${file.name}", e)
        }
    }

    private fun writeJsonMap(file: File, map: Map<String, Any?>) {
        val bytes = gson.toJson(map).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_JSON_BYTES) {
            "Plugin data exceeds the storage size limit"
        }
        val parent = file.parentFile

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Cannot create directory for ${file.name}")
        }

        val atomicFile = AtomicFile(file)
        var stream: FileOutputStream? = null

        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            atomicFile.finishWrite(stream)
            stream = null
        } catch (e: Exception) {
            if (stream != null) {
                try {
                    atomicFile.failWrite(stream)
                } catch (failure: Exception) {
                    e.addSuppressed(failure)
                }
            }
            Log.e(TAG, "Error writing JSON file ${file.name}", e)
            throw IllegalStateException("Could not write plugin data file ${file.name}", e)
        }
    }

    private fun checkStorageKey(key: LuaValue): String {
        val keyString = key.checkjstring()
        require(keyString.isNotBlank() && keyString.length <= MAX_STORAGE_KEY_LENGTH) {
            "Storage key must contain 1-$MAX_STORAGE_KEY_LENGTH characters"
        }
        return keyString
    }

    private fun boundedLogMessage(value: LuaValue): String {
        val message = value.tojstring()
        return if (message.length <= 4_096) message else message.take(4_096)
    }

    private fun toLuaValue(obj: Any?, depth: Int = 0): LuaValue {
        require(depth <= MAX_JSON_DEPTH) {
            "Maximum JSON nesting depth exceeded"
        }

        return when (obj) {
            null -> LuaValue.NIL
            is Boolean -> LuaValue.valueOf(obj)
            is Number -> LuaValue.valueOf(obj.toDouble())
            is String -> LuaValue.valueOf(obj)

            is List<*> -> LuaTable().apply {
                require(obj.size <= MAX_TABLE_ENTRIES) {
                    "JSON array exceeds the entry limit"
                }
                obj.forEachIndexed { index, value ->
                    set(index + 1, toLuaValue(value, depth + 1))
                }
            }

            is Map<*, *> -> {
                require(obj.size <= MAX_TABLE_ENTRIES) {
                    "JSON object exceeds the entry limit"
                }
                LuaTable().apply {
                    obj.forEach { (key, value) ->
                        require(key is String) {
                            "JSON objects must use string keys"
                        }
                        set(key, toLuaValue(value, depth + 1))
                    }
                }
            }

            else -> LuaValue.valueOf(obj.toString())
        }
    }

    private fun fromLuaValue(
        value: LuaValue,
        depth: Int = 0
    ): Any? {
        require(depth <= MAX_JSON_DEPTH) {
            "Maximum JSON nesting depth exceeded"
        }

        return when {
            value.isnil() -> null
            value.isboolean() -> value.toboolean()
            value.isnumber() -> {
                val number: Double = value.todouble()
                require(number.isFinite()) {
                    "NaN and infinite values cannot be stored in JSON"
                }
                number
            }
            value.isstring() -> value.tojstring().also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) {
                    "Stored string exceeds the size limit"
                }
            }

            value.istable() -> {
                val table = value.checktable()
                val entries = mutableListOf<Pair<LuaValue, LuaValue>>()

                var key: LuaValue = LuaValue.NIL

                while (true) {
                    val result = table.next(key)
                    val nextKey: LuaValue = result.arg1()

                    if (nextKey.isnil()) break

                    val entryValue: LuaValue = result.arg(2)
                    entries.add(nextKey to entryValue)

                    require(entries.size <= MAX_TABLE_ENTRIES) {
                        "Lua table exceeds the entry limit"
                    }

                    key = nextKey
                }

                val indexes = entries.mapNotNull { (entryKey, _) ->
                    if (!entryKey.isnumber()) {
                        null
                    } else {
                        val n: Double = entryKey.todouble()

                        if (
                            n >= 1.0 &&
                            n <= entries.size.toDouble() &&
                            n % 1.0 == 0.0
                        ) {
                            n.toInt()
                        } else {
                            null
                        }
                    }
                }

                val isArray =
                    entries.isNotEmpty() &&
                            indexes.size == entries.size &&
                            indexes.toSet().size == entries.size &&
                            indexes.sorted() == (1..entries.size).toList()

                if (isArray) {
                    entries
                        .sortedBy { (entryKey, _) -> entryKey.todouble() }
                        .map { (_, entryValue) ->
                            fromLuaValue(entryValue, depth + 1)
                        }
                } else {
                    require(entries.all { (entryKey, _) ->
                        entryKey.isstring()
                    }) {
                        "JSON objects must use string keys; mixed-key tables are unsupported"
                    }

                    val result: MutableMap<String, Any?> = linkedMapOf()

                    for ((entryKey, entryValue) in entries) {
                        result[entryKey.tojstring()] =
                            fromLuaValue(entryValue, depth + 1)
                    }

                    result
                }
            }

            else -> throw IllegalArgumentException(
                "Unsupported Lua value type for JSON storage"
            )
        }
    }

    private fun showSettingsDialog(
        activity: Activity,
        title: String,
        itemsTable: LuaValue,
        onSaveFn: LuaValue
    ) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }

        val configMap = readJsonMap(configFile)
        val viewMap = mutableMapOf<String, Any>()

        if (itemsTable.istable()) {
            val length = itemsTable.length()
            for (i in 1..length) {
                val item = itemsTable.get(i)
                if (item.istable()) {
                    val type = item.get("type").optjstring("text")
                    val key = item.get("key").optjstring("field_$i")
                    val label = item.get("label").optjstring(key)
                    val defaultVal = configMap[key] ?: item.get("default").let { if (it.isboolean()) it.toboolean() else it.optjstring("") }

                    if (type == "switch") {
                        val checkBox = MaterialCheckBox(activity).apply {
                            text = label
                            isChecked = when (defaultVal) {
                                is Boolean -> defaultVal
                                else -> defaultVal.toString().toBoolean()
                            }
                        }
                        layout.addView(checkBox)
                        viewMap[key] = checkBox
                    } else {
                        val textLayout = TextInputLayout(activity).apply {
                            this.hint = label
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = 16 }
                        }
                        val editText = TextInputEditText(activity).apply {
                            setText(defaultVal?.toString() ?: "")
                        }
                        textLayout.addView(editText)
                        layout.addView(textLayout)
                        viewMap[key] = editText
                    }
                }
            }
        }

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(R.string.dialog_generic_button_okay) { _, _ ->
                val resultTable = LuaTable()
                val configUpdates = mutableMapOf<String, Any?>()

                viewMap.forEach { (key, view) ->
                    when (view) {
                        is MaterialCheckBox -> {
                            val checked = view.isChecked
                            resultTable.set(key, LuaValue.valueOf(checked))
                            configUpdates[key] = checked
                        }
                        is TextInputEditText -> {
                            val text = view.text.toString()
                            resultTable.set(key, LuaValue.valueOf(text))
                            configUpdates[key] = text
                        }
                    }
                }

                val map = readJsonMap(configFile)
                map.putAll(configUpdates)
                writeJsonMap(configFile, map)

                if (onSaveFn.isfunction()) {
                    try {
                        onSaveFn.call(resultTable)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in on_save callback for plugin '${manifest.id}'", e)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_generic_button_cancel, null)
            .show()
    }
}
