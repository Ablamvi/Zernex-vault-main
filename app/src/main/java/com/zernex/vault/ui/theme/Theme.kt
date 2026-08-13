package com.zernex.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF6B35),
    onPrimary = Color.White,
    secondary = Color(0xFF00D9FF),
    onSecondary = Color.Black,
    background = Color(0xFF0A0A12),
    onBackground = Color(0xFFEAEAEA),
    surface = Color(0xFF141422),
    onSurface = Color(0xFFEAEAEA),
    surfaceVariant = Color(0xFF1E1E32),
    onSurfaceVariant = Color(0xFFB0B0C0),
    error = Color(0xFFFF5252)
)

@Composable
fun ZernexVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
