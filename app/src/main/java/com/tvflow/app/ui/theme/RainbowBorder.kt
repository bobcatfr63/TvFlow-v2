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

private val rainbow = listOf(
    Color(0xFFFF355E), Color(0xFFFFA600), Color(0xFFFFFF55),
    Color(0xFF4AFB9C), Color(0xFF34CBFF), Color(0xFFAD66FF), Color(0xFFFF355E)
)

/** Animated sweep across the outline itself. The content and typography never rotate. */
@Composable
fun Modifier.rainbowBorder(active: Boolean, radius: Dp, width: Dp = 3.dp): Modifier {
    if (!LocalRainbowEnabled.current || !active) return this
    val transition = rememberInfiniteTransition(label = "rainbowOutline")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "rainbowRotation"
    )
    val offset = angle.value / 360f * (rainbow.size - 1)
    val shift = offset.toInt()
    val fraction = offset - shift
    val colors = (0 until rainbow.size).map { i ->
        val size = rainbow.size - 1
        lerp(rainbow[(i + shift) % size], rainbow[(i + shift + 1) % size], fraction)
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
