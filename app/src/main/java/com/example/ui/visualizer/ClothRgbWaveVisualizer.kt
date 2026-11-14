package com.example.ui.visualizer

import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.data.model.AudioSpectrumFrame
import com.example.ui.theme.CyberMagenta
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonOrange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ClothRgbWaveVisualizer(
    spectrumFrame: AudioSpectrumFrame,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableFloatStateOf(0f) }
    var unfurlProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastNanos != 0L) {
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val speed = if (isPlaying) 1.35f + spectrumFrame.mid * 1.6f else 0.35f
                    phase += dt * speed

                    val energy = (spectrumFrame.amplitude * 1.15f + spectrumFrame.bass * 0.45f).coerceIn(0f, 1f)
                    val targetUnfurl = if (!isPlaying || energy < 0.04f) {
                        0f
                    } else {
                        ((energy - 0.04f) / 0.36f).coerceIn(0f, 1f)
                    }
                    val lerpRate = if (targetUnfurl > unfurlProgress) 0.14f else 0.06f
                    unfurlProgress += (targetUnfurl - unfurlProgress) * lerpRate
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

    val glowPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    val strandPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    val crossMeshPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    }

    val strandPaths = remember { List(22) { Path() } }
    val ribPath = remember { Path() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f

        val bass = spectrumFrame.bass
        val mid = spectrumFrame.mid
        val treble = spectrumFrame.treble
        val bins = spectrumFrame.spectrumBins
        val waveform = spectrumFrame.waveform

        val horizontalGradient = LinearGradient(
            0f,
            centerY,
            w,
            centerY,
            neonColorsInt,
            null,
            Shader.TileMode.MIRROR
        )

        val strandCount = strandPaths.size
        val sampleCount = 72
        val maxSpreadY = h * 0.27f * unfurlProgress
        val maxWaveAmp = h * (0.015f + (0.14f * unfurlProgress) + bass * 0.08f)

        // Precompute strand Y coordinates so we can also draw 3D perspective silk mesh ribs
        val yGrid = Array(strandCount) { FloatArray(sampleCount + 1) }

        for (s in 0 until strandCount) {
            val strandNorm = (s.toFloat() / (strandCount - 1).toFloat()) * 2f - 1f // -1..1
            val depthPerspective = 1f - abs(strandNorm) * 0.28f * unfurlProgress
            val strandPhaseOffset = strandNorm * 1.85f * unfurlProgress
            val path = strandPaths[s]
            path.reset()

            for (i in 0..sampleCount) {
                val xNorm = i.toFloat() / sampleCount.toFloat()
                val x = xNorm * w

                // Taper wave softly at left/right edges for sculptural framing
                val edgeEnvelope = sin(xNorm * PI.toFloat())
                val binIdx = (xNorm * (bins.size - 1)).toInt().coerceIn(0, bins.lastIndex)
                val waveIdx = (xNorm * (waveform.size - 1)).toInt().coerceIn(0, waveform.lastIndex)
                val sampleBin = bins[binIdx]
                val sampleWave = waveform[waveIdx]

                // Cloth-like multi-frequency traveling wave
                val primarySilk = sin(xNorm * 5.2f * depthPerspective - phase * 2.1f + strandPhaseOffset) *
                    maxWaveAmp * 0.65f
                val secondaryFold = cos(xNorm * 9.5f + phase * 1.4f - strandPhaseOffset * 1.3f) *
                    maxWaveAmp * (0.28f + mid * 0.25f) * unfurlProgress
                val trebleShimmer = sin(xNorm * 22f - phase * 5.2f + s * 0.5f) *
                    (h * 0.018f * treble * unfurlProgress)
                val audioDisplacement = (sampleWave * 0.45f + (sampleBin - 0.3f) * 0.35f) *
                    maxWaveAmp * (0.4f + unfurlProgress * 0.6f)

                val perspectiveOffset = strandNorm * maxSpreadY * (0.55f + 0.45f * edgeEnvelope) *
                    (0.85f + 0.15f * sin(xNorm * 3.1f + phase + strandNorm))

                val y = centerY + perspectiveOffset +
                    (primarySilk + secondaryFold + trebleShimmer + audioDisplacement) *
                    edgeEnvelope * depthPerspective

                yGrid[s][i] = y
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas

            // When idle / quiet, render the single razor-sharp glowing neon horizontal line
            val centerStrand = strandPaths[strandCount / 2]
            glowPaint.shader = horizontalGradient
            glowPaint.strokeWidth = 10f + bass * 12f
            glowPaint.alpha = 210
            native.drawPath(centerStrand.asAndroidPath(), glowPaint)

            if (unfurlProgress > 0.05f) {
                // Draw subtle silk transverse perspective ribs connecting strands
                crossMeshPaint.shader = horizontalGradient
                crossMeshPaint.strokeWidth = 1.6f
                crossMeshPaint.alpha = (75f * unfurlProgress).toInt().coerceIn(0, 95)
                for (i in 4 until sampleCount step 4) {
                    ribPath.reset()
                    for (s in 0 until strandCount) {
                        val x = (i.toFloat() / sampleCount.toFloat()) * w
                        val y = yGrid[s][i]
                        if (s == 0) ribPath.moveTo(x, y) else ribPath.lineTo(x, y)
                    }
                    native.drawPath(ribPath.asAndroidPath(), crossMeshPaint)
                }

                // Draw all unfurled luminous silk strands
                strandPaint.shader = horizontalGradient
                for (s in 0 until strandCount) {
                    val distFromCenter = abs(s - strandCount / 2f) / (strandCount / 2f)
                    val strandAlpha = ((1f - distFromCenter * 0.55f) * 235f * unfurlProgress)
                        .toInt()
                        .coerceIn(0, 245)
                    strandPaint.strokeWidth = if (s == strandCount / 2) 4.5f else 2.2f + (1f - distFromCenter) * 1.4f
                    strandPaint.alpha = strandAlpha
                    native.drawPath(strandPaths[s].asAndroidPath(), strandPaint)
                }
            }

            // Always keep the central hero neon spine crisp and luminous
            strandPaint.shader = horizontalGradient
            strandPaint.strokeWidth = 4.5f + bass * 2.5f
            strandPaint.alpha = 255
            native.drawPath(centerStrand.asAndroidPath(), strandPaint)
        }
    }
}
