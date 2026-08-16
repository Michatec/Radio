package com.michatec.radio.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.mediarouter.app.MediaRouteControllerDialogFragment
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
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
    private var castSession: CastSession? = null

    private val castListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            updateVolumeSeekBar()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        _binding = DialogCastConnectedBinding.inflate(LayoutInflater.from(context))

        val castContext = CastContext.getSharedInstance(context)
        castSession = castContext.sessionManager.currentCastSession
        val deviceName = castSession?.castDevice?.friendlyName ?: context.getString(R.string.media_route_menu_title)

        binding.textViewDeviceName.text = deviceName

        updateVolumeSeekBar()
        binding.seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    castSession?.volume = progress / 100.0
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

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

    override fun onStart() {
        super.onStart()
        castSession?.addCastListener(castListener)
        updateVolumeSeekBar()
    }

    override fun onStop() {
        super.onStop()
        castSession?.removeCastListener(castListener)
    }

    private fun updateVolumeSeekBar() {
        val volume = castSession?.volume ?: 0.0
        binding.seekBarVolume.progress = (volume * 100).toInt()
        binding.seekBarVolume.isEnabled = castSession?.isMute == false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}