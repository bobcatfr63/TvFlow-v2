package com.tvflow.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin

/** A light, non-interactive animated atmosphere shared by every navigation destination. */
@Composable
fun SignatureAtmosphere(theme: String, modifier: Modifier = Modifier) {
    val tones = when (theme) {
        "NEBULA" -> listOf(Color(0xFF9E67FF), Color(0xFF3078ED), Color(0xFFDC6DEA))
        "INFERNO" -> listOf(Color(0xFFFF632C), Color(0xFFFFBB46), Color(0xFFAD2B47))
        "CRYSTAL" -> listOf(Color(0xFFA5EFFF), Color(0xFF899CF9), Color(0xFFDCF5FF))
        "QUANTUM" -> listOf(Color(0xFF47FFCE), Color(0xFF3B97FF), Color(0xFF9BF7E6))
        "OCEAN" -> listOf(Color(0xFF1ABFD8), Color(0xFF1665B2), Color(0xFF77DFE9))
        "AURORA" -> listOf(Color(0xFF57DFB1), Color(0xFF9281DE), Color(0xFF399DC7))
        else -> return
    }
    val transition = rememberInfiniteTransition(label = "signatureAtmosphere")
    val phase = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "signatureAtmospherePhase"
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val progress = phase.value * 6.283185f
        val radius = maxOf(w, h) * 0.58f
        val spots = listOf(
            Offset(w * (0.12f + 0.045f * sin(progress)), h * 0.12f),
            Offset(w * 0.88f, h * (0.78f + 0.06f * sin(progress + 2.1f))),
            Offset(w * (0.54f + 0.07f * sin(progress + 4.2f)), h * 0.48f)
        )
        spots.forEachIndexed { i, center ->
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tones[i].copy(alpha = if (i == 0) 0.19f else 0.13f),
                    1f to tones[i].copy(alpha = 0f),
                    center = center, radius = radius
                ),
                radius = radius, center = center
            )
        }
        // Slow, distinct accent trails are deliberately sparse for low-end Android TV hardware.
        repeat(5) { i ->
            val x = w * ((i + 1f) / 6f)
            val y = h * (0.18f + 0.15f * sin(progress + i * 1.4f))
            drawCircle(tones[i % 3].copy(alpha = 0.28f), radius = 1.8f, center = Offset(x, y))
        }
        if (theme == "QUANTUM" || theme == "CRYSTAL") {
            drawCircle(
                color = tones[1].copy(alpha = 0.09f),
                radius = minOf(w, h) * 0.42f,
                center = spots[0], style = Stroke(width = 1.4f)
            )
        }
    }
}
