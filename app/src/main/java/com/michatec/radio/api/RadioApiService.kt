package com.michatec.radio.api

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.google.gson.Gson
import com.michatec.radio.PlayerService
import com.michatec.radio.helpers.CollectionHelper
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.PreferencesHelper

class RadioApiService : Service() {

    interface PlaybackController {
        fun play()
        fun pause()
        fun stop()
        fun isPlaying(): Boolean
        fun getCurrentStationName(): String
        fun getCurrentMetadata(): String
        fun playStationByUuid(uuid: String)
        fun getSavedStationsJson(): String
        fun getFavoriteStationsJson(): String
    }

    companion object {
        var playbackController: PlaybackController? = null
    }

    private val binder = object : IRadioApi.Stub() {
        @OptIn(UnstableApi::class)
        override fun play() {
            if (playbackController != null) {
                playbackController?.play()
            } else {
                val intent = Intent(applicationContext, PlayerService::class.java).apply {
                    action = "com.michatec.radio.action.START_PLAYER_SERVICE"
                }
                applicationContext.startService(intent)
                playbackController?.play()
            }
        }

        @OptIn(UnstableApi::class)
        override fun pause() {
            if (playbackController != null) {
                playbackController?.pause()
            } else {
                PreferencesHelper.saveIsPlaying(false)
            }
        }

        @OptIn(UnstableApi::class)
        override fun stop() {
            if (playbackController != null) {
                playbackController?.stop()
            } else {
                PreferencesHelper.saveIsPlaying(false)
            }
        }

        @OptIn(UnstableApi::class)
        override fun isPlaying(): Boolean {
            return playbackController?.isPlaying() ?: PreferencesHelper.loadPlayerState().isPlaying
        }

        @OptIn(UnstableApi::class)
        override fun getCurrentStationName(): String {
            return playbackController?.getCurrentStationName() ?: run {
                try {
                    val collection = FileHelper.readCollection(applicationContext)
                    val currentUuid = PreferencesHelper.loadPlayerState().stationUuid
                    CollectionHelper.getStation(collection, currentUuid).name
                } catch (_: Exception) {
                    ""
                }
            }
        }

        @OptIn(UnstableApi::class)
        override fun getCurrentMetadata(): String {
            return playbackController?.getCurrentMetadata() ?: ""
        }

        @OptIn(UnstableApi::class)
        override fun playStationByUuid(uuid: String) {
            if (playbackController != null) {
                playbackController?.playStationByUuid(uuid)
            } else {
                PreferencesHelper.saveCurrentStationId(uuid)
                val intent = Intent(applicationContext, PlayerService::class.java).apply {
                    action = "com.michatec.radio.action.START_PLAYER_SERVICE"
                }
                applicationContext.startService(intent)
                playbackController?.playStationByUuid(uuid)
            }
        }

        override fun getSavedStationsJson(): String {
            return playbackController?.getSavedStationsJson() ?: run {
                try {
                    val collection = FileHelper.readCollection(applicationContext)
                    Gson().toJson(collection.stations)
                } catch (_: Exception) {
                    "[]"
                }
            }
        }

        override fun getFavoriteStationsJson(): String {
            return playbackController?.getFavoriteStationsJson() ?: run {
                try {
                    val collection = FileHelper.readCollection(applicationContext)
                    val favorites = collection.stations.filter { it.starred }
                    Gson().toJson(favorites)
                } catch (_: Exception) {
                    "[]"
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}
