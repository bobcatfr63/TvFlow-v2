package com.tvflow.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalRainbowEnabled = compositionLocalOf { false }
val LocalSignatureTheme = compositionLocalOf { "RAINBOW" }

private val rainbow = listOf(
    Color(0xFFFF355E), Color(0xFFFFA600), Color(0xFFFFFF55),
    Color(0xFF4AFB9C), Color(0xFF34CBFF), Color(0xFFAD66FF), Color(0xFFFF355E)
)

private fun signatureColors(id: String): List<Color> = when (id) {
    "NEBULA" -> listOf(Color(0xFF7256D9), Color(0xFFAC8CFF), Color(0xFF3AACEF), Color(0xFF7256D9))
    "INFERNO" -> listOf(Color(0xFF9C271C), Color(0xFFFF6B24), Color(0xFFFFCB55), Color(0xFF9C271C))
    "CRYSTAL" -> listOf(Color(0xFF79A8FF), Color(0xFFD8F8FF), Color(0xFFB499FF), Color(0xFF79A8FF))
    "QUANTUM" -> listOf(Color(0xFF00BDAE), Color(0xFF79FFDA), Color(0xFF62B8FF), Color(0xFF00BDAE))
    "OCEAN" -> listOf(Color(0xFF125C94), Color(0xFF43D8EA), Color(0xFF2379C4), Color(0xFF125C94))
    "AURORA" -> listOf(Color(0xFF61E4AA), Color(0xFF79BAF2), Color(0xFFBB85EB), Color(0xFF61E4AA))
    "ECLIPSE" -> listOf(Color(0xFF8A572A), Color(0xFFFFDF9A), Color(0xFFF6A349), Color(0xFF8A572A))
    "INTERSTELLAR" -> listOf(Color(0xFF4A61C9), Color(0xFFB8CFFF), Color(0xFF8673E7), Color(0xFF4A61C9))
    "HOLOGRAPHIC" -> listOf(Color(0xFF28EEDE), Color(0xFF9EA4FF), Color(0xFF54D6FF), Color(0xFF28EEDE))
    else -> rainbow
}

/** Animated sweep across the outline itself. The content and typography never rotate. */
@Composable
fun Modifier.rainbowBorder(active: Boolean, radius: Dp, width: Dp = 3.dp): Modifier {
    if (!LocalRainbowEnabled.current || !active) return this
    val colorsForTheme = signatureColors(LocalSignatureTheme.current)
    val transition = rememberInfiniteTransition(label = "rainbowOutline")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbowRotation"
    )
    val offset = angle.value / 360f * (colorsForTheme.size - 1)
    val shift = offset.toInt()
    val fraction = offset - shift
    val colors = (0 until colorsForTheme.size).map { i ->
        val size = colorsForTheme.size - 1
        lerp(colorsForTheme[(i + shift) % size], colorsForTheme[(i + shift + 1) % size], fraction)
    }
    return this.drawWithContent {
        drawContent()
        val stroke = width.toPx()
        drawRoundRect(
            brush = Brush.sweepGradient(colors),
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2f, stroke / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(stroke)
        )
    }
}
