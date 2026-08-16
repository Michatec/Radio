package com.michatec.radio.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import com.google.android.gms.cast.framework.CastContext
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.databinding.DialogCastConnectedBinding

/**
 * A custom DialogFragment that replaces the default Google Cast controller dialog.
 * It is shown when a Cast session is already active.
 */
class CustomCastControllerDialogFragment : MediaRouteControllerDialogFragment() {

    private var _binding: DialogCastConnectedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        _binding = DialogCastConnectedBinding.inflate(LayoutInflater.from(context))

        val castContext = CastContext.getSharedInstance(context)
        val castSession = castContext.sessionManager.currentCastSession
        val deviceName = castSession?.castDevice?.friendlyName ?: context.getString(R.string.media_route_menu_title)

        binding.textViewDeviceName.text = deviceName

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .create()

        binding.buttonStopCasting.setOnClickListener {
            castContext.sessionManager.endCurrentSession(true)
            dialog.dismiss()
        }

        binding.buttonCancel.setOnClickListener {
            dialog.dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}