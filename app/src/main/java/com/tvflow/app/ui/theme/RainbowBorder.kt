package com.tvflow.app.ui.theme

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalRainbowEnabled = compositionLocalOf { false }

private val rainbow = listOf(
    Color(0xFFFF355E), Color(0xFFFFA600), Color(0xFFFFFF55),
    Color(0xFF4AFB9C), Color(0xFF34CBFF), Color(0xFFAD66FF)
)

/** Animates only focused or selected outlines; keeps text static and readable. */
@Composable
fun Modifier.rainbowBorder(active: Boolean, radius: Dp, width: Dp = 3.dp): Modifier {
    if (!LocalRainbowEnabled.current || !active) return this
    val transition = rememberInfiniteTransition(label = "rainbowOutline")
    val offset = transition.animateFloat(
        initialValue = 0f, targetValue = rainbow.size.toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(4200, easing = androidx.compose.animation.core.LinearEasing), repeatMode = RepeatMode.Restart),
        label = "rainbowTravel"
    ).value
    val start = offset.toInt() % rainbow.size
    val fraction = offset - offset.toInt()
    val colors = (0..rainbow.size).map { index ->
        lerp(rainbow[(start + index) % rainbow.size], rainbow[(start + index + 1) % rainbow.size], fraction)
    }
    return this.border(BorderStroke(width, Brush.sweepGradient(colors)), RoundedCornerShape(radius))
}
