/*
 * BackupHelper.kt
 * Implements the BackupHelper object
 * A BackupHelper provides helper methods for backing up and restoring the radio station collection
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.michatec.radio.helpers

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import com.google.android.material.snackbar.Snackbar
import com.michatec.radio.R
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupHelper {


    /* Define log tag */
    private val TAG: String = BackupHelper::class.java.simpleName


    /* Compresses all files in the app's external files directory into destination zip file */
    fun backup(view: View, context: Context, destinationUri: Uri) {
        val sourceFolder: File? = context.getExternalFilesDir("")
        if (sourceFolder != null && sourceFolder.isDirectory) {
            Snackbar.make(
                view,
                "${
                    FileHelper.getFileName(
                        context,
                        destinationUri
                    )
                } ${context.getString(R.string.toastmessage_backed_up)}",
                Snackbar.LENGTH_LONG
            ).show()
            val resolver: ContentResolver = context.contentResolver
            val outputStream: OutputStream? = resolver.openOutputStream(destinationUri)
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
                zipOutputStream.use {
                    zipFolder(it, sourceFolder, "")
                }
            }
        } else {
            Log.e(TAG, "Unable to access External Storage.")
        }
    }


    /* Extracts zip backup  file and restores files and folders - Credit: https://www.baeldung.com/java-compress-and-uncompress*/
    fun restore(view: View, context: Context, sourceUri: Uri) {
        Snackbar.make(view, R.string.toastmessage_restored, Snackbar.LENGTH_LONG).show()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // bypass "ZipException" for Android 14 or above applications when zip file names contain ".." or start with "/"
            dalvik.system.ZipPathValidator.clearCallback()
        }

        val resolver: ContentResolver = context.contentResolver
        val sourceInputStream: InputStream? = resolver.openInputStream(sourceUri)
        val destinationFolder: File? = context.getExternalFilesDir("")
        val buffer = ByteArray(1024)
        val zipInputStream = ZipInputStream(sourceInputStream)
        var zipEntry: ZipEntry? = zipInputStream.nextEntry

        // iterate through ZipInputStream until last ZipEntry
        while (zipEntry != null) {
            try {
                val newFile: File = getFile(destinationFolder!!, zipEntry)
                when (zipEntry.isDirectory) {
                    // CASE: Folder
                    true -> {
                        // create folder if zip entry is a folder
                        if (!newFile.isDirectory && !newFile.mkdirs()) {
                            Log.w(TAG, "Failed to create directory $newFile")
                        }
                    }
                    // CASE: File
                    false -> {
                        // create parent directory, if necessary
                        val parent: File? = newFile.parentFile
                        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
                            Log.w(TAG, "Failed to create directory $parent")
                        }
                        // write file content
                        val fileOutputStream = FileOutputStream(newFile)
                        var len: Int
                        while (zipInputStream.read(buffer).also { len = it } > 0) {
                            fileOutputStream.write(buffer, 0, len)
                        }
                        fileOutputStream.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to safely create file. $e")
            }
            // get next entry - zipEntry will be null, when zipInputStream has no more entries left
            zipEntry = zipInputStream.nextEntry
        }
        zipInputStream.closeEntry()
        zipInputStream.close()

        // notify CollectionViewModel that collection has changed
        CollectionHelper.sendCollectionBroadcast(
            context,
            modificationDate = Calendar.getInstance().time
        )
    }


    /* Compresses folder into ZIP file - Credit: https://stackoverflow.com/a/52216574 */
    private fun zipFolder(zipOutputStream: ZipOutputStream, source: File, parentDirPath: String) {
        // source.listFiles() will return null, if source is not a directory
        if (source.isDirectory) {
            val data = ByteArray(2048)
            // get all File objects in folder
            for (file in source.listFiles()!!) {
                // make sure that path does not start with a separator (/)
                val path: String = if (parentDirPath.isEmpty()) file.name else parentDirPath + File.separator + file.name
                when (file.isDirectory) {
                    // CASE: Folder
                    true -> {
                        // call zipFolder recursively to add files within this folder
                        zipFolder(zipOutputStream, file, path)
                    }
                    // CASE: File
                    false -> {
                        FileInputStream(file).use { fileInputStream ->
                            BufferedInputStream(fileInputStream).use { bufferedInputStream ->
                                val entry = ZipEntry(path)
                                entry.time = file.lastModified()
                                entry.size = file.length()
                                zipOutputStream.putNextEntry(entry)
                                while (true) {
                                    val readBytes = bufferedInputStream.read(data)
                                    if (readBytes == -1) {
                                        break
                                    }
                                    zipOutputStream.write(data, 0, readBytes)
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    /* Normalize file path - protects against zip slip attack */
    @Throws(IOException::class)
    private fun getFile(destinationFolder: File, zipEntry: ZipEntry): File {
        val destinationFile = File(destinationFolder, zipEntry.name)
        val destinationFolderPath = destinationFolder.canonicalPath
        val destinationFilePath = destinationFile.canonicalPath
        // make sure that zipEntry path is in the destination folder
        if (!destinationFilePath.startsWith(destinationFolderPath + File.separator)) {
            throw IOException("ZIP entry is not within of the destination folder: " + zipEntry.name)
        }
        return destinationFile
    }


}
