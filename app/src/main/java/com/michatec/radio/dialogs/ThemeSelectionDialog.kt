package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.Keys
import com.michatec.radio.R
import com.michatec.radio.databinding.DialogThemeSelectionBinding
import com.michatec.radio.helpers.AppThemeHelper
import com.michatec.radio.helpers.PreferencesHelper


/*
 * ThemeSelectionDialog class
 */
class ThemeSelectionDialog {
    /* Main class variables */
    private lateinit var dialog: AlertDialog


    /* Construct and show dialog */
    fun show(context: Context) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // inflate custom layout
        val binding = DialogThemeSelectionBinding.inflate(LayoutInflater.from(context))

        // set current selection
        val currentTheme = AppThemeHelper.getCurrentTheme(context)
        when (currentTheme) {
            context.getString(R.string.pref_theme_selection_mode_device_default) -> {
                binding.radioThemeFollowSystem.isChecked = true
            }
            context.getString(R.string.pref_theme_selection_mode_light) -> {
                binding.radioThemeLight.isChecked = true
            }
            context.getString(R.string.pref_theme_selection_mode_dark) -> {
                binding.radioThemeDark.isChecked = true
            }
        }

        // set up radio group listener
        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
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
            // dismiss dialog
            dialog.dismiss()
        }

        // set custom view
        builder.setView(binding.root)

        // display dialog
        dialog = builder.create()
        dialog.show()
    }
}
