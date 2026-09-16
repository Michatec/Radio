package com.michatec.radio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val isSystemDark = isSystemInDarkTheme()

    val colorScheme = remember(context, isSystemDark) {
        val customThemeEnabled = PreferencesHelper.loadCustomThemeEnabled()
        if (customThemeEnabled) {
            var colorInt = PreferencesHelper.loadCustomThemeColor(context)
            val index = PreferencesHelper.loadCustomThemeIndex()
            if (index != -1) {
                val colors = ThemeHelper.getPredefinedColors(context)
                if (index < colors.size) {
                    colorInt = colors[index]
                }
            }
            val backgroundColor = Color(colorInt)
            val isDark = backgroundColor.luminance() < 0.5f
            if (isDark) {
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
        } else {
            if (isSystemDark) {
                darkColorScheme()
            } else {
                lightColorScheme()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            Surface(content = content)
        }
    )
}
