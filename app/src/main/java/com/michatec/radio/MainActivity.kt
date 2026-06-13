package com.michatec.radio

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.util.TypedValue
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import com.google.android.material.snackbar.Snackbar
import com.michatec.radio.helpers.AppThemeHelper
import com.michatec.radio.helpers.FileHelper
import com.michatec.radio.helpers.LanguageHelper
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.ThemeHelper
import org.woheller69.freeDroidWarn.FreeDroidWarn
import java.util.Locale

/*
 * MainActivity class
 */
class MainActivity : AppCompatActivity() {

    /* Main class variables */
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var mainRoot: View

    // Check if the device running the app is an Android TV instance
    private val isAndroidTV: Boolean by lazy {
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    // request notification permission (for Android 13+)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            val snackbar = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.snackbar_failed_permission_notification,
                Snackbar.LENGTH_LONG
            )
            // If the user permanently denied the permission, show a link to settings
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                snackbar.setAction(R.string.fragment_settings_title) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
            }
            snackbar.anchorView = findViewById(R.id.bottom_sheet)
            snackbar.show()
        }
    }

    /* Overrides attachBaseContext from AppCompatActivity */
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

    /* Overrides onCreate from AppCompatActivity */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)

        // Free Android
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)

        // set up views
        setContentView(R.layout.activity_main)
        mainRoot = findViewById(R.id.main_root)
        applyCustomTheme()

        // create .nomedia file - if not yet existing
        FileHelper.createNomediaFile(getExternalFilesDir(null))

        // set up action bar
        setSupportActionBar(findViewById(R.id.main_toolbar))
        val toolbar: Toolbar = findViewById(R.id.main_toolbar)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_host_container) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)
        supportActionBar?.hide()

        // TV-specific loading logic: Hide the overlay once the app is ready
        if (isAndroidTV) {
            Handler(Looper.getMainLooper()).postDelayed({
                hideLoadingOverlay()
            }, 1200)
        } else {
            findViewById<View>(R.id.loading_layout)?.visibility = View.GONE
        }

        // register listener for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)

        // request permissions
        if (!isAndroidTV && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /* Hides the loading/splash overlay */
    private fun hideLoadingOverlay() {
        findViewById<View>(R.id.loading_layout)?.let { overlay ->
            if (overlay.isVisible) {
                overlay.animate().alpha(0f).setDuration(500)
                    .withEndAction { overlay.visibility = View.GONE }
            }
        }
    }

    private fun applyCustomTheme() {
        val enabled = PreferencesHelper.loadCustomThemeEnabled()
        if (enabled) {
            var color = PreferencesHelper.loadCustomThemeColor(this)
            val index = PreferencesHelper.loadCustomThemeIndex()
            
            if (index != -1) {
                // Color belongs to a predefined group. Update it based on current mode.
                val colors = ThemeHelper.getPredefinedColors(this)
                if (index < colors.size) {
                    val updatedColor = colors[index]
                    if (updatedColor != color) {
                        color = updatedColor
                        // Save the updated color to keep preferences in sync with the current mode
                        PreferencesHelper.saveCustomThemeColor(color)
                    }
                }
            }
            mainRoot.setBackgroundColor(color)
        } else {
            // Reset to default theme background color
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            mainRoot.setBackgroundColor(typedValue.data)
        }
    }


    /* Overrides onResume from AppCompatActivity */
    override fun onResume() {
        try {
            super.onResume()
        } catch (_: ClassCastException) {
            // Do nothing
        }
    }


    /* Overrides onSupportNavigateUp from AppCompatActivity */
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_host_container) as NavHostFragment
        val navController = navHostFragment.navController
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }


    /* Overrides onDestroy from AppCompatActivity */
    override fun onDestroy() {
        super.onDestroy()
        // unregister listener for changes in shared preferences
        PreferencesHelper.unregisterPreferenceChangeListener(sharedPreferenceChangeListener)
    }


    /*
     * Defines the listener for changes in shared preferences
     */
    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Keys.PREF_THEME_SELECTION -> {
                    AppThemeHelper.setTheme(PreferencesHelper.loadThemeSelection())
                }
                Keys.PREF_LANGUAGE_SELECTED -> {
                    LanguageHelper.setLanguage(this, PreferencesHelper.loadSelectedLanguage())
                }
                Keys.PREF_CUSTOM_THEME_COLOR, Keys.PREF_CUSTOM_THEME_ENABLED, Keys.PREF_CUSTOM_THEME_INDEX -> {
                    applyCustomTheme()
                }
            }
        }
    /*
     * End of declaration
     */

}
