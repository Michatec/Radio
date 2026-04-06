package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.getString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
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
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_preset_selection, null)

        // find radio buttons
        val radioGroup = view.findViewById<android.widget.RadioGroup>(R.id.preset_radio_group)
        val radioNone = view.findViewById<RadioButton>(R.id.radio_preset_none)
        val radioRock = view.findViewById<RadioButton>(R.id.radio_preset_rock)
        val radioPop = view.findViewById<RadioButton>(R.id.radio_preset_pop)
        val radioJazz = view.findViewById<RadioButton>(R.id.radio_preset_jazz)
        val radioFlat = view.findViewById<RadioButton>(R.id.radio_preset_flat)

        // set current selection
        val currentPreset = PreferencesHelper.loadSelectedPreset()
        when (currentPreset) {
            "" -> radioNone.isChecked = true
            getString(context, R.string.pref_preset_rock) -> radioRock.isChecked = true
            getString(context, R.string.pref_preset_pop) -> radioPop.isChecked = true
            getString(context, R.string.pref_preset_jazz) -> radioJazz.isChecked = true
            getString(context, R.string.pref_preset_flat) -> radioFlat.isChecked = true
            else -> radioNone.isChecked = true
        }

        // set up radio group listener
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
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
        builder.setView(view)

        // handle outside-click as cancel
        builder.setOnCancelListener {
            presetSelectionDialogListener.onPresetSelectionDialog(false, "")
        }

        // display dialog
        dialog = builder.create()
        dialog.show()
    }
}