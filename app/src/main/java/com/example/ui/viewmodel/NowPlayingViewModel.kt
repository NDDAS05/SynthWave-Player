package com.example.ui.viewmodel

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.PlaybackCoordinator
import com.example.audio.VisualizerEngine
import com.example.data.model.AudioSpectrumFrame
import com.example.data.model.AudioTrack
import com.example.data.model.LyricsData
import com.example.data.model.PlaybackUiState
import com.example.data.model.VisualizerMode
import com.example.data.repository.MusicRepository
import com.example.data.scanner.LyricsResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LyricsUiState(
    val lyrics: LyricsData = LyricsData(),
    val activeLineIndex: Int = -1,
    val isLoading: Boolean = false
)

class NowPlayingViewModel(
    private val playbackCoordinator: PlaybackCoordinator,
    private val visualizerEngine: VisualizerEngine,
    private val lyricsResolver: LyricsResolver,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playbackCoordinator.playbackState
    val spectrumFrame: StateFlow<AudioSpectrumFrame> = visualizerEngine.spectrumFrame
    val favoriteIds: StateFlow<Set<String>> = musicRepository.favoriteIds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet()
    )

    private val _lyricsData = MutableStateFlow(LyricsData())
    private val _isLoadingLyrics = MutableStateFlow(false)

    val lyricsUiState: StateFlow<LyricsUiState> = combine(
        _lyricsData,
        _isLoadingLyrics,
        playbackCoordinator.playbackState.map { it.positionMs }
    ) { lyrics, loading, positionMs ->
        val activeIndex = if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            var matched = -1
            for (i in lyrics.lines.indices) {
                val ts = lyrics.lines[i].timestampMs ?: continue
                if (ts <= positionMs + 180L) {
                    matched = i
                } else {
                    break
                }
            }
            matched
        } else {
            -1
        }
        LyricsUiState(
            lyrics = lyrics,
            activeLineIndex = activeIndex,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LyricsUiState()
    )

    init {
        viewModelScope.launch {
            playbackCoordinator.playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { track ->
                    if (track == null) {
                        _lyricsData.value = LyricsData()
                    } else {
                        loadLyricsForTrack(track)
                    }
                }
        }
    }

    private fun loadLyricsForTrack(track: AudioTrack) {
        viewModelScope.launch {
            _isLoadingLyrics.value = true
            _lyricsData.value = lyricsResolver.resolveLyrics(track)
            _isLoadingLyrics.value = false
        }
    }

    fun saveCustomLyrics(rawText: String) {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            _lyricsData.value = lyricsResolver.saveCustomLyrics(track.id, rawText)
        }
    }

    fun importLyricsFromUri(uri: Uri) {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            val content = lyricsResolver.readTextFromUri(uri)
            if (!content.isNullOrBlank()) {
                _lyricsData.value = lyricsResolver.saveCustomLyrics(track.id, content)
            }
        }
    }

    fun clearCustomLyrics() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            _lyricsData.value = lyricsResolver.clearCustomLyrics(track)
        }
    }

    fun toggleFavoriteCurrent() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    fun togglePlayPause() = playbackCoordinator.togglePlayPause()
    fun skipNext() = playbackCoordinator.skipToNext()
    fun skipPrevious() = playbackCoordinator.skipToPrevious()
    fun seekTo(positionMs: Long) = playbackCoordinator.seekTo(positionMs)
    fun toggleShuffle() = playbackCoordinator.toggleShuffle()
    fun cycleRepeatMode() = playbackCoordinator.cycleRepeatMode()
    fun setVisualizerMode(mode: VisualizerMode) = playbackCoordinator.setVisualizerMode(mode)
    fun setNowPlayingExpanded(expanded: Boolean) = playbackCoordinator.setNowPlayingExpanded(expanded)
    fun toggleVideoPreviewExpanded() = playbackCoordinator.toggleVideoPreviewExpanded()
    fun clearYouTubeError() = playbackCoordinator.clearYouTubeError()
    fun jumpToQueueIndex(index: Int) = playbackCoordinator.jumpToQueueIndex(index)
    fun removeFromQueue(index: Int) = playbackCoordinator.removeFromQueue(index)
}
