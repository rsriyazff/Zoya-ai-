package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZoyaColorScheme = darkColorScheme(
    primary = GlowNeon,
    secondary = GlowBlue,
    tertiary = AccentRed,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZoyaColorScheme,
        typography = Typography,
        content = content
    )
}
