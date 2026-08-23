package com.example.htmltoapk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    secondary = EmeraldAccent,
    onSecondary = Color.Black,
    tertiary = CyanAccent,
    background = NavyBackground,
    onBackground = OnDarkPrimary,
    surface = NavySurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,
    error = ErrorRed
)

private val LightColors = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    secondary = EmeraldAccent,
    onSecondary = Color.Black,
    tertiary = CyanAccent,
    background = LightBackground,
    onBackground = Color(0xFF10152B),
    surface = LightSurface,
    onSurface = Color(0xFF10152B),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF474E6B),
    error = ErrorRed
)

@Composable
fun HtmlToApkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
