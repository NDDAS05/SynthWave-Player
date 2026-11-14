package com.example.ui.visualizer

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SweepGradient
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.data.model.AudioSpectrumFrame
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun TwistingRgbRingVisualizer(
    spectrumFrame: AudioSpectrumFrame,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val speed = if (isPlaying) 1.0f + spectrumFrame.mid * 1.4f else 0.25f
                    phase += dt * speed
                }
                lastNanos = nanos
            }
        }
    }

    val neonColorsInt = remember {
        intArrayOf(
            NeonCyan.toArgb(),
            CyberMagenta.toArgb(),
            NeonOrange.toArgb(),
            ElectricLime.toArgb(),
            NeonCyan.toArgb()
        )
    }

    val neonColorsCompose = remember {
        listOf(NeonCyan, CyberMagenta, NeonOrange, ElectricLime, NeonCyan)
    }

    val glowPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(28f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    val crispPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    val shaderMatrix = remember { Matrix() }
    val ringPath1 = remember { Path() }
    val ringPath2 = remember { Path() }
    val ringPath3 = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val minDim = min(size.width, size.height)

        val bass = spectrumFrame.bass
        val mid = spectrumFrame.mid
        val treble = spectrumFrame.treble
        val bins = spectrumFrame.spectrumBins

        val baseRadius = minDim * (0.28f + bass * 0.075f)
        val twistAmp = minDim * (0.015f + mid * 0.055f)
        val trebleRippleAmp = minDim * (0.008f + treble * 0.048f)

        // Subtle inner luminous aura reacting to kick drums
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = (0.14f + bass * 0.22f).coerceIn(0f, 0.45f)),
                    CyberMagenta.copy(alpha = (0.06f + mid * 0.14f).coerceIn(0f, 0.28f)),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseRadius * 1.45f
            ),
            radius = baseRadius * 1.45f,
            center = Offset(cx, cy)
        )

        fun buildTwistedRingPath(
            target: Path,
            layerOffset: Float,
            twistLobes: Int,
            radialScale: Float,
            includeTrebleSpikes: Boolean
        ) {
            target.reset()
            val segments = 144
            for (i in 0..segments) {
                val t = i.toFloat() / segments.toFloat()
                val angle = t * (2f * PI.toFloat())
                val binIdx = ((t * 63f).toInt()).coerceIn(0, bins.lastIndex)
                val binEnergy = bins[binIdx]

                val harmonicTwist = sin(angle * twistLobes + phase * 2.2f + layerOffset) * twistAmp
                val counterWave = cos(angle * (twistLobes + 2) - phase * 1.6f + layerOffset) * (twistAmp * 0.45f)
                val crystallineRipple = if (includeTrebleSpikes) {
                    sin(angle * 24f + phase * 6.5f) * trebleRippleAmp * (0.4f + binEnergy * 0.9f) +
                        binEnergy * minDim * 0.032f
                } else {
                    binEnergy * minDim * 0.018f
                }

                val r = baseRadius * radialScale + harmonicTwist + counterWave + crystallineRipple
                val x = cx + cos(angle) * r
                val y = cy + sin(angle) * r
                if (i == 0) target.moveTo(x, y) else target.lineTo(x, y)
            }
            target.close()
        }

        buildTwistedRingPath(ringPath1, layerOffset = 0f, twistLobes = 3, radialScale = 1.0f, includeTrebleSpikes = true)
        buildTwistedRingPath(ringPath2, layerOffset = 2.1f, twistLobes = 4, radialScale = 0.94f, includeTrebleSpikes = false)
        buildTwistedRingPath(ringPath3, layerOffset = 4.2f, twistLobes = 5, radialScale = 1.06f, includeTrebleSpikes = true)

        // Rotating SweepGradient shader for native Canvas neon glow
        val sweep = SweepGradient(cx, cy, neonColorsInt, null)
        shaderMatrix.reset()
        shaderMatrix.setRotate(phase * 48f, cx, cy)
        sweep.setLocalMatrix(shaderMatrix)

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // Outer BlurMaskFilter Neon Bloom
            glowPaint.shader = sweep
            glowPaint.strokeWidth = minDim * (0.022f + bass * 0.018f)
            glowPaint.alpha = (180 + (bass * 75).toInt()).coerceIn(140, 255)
            native.drawPath(ringPath1.asAndroidPath(), glowPaint)

            glowPaint.strokeWidth = minDim * 0.014f
            glowPaint.alpha = 165
            native.drawPath(ringPath3.asAndroidPath(), glowPaint)

            // Crisp High-Brightness Foreground Neon Strands
            crispPaint.shader = sweep
            crispPaint.strokeWidth = minDim * 0.0085f
            crispPaint.alpha = 255
            native.drawPath(ringPath1.asAndroidPath(), crispPaint)

            crispPaint.strokeWidth = minDim * 0.0055f
            crispPaint.alpha = 225
            native.drawPath(ringPath2.asAndroidPath(), crispPaint)

            crispPaint.strokeWidth = minDim * 0.0045f
            crispPaint.alpha = 200
            native.drawPath(ringPath3.asAndroidPath(), crispPaint)
        }

        // Compose SweepGradient crisp inner orbit ring + treble crystalline spikes
        val sweepBrush = Brush.sweepGradient(
            colors = neonColorsCompose,
            center = Offset(cx, cy)
        )
        drawCircle(
            brush = sweepBrush,
            radius = baseRadius * 0.78f,
            center = Offset(cx, cy),
            alpha = 0.45f + bass * 0.45f,
            style = Stroke(width = minDim * 0.004f, cap = StrokeCap.Round)
        )

        // Crystalline treble micro-sparks along the outer perimeter
        val sparkCount = 48
        for (i in 0 until sparkCount) {
            val t = i.toFloat() / sparkCount.toFloat()
            val angle = t * (2f * PI.toFloat()) + phase * 0.35f
            val binVal = bins[(t * 63f).toInt().coerceIn(0, bins.lastIndex)]
            val innerR = baseRadius * 1.11f
            val outerR = innerR + minDim * (0.01f + (treble * 0.055f + binVal * 0.045f))
            val start = Offset(cx + cos(angle) * innerR, cy + sin(angle) * innerR)
            val end = Offset(cx + cos(angle) * outerR, cy + sin(angle) * outerR)
            val sparkColor = when (i % 4) {
                0 -> NeonCyan
                1 -> CyberMagenta
                2 -> ElectricLime
                else -> NeonOrange
            }
            drawLine(
                color = sparkColor.copy(alpha = (0.35f + treble * 0.65f).coerceIn(0.25f, 1f)),
                start = start,
                end = end,
                strokeWidth = minDim * 0.004f,
                cap = StrokeCap.Round
            )
        }
    }
}
