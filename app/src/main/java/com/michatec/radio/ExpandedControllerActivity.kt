package com.michatec.radio

import android.content.Context
import android.content.res.Configuration
import android.view.Menu
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
import com.michatec.radio.helpers.PreferencesHelper
import java.util.Locale

class ExpandedControllerActivity : ExpandedControllerActivity() {
    override fun attachBaseContext(newBase: Context) {
        val languageCode = PreferencesHelper.loadSelectedLanguage()
        val context = if (languageCode.isEmpty() || languageCode == "system") {
            // Use system default locale
            newBase
        } else {
            val locale = Locale.forLanguageTag(languageCode)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        }
        super.attachBaseContext(context)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.expanded_controller, menu)
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        return true
    }

    override fun onResume() {
        try {
            super.onResume()
        } catch (_: ClassCastException) {
            // Fix for lifecycle exception on some devices (e.g. Samsung)
        }
    }
}
