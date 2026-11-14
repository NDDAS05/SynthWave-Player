package com.example.audio

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

class YouTubePlayerBridge(
    private val onStateChanged: (isPlaying: Boolean) -> Unit,
    private val onProgressChanged: (positionMs: Long) -> Unit,
    private val onDurationChanged: (durationMs: Long) -> Unit,
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var youTubePlayer: YouTubePlayer? = null
    private var pendingVideoId: String? = null
    private var pendingAutoPlay: Boolean = false
    private var currentLoadedVideoId: String? = null

    fun attachPlayerView(view: YouTubePlayerView) {
        view.enableBackgroundPlayback(true)
        view.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(player: YouTubePlayer) {
                youTubePlayer = player
                val queuedId = pendingVideoId
                if (!queuedId.isNullOrBlank()) {
                    currentLoadedVideoId = queuedId
                    if (pendingAutoPlay) {
                        player.loadVideo(queuedId, 0f)
                    } else {
                        player.cueVideo(queuedId, 0f)
                    }
                    pendingVideoId = null
                }
            }

            override fun onStateChange(
                player: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                when (state) {
                    PlayerConstants.PlayerState.PLAYING -> onStateChanged(true)
                    PlayerConstants.PlayerState.PAUSED,
                    PlayerConstants.PlayerState.BUFFERING -> onStateChanged(false)
                    PlayerConstants.PlayerState.ENDED -> {
                        onStateChanged(false)
                        onEnded()
                    }
                    else -> Unit
                }
            }

            override fun onCurrentSecond(player: YouTubePlayer, second: Float) {
                onProgressChanged((second * 1000f).toLong().coerceAtLeast(0L))
            }

            override fun onVideoDuration(player: YouTubePlayer, duration: Float) {
                if (duration > 0f) {
                    onDurationChanged((duration * 1000f).toLong())
                }
            }

            override fun onError(
                player: YouTubePlayer,
                error: PlayerConstants.PlayerError
            ) {
                val msg = when (error) {
                    PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                        "Video owner disabled embedded playback. Try another stream."
                    PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
                        "YouTube video could not be found."
                    else -> "YouTube playback error: ${error.name}"
                }
                onError(msg)
            }
        })
    }

    fun loadAndPlay(videoId: String) {
        val player = youTubePlayer
        if (player == null) {
            pendingVideoId = videoId
            pendingAutoPlay = true
            return
        }
        if (currentLoadedVideoId != videoId) {
            currentLoadedVideoId = videoId
            player.loadVideo(videoId, 0f)
        } else {
            player.play()
        }
    }

    fun play() {
        youTubePlayer?.play()
    }

    fun pause() {
        youTubePlayer?.pause()
    }

    fun seekTo(positionMs: Long) {
        youTubePlayer?.seekTo(positionMs / 1000f)
    }

    fun resetLoadedVideo() {
        currentLoadedVideoId = null
        pendingVideoId = null
    }
}
