package com.example.audio

import android.media.audiofx.Visualizer
import com.example.data.model.AudioSpectrumFrame
import com.example.data.model.SpectrumSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

class VisualizerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _spectrumFrame = MutableStateFlow(AudioSpectrumFrame())
    val spectrumFrame: StateFlow<AudioSpectrumFrame> = _spectrumFrame.asStateFlow()

    private var visualizer: Visualizer? = null
    private var attachedSessionId: Int = 0
    private var simulationJob: Job? = null

    private var hasPermission: Boolean = false
    private var isYouTubeActive: Boolean = false
    private var isPlaying: Boolean = false
    private var bpm: Int = 122

    private var smoothBass = 0f
    private var smoothMid = 0f
    private var smoothTreble = 0f
    private var smoothAmp = 0f
    private var smoothBeat = 0f
    private val smoothBins = FloatArray(64)
    private val latestWaveform = FloatArray(64)

    fun updatePlaybackContext(
        audioSessionId: Int,
        hasRecordAudioPermission: Boolean,
        isYouTubeMode: Boolean,
        isCurrentlyPlaying: Boolean,
        trackBpm: Int
    ) {
        hasPermission = hasRecordAudioPermission
        isYouTubeActive = isYouTubeMode
        isPlaying = isCurrentlyPlaying
        bpm = trackBpm.coerceIn(70, 175)

        val shouldUseHardwareFft = hasPermission && !isYouTubeActive && isPlaying && audioSessionId > 0
        if (shouldUseHardwareFft) {
            stopProceduralLoop()
            attachHardwareVisualizer(audioSessionId)
        } else {
            releaseHardwareVisualizer()
            startProceduralLoop()
        }
    }

    private fun attachHardwareVisualizer(sessionId: Int) {
        if (visualizer != null && attachedSessionId == sessionId) {
            runCatching {
                if (visualizer?.enabled == false) {
                    visualizer?.enabled = true
                }
            }
            return
        }

        releaseHardwareVisualizer()

        try {
            val vis = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            val targetSize = 1024.coerceIn(range[0], range[1])
            vis.enabled = false
            vis.captureSize = targetSize

            val rate = Visualizer.getMaxCaptureRate().coerceAtLeast(20_000)
            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        val step = (waveform.size / 64).coerceAtLeast(1)
                        var sumSq = 0f
                        for (i in 0 until 64) {
                            val idx = (i * step).coerceAtMost(waveform.lastIndex)
                            val sample = ((waveform[idx].toInt() and 0xFF) - 128) / 128f
                            latestWaveform[i] = sample
                            sumSq += sample * sample
                        }
                        val rms = kotlin.math.sqrt(sumSq / 64f).coerceIn(0f, 1f)
                        smoothAmp = smooth(smoothAmp, (rms * 2.2f).coerceIn(0f, 1f), 0.35f, 0.14f)
                    }

                    override fun onFftDataCapture(
                        v: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 8) return
                        processFftBytes(fft)
                    }
                },
                rate,
                true,
                true
            )
            vis.enabled = true
            visualizer = vis
            attachedSessionId = sessionId
        } catch (_: Throwable) {
            // Device/emulator rejected Visualizer attachment — fall back seamlessly to procedural BPM engine
            releaseHardwareVisualizer()
            startProceduralLoop()
        }
    }

    private fun processFftBytes(fft: ByteArray) {
        val binCount = fft.size / 2
        if (binCount < 4) return

        var bassSum = 0f
        var bassCount = 0
        var midSum = 0f
        var midCount = 0
        var trebleSum = 0f
        var trebleCount = 0

        val outBins = FloatArray(64)
        for (i in 0 until 64) {
            // Logarithmic-ish mapping so low/mid frequencies occupy expressive visual space
            val fraction = i / 64f
            val mappedBin = (1 + ((fraction * fraction) * (binCount - 2)).toInt()).coerceIn(1, binCount - 1)
            val r = fft[mappedBin * 2].toFloat()
            val im = fft[mappedBin * 2 + 1].toFloat()
            val mag = (hypot(r, im) / 128f).coerceIn(0f, 1.5f)
            val normalized = (mag * 0.85f).coerceIn(0f, 1f)
            smoothBins[i] = smooth(smoothBins[i], normalized, 0.48f, 0.18f)
            outBins[i] = smoothBins[i]
        }

        val bassEnd = (binCount * 0.08f).toInt().coerceAtLeast(3)
        val midEnd = (binCount * 0.35f).toInt().coerceAtLeast(bassEnd + 2)

        for (k in 1 until binCount) {
            val r = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            val mag = (hypot(r, im) / 96f).coerceIn(0f, 1.4f)
            when {
                k <= bassEnd -> {
                    bassSum += mag
                    bassCount++
                }
                k <= midEnd -> {
                    midSum += mag
                    midCount++
                }
                else -> {
                    trebleSum += mag
                    trebleCount++
                }
            }
        }

        val rawBass = if (bassCount > 0) (bassSum / bassCount).coerceIn(0f, 1f) else 0f
        val rawMid = if (midCount > 0) (midSum / midCount).coerceIn(0f, 1f) else 0f
        val rawTreble = if (trebleCount > 0) (trebleSum / trebleCount * 1.35f).coerceIn(0f, 1f) else 0f

        smoothBass = smooth(smoothBass, rawBass, 0.55f, 0.16f)
        smoothMid = smooth(smoothMid, rawMid, 0.45f, 0.18f)
        smoothTreble = smooth(smoothTreble, rawTreble, 0.52f, 0.22f)
        val combinedAmp = ((smoothBass * 0.45f + smoothMid * 0.35f + smoothTreble * 0.2f + smoothAmp) * 0.65f)
            .coerceIn(0f, 1f)
        smoothBeat = smooth(smoothBeat, if (rawBass > 0.45f) rawBass else 0f, 0.6f, 0.12f)

        _spectrumFrame.value = AudioSpectrumFrame(
            bass = smoothBass,
            mid = smoothMid,
            treble = smoothTreble,
            amplitude = combinedAmp,
            waveform = latestWaveform.copyOf(),
            spectrumBins = outBins,
            source = SpectrumSource.HARDWARE_FFT,
            beatPulse = smoothBeat
        )
    }

    private fun startProceduralLoop() {
        if (simulationJob?.isActive == true) return
        simulationJob = scope.launch {
            var timeSec = 0f
            while (isActive) {
                if (!isPlaying) {
                    // Calm idle breath when paused
                    smoothBass = smooth(smoothBass, 0f, 0.2f, 0.12f)
                    smoothMid = smooth(smoothMid, 0f, 0.2f, 0.12f)
                    smoothTreble = smooth(smoothTreble, 0f, 0.2f, 0.12f)
                    smoothAmp = smooth(smoothAmp, 0f, 0.2f, 0.12f)
                    smoothBeat = smooth(smoothBeat, 0f, 0.2f, 0.12f)
                    for (i in 0 until 64) {
                        smoothBins[i] *= 0.86f
                        latestWaveform[i] *= 0.85f
                    }
                    _spectrumFrame.value = AudioSpectrumFrame(
                        bass = smoothBass,
                        mid = smoothMid,
                        treble = smoothTreble,
                        amplitude = smoothAmp,
                        waveform = latestWaveform.copyOf(),
                        spectrumBins = smoothBins.copyOf(),
                        source = SpectrumSource.PROCEDURAL_BPM,
                        beatPulse = smoothBeat
                    )
                    delay(32L)
                    continue
                }

                val dt = 0.016f
                timeSec += dt
                val beatsPerSec = bpm / 60f
                val beatPhase = (timeSec * beatsPerSec) % 1f
                val barBeatIndex = ((timeSec * beatsPerSec).toInt()) % 4

                // Kick drum transient every beat
                val kick = exp(-beatPhase * 6.5f)
                // Snare burst on beats 2 & 4
                val snareHit = if (barBeatIndex == 1 || barBeatIndex == 3) exp(-beatPhase * 8.0f) else 0f
                // 16th-note hi-hat sparkle
                val sixteenthPhase = (timeSec * beatsPerSec * 4f) % 1f
                val hiHat = exp(-sixteenthPhase * 11f) * 0.65f
                // Musical phrase swell
                val phraseEnergy = 0.62f + 0.38f * sin(timeSec * 0.65f)

                val targetBass = (kick * 0.88f * phraseEnergy).coerceIn(0.08f, 1f)
                val targetMid = ((0.28f + 0.48f * snareHit + 0.22f * abs(sin(timeSec * 2.4f))) * phraseEnergy)
                    .coerceIn(0.05f, 1f)
                val targetTreble = ((0.18f + hiHat * 0.72f + 0.15f * abs(sin(timeSec * 7.1f))) * phraseEnergy)
                    .coerceIn(0.05f, 1f)

                smoothBass = smooth(smoothBass, targetBass, 0.52f, 0.16f)
                smoothMid = smooth(smoothMid, targetMid, 0.45f, 0.18f)
                smoothTreble = smooth(smoothTreble, targetTreble, 0.55f, 0.24f)
                smoothAmp = ((smoothBass * 0.45f + smoothMid * 0.35f + smoothTreble * 0.2f)).coerceIn(0f, 1f)
                smoothBeat = kick

                for (i in 0 until 64) {
                    val norm = i / 63f
                    val bandEnergy = when {
                        norm < 0.25f -> smoothBass * (1f - norm * 1.4f)
                        norm < 0.65f -> smoothMid * (0.75f + 0.25f * sin(timeSec * 4f + i * 0.4f))
                        else -> smoothTreble * (0.65f + 0.35f * sin(timeSec * 9f + i * 0.8f))
                    }
                    smoothBins[i] = bandEnergy.coerceIn(0f, 1f)
                    val angle = norm * 4f * PI.toFloat() + timeSec * 5.2f
                    latestWaveform[i] = (
                        sin(angle) * smoothBass * 0.55f +
                            sin(angle * 2.3f + 1.1f) * smoothMid * 0.32f +
                            sin(angle * 5.1f) * smoothTreble * 0.22f
                        ).coerceIn(-1f, 1f)
                }

                _spectrumFrame.value = AudioSpectrumFrame(
                    bass = smoothBass,
                    mid = smoothMid,
                    treble = smoothTreble,
                    amplitude = smoothAmp,
                    waveform = latestWaveform.copyOf(),
                    spectrumBins = smoothBins.copyOf(),
                    source = SpectrumSource.PROCEDURAL_BPM,
                    beatPulse = smoothBeat
                )
                delay(16L)
            }
        }
    }

    private fun stopProceduralLoop() {
        simulationJob?.cancel()
        simulationJob = null
    }

    private fun releaseHardwareVisualizer() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        attachedSessionId = 0
    }

    private fun smooth(current: Float, target: Float, attack: Float, decay: Float): Float {
        val factor = if (target > current) attack else decay
        return current + (target - current) * factor
    }

    fun release() {
        stopProceduralLoop()
        releaseHardwareVisualizer()
    }
}
