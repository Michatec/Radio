package com.michatec.radio.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import com.google.android.gms.cast.framework.CastContext
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R

/**
 * A custom DialogFragment that replaces the default Google Cast controller dialog.
 * It is shown when a Cast session is already active.
 */
class CustomCastControllerDialogFragment : MediaRouteControllerDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_cast_connected, null)

        val castContext = CastContext.getSharedInstance(context)
        val castSession = castContext.sessionManager.currentCastSession
        val deviceName = castSession?.castDevice?.friendlyName ?: context.getString(R.string.media_route_menu_title)

        view.findViewById<TextView>(R.id.textViewDeviceName).text = deviceName

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .create()

        view.findViewById<MaterialButton>(R.id.buttonStopCasting).setOnClickListener {
            castContext.sessionManager.endCurrentSession(true)
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.buttonCancel).setOnClickListener {
            dialog.dismiss()
        }

        return dialog
    }
}