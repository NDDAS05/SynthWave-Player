package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.LibraryTab
import com.example.data.model.PlaylistSummary
import com.example.data.model.formatDuration
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.PermissionOnboardingCard
import com.example.ui.components.TrackActionsBottomSheet
import com.example.ui.components.TrackArtwork
import com.example.ui.components.glassPanel
import com.example.ui.viewmodel.LibraryUiState
import com.example.ui.viewmodel.TrackGroup
import kotlinx.coroutines.flow.Flow

@Composable
fun LocalLibraryScreen(
    uiState: LibraryUiState,
    currentTrackId: String?,
    onSearchQueryChange: (String) -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onToggleRecentsFilter: () -> Unit,
    onRequestAllPermissions: () -> Unit,
    onRequestStorageOnly: () -> Unit,
    onRescanLibrary: () -> Unit,
    onPlayTrack: (AudioTrack, List<AudioTrack>) -> Unit,
    onPlayNext: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onCreatePlaylist: (String, AudioTrack?) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddTrackToPlaylist: (Long, AudioTrack) -> Unit,
    onRemoveTrackFromPlaylist: (Long, String) -> Unit,
    getPlaylistTracksFlow: (Long) -> Flow<List<AudioTrack>>,
    loadEmbeddedArt: suspend (AudioTrack) -> ByteArray?,
    onOpenQueue: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    var activeGroup by remember { mutableStateOf<TrackGroup?>(null) }
    var activePlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<AudioTrack?>(null) }

    if (activeGroup != null || activePlaylist != null) {
        BackHandler {
            activeGroup = null
            activePlaylist = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${uiState.allTracks.size} local tracks • ${uiState.playlists.size} playlists",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { showCreatePlaylistDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("new_playlist_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New playlist",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("open_queue_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Open queue",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            if (uiState.hasStoragePermission) {
                IconButton(
                    onClick = onRescanLibrary,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("rescan_library_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Rescan library",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Reactive Search Field
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_local_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("local_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Tabs + Smart Filters (Favorites / Recently Played)
        if (activeGroup == null && activePlaylist == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryTab.entries.forEach { tab ->
                    val selected = uiState.selectedTab == tab &&
                        !uiState.showFavoritesOnly &&
                        !uiState.showRecentsOnly
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTab(tab) },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("library_tab_${tab.name.lowercase()}")
                    )
                }

                FilterChip(
                    selected = uiState.showFavoritesOnly,
                    onClick = onToggleFavoritesFilter,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Favorites (${uiState.favorites.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.secondary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.secondary,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("filter_favorites_chip")
                )

                FilterChip(
                    selected = uiState.showRecentsOnly,
                    onClick = onToggleRecentsFilter,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Recent (${uiState.recentPlays.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.20f),
                        selectedLabelColor = MaterialTheme.colorScheme.tertiary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.tertiary,
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.testTag("filter_recents_chip")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Record Audio subtle banner if storage granted but mic declined
        if (uiState.hasStoragePermission && !uiState.hasRecordAudioPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .glassPanel(shape = RoundedCornerShape(14.dp), alpha = 0.65f)
                    .clickable(onClick = onRequestAllPermissions)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MicOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Visualizer running in BPM Beat fallback. Tap to enable live FFT audio capture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Main Content Area
        when {
            !uiState.hasStoragePermission && !uiState.showFavoritesOnly && !uiState.showRecentsOnly && uiState.selectedTab != LibraryTab.PLAYLISTS -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        PermissionOnboardingCard(
                            hasStoragePermission = uiState.hasStoragePermission,
                            hasRecordAudioPermission = uiState.hasRecordAudioPermission,
                            onRequestAllPermissions = onRequestAllPermissions,
                            onRequestStorageOnly = onRequestStorageOnly
                        )
                    }
                }
            }

            uiState.isScanning -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Scanning internal & SD card audio...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            activePlaylist != null -> {
                val playlist = activePlaylist!!
                val playlistTracks by getPlaylistTracksFlow(playlist.id).collectAsStateWithLifecycle(emptyList())
                DrillDownHeader(
                    title = playlist.name,
                    subtitle = "${playlistTracks.size} tracks",
                    onBack = { activePlaylist = null },
                    onPlayAll = {
                        if (playlistTracks.isNotEmpty()) {
                            onPlayTrack(playlistTracks.first(), playlistTracks)
                        }
                    }
                )
                if (playlistTracks.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        title = "Playlist is empty",
                        subtitle = "Tap the three-dot menu on any local or YouTube track to add songs here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlistTracks, key = { "${playlist.id}_${it.id}" }) { track ->
                            TrackRowItem(
                                track = track,
                                isPlayingThis = track.id == currentTrackId,
                                isFavorite = track.id in uiState.favoriteIds,
                                loadEmbeddedArt = loadEmbeddedArt,
                                onClick = { onPlayTrack(track, playlistTracks) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onMoreClick = { onRemoveTrackFromPlaylist(playlist.id, track.id) },
                                moreIcon = Icons.Rounded.DeleteOutline,
                                moreDescription = "Remove from playlist"
                            )
                        }
                    }
                }
            }

            activeGroup != null -> {
                val group = activeGroup!!
                DrillDownHeader(
                    title = group.title,
                    subtitle = group.subtitle,
                    onBack = { activeGroup = null },
                    onPlayAll = {
                        if (group.tracks.isNotEmpty()) {
                            onPlayTrack(group.tracks.first(), group.tracks)
                        }
                    }
                )
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(group.tracks, key = { it.id }) { track ->
                        TrackRowItem(
                            track = track,
                            isPlayingThis = track.id == currentTrackId,
                            isFavorite = track.id in uiState.favoriteIds,
                            loadEmbeddedArt = loadEmbeddedArt,
                            onClick = { onPlayTrack(track, group.tracks) },
                            onToggleFavorite = { onToggleFavorite(track) },
                            onMoreClick = { actionTrack = track }
                        )
                    }
                }
            }

            uiState.selectedTab == LibraryTab.PLAYLISTS -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassPanel(shape = RoundedCornerShape(18.dp), alpha = 0.72f)
                                .clickable { showCreatePlaylistDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text(
                                    text = "Create New Playlist",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Saved locally across restarts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (uiState.playlists.isEmpty()) {
                        item {
                            EmptyLibraryState(
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                title = "No custom playlists yet",
                                subtitle = "Create a playlist above or add songs from your library."
                            )
                        }
                    } else {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassPanel(shape = RoundedCornerShape(18.dp), alpha = 0.65f)
                                    .clickable { activePlaylist = playlist }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.trackCount} ${if (playlist.trackCount == 1) "track" else "tracks"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { onDeletePlaylist(playlist.id) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete playlist",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            uiState.selectedTab == LibraryTab.FOLDERS -> {
                GroupListView(
                    groups = uiState.folderGroups,
                    icon = Icons.Rounded.Folder,
                    emptyTitle = "No audio folders found",
                    emptySubtitle = "Audio files longer than 15 seconds will be grouped by directory.",
                    bottomPadding = bottomPadding,
                    onGroupClick = { activeGroup = it }
                )
            }

            uiState.selectedTab == LibraryTab.ARTISTS -> {
                GroupListView(
                    groups = uiState.artistGroups,
                    icon = Icons.Rounded.Person,
                    emptyTitle = "No artists found",
                    emptySubtitle = "Scan your audio library to organize tracks by artist.",
                    bottomPadding = bottomPadding,
                    onGroupClick = { activeGroup = it }
                )
            }

            uiState.selectedTab == LibraryTab.ALBUMS -> {
                GroupListView(
                    groups = uiState.albumGroups,
                    icon = Icons.Rounded.Album,
                    emptyTitle = "No albums found",
                    emptySubtitle = "Scan your audio library to organize tracks by album.",
                    bottomPadding = bottomPadding,
                    onGroupClick = { activeGroup = it }
                )
            }

            else -> {
                val list = uiState.filteredTracks
                if (list.isEmpty()) {
                    EmptyLibraryState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = when {
                            uiState.showFavoritesOnly -> "No favorite tracks yet"
                            uiState.showRecentsOnly -> "No recently played tracks"
                            uiState.searchQuery.isNotBlank() -> "No matching tracks"
                            else -> "No audio tracks over 15s found"
                        },
                        subtitle = when {
                            uiState.showFavoritesOnly -> "Tap the heart icon on any track to save it here."
                            uiState.showRecentsOnly -> "Tracks you play will appear here automatically."
                            else -> "Add MP3, FLAC, M4A, or WAV files (>15s) to device storage and tap Rescan."
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { track ->
                            TrackRowItem(
                                track = track,
                                isPlayingThis = track.id == currentTrackId,
                                isFavorite = track.id in uiState.favoriteIds,
                                loadEmbeddedArt = loadEmbeddedArt,
                                onClick = { onPlayTrack(track, list) },
                                onToggleFavorite = { onToggleFavorite(track) },
                                onMoreClick = { actionTrack = track }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name -> onCreatePlaylist(name, null) }
        )
    }

    actionTrack?.let { selected ->
        TrackActionsBottomSheet(
            track = selected,
            isFavorite = selected.id in uiState.favoriteIds,
            playlists = uiState.playlists,
            onDismiss = { actionTrack = null },
            onPlayNow = { onPlayTrack(selected, uiState.filteredTracks) },
            onPlayNext = { onPlayNext(selected) },
            onAddToQueue = { onAddToQueue(selected) },
            onToggleFavorite = { onToggleFavorite(selected) },
            onAddToPlaylist = { playlistId -> onAddTrackToPlaylist(playlistId, selected) },
            onCreatePlaylistAndAdd = { newName -> onCreatePlaylist(newName, selected) }
        )
    }
}

@Composable
private fun DrillDownHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onPlayAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .glassPanel(shape = RoundedCornerShape(16.dp), alpha = 0.7f)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onPlayAll, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play all",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun GroupListView(
    groups: List<TrackGroup>,
    icon: ImageVector,
    emptyTitle: String,
    emptySubtitle: String,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onGroupClick: (TrackGroup) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyLibraryState(icon = icon, title = emptyTitle, subtitle = emptySubtitle)
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groups, key = { it.key }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(shape = RoundedCornerShape(18.dp), alpha = 0.65f)
                    .clickable { onGroupClick(group) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = group.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TrackRowItem(
    track: AudioTrack,
    isPlayingThis: Boolean,
    isFavorite: Boolean,
    loadEmbeddedArt: suspend (AudioTrack) -> ByteArray?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    moreIcon: ImageVector = Icons.Rounded.MoreVert,
    moreDescription: String = "Track options"
) {
    val borderColor = if (isPlayingThis) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel(shape = RoundedCornerShape(18.dp), alpha = if (isPlayingThis) 0.82f else 0.62f, borderColor = borderColor)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TrackArtwork(
            track = track,
            loadEmbeddedArt = loadEmbeddedArt,
            modifier = Modifier.size(50.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlayingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.artist} • ${track.folderName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.durationMs > 0L) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatDuration(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = moreIcon,
                contentDescription = moreDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLibraryState(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
