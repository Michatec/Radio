package com.michatec.radio.helpers

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import com.michatec.radio.R
import java.util.Locale


/*
 * LanguageHelper object
 */
object LanguageHelper {

    /* Define log tag */
    private val TAG: String = LanguageHelper::class.java.simpleName


    /* Sets the app language on the activity */
    fun setLanguage(context: Context, languageCode: String): Boolean {
        if (languageCode.isEmpty()) {
            Log.i(TAG, "No language code provided, using system default")
            return false
        }

        if (languageCode == "system") {
            Log.i(TAG, "Reverting to system default locale")
            if (context is Activity) {
                context.recreate()
            }
            return true
        }

        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)


        if (context is Activity) {
            context.recreate()
        }

        Log.i(TAG, "Locale changed to: $languageCode")
        return true
    }


    /* Returns a localized resources object */
    fun getCurrentLanguage(context: Context): String {
        return when (val languageCode = PreferencesHelper.loadSelectedLanguage()) {
            "system" -> context.getString(R.string.pref_language_system)
            "en" -> context.getString(R.string.pref_language_en)
            "de" -> context.getString(R.string.pref_language_de)
            "fr" -> context.getString(R.string.pref_language_fr)
            "ru" -> context.getString(R.string.pref_language_ru)
            "ja" -> context.getString(R.string.pref_language_ja)
            "nl" -> context.getString(R.string.pref_language_nl)
            "pl" -> context.getString(R.string.pref_language_pl)
            "el" -> context.getString(R.string.pref_language_el)
            "da" -> context.getString(R.string.pref_language_da)
            else -> languageCode
        }
    }
}
