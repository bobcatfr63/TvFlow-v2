package com.tvflow.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
    "RAINBOW" -> ThemePalette(
        primary = Color(0xFF6FDCFF), onPrimary = Color(0xFF07121F),
        background = Color(0xFF080D1A), surface = Color(0xFF101A2C),
        surfaceVariant = Color(0xFF1A2A42), text = Color.White,
        textSecondary = Color(0xFFD5E4F5)
    )
    "NEBULA" -> ThemePalette(
        primary = Color(0xFFA994FF), onPrimary = Color(0xFF0C0920),
        background = Color(0xFF09091B), surface = Color(0xFF16112B),
        surfaceVariant = Color(0xFF29204D), text = Color.White,
        textSecondary = Color(0xFFDDD4FF)
    )
    "INFERNO" -> ThemePalette(
        primary = Color(0xFFFFA24B), onPrimary = Color(0xFF241007),
        background = Color(0xFF150B0A), surface = Color(0xFF29120F),
        surfaceVariant = Color(0xFF4D2516), text = Color(0xFFFFF4E9),
        textSecondary = Color(0xFFFFD5AF)
    )
    "CRYSTAL" -> ThemePalette(
        primary = Color(0xFFB7E9FF), onPrimary = Color(0xFF09202B),
        background = Color(0xFF09151E), surface = Color(0xFF142733),
        surfaceVariant = Color(0xFF284858), text = Color.White,
        textSecondary = Color(0xFFDBF4FF)
    )
    "QUANTUM" -> ThemePalette(
        primary = Color(0xFF69F8DA), onPrimary = Color(0xFF021B17),
        background = Color(0xFF061716), surface = Color(0xFF0B2926),
        surfaceVariant = Color(0xFF184941), text = Color(0xFFF3FFFB),
        textSecondary = Color(0xFFBCEFE4)
    )
    "OCEAN" -> ThemePalette(
        primary = Color(0xFF4BC9E6), onPrimary = Color(0xFF03212A),
        background = Color(0xFF04121F), surface = Color(0xFF09283A),
        surfaceVariant = Color(0xFF14445A), text = Color.White,
        textSecondary = Color(0xFFBDE6F4)
    )
    "AURORA" -> ThemePalette(
        primary = Color(0xFF8EF2C1), onPrimary = Color(0xFF051D18),
        background = Color(0xFF07141B), surface = Color(0xFF122934),
        surfaceVariant = Color(0xFF274650), text = Color.White,
        textSecondary = Color(0xFFD3F5E8)
    )
    "ECLIPSE" -> ThemePalette(
        primary = Color(0xFFFFD18A), onPrimary = Color(0xFF211403),
        background = Color.Black, surface = Color(0xFF100F10),
        surfaceVariant = Color(0xFF30271C), text = Color.White,
        textSecondary = Color(0xFFE8D8BC)
    )
    "INTERSTELLAR" -> ThemePalette(
        primary = Color(0xFF99BDFF), onPrimary = Color(0xFF07101F),
        background = Color(0xFF040712), surface = Color(0xFF10182C),
        surfaceVariant = Color(0xFF233753), text = Color.White,
        textSecondary = Color(0xFFC9DBFF)
    )
    "HOLOGRAPHIC" -> ThemePalette(
        primary = Color(0xFF5DFDE9), onPrimary = Color(0xFF04211F),
        background = Color(0xFF060D17), surface = Color(0xFF102433),
        surfaceVariant = Color(0xFF234759), text = Color.White,
        textSecondary = Color(0xFFCBF7F5)
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
fun TVFlowTheme(accent: String = "RAINBOW", showAtmosphere: Boolean = true, content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isLegacyTv = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION && android.os.Build.VERSION.SDK_INT <= 28
    CompositionLocalProvider(LocalRainbowEnabled provides (!isLegacyTv && accent in setOf("RAINBOW", "NEBULA", "INFERNO", "CRYSTAL", "QUANTUM", "OCEAN", "AURORA", "ECLIPSE", "INTERSTELLAR", "HOLOGRAPHIC"))) {
        CompositionLocalProvider(LocalSignatureTheme provides accent) {
            MaterialTheme(colorScheme = palette(accent).toColorScheme()) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    // Ambient effects belong above opaque menus, but NEVER above video playback.
                    if (showAtmosphere) SignatureAtmosphere(accent, animate = !isLegacyTv)
                }
            }
        }
    }
}
