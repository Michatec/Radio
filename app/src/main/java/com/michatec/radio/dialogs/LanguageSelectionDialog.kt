package com.michatec.radio.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.michatec.radio.R
import com.michatec.radio.helpers.PreferencesHelper


/*
 * LanguageSelectionDialog class
 */
class LanguageSelectionDialog(private var languageSelectionDialogListener: LanguageSelectionDialogListener) {

    /* Interface used to communicate back to activity */
    interface LanguageSelectionDialogListener {
        fun onLanguageSelectionDialog(dialogResult: Boolean, selectedLanguage: String)
    }


    /* Main class variables */
    private lateinit var dialog: AlertDialog


    /* Data class representing a supported language */
    data class Language(
        val code: String,
        val nameResId: Int
    )


    /* List of supported languages - displayed in their own language */
    private val supportedLanguages = listOf(
        Language("system", R.string.pref_language_system),
        Language("en", R.string.pref_language_en),
        Language("de", R.string.pref_language_de),
        Language("fr", R.string.pref_language_fr),
        Language("ru", R.string.pref_language_ru),
        Language("uk", R.string.pref_language_uk),
        Language("ja", R.string.pref_language_ja),
        Language("nl", R.string.pref_language_nl),
        Language("pl", R.string.pref_language_pl),
        Language("el", R.string.pref_language_el),
        Language("da", R.string.pref_language_da)
    )


    /* Counter for generating unique view IDs */
    private var viewIdCounter = 0x7F010001 // Starting after android.R.id.home


    /* Construct and show dialog */
    fun show(context: Context) {
        // prepare dialog builder
        val builder = MaterialAlertDialogBuilder(context)

        // inflate custom layout
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_language_selection, null)

        // find radio group
        val radioGroup = view.findViewById<RadioGroup>(R.id.language_radio_group)
        val currentLanguage = PreferencesHelper.loadSelectedLanguage()

        // add radio buttons for each supported language
        for (language in supportedLanguages) {
            val radioButton = RadioButton(context).apply {
                id = generateViewId()
                tag = language.code
                text = context.getString(language.nameResId)
                textSize = if (isTelevision(context)) 20f else 16f
                setPadding(dpToPx(context, 8), dpToPx(context, 16), dpToPx(context, 16), dpToPx(context, 16))
            }
            radioGroup.addView(radioButton)
        }

        // set current selection
        for (i in 0 until radioGroup.childCount) {
            val radioButton = radioGroup.getChildAt(i) as RadioButton
            if (radioButton.tag == currentLanguage) {
                radioButton.isChecked = true
                break
            }
        }

        // if no language is selected, check the first one (system)
        if (radioGroup.checkedRadioButtonId == -1) {
            val firstButton = radioGroup.getChildAt(0) as RadioButton
            firstButton.isChecked = true
        }

        // set up radio group listener
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedButton = radioGroup.findViewById<RadioButton>(checkedId)
            val selectedLanguageCode = selectedButton?.tag as? String ?: "system"

            // save language selection to preferences
            PreferencesHelper.saveSelectedLanguage(selectedLanguageCode)

            // notify listener
            languageSelectionDialogListener.onLanguageSelectionDialog(true, selectedLanguageCode)

            // dismiss dialog
            dialog.dismiss()
        }

        // set custom view
        builder.setView(view)

        // handle outside-click as cancel
        builder.setOnCancelListener {
            languageSelectionDialogListener.onLanguageSelectionDialog(false, "")
        }

        // display dialog
        dialog = builder.create()
        dialog.show()
    }


    /* Generate a unique view ID */
    private fun generateViewId(): Int {
        return viewIdCounter++
    }


    /* Helper function to check if device is a TV */
    private fun isTelevision(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode
        return (uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }


    /* Helper function to convert dp to pixels */
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}