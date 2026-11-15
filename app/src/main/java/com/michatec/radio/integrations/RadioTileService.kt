package com.michatec.radio.integrations

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.michatec.radio.MainActivity
import com.michatec.radio.R
import com.michatec.radio.api.RadioApiService
import com.michatec.radio.helpers.PreferencesHelper

class RadioTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            val isPlaying = try {
                RadioApiService.playbackController?.isPlaying() ?: PreferencesHelper.loadPlayerState().isPlaying
            } catch (_: Exception) {
                PreferencesHelper.loadPlayerState().isPlaying
            }
            tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (isPlaying) {
                try {
                    RadioApiService.playbackController?.getCurrentStationName() ?: getString(R.string.app_name)
                } catch (_: Exception) {
                    getString(R.string.app_name)
                }
            } else {
                getString(R.string.app_name)
            }
            tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_radio_tile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tile.subtitle = if (isPlaying) {
                    try {
                        RadioApiService.playbackController?.getCurrentStationName() ?: getString(R.string.app_name)
                    } catch (_: Exception) {
                        getString(R.string.app_name)
                    }
                } else {
                    getString(R.string.tile_subtitle_tap_to_play)
                }
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @SuppressLint("StartActivityAndCollapseDeprecated")
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
