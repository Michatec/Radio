package com.michatec.radio.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.luaj.vm2.LuaError
import java.io.File

class PluginSandboxTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sandboxHidesHostAccessAndKeepsPluginModulesAvailable() {
        val pluginDir = tempFolder.newFolder("plugin")
        val modulesDir = File(pluginDir, "modules").apply { mkdirs() }
        File(modulesDir, "helper.lua").writeText("return { value = 42 }")
        val outsideFile = File(tempFolder.root, "outside.lua").apply {
            writeText("return { value = 99 }")
        }

        val sandbox = PluginSandbox.createSandboxGlobals(pluginDir)
        val globals = sandbox.globals

        assertTrue(globals.get("io").isnil())
        assertTrue(globals.get("os").get("execute").isnil())
        assertTrue(globals.get("os").get("time").isfunction())
        assertTrue(globals.get("debug").isnil())
        assertTrue(globals.get("luajava").isnil())
        assertTrue(globals.get("coroutine").isnil())
        assertTrue(globals.get("package").get("searchers").get(3).isnil())
        assertEquals(42, globals.get("require").call("helper").get("value").checkint())
        assertEquals(
            "xxa-b",
            globals.load("return string.rep('x', 2) .. table.concat({'a', 'b'}, '-')", "bounded-strings")
                .call()
                .tojstring()
        )
        assertTrue(globals.finder.findResource(outsideFile.absolutePath) == null)
    }

    @Test
    fun sandboxStopsExcessiveLuaExecution() {
        val sandbox = PluginSandbox.createSandboxGlobals(tempFolder.newFolder("loop_plugin"))
        sandbox.resetInstructionBudget()

        sandbox.globals.load("local t = os.time()", "os-time").call()

        try {
            sandbox.globals.load("while true do end", "instruction-limit").call()
            throw AssertionError("Expected Lua instruction limit to stop the script")
        } catch (error: LuaError) {
            assertTrue(error.message.orEmpty().contains("Plugin exceeded its instruction limit"))
        }
    }

    @Test
    fun sandboxLimitsLargeStringAllocations() {
        val globals = PluginSandbox.createSandboxGlobals(tempFolder.newFolder("large_string_plugin")).globals

        try {
            globals.load("return string.rep('x', 2000000)", "string-limit").call()
            throw AssertionError("Expected string allocation limit to reject the result")
        } catch (error: LuaError) {
            assertTrue(error.message.orEmpty().contains("string.rep result exceeds the size size limit") || error.message.orEmpty().contains("string.rep result exceeds the size limit"))
        }
    }
}
