package com.example.ui.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.data.model.AudioSpectrumFrame
import com.example.ui.theme.DarkCrimson
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.MidnightViolet
import com.example.ui.theme.ObsidianBlack
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientRgbBackground(
    spectrumFrame: AudioSpectrumFrame,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    time += dt * if (isPlaying) 0.42f else 0.14f
                }
                lastNanos = nanos
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val bassBoost = (spectrumFrame.bass * 0.45f + spectrumFrame.beatPulse * 0.25f).coerceIn(0f, 0.65f)

        // Base deep obsidian fill
        drawRect(color = ObsidianBlack)

        // Slowly shifting Midnight Violet node (top-left orbit)
        val violetCenter = Offset(
            x = w * (0.28f + 0.18f * cos(time * 0.7f)),
            y = h * (0.24f + 0.14f * sin(time * 0.55f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    MidnightViolet.copy(alpha = (0.72f + bassBoost * 0.25f).coerceAtMost(0.95f)),
                    MidnightViolet.copy(alpha = 0.25f),
                    Color.Transparent
                ),
                center = violetCenter,
                radius = maxOf(w, h) * (0.78f + bassBoost * 0.12f)
            ),
            radius = maxOf(w, h) * 0.9f,
            center = violetCenter
        )

        // Slowly shifting Dark Crimson node (center-right orbit)
        val crimsonCenter = Offset(
            x = w * (0.76f + 0.16f * sin(time * 0.5f + 1.4f)),
            y = h * (0.48f + 0.18f * cos(time * 0.62f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    DarkCrimson.copy(alpha = (0.65f + bassBoost * 0.30f).coerceAtMost(0.92f)),
                    DarkCrimson.copy(alpha = 0.20f),
                    Color.Transparent
                ),
                center = crimsonCenter,
                radius = maxOf(w, h) * (0.72f + bassBoost * 0.14f)
            ),
            radius = maxOf(w, h) * 0.85f,
            center = crimsonCenter
        )

        // Slowly shifting Deep Navy node (bottom-left orbit)
        val navyCenter = Offset(
            x = w * (0.35f + 0.20f * sin(time * 0.45f + 2.8f)),
            y = h * (0.82f + 0.12f * cos(time * 0.52f + 0.9f))
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    DeepNavy.copy(alpha = (0.78f + bassBoost * 0.20f).coerceAtMost(0.96f)),
                    DeepNavy.copy(alpha = 0.24f),
                    Color.Transparent
                ),
                center = navyCenter,
                radius = maxOf(w, h) * (0.76f + bassBoost * 0.10f)
            ),
            radius = maxOf(w, h) * 0.88f,
            center = navyCenter
        )
    }
}
