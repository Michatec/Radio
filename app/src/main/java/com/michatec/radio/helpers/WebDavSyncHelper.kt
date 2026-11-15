package com.michatec.radio.helpers

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object WebDavSyncHelper {

    private val TAG = WebDavSyncHelper::class.java.simpleName

    suspend fun testConnection(serverUrl: String, username: String, pass: String): Int = withContext(Dispatchers.IO) {
        try {
            val url = URI(serverUrl).toURL()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 5000
                readTimeout = 5000
                if (username.isNotEmpty() && pass.isNotEmpty()) {
                    val auth = "$username:$pass"
                    val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encodedAuth")
                }
            }
            connection.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV connection test failed: $e")
            -1
        }
    }

    suspend fun uploadBackup(context: Context, serverUrl: String, username: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFolder = context.getExternalFilesDir("") ?: return@withContext false
            val tempZipFile = File(context.cacheDir, "radio_backup_temp.zip")
            if (tempZipFile.exists()) tempZipFile.delete()

            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    zipFolder(zos, sourceFolder, "")
                }
            }

            val url = URI(serverUrl).toURL()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                doOutput = true
                setChunkedStreamingMode(0)
                if (username.isNotEmpty() && pass.isNotEmpty()) {
                    val auth = "$username:$pass"
                    val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encodedAuth")
                }
                setRequestProperty("Content-Type", "application/zip")
            }

            connection.outputStream.use { os ->
                FileInputStream(tempZipFile).use { fis ->
                    fis.copyTo(os)
                }
            }

            val responseCode = connection.responseCode
            tempZipFile.delete()
            responseCode in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV upload failed: $e")
            false
        }
    }

    suspend fun downloadAndRestore(context: Context, serverUrl: String, username: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URI(serverUrl).toURL()
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                if (username.isNotEmpty() && pass.isNotEmpty()) {
                    val auth = "$username:$pass"
                    val encodedAuth = Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP)
                    setRequestProperty("Authorization", "Basic $encodedAuth")
                }
            }

            if (connection.responseCode !in 200..299) {
                Log.e(TAG, "WebDAV download failed with response code: ${connection.responseCode}")
                return@withContext false
            }

            val tempZipFile = File(context.cacheDir, "radio_download_temp.zip")
            connection.inputStream.use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output)
                }
            }

            val destinationFolder = context.getExternalFilesDir("") ?: return@withContext false
            val buffer = ByteArray(1024)
            ZipInputStream(FileInputStream(tempZipFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    try {
                        val newFile = getFile(destinationFolder, entry)
                        if (entry.isDirectory) {
                            if (!newFile.isDirectory && !newFile.mkdirs()) {
                                Log.w(TAG, "Failed to create directory $newFile")
                            }
                        } else {
                            newFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                            FileOutputStream(newFile).use { fos ->
                                var len: Int
                                while (zipIn.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Skipping malicious or invalid ZIP entry: ${entry.name}, $e")
                    }
                    entry = zipIn.nextEntry
                }
            }

            tempZipFile.delete()

            withContext(Dispatchers.Main) {
                CollectionHelper.sendCollectionBroadcast(
                    context,
                    java.util.Calendar.getInstance().time
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "WebDAV restore failed: $e")
            false
        }
    }

    @Throws(IOException::class)
    private fun getFile(destinationFolder: File, zipEntry: ZipEntry): File {
        val destinationFile = File(destinationFolder, zipEntry.name)
        val destinationFolderPath = destinationFolder.canonicalPath
        val destinationFilePath = destinationFile.canonicalPath
        if (!destinationFilePath.startsWith(destinationFolderPath + File.separator)) {
            throw IOException("ZIP entry is outside of destination folder: " + zipEntry.name)
        }
        return destinationFile
    }

    private fun zipFolder(zipOutputStream: ZipOutputStream, source: File, parentDirPath: String) {
        if (source.isDirectory) {
            val data = ByteArray(2048)
            for (file in source.listFiles() ?: emptyArray()) {
                val path = if (parentDirPath.isEmpty()) file.name else parentDirPath + File.separator + file.name
                if (file.isDirectory) {
                    zipFolder(zipOutputStream, file, path)
                } else {
                    FileInputStream(file).use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            val entry = ZipEntry(path)
                            entry.time = file.lastModified()
                            entry.size = file.length()
                            zipOutputStream.putNextEntry(entry)
                            while (true) {
                                val readBytes = bis.read(data)
                                if (readBytes == -1) break
                                zipOutputStream.write(data, 0, readBytes)
                            }
                        }
                    }
                }
            }
        }
    }
}
