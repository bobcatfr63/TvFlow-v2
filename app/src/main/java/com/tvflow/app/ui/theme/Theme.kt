package com.tvflow.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private data class ThemePalette(
    val primary: Color,
    val onPrimary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val textSecondary: Color
)

private fun palette(id: String): ThemePalette = when (id) {
    "DARK" -> ThemePalette(
        primary = Color(0xFF8AB4F8),
        onPrimary = Color(0xFF07111C),
        background = Color(0xFF101216),
        surface = Color(0xFF1A1D23),
        surfaceVariant = Color(0xFF282D35),
        text = Color(0xFFF7F9FC),
        textSecondary = Color(0xFFD3D9E2)
    )
    "OLED" -> ThemePalette(
        primary = Color(0xFF55B8FF),
        onPrimary = Color.Black,
        background = Color.Black,
        surface = Color(0xFF080B0E),
        surfaceVariant = Color(0xFF14191E),
        text = Color.White,
        textSecondary = Color(0xFFD7E0E8)
    )
    "PURPLE" -> ThemePalette(
        primary = Color(0xFFC6A8FF),
        onPrimary = Color(0xFF160A27),
        background = Color(0xFF0E0915),
        surface = Color(0xFF1B1227),
        surfaceVariant = Color(0xFF302044),
        text = Color(0xFFF9F5FF),
        textSecondary = Color(0xFFDDD1EB)
    )
    "GREEN", "TEAL" -> ThemePalette(
        primary = Color(0xFF5EE0B8),
        onPrimary = Color(0xFF031A13),
        background = Color(0xFF07110E),
        surface = Color(0xFF10201B),
        surfaceVariant = Color(0xFF1C352D),
        text = Color(0xFFF2FFF9),
        textSecondary = Color(0xFFCBE5DB)
    )
    "HIGH_CONTRAST", "AMBER" -> ThemePalette(
        primary = Color(0xFFFFD54F),
        onPrimary = Color.Black,
        background = Color.Black,
        surface = Color(0xFF090909),
        surfaceVariant = Color(0xFF202020),
        text = Color.White,
        textSecondary = Color(0xFFF2F2F2)
    )
    else -> ThemePalette(
        primary = Color(0xFF4BA8FF),
        onPrimary = Color(0xFF06111C),
        background = Color(0xFF07111A),
        surface = Color(0xFF111D28),
        surfaceVariant = Color(0xFF1D3345),
        text = Color(0xFFF7FBFF),
        textSecondary = Color(0xFFD4E3EE)
    )
}

private fun ThemePalette.toColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondary = primary,
    onSecondary = onPrimary,
    primaryContainer = surfaceVariant,
    onPrimaryContainer = text,
    secondaryContainer = surfaceVariant,
    onSecondaryContainer = text,
    background = background,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = textSecondary
)

@Composable
fun TVFlowTheme(accent: String = "BLUE", content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = palette(accent).toColorScheme(),
        content = content
    )
}
