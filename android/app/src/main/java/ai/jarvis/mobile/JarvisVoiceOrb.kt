package ai.jarvis.mobile

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Drawn in Compose – NOT a static animated GIF or screenshot.
 * Listening: reacts to Android SpeechRecognizer RMS; speaking: reacts to
 * TextToSpeech synthesis amplitude when delivered by the device TTS engine.
 * Some engines only provide a rough envelope, so visual sync is best-effort.
 */
@Composable
internal fun JarvisVoiceOrb(modifier: Modifier = Modifier) {
    // Idle draws a fixed, perfectly still orb and waves. The animation clock is
    // not created until Android detects actual speech or Jarvis is speaking.
    val userSpeaking = HandsFreeStatus.userSpeaking
    val speaking = HandsFreeStatus.speaking
    val active = userSpeaking || speaking
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "Speaking-only Jarvis energy")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(1350, easing = LinearEasing), RepeatMode.Restart),
            label = "Live voice phase"
        )
        animated
    } else 0f
    val level = when {
        speaking -> max(0.16f, HandsFreeStatus.outputLevel.coerceIn(0f, 1f))
        userSpeaking -> max(0.10f, HandsFreeStatus.micLevel.coerceIn(0f, 1f))
        else -> 0f
    }
    val energy = if (active) (0.12f + level * 0.83f).coerceIn(0f, 1f) else 0f
    Canvas(modifier.fillMaxWidth().height(285.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val cy = h * 0.50f
        val baseRadius = min(w * 0.235f, h * 0.305f)
        val pulse = 1f + energy * 0.075f
        val radius = baseRadius * pulse
        val center = Offset(cx, cy)
        val cyan = Color(0xFF21D4FF)
        val bright = Color(0xFF5EE9FF)
        val blue = Color(0xFF065BFF)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0x4458C7FF), Color(0x222A94FF), Color.Transparent
                ),
                center = center, radius = radius * 2.25f
            ),
            radius = radius * 2.2f, center = center
        )
        for (index in 1..3) {
            val expansion = index * 0.17f +
                (if (active) (phase / (2 * PI)).toFloat() * 0.06f else 0f)
            drawCircle(
                color = Color(0xFF72C9FF).copy(alpha = (0.17f - index * 0.028f).coerceAtLeast(0f)),
                center = center,
                radius = radius * (1.28f + expansion),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
        // Each moving energy ribbon is a real animated Path, not a fixed bitmap.
        for (track in 0..5) {
            val path = Path()
            val amplitude = h * (0.082f + 0.021f * track) * (0.40f + energy * 0.75f)
            val travel = if (active) phase * (if (track % 2 == 0) 1f else -0.7f) else 0f
            for (xIndex in 0..120) {
                val x = w * xIndex / 120f
                val wave = sin((x / w * (2.0 + track * 0.32) * PI + travel).toDouble()).toFloat()
                val modulation = sin((x / w * 5.8 * PI - phase * 0.6).toDouble()).toFloat()
                val y = cy + wave * amplitude * 0.72f +
                    modulation * h * 0.027f
                if (xIndex == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = when (track % 3) {
                    0 -> Color(0xFF02A9FF)
                    1 -> bright
                    else -> blue
                }.copy(alpha = 0.31f + energy * 0.33f),
                style = Stroke(width = (1.1f + (track % 3) * 0.55f).dp.toPx(),
                    cap = StrokeCap.Round)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xBB8DEEFF), Color(0xFFA6FFFF), Color(0xFF1EAFFF)
                ),
                center = center, radius = radius * 1.09f
            ),
            radius = radius * 1.075f, center = center
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF21CCFF),
                    Color(0xFF0063EF),
                    Color(0xFF02156F),
                    Color(0xFF001354)
                ),
                center = Offset(cx - radius * 0.32f, cy - radius * 0.33f),
                radius = radius * 1.55f
            ),
            radius = radius, center = center
        )
        // Glossy upper-left reflection
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xB56DEFFF), Color.Transparent),
                center = Offset(cx - radius * 0.36f, cy - radius * 0.66f),
                radius = radius * 0.80f
            ),
            center = Offset(cx - radius * 0.36f, cy - radius * 0.66f),
            radius = radius * 0.80f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.84f),
            radius = radius * 1.015f, center = center,
            style = Stroke(width = 2.5.dp.toPx())
        )
        // Reactive center waveform: narrow bars suggest Jarvis "speaking".
        val bars = 27
        for (i in 0 until bars) {
            val relative = (i - (bars - 1) / 2f) / (bars / 2f)
            val falloff = (1f - abs(relative)).coerceAtLeast(0.08f)
            val modulation = if (active)
                abs(sin((i * 0.53 + phase * 1.75).toDouble())).toFloat()
                else 0.12f
            val barH = (radius * (0.05f + falloff * modulation *
                (0.19f + level * 0.55f))).coerceAtLeast(2.dp.toPx())
            val x = cx + (i - (bars - 1) / 2f) * radius * 0.056f
            drawLine(
                color = Color(0xFFE4FFFF).copy(alpha = 0.76f + energy * 0.22f),
                start = Offset(x, cy - barH * 0.5f),
                end = Offset(x, cy + barH * 0.5f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
