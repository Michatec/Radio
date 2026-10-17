package com.michatec.radio.plugins

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PluginManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testMasterCompanionPluginPackagingAndManifest() {
        val pluginSourceDir = tempFolder.newFolder("master_source")

        File(pluginSourceDir, "manifest.json").writeText("""
            {
              "schemaVersion": 1,
              "id": "com.example.radio.mastercompanion",
              "name": "Radio Master Companion",
              "version": "1.0.0",
              "main": "main.lua",
              "permissions": [
                "radio.read",
                "audio.control",
                "network.http",
                "events.listen",
                "ui.notify",
                "config.read",
                "config.write",
                "storage.local",
                "plugins.read",
                "log"
              ],
              "entrypoints": {
                "load": "on_load",
                "unload": "on_unload",
                "settings": "on_settings"
              }
            }
        """.trimIndent())

        File(pluginSourceDir, "main.lua").writeText("""
            local plugin = {}
            function plugin.on_load(api)
                api.log.info("Master Companion loaded")
            end
            function plugin.on_station_changed(station)
                api.log.info("Station changed: " .. station.name)
            end
            function plugin.on_unload()
                print("Master Companion unloaded")
            end
            return plugin
        """.trimIndent())

        val rdsFile = tempFolder.newFile("master.rds")
        RdsContainer.pack(pluginSourceDir, rdsFile)

        val targetDir = tempFolder.newFolder("master_target")
        val manifest = RdsContainer.unpack(rdsFile, targetDir)

        assertEquals("com.example.radio.mastercompanion", manifest.id)
        assertEquals("Radio Master Companion", manifest.name)
        assertTrue(File(targetDir, "main.lua").exists())
    }
}
