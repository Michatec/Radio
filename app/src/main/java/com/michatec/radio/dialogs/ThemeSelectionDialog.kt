package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.Keys
import com.michatec.radio.R
import com.michatec.radio.helpers.AppThemeHelper
import com.michatec.radio.helpers.PreferencesHelper


/*
 * ThemeSelectionDialog class
 */
class ThemeSelectionDialog(private var themeSelectionDialogListener: ThemeSelectionDialogListener) {

    /* Interface used to communicate back to activity */
    interface ThemeSelectionDialogListener {
        fun onThemeSelectionDialog(dialogResult: Boolean, selectedTheme: String) {
        }
    }


    /* Main class variables */
    private lateinit var dialog: AlertDialog


    /* Construct and show dialog */
    fun show(context: Context) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // inflate custom layout
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_theme_selection, null)

        // find radio buttons
        val radioGroup = view.findViewById<android.widget.RadioGroup>(R.id.theme_radio_group)
        val radioFollowSystem = view.findViewById<RadioButton>(R.id.radio_theme_follow_system)
        val radioLight = view.findViewById<RadioButton>(R.id.radio_theme_light)
        val radioDark = view.findViewById<RadioButton>(R.id.radio_theme_dark)

        // set current selection
        val currentTheme = AppThemeHelper.getCurrentTheme(context)
        when (currentTheme) {
            context.getString(R.string.pref_theme_selection_mode_device_default) -> {
                radioFollowSystem.isChecked = true
            }
            context.getString(R.string.pref_theme_selection_mode_light) -> {
                radioLight.isChecked = true
            }
            context.getString(R.string.pref_theme_selection_mode_dark) -> {
                radioDark.isChecked = true
            }
        }

        // set up radio group listener
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                R.id.radio_theme_follow_system -> Keys.STATE_THEME_FOLLOW_SYSTEM
                R.id.radio_theme_light -> Keys.STATE_THEME_LIGHT_MODE
                R.id.radio_theme_dark -> Keys.STATE_THEME_DARK_MODE
                else -> Keys.STATE_THEME_FOLLOW_SYSTEM
            }
            // save theme selection to preferences
            PreferencesHelper.saveThemeSelection(selectedTheme)
            // apply theme immediately
            AppThemeHelper.setTheme(selectedTheme)
            // notify listener
            themeSelectionDialogListener.onThemeSelectionDialog(true, selectedTheme)
            // dismiss dialog
            dialog.dismiss()
        }

        // set custom view
        builder.setView(view)

        // handle outside-click as cancel
        builder.setOnCancelListener {
            themeSelectionDialogListener.onThemeSelectionDialog(false, Keys.STATE_THEME_FOLLOW_SYSTEM)
        }

        // display dialog
        dialog = builder.create()
        dialog.show()
    }
}
