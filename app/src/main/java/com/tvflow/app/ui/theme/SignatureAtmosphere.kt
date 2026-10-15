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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ambient FX layer: no pointer handlers, few draw calls, muted contrast on top of
 * existing screens. Different geometric motifs per Signature identity.
 */
@Composable
fun SignatureAtmosphere(theme: String, modifier: Modifier = Modifier) {
    val tones = when (theme) {
        "NEBULA" -> listOf(Color(0xFFBD78FF), Color(0xFF447AFF), Color(0xFFE59BFF))
        "INFERNO" -> listOf(Color(0xFFFF6737), Color(0xFFFFC05A), Color(0xFFE03A62))
        "CRYSTAL" -> listOf(Color(0xFFB6F2FF), Color(0xFF8F9DFF), Color(0xFFD6F9FF))
        "QUANTUM" -> listOf(Color(0xFF55FCD0), Color(0xFF529AFF), Color(0xFF9FFFE1))
        "OCEAN" -> listOf(Color(0xFF38D1E5), Color(0xFF2375C9), Color(0xFF91F4EC))
        "AURORA" -> listOf(Color(0xFF6DFAC0), Color(0xFFA08AF2), Color(0xFF47C9DA))
        else -> return
    }
    val transition = rememberInfiniteTransition(label = "signatureScene")
    val phase = transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "cinematicDrift"
    )
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val time = phase.value * 6.283185f
        val large = maxOf(w, h)
        val glowCenters = listOf(
            Offset(w * (0.13f + 0.08f * sin(time)), h * 0.12f),
            Offset(w * 0.86f, h * (0.8f + 0.09f * sin(time + 2f))),
            Offset(w * (0.5f + 0.1f * sin(time + 4f)), h * 0.48f)
        )
        glowCenters.forEachIndexed { index, center ->
            val radius = large * (if (theme == "NEBULA" || theme == "AURORA") 0.55f else 0.4f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tones[index].copy(alpha = 0.17f),
                    1f to tones[index].copy(alpha = 0f),
                    center = center, radius = radius
                ),
                radius = radius, center = center
            )
        }
        when (theme) {
            "NEBULA" -> repeat(26) { index ->
                val x = w * ((index * 0.618034f) % 1f)
                val y = h * ((index * 0.414214f + phase.value * (0.025f + (index % 3) * 0.01f)) % 1f)
                drawCircle(tones[index % 3].copy(alpha = 0.3f), 0.7f + index % 3, Offset(x, y))
            }
            "INFERNO" -> repeat(16) { index ->
                val x = w * ((index * 0.537f + sin(time + index) * 0.014f) % 1f)
                val y = h * (1f - ((index * 0.129f + phase.value * (0.18f + index % 4 * 0.03f)) % 1f))
                drawCircle(tones[index % 3].copy(alpha = 0.25f), 1.3f + index % 3, Offset(x, y))
            }
            "CRYSTAL" -> repeat(6) { index ->
                val center = Offset(w * (0.16f + index * 0.15f), h * (0.16f + 0.1f * sin(time + index)))
                drawCircle(tones[index % 3].copy(alpha = 0.12f), large * (0.09f + index % 3 * 0.025f), center, style = Stroke(1.2f))
            }
            "QUANTUM" -> repeat(10) { index ->
                val y = h * (0.08f + index * 0.09f)
                val x = w * (0.72f + 0.035f * sin(time + index))
                drawLine(tones[index % 3].copy(alpha = 0.12f), Offset(x, y), Offset(x + w * 0.12f, y), strokeWidth = 1.5f)
            }
            "OCEAN" -> repeat(8) { index ->
                val y = h * (0.16f + index * 0.105f) + h * 0.018f * sin(time + index)
                drawLine(tones[index % 3].copy(alpha = 0.12f), Offset(0f, y), Offset(w, y + h * 0.025f * sin(time + index * 0.9f)), strokeWidth = 1.8f)
            }
            "AURORA" -> repeat(7) { index ->
                val x = w * (0.1f + index * 0.12f + 0.02f * sin(time + index))
                drawLine(tones[index % 3].copy(alpha = 0.10f), Offset(x, 0f), Offset(x + w * 0.18f * cos(time + index), h * 0.65f), strokeWidth = 2.8f)
            }
        }
    }
}
