/*
 * AudioHelper.kt
 * Implements the AudioHelper object
 * A AudioHelper provides helper methods for handling audio files
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.michatec.radio.helpers

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.michatec.radio.Keys
import kotlin.math.min


/*
 * AudioHelper object
 */
object AudioHelper {


    /* Define log tag */
    private val TAG: String = AudioHelper::class.java.simpleName


    /* Extract audio stream metadata */
    @OptIn(UnstableApi::class)
    fun getMetadataString(metadata: Metadata): String {
        var title = ""
        var artist = ""
        var album = ""
        for (i in 0 until metadata.length()) {
            // extract IceCast metadata
            when (val entry = metadata.get(i)) {
                is IcyInfo -> {
                    title = entry.title.toString()
                }

                is IcyHeaders -> {
                    Log.i(TAG, "icyHeaders:" + entry.name + " - " + entry.genre)
                }

                is Id3Frame -> {
                    when (entry) {
                        is TextInformationFrame -> {
                            when (entry.id) {
                                "TIT2" -> title = entry.values.getOrNull(0) ?: "" // Title
                                "TPE1" -> artist = entry.values.getOrNull(0) ?: "" // Artist
                                "TALB" -> album = entry.values.getOrNull(0) ?: "" // Album
                            }
                        }
                        else -> {
                            Log.d(TAG, "Unhandled ID3 frame: ${entry.javaClass.simpleName}")
                        }
                    }
                }

                else -> {
                    Log.w(TAG, "Unsupported metadata received (type = ${entry.javaClass.simpleName})")
                }
            }
        }
        // Build metadata string
        var metadataString = title
        if (artist.isNotEmpty() && title.isNotEmpty()) {
            metadataString = "$artist - $title"
        }
        if (album.isNotEmpty() && metadataString.isNotEmpty()) {
            metadataString += " ($album)"
        }
        // ensure a max length of the metadata string
        if (metadataString.isNotEmpty()) {
            metadataString = metadataString.take(min(metadataString.length, Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY))
        }
        return metadataString
    }


}
