package com.michatec.radio.integrations

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import rikka.shizuku.Shizuku as ShizukuApi

class Shizuku(private val context: Context) {

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

    fun isInstalled(): Boolean {
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
            return ShellResult(false, "", "Shizuku permission not granted.", -1)
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

            val stdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
            val exitCode = process.waitFor()

            ShellResult(
                success = exitCode == 0,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ShellResult(false, "", e.message ?: "Execution error", -1)
        }
    }
}
