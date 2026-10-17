package com.michatec.radio.plugins

import org.junit.Assert.*
import org.junit.Test

class PluginManifestTest {

    @Test
    fun testValidManifest() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "com.example.radio.test",
              "name": "Test Plugin",
              "version": "1.0.0",
              "main": "main.lua",
              "permissions": ["radio.read", "ui.notify"]
            }
        """.trimIndent()

        val manifest = PluginManifest.fromJson(json)
        assertEquals("com.example.radio.test", manifest.id)
        assertEquals("Test Plugin", manifest.name)
        assertEquals("1.0.0", manifest.version)
        assertEquals(2, manifest.permissions.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidId() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "invalid id with spaces",
              "name": "Test",
              "version": "1.0.0",
              "main": "main.lua"
            }
        """.trimIndent()
        PluginManifest.fromJson(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidVersion() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "com.valid.id",
              "name": "Test",
              "version": "not-semver",
              "main": "main.lua"
            }
        """.trimIndent()
        PluginManifest.fromJson(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsPathTraversalInMainScript() {
        PluginManifest.fromJson(
            """
            {
              "id": "com.example.test",
              "name": "Test",
              "version": "1.0.0",
              "main": "../outside.lua"
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsTraversalPluginId() {
        PluginManifest.fromJson(
            """
            {
              "id": "..",
              "name": "Test",
              "version": "1.0.0"
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsUnknownPermission() {
        PluginManifest.fromJson(
            """
            {
              "id": "com.example.test",
              "name": "Test",
              "version": "1.0.0",
              "permissions": ["application.admin"]
            }
            """.trimIndent()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRejectsInvalidEntrypoint() {
        PluginManifest.fromJson(
            """
            {
              "id": "com.example.test",
              "name": "Test",
              "version": "1.0.0",
              "entrypoints": {"load": "load; os.execute('bad')"}
            }
            """.trimIndent()
        )
    }
}
