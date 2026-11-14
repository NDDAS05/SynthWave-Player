package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import com.example.data.model.AudioTrack
import com.example.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreAudioScanner(private val context: Context) {

    private val artCache = object : LruCache<String, ByteArray>(36 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    suspend fun scanDeviceAudio(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        // Filter out short notification sounds, UI clicks, and ringtones (< 15 seconds)
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("15000")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relativePathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val durationMs = cursor.getLong(durationCol)
                    if (durationMs < 15_000L) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    )

                    val rawTitle = cursor.getString(titleCol).orEmpty()
                    val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol).orEmpty() else ""
                    val title = rawTitle.ifBlank {
                        displayName.substringBeforeLast('.').ifBlank { "Untitled Track" }
                    }

                    val rawArtist = cursor.getString(artistCol).orEmpty()
                    val artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist

                    val rawAlbum = cursor.getString(albumCol).orEmpty()
                    val album = if (rawAlbum.isBlank() || rawAlbum == "<unknown>") "Unknown Album" else rawAlbum

                    val albumId = cursor.getLong(albumIdCol)
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val relativePath = if (relativePathCol >= 0) cursor.getString(relativePathCol) else null

                    val folderPath = when {
                        !filePath.isNullOrBlank() -> File(filePath).parent.orEmpty()
                        !relativePath.isNullOrBlank() -> relativePath.trimEnd('/')
                        else -> "Music"
                    }
                    val folderName = folderPath
                        .trimEnd('/')
                        .substringAfterLast('/')
                        .ifBlank { "Internal Audio" }

                    tracks += AudioTrack(
                        id = "local:$mediaId",
                        source = TrackSource.LOCAL,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        contentUri = contentUri.toString(),
                        filePath = filePath,
                        folderName = folderName,
                        folderPath = folderPath,
                        albumId = albumId
                    )
                }
            }
        }
        tracks
    }

    suspend fun extractEmbeddedArtwork(track: AudioTrack): ByteArray? = withContext(Dispatchers.IO) {
        if (track.source != TrackSource.LOCAL || track.contentUri.isBlank()) return@withContext null
        artCache.get(track.id)?.let { return@withContext it }

        val retriever = MediaMetadataRetriever()
        val bytes = try {
            retriever.setDataSource(context, Uri.parse(track.contentUri))
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }

        if (bytes != null) {
            artCache.put(track.id, bytes)
        }
        bytes
    }
}
