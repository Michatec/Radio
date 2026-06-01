package com.michatec.radio.helpers

import android.content.Context
import android.content.res.Configuration
import androidx.core.graphics.toColorInt

object ThemeHelper {
    fun getPredefinedColors(context: Context): List<Int> {
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return if (isDarkMode) {
            // Darker colors for dark mode background
            listOf(
                "#FF1D3E66".toColorInt(), // Default Dark
                "#FF3E1D1D".toColorInt(), // Red Dark
                "#FF1D3E3E".toColorInt(), // Teal Dark
                "#FF3E1D2E".toColorInt(), // Pink Dark
                "#FF001A33".toColorInt(), // Dark Blue Dark
                "#FF1D3E1D".toColorInt(), // Green Dark
                "#FF3E2E1D".toColorInt(), // Orange Dark
                "#FF2E1D1D".toColorInt(), // Brown Dark
                "#FF1D242E".toColorInt(), // Blue Grey Dark
                "#FF000000".toColorInt()  // Black
            )
        } else {
            // Lighter colors for light mode background
            listOf(
                "#FFDAE2FF".toColorInt(), // Light Default
                "#FFFF897D".toColorInt(), // Light Red
                "#FF4DB6AC".toColorInt(), // Light Teal
                "#FFF48FB1".toColorInt(), // Light Pink
                "#FF90CAF9".toColorInt(), // Light Blue
                "#FFA5D6A7".toColorInt(), // Light Green
                "#FFFFAB91".toColorInt(), // Light Orange
                "#FFBCAAA4".toColorInt(), // Light Brown
                "#FFB0BEC5".toColorInt(), // Light Blue Grey
                "#FFFFFFFF".toColorInt()  // White
            )
        }
    }
}
