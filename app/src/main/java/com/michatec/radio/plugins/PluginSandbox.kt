package com.michatec.radio.plugins

import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaTable
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.Bit32Lib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.PackageLib
import org.luaj.vm2.lib.ResourceFinder
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.TwoArgFunction
import org.luaj.vm2.lib.VarArgFunction
import org.luaj.vm2.lib.jse.JseBaseLib
import org.luaj.vm2.lib.jse.JseMathLib
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PluginSandbox {
    private const val MAX_SCRIPT_BYTES = 5 * 1024 * 1024
    private const val INSTRUCTION_HOOK_INTERVAL = 1_000
    private const val MAX_INSTRUCTIONS = 500_000
    private const val MAX_STRING_RESULT_BYTES = 1_048_576

    class Runtime internal constructor(
        val globals: Globals,
        private val resetBudget: () -> Unit
    ) {
        fun resetInstructionBudget() = resetBudget()
    }

    fun createSandboxGlobals(pluginDir: File): Runtime {
        val root = pluginDir.canonicalFile.toPath()
        val globals = Globals()
        globals.load(JseBaseLib())
        globals.load(PackageLib())
        globals.load(Bit32Lib())
        globals.load(TableLib())
        globals.load(StringLib())
        globals.load(JseMathLib())
        limitLargeAllocations(globals)

        globals.finder = ResourceFinder { filename ->
            try {
                val candidate = File(filename).canonicalFile
                val candidatePath = candidate.toPath()
                if (!candidatePath.startsWith(root) ||
                    !candidate.isFile ||
                    candidate.length() > MAX_SCRIPT_BYTES
                ) {
                    null
                } else {
                    val signature = ByteArray(4)
                    val signatureBytes = FileInputStream(candidate).use { it.read(signature) }
                    if (
                        signatureBytes == 4 &&
                        signature.contentEquals(byteArrayOf(0x1b, 0x4c, 0x75, 0x61))
                    ) {
                        null
                    } else {
                        FileInputStream(candidate)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        val packageTable = globals.get("package")
        packageTable.get("searchers").set(3, LuaValue.NIL)
        packageTable.set("loadlib", LuaValue.NIL)
        packageTable.set("cpath", LuaValue.valueOf(""))
        packageTable.set(
            "path",
            LuaValue.valueOf(
                "${pluginDir.canonicalPath}${File.separator}?.lua;" +
                    "${pluginDir.canonicalPath}${File.separator}modules${File.separator}?.lua"
            )
        )

        val debugLibrary = DebugLib()
        globals.load(debugLibrary)
        val instructionBudget = InstructionBudget()
        globals.get("debug").get("sethook").call(
            instructionBudget.hook,
            LuaValue.valueOf(""),
            LuaValue.valueOf(INSTRUCTION_HOOK_INTERVAL)
        )

        globals.set("io", LuaValue.NIL)
        val safeOs = LuaTable().apply {
            set("time", object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    return valueOf(System.currentTimeMillis() / 1000.0)
                }
            })
            set("date", object : VarArgFunction() {
                override fun invoke(args: Varargs): Varargs {
                    val format = args.optjstring(1, "%Y-%m-%d %H:%M:%S")
                    val time = args.optlong(2, System.currentTimeMillis() / 1000)
                    val date = Date(time * 1000)
                    val formatted = SimpleDateFormat(
                        format.replace("%Y", "yyyy").replace("%m", "MM").replace("%d", "dd")
                            .replace("%H", "HH").replace("%M", "mm").replace("%S", "ss"),
                        Locale.getDefault()
                    ).format(date)
                    return valueOf(formatted)
                }
            })
        }
        globals.set("os", safeOs)
        globals.set("debug", LuaValue.NIL)
        globals.set("coroutine", LuaValue.NIL)
        globals.set("loadfile", LuaValue.NIL)
        globals.set("dofile", LuaValue.NIL)
        globals.set("load", LuaValue.NIL)
        globals.set("loadstring", LuaValue.NIL)
        globals.set("luajava", LuaValue.NIL)
        globals.STDIN = ByteArrayInputStream(byteArrayOf())
        val discardOutput = PrintStream(object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) = Unit
        })
        globals.STDOUT = discardOutput
        globals.STDERR = discardOutput

        LoadState.install(globals)
        LuaC.install(globals)
        return Runtime(globals, instructionBudget::reset)
    }

    private fun limitLargeAllocations(globals: Globals) {
        val stringLibrary = globals.get("string")
        stringLibrary.set("format", LuaValue.NIL)
        stringLibrary.set("gsub", LuaValue.NIL)
        stringLibrary.set("rep", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val source = args.checkjstring(1)
                val count = args.checkint(2)
                val separator = args.optjstring(3, "")
                if (count < 0) throw LuaError("string.rep count cannot be negative")
                val outputBytes = source.toByteArray(Charsets.UTF_8).size.toLong() * count +
                    separator.toByteArray(Charsets.UTF_8).size.toLong() * (count - 1).coerceAtLeast(0)
                if (outputBytes > MAX_STRING_RESULT_BYTES) {
                    throw LuaError("string.rep result exceeds the size limit")
                }
                return valueOf(buildString {
                    repeat(count) { index ->
                        if (index > 0) append(separator)
                        append(source)
                    }
                })
            }
        })

        globals.get("table").set("concat", object : VarArgFunction() {
            override fun invoke(args: Varargs): Varargs {
                val table = args.checktable(1)
                val separator = args.optjstring(2, "")
                val first = args.optint(3, 1)
                val last = args.optint(4, table.length())
                if (first < 1 || last < first - 1 || last - first >= 10_000) {
                    throw LuaError("table.concat range is invalid or too large")
                }

                val result = StringBuilder()
                var resultBytes = 0L
                for (index in first..last) {
                    val value = table.get(index)
                    if (!value.isstring() && !value.isnumber()) {
                        throw LuaError("table.concat values must be strings or numbers")
                    }
                    val part = value.tojstring()
                    if (index > first) {
                        resultBytes += separator.toByteArray(Charsets.UTF_8).size
                        result.append(separator)
                    }
                    resultBytes += part.toByteArray(Charsets.UTF_8).size
                    if (resultBytes > MAX_STRING_RESULT_BYTES) {
                        throw LuaError("table.concat result exceeds the size limit")
                    }
                    result.append(part)
                }
                return valueOf(result.toString())
            }
        })
    }

    private class InstructionBudget {
        private var remaining = MAX_INSTRUCTIONS

        val hook = object : TwoArgFunction() {
            override fun call(event: LuaValue, line: LuaValue): LuaValue {
                remaining -= INSTRUCTION_HOOK_INTERVAL
                if (remaining <= 0) {
                    throw LuaError("Plugin exceeded its instruction limit")
                }
                return NIL
            }
        }

        fun reset() {
            remaining = MAX_INSTRUCTIONS
        }
    }
}
