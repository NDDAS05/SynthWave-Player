package com.example.data.repository

import com.example.data.local.FavoriteEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackEntity
import com.example.data.local.RecentPlayEntity
import com.example.data.local.SynthWaveDao
import com.example.data.model.AudioTrack
import com.example.data.model.PlaylistSummary
import com.example.data.scanner.MediaStoreAudioScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepository(
    private val dao: SynthWaveDao,
    private val scanner: MediaStoreAudioScanner
) {
    val playlists: Flow<List<PlaylistSummary>> = dao.getPlaylistSummaries().map { list ->
        list.map { tuple ->
            PlaylistSummary(
                id = tuple.id,
                name = tuple.name,
                trackCount = tuple.trackCount,
                createdAt = tuple.createdAt
            )
        }
    }

    val favorites: Flow<List<AudioTrack>> = dao.getFavorites().map { entities ->
        entities.map { it.toAudioTrack() }
    }

    val favoriteIds: Flow<Set<String>> = dao.getFavoriteTrackIds().map { it.toSet() }

    val recentPlays: Flow<List<AudioTrack>> = dao.getRecentPlays().map { entities ->
        entities.map { it.toAudioTrack() }
    }

    fun getPlaylistTracks(playlistId: Long): Flow<List<AudioTrack>> {
        return dao.getTracksForPlaylist(playlistId).map { list ->
            list.map { it.toAudioTrack() }
        }
    }

    suspend fun scanLocalAudioLibrary(): List<AudioTrack> {
        return scanner.scanDeviceAudio()
    }

    suspend fun loadEmbeddedArtwork(track: AudioTrack): ByteArray? {
        return scanner.extractEmbeddedArtwork(track)
    }

    suspend fun createPlaylist(name: String): Long {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return -1L
        return dao.insertPlaylist(PlaylistEntity(name = cleanName))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) {
        if (dao.isTrackInPlaylist(playlistId, track.id) > 0) return
        val nextPosition = dao.getMaxTrackPosition(playlistId) + 1
        dao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackId = track.id,
                position = nextPosition,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                source = track.source.name,
                contentUri = track.contentUri,
                filePath = track.filePath,
                folderName = track.folderName,
                albumId = track.albumId,
                videoId = track.videoId,
                thumbnailUrl = track.thumbnailUrl,
                bpmHint = track.bpmHint
            )
        )
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun toggleFavorite(track: AudioTrack) {
        if (dao.isFavorite(track.id)) {
            dao.deleteFavorite(track.id)
        } else {
            dao.insertFavorite(
                FavoriteEntity(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    source = track.source.name,
                    contentUri = track.contentUri,
                    filePath = track.filePath,
                    folderName = track.folderName,
                    albumId = track.albumId,
                    videoId = track.videoId,
                    thumbnailUrl = track.thumbnailUrl,
                    bpmHint = track.bpmHint
                )
            )
        }
    }

    suspend fun recordPlay(track: AudioTrack) {
        dao.upsertRecentPlay(
            RecentPlayEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                durationMs = track.durationMs,
                source = track.source.name,
                contentUri = track.contentUri,
                filePath = track.filePath,
                folderName = track.folderName,
                albumId = track.albumId,
                videoId = track.videoId,
                thumbnailUrl = track.thumbnailUrl,
                bpmHint = track.bpmHint,
                playedAt = System.currentTimeMillis()
            )
        )
        dao.trimRecentPlays()
    }
}
