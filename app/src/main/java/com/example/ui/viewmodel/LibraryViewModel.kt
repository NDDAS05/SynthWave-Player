package com.example.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AudioTrack
import com.example.data.model.LibraryTab
import com.example.data.model.PlaylistSummary
import com.example.data.repository.MusicRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class TrackGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val tracks: List<AudioTrack>
)

@Immutable
data class LibraryUiState(
    val hasStoragePermission: Boolean = false,
    val hasRecordAudioPermission: Boolean = false,
    val isScanning: Boolean = false,
    val searchQuery: String = "",
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val showFavoritesOnly: Boolean = false,
    val showRecentsOnly: Boolean = false,
    val allTracks: List<AudioTrack> = emptyList(),
    val filteredTracks: List<AudioTrack> = emptyList(),
    val folderGroups: List<TrackGroup> = emptyList(),
    val artistGroups: List<TrackGroup> = emptyList(),
    val albumGroups: List<TrackGroup> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val favorites: List<AudioTrack> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val recentPlays: List<AudioTrack> = emptyList()
)

class LibraryViewModel(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(LibraryTab.SONGS)
    private val _showFavoritesOnly = MutableStateFlow(false)
    private val _showRecentsOnly = MutableStateFlow(false)
    private val _isScanning = MutableStateFlow(false)
    private val _hasStoragePermission = MutableStateFlow(false)
    private val _hasRecordAudioPermission = MutableStateFlow(false)

    private val filterState = combine(
        _searchQuery,
        _selectedTab,
        _showFavoritesOnly,
        _showRecentsOnly,
        _isScanning
    ) { query, tab, favOnly, recentsOnly, scanning ->
        FilterParams(query, tab, favOnly, recentsOnly, scanning)
    }

    private val permissionState = combine(
        _hasStoragePermission,
        _hasRecordAudioPermission
    ) { storage, audio ->
        storage to audio
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        _allTracks,
        filterState,
        permissionState,
        musicRepository.playlists,
        combine(
            musicRepository.favorites,
            musicRepository.favoriteIds,
            musicRepository.recentPlays
        ) { favs, favIds, recents -> Triple(favs, favIds, recents) }
    ) { tracks, filter, perms, playlists, persisted ->
        val (favorites, favoriteIds, recents) = persisted
        val q = filter.query.trim()
        val baseTracks = when {
            filter.favOnly -> favorites
            filter.recentsOnly -> recents
            else -> tracks
        }
        val filtered = if (q.isEmpty()) {
            baseTracks
        } else {
            baseTracks.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true) ||
                    it.folderName.contains(q, ignoreCase = true)
            }
        }

        val folders = filtered.groupBy { it.folderPath.ifBlank { it.folderName } }
            .map { (path, list) ->
                val displayFolder = list.firstOrNull()?.folderName ?: path
                TrackGroup(
                    key = path,
                    title = displayFolder,
                    subtitle = "${list.size} ${if (list.size == 1) "track" else "tracks"}",
                    tracks = list
                )
            }
            .sortedBy { it.title.lowercase() }

        val artists = filtered.groupBy { it.artist }
            .map { (artist, list) ->
                val albumCount = list.map { it.album }.distinct().size
                TrackGroup(
                    key = artist,
                    title = artist,
                    subtitle = "${list.size} tracks • $albumCount ${if (albumCount == 1) "album" else "albums"}",
                    tracks = list
                )
            }
            .sortedBy { it.title.lowercase() }

        val albums = filtered.groupBy { "${it.album}::${it.artist}" }
            .map { (key, list) ->
                val first = list.first()
                TrackGroup(
                    key = key,
                    title = first.album,
                    subtitle = "${first.artist} • ${list.size} tracks",
                    tracks = list
                )
            }
            .sortedBy { it.title.lowercase() }

        LibraryUiState(
            hasStoragePermission = perms.first,
            hasRecordAudioPermission = perms.second,
            isScanning = filter.scanning,
            searchQuery = filter.query,
            selectedTab = filter.tab,
            showFavoritesOnly = filter.favOnly,
            showRecentsOnly = filter.recentsOnly,
            allTracks = tracks,
            filteredTracks = filtered,
            folderGroups = folders,
            artistGroups = artists,
            albumGroups = albums,
            playlists = playlists,
            favorites = favorites,
            favoriteIds = favoriteIds,
            recentPlays = recents
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    fun updatePermissions(storageGranted: Boolean, recordAudioGranted: Boolean) {
        val wasStorageGranted = _hasStoragePermission.value
        _hasStoragePermission.value = storageGranted
        _hasRecordAudioPermission.value = recordAudioGranted
        if (storageGranted && (!wasStorageGranted || _allTracks.value.isEmpty())) {
            scanLibrary()
        }
    }

    fun scanLibrary() {
        if (!_hasStoragePermission.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val scanned = musicRepository.scanDeviceAudioLibrarySafe()
            _allTracks.value = scanned
            _isScanning.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
        _showFavoritesOnly.value = false
        _showRecentsOnly.value = false
    }

    fun toggleFavoritesFilter() {
        val next = !_showFavoritesOnly.value
        _showFavoritesOnly.value = next
        if (next) {
            _showRecentsOnly.value = false
            _selectedTab.value = LibraryTab.SONGS
        }
    }

    fun toggleRecentsFilter() {
        val next = !_showRecentsOnly.value
        _showRecentsOnly.value = next
        if (next) {
            _showFavoritesOnly.value = false
            _selectedTab.value = LibraryTab.SONGS
        }
    }

    fun getTracksForPlaylist(playlistId: Long): Flow<List<AudioTrack>> {
        return musicRepository.getPlaylistTracks(playlistId)
    }

    fun createPlaylist(name: String, initialTrack: AudioTrack? = null) {
        viewModelScope.launch {
            val id = musicRepository.createPlaylist(name)
            if (id > 0L && initialTrack != null) {
                musicRepository.addTrackToPlaylist(id, initialTrack)
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun toggleFavorite(track: AudioTrack) {
        viewModelScope.launch {
            musicRepository.toggleFavorite(track)
        }
    }

    suspend fun loadEmbeddedArt(track: AudioTrack): ByteArray? {
        return musicRepository.loadEmbeddedArtwork(track)
    }

    private suspend fun MusicRepository.scanDeviceAudioLibrarySafe(): List<AudioTrack> {
        return runCatching { scanLocalAudioLibrary() }.getOrDefault(emptyList())
    }

    private data class FilterParams(
        val query: String,
        val tab: LibraryTab,
        val favOnly: Boolean,
        val recentsOnly: Boolean,
        val scanning: Boolean
    )
}
