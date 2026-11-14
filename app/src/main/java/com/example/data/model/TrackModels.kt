package com.example.data.model

import androidx.compose.runtime.Immutable

enum class TrackSource {
    LOCAL,
    YOUTUBE
}

@Immutable
data class AudioTrack(
    val id: String,
    val source: TrackSource,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val contentUri: String = "",
    val filePath: String? = null,
    val folderName: String = "Music",
    val folderPath: String = "",
    val albumId: Long = 0L,
    val videoId: String? = null,
    val thumbnailUrl: String? = null,
    val bpmHint: Int = 122,
    val isDemoStream: Boolean = false
)

enum class LibraryTab(val label: String) {
    SONGS("Songs"),
    PLAYLISTS("Playlists"),
    FOLDERS("Folders"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}

enum class MainDestination {
    LOCAL_LIBRARY,
    YOUTUBE_MUSIC
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class VisualizerMode {
    TWISTING_RING,
    CLOTH_WAVE
}

enum class SpectrumSource {
    HARDWARE_FFT,
    PROCEDURAL_BPM
}

@Immutable
data class AudioSpectrumFrame(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val treble: Float = 0f,
    val amplitude: Float = 0f,
    val waveform: FloatArray = FloatArray(64),
    val spectrumBins: FloatArray = FloatArray(64),
    val source: SpectrumSource = SpectrumSource.PROCEDURAL_BPM,
    val beatPulse: Float = 0f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSpectrumFrame) return false
        return bass == other.bass &&
            mid == other.mid &&
            treble == other.treble &&
            amplitude == other.amplitude &&
            source == other.source &&
            beatPulse == other.beatPulse &&
            waveform.contentEquals(other.waveform) &&
            spectrumBins.contentEquals(other.spectrumBins)
    }

    override fun hashCode(): Int {
        var result = bass.hashCode()
        result = 31 * result + mid.hashCode()
        result = 31 * result + treble.hashCode()
        result = 31 * result + amplitude.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + beatPulse.hashCode()
        result = 31 * result + waveform.contentHashCode()
        result = 31 * result + spectrumBins.contentHashCode()
        return result
    }
}

enum class LyricsSource(val label: String) {
    LRC_FILE("Synced .lrc"),
    TXT_FILE("Folder .txt"),
    EMBEDDED_USLT("Embedded USLT"),
    MANUAL("Custom Lyrics"),
    NONE("No Lyrics")
}

@Immutable
data class LyricLine(
    val timestampMs: Long?,
    val text: String
)

@Immutable
data class LyricsData(
    val source: LyricsSource = LyricsSource.NONE,
    val lines: List<LyricLine> = emptyList(),
    val isSynced: Boolean = false,
    val rawText: String = ""
)

@Immutable
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAt: Long
)

@Immutable
data class PlaybackUiState(
    val currentTrack: AudioTrack? = null,
    val queue: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val visualizerMode: VisualizerMode = VisualizerMode.TWISTING_RING,
    val isYouTubeActive: Boolean = false,
    val isVideoPreviewExpanded: Boolean = false,
    val isNowPlayingExpanded: Boolean = false,
    val hasRecordAudioPermission: Boolean = false,
    val youtubePlayerError: String? = null
)

fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
