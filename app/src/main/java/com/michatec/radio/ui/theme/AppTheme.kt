package com.michatec.radio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.michatec.radio.helpers.PreferencesHelper
import com.michatec.radio.helpers.ThemeHelper

@Composable
fun RadioTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val customThemeEnabled = PreferencesHelper.loadCustomThemeEnabled()
    val isDark: Boolean
    val backgroundColor: Color

    if (customThemeEnabled) {
        var colorInt = PreferencesHelper.loadCustomThemeColor(context)
        val index = PreferencesHelper.loadCustomThemeIndex()
        if (index != -1) {
            val colors = ThemeHelper.getPredefinedColors(context)
            if (index < colors.size) {
                colorInt = colors[index]
            }
        }
        backgroundColor = Color(colorInt)
        isDark = backgroundColor.luminance() < 0.5f
    } else {
        isDark = isSystemInDarkTheme()
        backgroundColor = if (isDark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            background = backgroundColor,
            surface = backgroundColor
        )
    } else {
        lightColorScheme(
            background = backgroundColor,
            surface = backgroundColor
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
