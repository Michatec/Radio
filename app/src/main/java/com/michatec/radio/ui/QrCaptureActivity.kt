package com.michatec.radio.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Overrides the zxing-embedded capture activity, which is declared with
 * android:screenOrientation="sensorLandscape" in the library manifest.
 * This variant follows the device orientation in both portrait and landscape.
 */
class QrCaptureActivity : CaptureActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        super.onCreate(savedInstanceState)
    }
}
