package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AudioTrack
import com.example.data.model.TrackSource

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId"), Index("trackId")]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val source: String,
    val contentUri: String,
    val filePath: String?,
    val folderName: String,
    val albumId: Long,
    val videoId: String?,
    val thumbnailUrl: String?,
    val bpmHint: Int,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toAudioTrack(): AudioTrack = AudioTrack(
        id = trackId,
        source = if (source == TrackSource.YOUTUBE.name) TrackSource.YOUTUBE else TrackSource.LOCAL,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = contentUri,
        filePath = filePath,
        folderName = folderName,
        albumId = albumId,
        videoId = videoId,
        thumbnailUrl = thumbnailUrl,
        bpmHint = bpmHint
    )
}

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val source: String,
    val contentUri: String,
    val filePath: String?,
    val folderName: String,
    val albumId: Long,
    val videoId: String?,
    val thumbnailUrl: String?,
    val bpmHint: Int,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toAudioTrack(): AudioTrack = AudioTrack(
        id = trackId,
        source = if (source == TrackSource.YOUTUBE.name) TrackSource.YOUTUBE else TrackSource.LOCAL,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = contentUri,
        filePath = filePath,
        folderName = folderName,
        albumId = albumId,
        videoId = videoId,
        thumbnailUrl = thumbnailUrl,
        bpmHint = bpmHint
    )
}

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val source: String,
    val contentUri: String,
    val filePath: String?,
    val folderName: String,
    val albumId: Long,
    val videoId: String?,
    val thumbnailUrl: String?,
    val bpmHint: Int,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toAudioTrack(): AudioTrack = AudioTrack(
        id = trackId,
        source = if (source == TrackSource.YOUTUBE.name) TrackSource.YOUTUBE else TrackSource.LOCAL,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        contentUri = contentUri,
        filePath = filePath,
        folderName = folderName,
        albumId = albumId,
        videoId = videoId,
        thumbnailUrl = thumbnailUrl,
        bpmHint = bpmHint
    )
}

@Entity(tableName = "custom_lyrics")
data class CustomLyricsEntity(
    @PrimaryKey val trackId: String,
    val rawLyrics: String,
    val updatedAt: Long = System.currentTimeMillis()
)

data class PlaylistSummaryTuple(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int
)
