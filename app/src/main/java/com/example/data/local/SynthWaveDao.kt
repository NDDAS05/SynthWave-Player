package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SynthWaveDao {

    // Playlists
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, COUNT(pt.id) AS trackCount
        FROM playlists p
        LEFT JOIN playlist_tracks pt ON p.id = pt.playlistId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun getPlaylistSummaries(): Flow<List<PlaylistSummaryTuple>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    // Playlist Tracks
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxTrackPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun isTrackInPlaylist(playlistId: Long, trackId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT trackId FROM favorites")
    fun getFavoriteTrackIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

    // Recently Played
    @Query("SELECT * FROM recent_plays ORDER BY playedAt DESC LIMIT 40")
    fun getRecentPlays(): Flow<List<RecentPlayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentPlay(recentPlay: RecentPlayEntity)

    @Query(
        """
        DELETE FROM recent_plays
        WHERE trackId NOT IN (
            SELECT trackId FROM recent_plays ORDER BY playedAt DESC LIMIT 40
        )
        """
    )
    suspend fun trimRecentPlays()

    // Custom Lyrics
    @Query("SELECT * FROM custom_lyrics WHERE trackId = :trackId")
    fun observeCustomLyrics(trackId: String): Flow<CustomLyricsEntity?>

    @Query("SELECT * FROM custom_lyrics WHERE trackId = :trackId")
    suspend fun getCustomLyricsOnce(trackId: String): CustomLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomLyrics(entity: CustomLyricsEntity)

    @Query("DELETE FROM custom_lyrics WHERE trackId = :trackId")
    suspend fun deleteCustomLyrics(trackId: String)
}
