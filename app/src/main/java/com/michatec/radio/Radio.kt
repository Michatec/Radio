package com.michatec.radio

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import com.michatec.radio.helpers.AppThemeHelper
import com.michatec.radio.helpers.LanguageHelper
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.PreferencesHelper.initPreferences


/**
 * Radio.class
 */
class Radio : Application(), SharedPreferences.OnSharedPreferenceChangeListener {


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        initPreferences()
        // set Dark / Light theme state
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
        LanguageHelper.setLanguage(this, PreferencesHelper.loadSelectedLanguage())

        // register listener for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Keys.PREF_THEME_SELECTION,
            Keys.PREF_CUSTOM_THEME_COLOR,
            Keys.PREF_CUSTOM_THEME_ENABLED,
            Keys.PREF_CUSTOM_THEME_INDEX -> {
                val intent = Intent(Keys.ACTION_THEME_CHANGED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(intent)
            }
        }
    }

}
