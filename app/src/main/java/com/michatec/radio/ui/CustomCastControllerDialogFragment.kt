package com.michatec.radio.ui

import android.app.Dialog
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.framework.CastContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.ui.screens.CastControllerContent
import com.michatec.radio.ui.theme.RadioTheme

/**
 * A custom DialogFragment that replaces the default Google Cast controller dialog.
 * It is shown when a Cast session is already active.
 */
class CustomCastControllerDialogFragment : MediaRouteControllerDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val castContext = CastContext.getSharedInstance(context)

        val composeView = ComposeView(context).apply {
            setContent {
                RadioTheme {
                    val castSession = remember { castContext.sessionManager.currentCastSession }
                    var volume by remember { mutableFloatStateOf(castSession?.volume?.toFloat() ?: 0f) }
                    var isMute by remember { mutableStateOf(castSession?.isMute ?: false) }
                    val deviceName = remember { castSession?.castDevice?.friendlyName ?: context.getString(R.string.media_route_menu_title) }

                    DisposableEffect(castSession) {
                        val listener = object : Cast.Listener() {
                            override fun onVolumeChanged() {
                                volume = castSession?.volume?.toFloat() ?: 0f
                                isMute = castSession?.isMute ?: false
                            }
                        }
                        castSession?.addCastListener(listener)
                        onDispose {
                            castSession?.removeCastListener(listener)
                        }
                    }

                    CastControllerContent(
                        deviceName = deviceName,
                        volume = volume,
                        isMute = isMute,
                        onVolumeChanged = { newVolume ->
                            castSession?.volume = newVolume.toDouble()
                            volume = newVolume
                        },
                        onStopCasting = {
                            castContext.sessionManager.endCurrentSession(true)
                            dismiss()
                        },
                        onCancel = {
                            dismiss()
                        }
                    )
                }
            }
        }

        return MaterialAlertDialogBuilder(context)
            .setView(composeView)
            .create()
    }
}