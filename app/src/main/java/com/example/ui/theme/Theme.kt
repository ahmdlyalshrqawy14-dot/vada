package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = CyanGlow,
    onPrimaryContainer = CyanPrimary,
    secondary = CyanSecondary,
    background = OceanBgDark,
    onBackground = Color.White,
    surface = OceanSurfaceDark,
    onSurface = Color.White,
    surfaceVariant = OceanCardBorderDark,
    onSurfaceVariant = TextMuted,
    outline = GlassBorderWhite
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F7FF),
    onPrimaryContainer = Color(0xFF006680),
    secondary = CyanSecondary,
    background = OceanBgLight,
    onBackground = Color(0xFF0B0E14),
    surface = OceanSurfaceLight,
    onSurface = Color(0xFF0B0E14),
    surfaceVariant = Color(0xFFE4E9F0),
    onSurfaceVariant = Color(0xFF506070),
    outline = Color(0xFFD0D7E0)
)

@Composable
fun VodaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

