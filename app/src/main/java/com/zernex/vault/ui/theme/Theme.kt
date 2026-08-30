package com.zernex.vault.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Palette alignée sur l’app web ZERNEX Vault
 * (bg #0b0b10, surface #14141c, elevated #1c1c28, accent #ff6b35)
 */
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF6B35),
    onPrimary = Color(0xFF1A0904),
    secondary = Color(0xFF3DBA8B),
    onSecondary = Color.Black,
    tertiary = Color(0xFF00D9FF),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF14141C),
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF1C1C28),
    onSurfaceVariant = Color(0xFF9C98A4),
    outline = Color(0xFF2A2A36),
    error = Color(0xFFE85D5D),
    onError = Color.White
)

@Composable
fun ZernexVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content
    )
}
