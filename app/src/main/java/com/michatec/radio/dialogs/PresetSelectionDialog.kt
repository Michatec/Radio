package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.databinding.DialogPresetSelectionBinding
import com.michatec.radio.helpers.PreferencesHelper


/*
 * PresetSelectionDialog class
 */
class PresetSelectionDialog(private var presetSelectionDialogListener: PresetSelectionDialogListener) {

    /* Interface used to communicate back to activity */
    interface PresetSelectionDialogListener {
        fun onPresetSelectionDialog(dialogResult: Boolean, selectedPreset: String)
    }


    /* Main class variables */
    private lateinit var dialog: AlertDialog


    /* Construct and show dialog */
    fun show(context: Context) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // inflate custom layout
        val binding = DialogPresetSelectionBinding.inflate(LayoutInflater.from(context))

        // set current selection
        val currentPreset = PreferencesHelper.loadSelectedPreset()
        when (currentPreset) {
            "" -> binding.radioPresetNone.isChecked = true
            getString(context, R.string.pref_preset_rock) -> binding.radioPresetRock.isChecked = true
            getString(context, R.string.pref_preset_pop) -> binding.radioPresetPop.isChecked = true
            getString(context, R.string.pref_preset_jazz) -> binding.radioPresetJazz.isChecked = true
            getString(context, R.string.pref_preset_flat) -> binding.radioPresetFlat.isChecked = true
            else -> binding.radioPresetNone.isChecked = true
        }

        // set up radio group listener
        binding.presetRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedPreset = when (checkedId) {
                R.id.radio_preset_none -> ""
                R.id.radio_preset_rock -> getString(context, R.string.pref_preset_rock)
                R.id.radio_preset_pop -> getString(context, R.string.pref_preset_pop)
                R.id.radio_preset_jazz -> getString(context, R.string.pref_preset_jazz)
                R.id.radio_preset_flat -> getString(context, R.string.pref_preset_flat)
                else -> ""
            }
            // save preset selection to preferences
            PreferencesHelper.saveSelectedPreset(selectedPreset)
            // notify listener
            presetSelectionDialogListener.onPresetSelectionDialog(true, selectedPreset)
            // dismiss dialog
            dialog.dismiss()
        }

        // set custom view
        builder.setView(binding.root)

        // handle outside-click as cancel
        builder.setOnCancelListener {
            presetSelectionDialogListener.onPresetSelectionDialog(false, "")
        }

        // display dialog
        dialog = builder.create()
        dialog.show()
    }
}