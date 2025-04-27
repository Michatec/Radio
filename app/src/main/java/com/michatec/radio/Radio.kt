/*
 * Radio.kt
 * Implements the Radio class
 * Radio is the base Application class that sets up day and night theme
 *
 * This file is part of
 * TRANSISTOR - Radio App for Android
 *
 * Copyright (c) 2015-22 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package com.michatec.radio

import android.app.Application
import com.michatec.radio.helpers.AppThemeHelper
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.PreferencesHelper.initPreferences


/**
 * Radio.class
 */
class Radio : Application() {

    /* Define log tag */
    private val TAG: String = Radio::class.java.simpleName

    /* Implements onCreate */
    override fun onCreate() {
        super.onCreate()
        initPreferences()
        // set Dark / Light theme state
        AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
    }


    /* Implements onTerminate */
    override fun onTerminate() {
        super.onTerminate()
    }

}
