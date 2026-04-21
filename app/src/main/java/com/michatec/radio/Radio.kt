package com.michatec.radio

import android.app.Application
import com.michatec.radio.helpers.AppThemeHelper
import com.michatec.radio.helpers.LanguageHelper
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.PreferencesHelper.initPreferences


/**
 * Radio.class
 */
class Radio : Application() {


    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        initPreferences()
        // set Dark / Light theme state
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
        LanguageHelper.setLanguage(this, PreferencesHelper.loadSelectedLanguage())
    }

}
