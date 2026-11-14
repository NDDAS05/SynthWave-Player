package com.example.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackUiState
import com.example.data.model.RepeatMode
import com.example.data.model.TrackSource
import com.example.data.model.VisualizerMode
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.MusicRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackCoordinator(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val visualizerEngine: VisualizerEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    val youTubeBridge: YouTubePlayerBridge = YouTubePlayerBridge(
        onStateChanged = { isPlaying ->
            if (_playbackState.value.isYouTubeActive) {
                _playbackState.update { it.copy(isPlaying = isPlaying, youtubePlayerError = null) }
                refreshVisualizerBinding()
            }
        },
        onProgressChanged = { posMs ->
            if (_playbackState.value.isYouTubeActive) {
                _playbackState.update { it.copy(positionMs = posMs) }
            }
        },
        onDurationChanged = { durMs ->
            if (_playbackState.value.isYouTubeActive) {
                _playbackState.update { it.copy(durationMs = durMs) }
            }
        },
        onEnded = {
            if (_playbackState.value.isYouTubeActive) {
                skipToNext()
            }
        },
        onError = { message ->
            _playbackState.update { it.copy(isPlaying = false, youtubePlayerError = message) }
            refreshVisualizerBinding()
        }
    )

    init {
        connectToMediaService()
        observePreferences()
        observeAudioSession()
        startLocalProgressPolling()
    }

    private fun connectToMediaService() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, SynthWavePlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }.onSuccess { controller ->
                    mediaController = controller
                    controller.addListener(mediaControllerListener)
                    applyShuffleAndRepeatToController()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private val mediaControllerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!_playbackState.value.isYouTubeActive) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                refreshVisualizerBinding()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_playbackState.value.isYouTubeActive || mediaItem == null) return
            val queue = _playbackState.value.queue
            val matchedIndex = queue.indexOfFirst { it.id == mediaItem.mediaId }
            if (matchedIndex >= 0) {
                val track = queue[matchedIndex]
                _playbackState.update {
                    it.copy(
                        currentTrack = track,
                        currentIndex = matchedIndex,
                        durationMs = track.durationMs.coerceAtLeast(
                            mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                        ),
                        positionMs = 0L
                    )
                }
                recordTrackPlay(track)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!_playbackState.value.isYouTubeActive && playbackState == Player.STATE_READY) {
                val dur = mediaController?.duration ?: 0L
                if (dur > 0L) {
                    _playbackState.update { it.copy(durationMs = dur) }
                }
            }
        }
    }

    private fun observePreferences() {
        scope.launch {
            settingsRepository.visualizerMode.collectLatest { mode ->
                _playbackState.update { it.copy(visualizerMode = mode) }
            }
        }
        scope.launch {
            settingsRepository.shuffleEnabled.collectLatest { shuffle ->
                _playbackState.update { it.copy(shuffleEnabled = shuffle) }
                applyShuffleAndRepeatToController()
            }
        }
        scope.launch {
            settingsRepository.repeatMode.collectLatest { repeat ->
                _playbackState.update { it.copy(repeatMode = repeat) }
                applyShuffleAndRepeatToController()
            }
        }
    }

    private fun observeAudioSession() {
        scope.launch {
            PlaybackSessionHolder.audioSessionId.collectLatest {
                refreshVisualizerBinding()
            }
        }
    }

    private fun startLocalProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val state = _playbackState.value
                if (!state.isYouTubeActive && state.currentTrack != null) {
                    val controller = mediaController
                    if (controller != null) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        val dur = controller.duration.takeIf { it > 0L } ?: state.durationMs
                        if (pos != state.positionMs || dur != state.durationMs) {
                            _playbackState.update { it.copy(positionMs = pos, durationMs = dur) }
                        }
                    }
                }
                delay(200L)
            }
        }
    }

    fun updateRecordAudioPermission(granted: Boolean) {
        _playbackState.update { it.copy(hasRecordAudioPermission = granted) }
        refreshVisualizerBinding()
    }

    fun setVisualizerMode(mode: VisualizerMode) {
        _playbackState.update { it.copy(visualizerMode = mode) }
        scope.launch { settingsRepository.saveVisualizerMode(mode) }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _playbackState.update {
            it.copy(
                isNowPlayingExpanded = expanded,
                isVideoPreviewExpanded = if (!expanded) false else it.isVideoPreviewExpanded
            )
        }
    }

    fun toggleVideoPreviewExpanded() {
        _playbackState.update { it.copy(isVideoPreviewExpanded = !it.isVideoPreviewExpanded) }
    }

    fun clearYouTubeError() {
        _playbackState.update { it.copy(youtubePlayerError = null) }
    }

    fun playTrack(track: AudioTrack, queue: List<AudioTrack>) {
        val safeQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val targetIndex = safeQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        if (track.source == TrackSource.YOUTUBE) {
            mediaController?.pause()
            val videoId = track.videoId ?: track.id.removePrefix("yt:")
            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    queue = safeQueue,
                    currentIndex = targetIndex,
                    isPlaying = true,
                    positionMs = 0L,
                    durationMs = track.durationMs,
                    isYouTubeActive = true,
                    youtubePlayerError = null
                )
            }
            youTubeBridge.loadAndPlay(videoId)
            recordTrackPlay(track)
            refreshVisualizerBinding()
            return
        }

        // Local Audio Playback via Media3 MediaController
        youTubeBridge.pause()
        youTubeBridge.resetLoadedVideo()

        val localTracks = safeQueue.filter { it.source == TrackSource.LOCAL }
        val localIndex = localTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = localTracks,
                currentIndex = localIndex,
                isPlaying = true,
                positionMs = 0L,
                durationMs = track.durationMs,
                isYouTubeActive = false,
                isVideoPreviewExpanded = false,
                youtubePlayerError = null
            )
        }

        val controller = mediaController
        if (controller != null) {
            val mediaItems = localTracks.map { it.toMediaItem() }
            controller.setMediaItems(mediaItems, localIndex, 0L)
            applyShuffleAndRepeatToController()
            controller.prepare()
            controller.play()
        }

        recordTrackPlay(track)
        refreshVisualizerBinding()
    }

    fun togglePlayPause() {
        val state = _playbackState.value
        val current = state.currentTrack ?: return
        if (state.isYouTubeActive || current.source == TrackSource.YOUTUBE) {
            if (state.isPlaying) {
                youTubeBridge.pause()
                _playbackState.update { it.copy(isPlaying = false) }
            } else {
                youTubeBridge.play()
                _playbackState.update { it.copy(isPlaying = true) }
            }
        } else {
            val controller = mediaController ?: return
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.mediaItemCount == 0) {
                    playTrack(current, state.queue)
                } else {
                    controller.play()
                }
            }
        }
        refreshVisualizerBinding()
    }

    fun seekTo(positionMs: Long) {
        val state = _playbackState.value
        val clamped = positionMs.coerceAtLeast(0L)
        _playbackState.update { it.copy(positionMs = clamped) }
        if (state.isYouTubeActive) {
            youTubeBridge.seekTo(clamped)
        } else {
            mediaController?.seekTo(clamped)
        }
    }

    fun skipToNext() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (state.isYouTubeActive) {
            val nextIdx = when {
                state.repeatMode == RepeatMode.ONE -> state.currentIndex
                state.shuffleEnabled && queue.size > 1 -> {
                    val candidates = queue.indices.filter { it != state.currentIndex }
                    candidates.random()
                }
                state.currentIndex + 1 < queue.size -> state.currentIndex + 1
                state.repeatMode == RepeatMode.ALL -> 0
                else -> return
            }
            playTrack(queue[nextIdx], queue)
        } else {
            val controller = mediaController ?: return
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else if (state.repeatMode == RepeatMode.ALL && queue.isNotEmpty()) {
                controller.seekToDefaultPosition(0)
                controller.play()
            }
        }
    }

    fun skipToPrevious() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (state.positionMs > 3500L) {
            seekTo(0L)
            return
        }

        if (state.isYouTubeActive) {
            val prevIdx = when {
                state.currentIndex > 0 -> state.currentIndex - 1
                state.repeatMode == RepeatMode.ALL -> queue.lastIndex
                else -> 0
            }
            playTrack(queue[prevIdx], queue)
        } else {
            val controller = mediaController ?: return
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else {
                controller.seekTo(0L)
            }
        }
    }

    fun toggleShuffle() {
        val next = !_playbackState.value.shuffleEnabled
        _playbackState.update { it.copy(shuffleEnabled = next) }
        scope.launch { settingsRepository.saveShuffleEnabled(next) }
        applyShuffleAndRepeatToController()
    }

    fun cycleRepeatMode() {
        val next = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playbackState.update { it.copy(repeatMode = next) }
        scope.launch { settingsRepository.saveRepeatMode(next) }
        applyShuffleAndRepeatToController()
    }

    fun addToQueue(track: AudioTrack) {
        val state = _playbackState.value
        if (state.currentTrack == null) {
            playTrack(track, listOf(track))
            return
        }
        if (state.isYouTubeActive != (track.source == TrackSource.YOUTUBE)) {
            // Switch smoothly if adding from the opposite source when user clicks Play Next
            playTrack(track, listOf(track))
            return
        }
        val updatedQueue = state.queue + track
        _playbackState.update { it.copy(queue = updatedQueue) }
        if (!state.isYouTubeActive) {
            mediaController?.addMediaItem(track.toMediaItem())
        }
    }

    fun playNext(track: AudioTrack) {
        val state = _playbackState.value
        if (state.currentTrack == null || state.isYouTubeActive != (track.source == TrackSource.YOUTUBE)) {
            playTrack(track, listOf(track))
            return
        }
        val insertIndex = (state.currentIndex + 1).coerceIn(0, state.queue.size)
        val updatedQueue = state.queue.toMutableList().apply { add(insertIndex, track) }
        _playbackState.update { it.copy(queue = updatedQueue) }
        if (!state.isYouTubeActive) {
            mediaController?.addMediaItem(insertIndex, track.toMediaItem())
        }
    }

    fun jumpToQueueIndex(index: Int) {
        val state = _playbackState.value
        if (index !in state.queue.indices) return
        if (state.isYouTubeActive) {
            playTrack(state.queue[index], state.queue)
        } else {
            mediaController?.seekToDefaultPosition(index)
            mediaController?.play()
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _playbackState.value
        if (index !in state.queue.indices) return
        if (index == state.currentIndex && state.queue.size == 1) return

        val updated = state.queue.toMutableList().apply { removeAt(index) }
        val adjustedCurrentIndex = when {
            index < state.currentIndex -> state.currentIndex - 1
            else -> state.currentIndex.coerceAtMost(updated.lastIndex)
        }
        _playbackState.update {
            it.copy(
                queue = updated,
                currentIndex = adjustedCurrentIndex,
                currentTrack = updated.getOrNull(adjustedCurrentIndex)
            )
        }
        if (!state.isYouTubeActive) {
            mediaController?.removeMediaItem(index)
        }
    }

    private fun applyShuffleAndRepeatToController() {
        val controller = mediaController ?: return
        val state = _playbackState.value
        controller.shuffleModeEnabled = state.shuffleEnabled
        controller.repeatMode = when (state.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    private fun refreshVisualizerBinding() {
        val state = _playbackState.value
        visualizerEngine.updatePlaybackContext(
            audioSessionId = PlaybackSessionHolder.audioSessionId.value,
            hasRecordAudioPermission = state.hasRecordAudioPermission,
            isYouTubeMode = state.isYouTubeActive,
            isCurrentlyPlaying = state.isPlaying,
            trackBpm = state.currentTrack?.bpmHint ?: 122
        )
    }

    private fun recordTrackPlay(track: AudioTrack) {
        scope.launch(Dispatchers.IO) {
            musicRepository.recordPlay(track)
        }
    }

    private fun AudioTrack.toMediaItem(): MediaItem {
        val artworkUri = if (albumId > 0L) {
            Uri.parse("content://media/external/audio/albumart/$albumId")
        } else null

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(contentUri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        progressJob?.cancel()
        visualizerEngine.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
