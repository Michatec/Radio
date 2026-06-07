package com.michatec.radio.helpers

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.michatec.radio.Keys
import java.util.Locale


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
            // extract metadata
            when (val entry = metadata.get(i)) {
                is IcyInfo -> {
                    val streamTitle = entry.title
                    if (!streamTitle.isNullOrEmpty()) {
                        if (streamTitle.contains(" - ")) {
                            artist = streamTitle.substringBefore(" - ").trim()
                            title = streamTitle.substringAfter(" - ").trim()
                        } else {
                            title = streamTitle
                        }
                    }
                }

                is IcyHeaders -> {
                    Log.i(TAG, "icyHeaders: ${entry.name} - ${entry.genre}")
                }

                is Id3Frame -> {
                    if (entry is TextInformationFrame) {
                        when (entry.id) {
                            "TIT2" -> entry.values.getOrNull(0)?.let { if (it.isNotEmpty()) title = it.trim() } // Title
                            "TPE1" -> entry.values.getOrNull(0)?.let { if (it.isNotEmpty()) artist = it.trim() } // Artist
                            "TALB" -> entry.values.getOrNull(0)?.let { if (it.isNotEmpty()) album = it.trim() } // Album
                        }
                    } else {
                        Log.d(TAG, "Unhandled ID3 frame: ${entry.javaClass.simpleName}")
                    }
                }

                is VorbisComment -> {
                    when (entry.key.uppercase(Locale.ROOT)) {
                        "TITLE" -> if (entry.value.isNotEmpty()) title = entry.value.trim()
                        "ARTIST" -> if (entry.value.isNotEmpty()) artist = entry.value.trim()
                        "ALBUM" -> if (entry.value.isNotEmpty()) album = entry.value.trim()
                    }
                }

                else -> {
                    Log.w(TAG, "Unsupported metadata received (type = ${entry.javaClass.simpleName})")
                }
            }
        }

        // Build metadata string
        var metadataString = when {
            artist.isNotEmpty() && title.isNotEmpty() -> "$artist - $title"
            artist.isNotEmpty() -> artist
            title.isNotEmpty() -> title
            else -> ""
        }

        if (album.isNotEmpty() && metadataString.isNotEmpty()) {
            metadataString += " ($album)"
        }

        // ensure a max length of the metadata string
        return metadataString.take(Keys.DEFAULT_MAX_LENGTH_OF_METADATA_ENTRY)
    }


}
