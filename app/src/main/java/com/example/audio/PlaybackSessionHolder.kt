package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackSessionHolder {
    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _isLocalPlaying = MutableStateFlow(false)
    val isLocalPlaying: StateFlow<Boolean> = _isLocalPlaying.asStateFlow()

    fun updateAudioSessionId(sessionId: Int) {
        if (sessionId > 0) {
            _audioSessionId.value = sessionId
        }
    }

    fun updateLocalPlaying(isPlaying: Boolean) {
        _isLocalPlaying.value = isPlaying
    }
}
