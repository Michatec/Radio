package com.michatec.radio

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.michatec.radio.helpers.PreferencesHelper

/*
 * EqualizerFragment class: Handles audio frequency settings with 10-band EQ
 */
class EqualizerFragment : PreferenceFragmentCompat() {

    // EQ band frequencies matching radio.cpp
    private val eqFrequencies = arrayOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz")
    private val eqKeys = arrayOf(
        Keys.PREF_EQ_LOW,           // Band 0: 31 Hz
        Keys.PREF_EQ_BAND_1,        // Band 1: 62 Hz
        Keys.PREF_EQ_BAND_2,        // Band 2: 125 Hz
        Keys.PREF_EQ_BAND_3,        // Band 3: 250 Hz
        Keys.PREF_EQ_BAND_4,        // Band 4: 500 Hz
        Keys.PREF_EQ_BAND_5,        // Band 5: 1 kHz
        Keys.PREF_EQ_MID,           // Band 6: 2 kHz
        Keys.PREF_EQ_BAND_6,        // Band 7: 4 kHz
        Keys.PREF_EQ_BAND_7,        // Band 8: 8 kHz
        Keys.PREF_EQ_HIGH           // Band 9: 16 kHz
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.title = getString(R.string.pref_equalizer_title)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        // Reset Button
        val resetPreference = Preference(context)
        resetPreference.title = getString(R.string.pref_equalizer_reset_title)
        resetPreference.setIcon(R.drawable.ic_refresh_24dp)
        resetPreference.setOnPreferenceClickListener {
            PreferencesHelper.resetEqualizer()
            for (key in eqKeys) {
                findPreference<SeekBarPreference>(key)?.value = 0
            }
            return@setOnPreferenceClickListener true
        }
        screen.addPreference(resetPreference)

        // Create 10-band EQ
        for (i in eqKeys.indices) {
            val eqBand = SeekBarPreference(context)
            eqBand.title = "Equalizer: ${eqFrequencies[i]}"
            eqBand.key = eqKeys[i]
            eqBand.setIcon(R.drawable.ic_music_note_24dp)
            eqBand.min = -12
            eqBand.max = 12
            eqBand.showSeekBarValue = true
            eqBand.setDefaultValue(0)
            screen.addPreference(eqBand)
        }

        preferenceScreen = screen
    }
}
