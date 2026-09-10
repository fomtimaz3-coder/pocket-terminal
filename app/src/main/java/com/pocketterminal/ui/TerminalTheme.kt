package com.pocketterminal.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BE15D),
    onPrimary = Color(0xFF17210E),
    secondary = Color(0xFF8AD9D3),
    background = Color(0xFF0B0D10),
    surface = Color(0xFF12161B),
    surfaceVariant = Color(0xFF1B2229),
    onSurface = Color(0xFFE8EDF2)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A7C24),
    onPrimary = Color.White,
    secondary = Color(0xFF146C68),
    background = Color(0xFFF4F6F2),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE4EAE0)
)

@Composable
fun PocketTerminalTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}