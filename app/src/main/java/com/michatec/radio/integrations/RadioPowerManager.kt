package com.michatec.radio.integrations

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

class RadioPowerManager(private val context: Context, private val shizuku: Shizuku) {

    companion object {
        private const val BACKUP_FILE_NAME = "radio_power_backup.json"
    }

    data class SystemState(
        val isDozeWhitelisted: Boolean,
        val runInBackgroundState: String,
        val runAnyInBackgroundState: String
    )

    data class OperationResult(
        val success: Boolean,
        val dozeResult: Shizuku.ShellResult?,
        val runInBackgroundResult: Shizuku.ShellResult?,
        val runAnyInBackgroundResult: Shizuku.ShellResult?,
        val errorMessage: String?
    )

    private val backupFile: File
        get() = File(context.filesDir, BACKUP_FILE_NAME)

    private val gson = Gson()

    fun hasBackup(): Boolean {
        return backupFile.exists()
    }

    private fun loadBackup(): SystemState? {
        if (!backupFile.exists()) return null
        return try {
            val json = backupFile.readText()
            gson.fromJson(json, SystemState::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveBackupAtomically(state: SystemState) {
        val tempFile = File(context.filesDir, "$BACKUP_FILE_NAME.tmp")
        try {
            val json = gson.toJson(state)
            FileOutputStream(tempFile).use { fos ->
                fos.write(json.toByteArray())
                fos.fd.sync()
            }
            if (backupFile.exists()) {
                backupFile.delete()
            }
            tempFile.renameTo(backupFile)
        } catch (_: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun clearBackup() {
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    fun captureCurrentState(): SystemState {
        val pkg = context.packageName

        val dozeResult = shizuku.execShellCommand("dumpsys deviceidle whitelist")
        val isDoze = dozeResult.success && dozeResult.stdout.contains(pkg)

        val appOpsRib = shizuku.execShellCommand("cmd appops get $pkg RUN_IN_BACKGROUND")
        val ribState = parseAppOpsState(appOpsRib.stdout)

        val appOpsRaib = shizuku.execShellCommand("cmd appops get $pkg RUN_ANY_IN_BACKGROUND")
        val raibState = parseAppOpsState(appOpsRaib.stdout)

        return SystemState(isDoze, ribState, raibState)
    }

    private fun parseAppOpsState(output: String): String {
        val lower = output.lowercase()
        return when {
            lower.contains("allow") -> "allow"
            lower.contains("ignore") -> "ignore"
            lower.contains("deny") -> "deny"
            else -> "default"
        }
    }

    fun enableOptimization(): OperationResult {
        val pkg = context.packageName

        if (!hasBackup()) {
            val initialState = captureCurrentState()
            saveBackupAtomically(initialState)
        }

        val dozeRes = shizuku.execShellCommand("cmd deviceidle whitelist +$pkg")
        val ribRes = shizuku.execShellCommand("cmd appops set $pkg RUN_IN_BACKGROUND allow")
        val raibRes = shizuku.execShellCommand("cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow")

        val allSuccess = dozeRes.success && ribRes.success && raibRes.success
        if (!allSuccess) {
            rollback()
            return OperationResult(
                false,
                dozeRes,
                ribRes,
                raibRes,
                "Failed to apply optimizations, rolled back."
            )
        }

        return OperationResult(true, dozeRes, ribRes, raibRes, null)
    }

    fun disableOptimization(): OperationResult {
        return restoreOriginalState()
    }

    fun restoreOriginalState(): OperationResult {
        val backup = loadBackup() ?: return OperationResult(true, null, null, null, "No backup found.")
        val pkg = context.packageName

        val dozeCmd = if (backup.isDozeWhitelisted) "cmd deviceidle whitelist +$pkg" else "cmd deviceidle whitelist -$pkg"
        val dozeRes = shizuku.execShellCommand(dozeCmd)

        val ribRes = shizuku.execShellCommand("cmd appops set $pkg RUN_IN_BACKGROUND ${backup.runInBackgroundState}")
        val raibRes = shizuku.execShellCommand("cmd appops set $pkg RUN_ANY_IN_BACKGROUND ${backup.runAnyInBackgroundState}")

        val allSuccess = dozeRes.success && ribRes.success && raibRes.success
        if (allSuccess) {
            clearBackup()
            return OperationResult(true, dozeRes, ribRes, raibRes, null)
        } else {
            return OperationResult(false, dozeRes, ribRes, raibRes, "Restoration partially failed.")
        }
    }

    private fun rollback() {
        val backup = loadBackup()
        if (backup != null) {
            val pkg = context.packageName
            val dozeCmd = if (backup.isDozeWhitelisted) "cmd deviceidle whitelist +$pkg" else "cmd deviceidle whitelist -$pkg"
            shizuku.execShellCommand(dozeCmd)
            shizuku.execShellCommand("cmd appops set $pkg RUN_IN_BACKGROUND ${backup.runInBackgroundState}")
            shizuku.execShellCommand("cmd appops set $pkg RUN_ANY_IN_BACKGROUND ${backup.runAnyInBackgroundState}")
            clearBackup()
        }
    }

    fun isOptimized(): Boolean {
        return hasBackup()
    }
}
