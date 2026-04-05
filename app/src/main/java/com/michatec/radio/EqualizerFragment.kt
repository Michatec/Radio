package com.michatec.radio

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.michatec.radio.helpers.PreferencesHelper

/*
 * EqualizerFragment class: Handles audio frequency settings
 */
class EqualizerFragment : PreferenceFragmentCompat() {

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
            // Manually update SeekBars to 0
            findPreference<SeekBarPreference>(Keys.PREF_EQ_LOW)?.value = 0
            findPreference<SeekBarPreference>(Keys.PREF_EQ_MID)?.value = 0
            findPreference<SeekBarPreference>(Keys.PREF_EQ_HIGH)?.value = 0
            return@setOnPreferenceClickListener true
        }
        screen.addPreference(resetPreference)

        // EQ Low
        val eqLow = SeekBarPreference(context)
        eqLow.title = getString(R.string.pref_eq_low_title)
        eqLow.key = Keys.PREF_EQ_LOW
        eqLow.setIcon(R.drawable.ic_music_note_24dp)
        eqLow.min = -12
        eqLow.max = 12
        eqLow.showSeekBarValue = true
        eqLow.setDefaultValue(0)
        screen.addPreference(eqLow)

        // EQ Mid
        val eqMid = SeekBarPreference(context)
        eqMid.title = getString(R.string.pref_eq_mid_title)
        eqMid.key = Keys.PREF_EQ_MID
        eqMid.setIcon(R.drawable.ic_music_note_24dp)
        eqMid.min = -12
        eqMid.max = 12
        eqMid.showSeekBarValue = true
        eqMid.setDefaultValue(0)
        screen.addPreference(eqMid)

        // EQ High
        val eqHigh = SeekBarPreference(context)
        eqHigh.title = getString(R.string.pref_eq_high_title)
        eqHigh.key = Keys.PREF_EQ_HIGH
        eqHigh.setIcon(R.drawable.ic_music_note_24dp)
        eqHigh.min = -12
        eqHigh.max = 12
        eqHigh.showSeekBarValue = true
        eqHigh.setDefaultValue(0)
        screen.addPreference(eqHigh)

        preferenceScreen = screen
    }
}
