package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioSpectrumFrame
import com.example.data.model.AudioTrack
import com.example.data.model.PlaybackUiState
import com.example.data.model.RepeatMode
import com.example.data.model.SpectrumSource
import com.example.data.model.VisualizerMode
import com.example.data.model.formatDuration
import com.example.ui.components.TrackArtwork
import com.example.ui.components.glassPanel
import com.example.ui.viewmodel.LyricsUiState
import com.example.ui.visualizer.AmbientRgbBackground
import com.example.ui.visualizer.ClothRgbWaveVisualizer
import com.example.ui.visualizer.TwistingRgbRingVisualizer

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    spectrumFrame: AudioSpectrumFrame,
    lyricsUiState: LyricsUiState,
    isFavorite: Boolean,
    loadEmbeddedArt: suspend (AudioTrack) -> ByteArray?,
    onCollapse: () -> Unit,
    onSelectVisualizerMode: (VisualizerMode) -> Unit,
    onToggleVideoPreview: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onSaveCustomLyrics: (String) -> Unit,
    onImportLyricsUri: (Uri) -> Unit,
    onClearCustomLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return
    var showLyricsSheet by remember { mutableStateOf(false) }

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    BackHandler {
        when {
            showLyricsSheet -> showLyricsSheet = false
            playbackState.isVideoPreviewExpanded -> onToggleVideoPreview()
            else -> onCollapse()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onVerticalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (totalDrag > 180f) {
                            onCollapse()
                        } else if (totalDrag < -180f) {
                            showLyricsSheet = true
                        }
                    }
                )
            }
    ) {
        // Dynamic Ambient RGB Mesh Background pulsing subtly with bass kicks
        AmbientRgbBackground(
            spectrumFrame = spectrumFrame,
            isPlaying = playbackState.isPlaying
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .widthIn(max = 640.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Controls: Collapse + Minimal Glassmorphic Visualizer Mode Pill + Telemetry
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse Now Playing",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Top-Center Glassmorphic Pill Switcher (Mode A vs Mode B)
                VisualizerModePill(
                    selectedMode = playbackState.visualizerMode,
                    onSelectMode = onSelectVisualizerMode,
                    modifier = Modifier.align(Alignment.Center)
                )

                // Spectrum Source Badge (Live FFT vs BPM Beat)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .glassPanel(shape = RoundedCornerShape(50), alpha = 0.62f)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (spectrumFrame.source == SpectrumSource.HARDWARE_FFT) {
                            stringResource(R.string.visualizer_fft_active)
                        } else {
                            stringResource(R.string.visualizer_bpm_active)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spectrumFrame.source == SpectrumSource.HARDWARE_FFT) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // Central Interactive Visualizer / Expandable YouTube Video Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!playbackState.isVideoPreviewExpanded) {
                    AnimatedContent(
                        targetState = playbackState.visualizerMode,
                        label = "visualizer_mode_switch"
                    ) { mode ->
                        when (mode) {
                            VisualizerMode.TWISTING_RING -> TwistingRgbRingVisualizer(
                                spectrumFrame = spectrumFrame,
                                isPlaying = playbackState.isPlaying,
                                modifier = Modifier.fillMaxSize()
                            )
                            VisualizerMode.CLOTH_WAVE -> ClothRgbWaveVisualizer(
                                spectrumFrame = spectrumFrame,
                                isPlaying = playbackState.isPlaying,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Expandable Video Preview Toggle when streaming YouTube
                if (playbackState.isYouTubeActive) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .glassPanel(shape = RoundedCornerShape(50), alpha = 0.78f)
                            .clickable(onClick = onToggleVideoPreview)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("toggle_video_preview_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isVideoPreviewExpanded) {
                                Icons.Rounded.GraphicEq
                            } else {
                                Icons.Rounded.OndemandVideo
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (playbackState.isVideoPreviewExpanded) {
                                "Show RGB Visualizer"
                            } else {
                                "Expand Video Preview"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (playbackState.youtubePlayerError != null) {
                Text(
                    text = playbackState.youtubePlayerError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Track Metadata + Artwork Badge + Quick Action Triggers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(shape = RoundedCornerShape(22.dp), alpha = 0.65f)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrackArtwork(
                    track = track,
                    loadEmbeddedArt = loadEmbeddedArt,
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("now_playing_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove favorite" else "Favorite",
                        tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { showLyricsSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("open_lyrics_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Open lyrics & transcripts",
                        tint = if (lyricsUiState.lyrics.lines.isNotEmpty()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Scrubber + Timestamps
            val safeDuration = playbackState.durationMs.coerceAtLeast(1L)
            val sliderPosition = if (isScrubbing) {
                scrubValue
            } else {
                (playbackState.positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPosition,
                    onValueChange = { fraction ->
                        isScrubbing = true
                        scrubValue = fraction
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onSeekTo((scrubValue * safeDuration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_seek_slider")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = if (isScrubbing) {
                        (scrubValue * safeDuration).toLong()
                    } else {
                        playbackState.positionMs
                    }
                    Text(
                        text = formatDuration(displayedPos),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (playbackState.durationMs > 0L) formatDuration(playbackState.durationMs) else "LIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Primary Transport Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("previous_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("now_playing_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("next_track_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("repeat_button")
                ) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == RepeatMode.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat mode",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Bottom subtle Swipe-up Lyrics / Queue Hint Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showLyricsSheet = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Swipe up for Lyrics",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onOpenQueue)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("now_playing_queue_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Queue (${playbackState.queue.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLyricsSheet) {
        LyricsOverlaySheet(
            trackTitle = track.title,
            lyricsUiState = lyricsUiState,
            onDismiss = { showLyricsSheet = false },
            onSeekToTimestamp = onSeekTo,
            onSaveCustomLyrics = onSaveCustomLyrics,
            onImportLyricsUri = onImportLyricsUri,
            onClearCustomLyrics = onClearCustomLyrics
        )
    }
}

@Composable
private fun VisualizerModePill(
    selectedMode: VisualizerMode,
    onSelectMode: (VisualizerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .glassPanel(shape = RoundedCornerShape(50), alpha = 0.72f)
            .padding(4.dp)
            .testTag("visualizer_mode_pill"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillOption(
            label = stringResource(R.string.visualizer_mode_ring),
            selected = selectedMode == VisualizerMode.TWISTING_RING,
            onClick = { onSelectMode(VisualizerMode.TWISTING_RING) },
            testTagName = "mode_ring_pill"
        )
        PillOption(
            label = stringResource(R.string.visualizer_mode_cloth),
            selected = selectedMode == VisualizerMode.CLOTH_WAVE,
            onClick = { onSelectMode(VisualizerMode.CLOTH_WAVE) },
            testTagName = "mode_cloth_pill"
        )
    }
}

@Composable
private fun PillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTagName: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag(testTagName),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
