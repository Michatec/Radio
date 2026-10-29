package com.michatec.radio.integrations

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import rikka.shizuku.Shizuku as ShizukuApi

class Shizuku {

    companion object {
        const val REQUEST_CODE = 1001
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    data class ShellResult(
        val success: Boolean,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    @Volatile
    private var binderReady = false

    private val binderReceivedListener =
        ShizukuApi.OnBinderReceivedListener {
            binderReady = true
        }

    private val binderDeadListener =
        ShizukuApi.OnBinderDeadListener {
            binderReady = false
        }

    init {
        try {
            ShizukuApi.addBinderReceivedListenerSticky(binderReceivedListener)
            ShizukuApi.addBinderDeadListener(binderDeadListener)
            binderReady = ShizukuApi.pingBinder()
        } catch (_: Exception) {
            binderReady = false
        }
    }

    fun isInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun isBinderAlive(): Boolean {
        return try {
            ShizukuApi.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (ShizukuApi.isPreV11()) return false
        if (!isBinderAlive()) return false
        return try {
            ShizukuApi.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE) {
        if (ShizukuApi.isPreV11()) return
        if (!isBinderAlive()) return
        try {
            ShizukuApi.requestPermission(requestCode)
        } catch (_: Exception) {
        }
    }

    fun execShellCommand(command: String): ShellResult {
        if (!hasPermission()) {
            return ShellResult(
                success = false,
                stdout = "",
                stderr = "Shizuku permission not granted.",
                exitCode = -1
            )
        }
        return try {
            val newProcessMethod = ShizukuApi::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            ShellResult(
                success = exitCode == 0,
                stdout = stdout.trim(),
                stderr = stderr.trim(),
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(
                success = false,
                stdout = "",
                stderr = e.message ?: "Execution error",
                exitCode = -1
            )
        }
    }

    fun optimizeBackgroundPlayback(context: Context): ShellResult {
        val pkg = context.packageName
        val cmd = "cmd deviceidle whitelist +$pkg || dumpsys deviceidle whitelist +$pkg; cmd appops set $pkg RUN_IN_BACKGROUND allow; cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow"
        return execShellCommand(cmd)
    }

    fun removeBackgroundPlaybackOptimization(context: Context): ShellResult {
        val pkg = context.packageName
        val cmd = "cmd deviceidle whitelist -$pkg || dumpsys deviceidle whitelist -$pkg; cmd appops set $pkg RUN_IN_BACKGROUND default; cmd appops set $pkg RUN_ANY_IN_BACKGROUND default"
        return execShellCommand(cmd)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun unregisterListeners() {
        try {
            ShizukuApi.removeBinderReceivedListener(binderReceivedListener)
            ShizukuApi.removeBinderDeadListener(binderDeadListener)
        } catch (_: Exception) {
        }
    }
}