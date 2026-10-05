package com.noryan.romrunner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private fun darkColors(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = onAccent(accent),
    secondary = Amber80,
    background = Slate90,
    surface = Slate90
)

private fun lightColors(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = onAccent(accent),
    secondary = Amber40,
    background = Slate10,
    surface = Slate10
)

/** Text on top of the accent: the dark navy on light accents (gold, orange), white on dark ones (blue, purple, red). */
private fun onAccent(accent: Color): Color = if (accent.luminance() > 0.4f) BrandNavy else Color.White

@Composable
fun RomRunnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Default changed from true to false: dynamic color derives its palette from the device
    // wallpaper, which never lands on pure black — it was quietly overriding DarkColors' OLED
    // black (Slate90) with a wallpaper-tinted dark gray. RomRunner's own palette wins now.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColors(Accent.color)
        else -> lightColors(Accent.color)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RomRunnerTypography,
        content = content
    )
}
