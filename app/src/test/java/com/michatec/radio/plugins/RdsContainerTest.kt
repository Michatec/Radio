package com.michatec.radio.plugins

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipOutputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry

class RdsContainerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testPackAndUnpack() {
        val sourceDir = tempFolder.newFolder("plugin_source")
        val manifestFile = File(sourceDir, "manifest.json")
        manifestFile.writeText("""
            {
              "schemaVersion": 1,
              "id": "com.test.plugin",
              "name": "Test Plugin",
              "version": "1.0.0",
              "main": "main.lua"
            }
        """.trimIndent())

        val mainLua = File(sourceDir, "main.lua")
        mainLua.writeText("print('hello')")

        val rdsFile = tempFolder.newFile("test_plugin.rds")
        RdsContainer.pack(sourceDir, rdsFile)

        val targetDir = tempFolder.newFolder("plugin_target")
        val manifest = RdsContainer.unpack(rdsFile, targetDir)

        assertEquals("com.test.plugin", manifest.id)
        assertTrue(File(targetDir, "main.lua").exists())
    }

    @Test(expected = SecurityException::class)
    fun testPathTraversalPrevention() {
        val rdsFile = tempFolder.newFile("malicious.rds")
        ZipOutputStream(FileOutputStream(rdsFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("malicious content".toByteArray())
            zos.closeEntry()
        }

        val targetDir = tempFolder.newFolder("target")
        RdsContainer.unpack(rdsFile, targetDir)
    }

    @Test(expected = SecurityException::class)
    fun testBackslashPathTraversalPrevention() {
        val rdsFile = tempFolder.newFile("backslash_traversal.rds")
        ZipOutputStream(FileOutputStream(rdsFile)).use { zos ->
            zos.putNextEntry(ZipEntry("..\\evil.txt"))
            zos.write("malicious content".toByteArray())
            zos.closeEntry()
        }

        RdsContainer.unpack(rdsFile, tempFolder.newFolder("backslash_target"))
    }

    @Test
    fun testDoesNotDeleteExistingExtractionContents() {
        val rdsFile = tempFolder.newFile("safe.rds")
        ZipOutputStream(FileOutputStream(rdsFile)).use { zos ->
            zos.putNextEntry(ZipEntry("main.lua"))
            zos.write("return {}".toByteArray())
            zos.closeEntry()
        }
        val targetDir = tempFolder.newFolder("non_empty_target")
        val sentinel = File(targetDir, "keep.txt").apply { writeText("preserve") }

        try {
            RdsContainer.unpack(rdsFile, targetDir)
            fail("Expected non-empty extraction directory to be rejected")
        } catch (_: IllegalArgumentException) {
            assertEquals("preserve", sentinel.readText())
        }
    }
}
