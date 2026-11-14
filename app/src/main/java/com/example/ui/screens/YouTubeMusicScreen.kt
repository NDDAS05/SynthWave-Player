package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.PlaylistSummary
import com.example.ui.components.TrackActionsBottomSheet
import com.example.ui.components.TrackArtwork
import com.example.ui.components.glassPanel
import com.example.ui.viewmodel.YouTubeUiState

@Composable
fun YouTubeMusicScreen(
    uiState: YouTubeUiState,
    currentTrackId: String?,
    favoriteIds: Set<String>,
    playlists: List<PlaylistSummary>,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSaveApiKey: (String) -> Unit,
    onResetToDemo: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onAddTrackToPlaylist: (Long, AudioTrack) -> Unit,
    onCreatePlaylist: (String, AudioTrack?) -> Unit,
    loadEmbeddedArt: suspend (AudioTrack) -> ByteArray?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    var showSettingsDialog by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<AudioTrack?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_youtube_music),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (uiState.apiKey.isNotBlank()) {
                        "YouTube Data API v3 Active • BPM Beat Sync"
                    } else {
                        "Royalty-Free Demo Streams Ready • Tap gear for API v3"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (uiState.apiKey.isNotBlank()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }

            IconButton(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("youtube_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "YouTube API Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_youtube_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = onResetToDemo) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Reset search"
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboard?.hide()
                        onSearchSubmit()
                    }
                ),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.secondary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("youtube_search_input")
            )

            IconButton(
                onClick = {
                    keyboard?.hide()
                    onSearchSubmit()
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f))
                    .testTag("youtube_search_submit_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search YouTube",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .glassPanel(
                        shape = RoundedCornerShape(14.dp),
                        alpha = 0.72f,
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (uiState.showingDemoResults) {
                            "Curated Royalty-Free Streams"
                        } else {
                            "YouTube Search Results (${uiState.tracks.size})"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                items(uiState.tracks, key = { it.id }) { track ->
                    val isPlayingThis = track.id == currentTrackId
                    val isFav = track.id in favoriteIds
                    val borderColor = if (isPlayingThis) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassPanel(
                                shape = RoundedCornerShape(18.dp),
                                alpha = if (isPlayingThis) 0.84f else 0.64f,
                                borderColor = borderColor
                            )
                            .clickable { onPlayTrack(track, uiState.tracks) }
                            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TrackArtwork(
                            track = track,
                            loadEmbeddedArt = loadEmbeddedArt,
                            modifier = Modifier.size(64.dp),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isPlayingThis) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Text(
                                    text = "• ${track.bpmHint} BPM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(onClick = { onToggleFavorite(track) }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = if (isFav) "Remove favorite" else "Add favorite",
                                tint = if (isFav) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { actionTrack = track }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Stream options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        YouTubeApiSettingsDialog(
            currentKey = uiState.apiKey,
            onDismiss = { showSettingsDialog = false },
            onSave = { key ->
                onSaveApiKey(key)
                showSettingsDialog = false
            }
        )
    }

    actionTrack?.let { selected ->
        TrackActionsBottomSheet(
            track = selected,
            isFavorite = selected.id in favoriteIds,
            playlists = playlists,
            onDismiss = { actionTrack = null },
            onPlayNow = { onPlayTrack(selected, uiState.tracks) },
            onPlayNext = { onPlayNext(selected) },
            onAddToQueue = { onAddToQueue(selected) },
            onToggleFavorite = { onToggleFavorite(selected) },
            onAddToPlaylist = { playlistId -> onAddTrackToPlaylist(playlistId, selected) },
            onCreatePlaylistAndAdd = { newName -> onCreatePlaylist(newName, selected) }
        )
    }
}

@Composable
private fun YouTubeApiSettingsDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var apiKeyText by remember(currentKey) { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.youtube_settings_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter a YouTube Data API v3 key to search all of YouTube Music. Built-in royalty-free demo streams remain available even without a key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    label = { Text(stringResource(R.string.youtube_api_key_label)) },
                    placeholder = { Text(stringResource(R.string.youtube_api_key_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("youtube_api_key_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(apiKeyText) },
                modifier = Modifier.testTag("save_youtube_api_key_button")
            ) {
                Text("Save", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
