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
import androidx.core.app.ActivityCompat
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import android.content.*
import com.google.android.material.snackbar.Snackbar
import com.michatec.radio.databinding.ActivityMainBinding
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
    private lateinit var binding: ActivityMainBinding
    private lateinit var appBarConfiguration: AppBarConfiguration

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
                binding.mainRoot,
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
            findViewById<View>(R.id.bottom_sheet)?.let {
                snackbar.anchorView = it
            }
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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Free Android
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE)

        // set up views
        applyCustomTheme()

        // create .nomedia file - if not yet existing
        FileHelper.createNomediaFile(getExternalFilesDir(null))

        // set up action bar
        setSupportActionBar(binding.mainToolbar)
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.main_host_container) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        NavigationUI.setupWithNavController(binding.mainToolbar, navController, appBarConfiguration)
        supportActionBar?.hide()

        // TV-specific loading logic: Hide the overlay once the app is ready
        val arg = PreferencesHelper.hasArgument("e2a5c13d8aff6f133c9bf0a0f2696d0ffa9924ba98330954516e1caf3fd9a3ee")
        if (isAndroidTV && !arg) {
            Handler(Looper.getMainLooper()).postDelayed({
                hideLoadingOverlay()
            }, 1200)
        } else {
            binding.loadingLayout.visibility = View.GONE
        }

        // register listener for changes in shared preferences
        PreferencesHelper.registerPreferenceChangeListener(sharedPreferenceChangeListener)

        // request permissions
        if (!isAndroidTV && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        // register remote server error receiver
        LocalBroadcastManager.getInstance(this).registerReceiver(
            remoteServerErrorReceiver,
            IntentFilter(Keys.ACTION_REMOTE_SERVER_ERROR)
        )
    }

    override fun onStop() {
        super.onStop()
        // unregister remote server error receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(remoteServerErrorReceiver)
    }

    /* Hides the loading/splash overlay */
    private fun hideLoadingOverlay() {
        binding.loadingLayout.let { overlay ->
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
            binding.mainRoot.setBackgroundColor(color)
        } else {
            // Reset to default theme background color
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
            binding.mainRoot.setBackgroundColor(typedValue.data)
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

    /* Overrides onNewIntent from AppCompatActivity */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }


    /* Overrides onSupportNavigateUp from AppCompatActivity */
    override fun onSupportNavigateUp(): Boolean {
        val navHostFragment =
            supportFragmentManager.findFragmentById(binding.mainHostContainer.id) as NavHostFragment
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
     * Receiver for remote server error
     */
    private val remoteServerErrorReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Keys.ACTION_REMOTE_SERVER_ERROR) {
                val snackbar = Snackbar.make(
                    binding.mainRoot,
                    R.string.error_webserver,
                    Snackbar.LENGTH_LONG
                )
                this@MainActivity.findViewById<View>(R.id.bottom_sheet)?.let {
                    snackbar.anchorView = it
                }
                snackbar.show()
            }
        }
    }
    /*
     * End of declaration
     */

}
