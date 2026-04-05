package com.michatec.radio.helpers

import android.content.Context
import android.text.TextUtils
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat

/*
 * Custom SwitchPreferenceCompat that enables marquee (scrolling text) for the title
 */
class MarqueeSwitchPreference(context: Context) : SwitchPreferenceCompat(context) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val title = holder.findViewById(android.R.id.title) as? TextView
        title?.apply {
            ellipsize = TextUtils.TruncateAt.MARQUEE
            setSingleLine(true)
            marqueeRepeatLimit = -1 // Repeat indefinitely
            isSelected = true       // Required for marquee to start
            setHorizontallyScrolling(true)
        }
    }
}
