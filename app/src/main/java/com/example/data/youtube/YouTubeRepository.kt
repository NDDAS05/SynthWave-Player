package com.example.data.youtube

import android.text.Html
import com.example.data.model.AudioTrack
import com.example.data.model.TrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YouTubeRepository(
    private val apiService: YouTubeApiService
) {
    val demoStreams: List<AudioTrack> = listOf(
        AudioTrack(
            id = "yt:4xDzrJKXOOY",
            source = TrackSource.YOUTUBE,
            title = "Synthwave Radio — Chill Synth / Retro Beats",
            artist = "Lofi Girl Synthwave",
            album = "Royalty-Free Stream",
            durationMs = 0L,
            videoId = "4xDzrJKXOOY",
            thumbnailUrl = "https://i.ytimg.com/vi/4xDzrJKXOOY/hqdefault.jpg",
            bpmHint = 118,
            isDemoStream = true
        ),
        AudioTrack(
            id = "yt:K4DyBUG242c",
            source = TrackSource.YOUTUBE,
            title = "Cartoon, Jéja - On & On (feat. Daniel Levi)",
            artist = "NCS (NoCopyrightSounds)",
            album = "NCS Electronic Mix",
            durationMs = 208_000L,
            videoId = "K4DyBUG242c",
            thumbnailUrl = "https://i.ytimg.com/vi/K4DyBUG242c/hqdefault.jpg",
            bpmHint = 128,
            isDemoStream = true
        ),
        AudioTrack(
            id = "yt:3nQNiWdeH2Q",
            source = TrackSource.YOUTUBE,
            title = "Janji - Heroes Tonight (feat. Johnning)",
            artist = "NCS (NoCopyrightSounds)",
            album = "NCS Future Bass",
            durationMs = 208_000L,
            videoId = "3nQNiWdeH2Q",
            thumbnailUrl = "https://i.ytimg.com/vi/3nQNiWdeH2Q/hqdefault.jpg",
            bpmHint = 128,
            isDemoStream = true
        ),
        AudioTrack(
            id = "yt:bM7SZ5SBzyY",
            source = TrackSource.YOUTUBE,
            title = "Alan Walker - Fade",
            artist = "NCS (NoCopyrightSounds)",
            album = "NCS Progressive House",
            durationMs = 262_000L,
            videoId = "bM7SZ5SBzyY",
            thumbnailUrl = "https://i.ytimg.com/vi/bM7SZ5SBzyY/hqdefault.jpg",
            bpmHint = 126,
            isDemoStream = true
        ),
        AudioTrack(
            id = "yt:AOeY-nDp7hI",
            source = TrackSource.YOUTUBE,
            title = "DEAF KEV - Invincible",
            artist = "NCS (NoCopyrightSounds)",
            album = "NCS Glitch Hop",
            durationMs = 273_000L,
            videoId = "AOeY-nDp7hI",
            thumbnailUrl = "https://i.ytimg.com/vi/AOeY-nDp7hI/hqdefault.jpg",
            bpmHint = 110,
            isDemoStream = true
        ),
        AudioTrack(
            id = "yt:jfKfPfyJRdk",
            source = TrackSource.YOUTUBE,
            title = "Lofi Hip Hop Radio — Beats to Relax / Study",
            artist = "Lofi Girl",
            album = "Royalty-Free Live Stream",
            durationMs = 0L,
            videoId = "jfKfPfyJRdk",
            thumbnailUrl = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            bpmHint = 92,
            isDemoStream = true
        )
    )

    suspend fun searchTracks(query: String, apiKey: String): Result<List<AudioTrack>> =
        withContext(Dispatchers.IO) {
            val trimmedQuery = query.trim()
            if (trimmedQuery.isEmpty()) {
                return@withContext Result.success(demoStreams)
            }
            if (apiKey.isBlank()) {
                val localDemoFiltered = demoStreams.filter {
                    it.title.contains(trimmedQuery, ignoreCase = true) ||
                        it.artist.contains(trimmedQuery, ignoreCase = true)
                }
                return@withContext Result.success(localDemoFiltered)
            }

            runCatching {
                val response = apiService.searchMusic(
                    query = trimmedQuery,
                    apiKey = apiKey.trim()
                )
                response.items.mapNotNull { item ->
                    val videoId = item.id?.videoId ?: return@mapNotNull null
                    val snippet = item.snippet ?: return@mapNotNull null
                    val decodedTitle = decodeHtml(snippet.title.orEmpty().ifBlank { "YouTube Track" })
                    val channel = decodeHtml(snippet.channelTitle.orEmpty().ifBlank { "YouTube Music" })
                    val thumb = snippet.thumbnails?.high?.url
                        ?: snippet.thumbnails?.medium?.url
                        ?: snippet.thumbnails?.default?.url
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    AudioTrack(
                        id = "yt:$videoId",
                        source = TrackSource.YOUTUBE,
                        title = decodedTitle,
                        artist = channel,
                        album = "YouTube Music",
                        durationMs = 0L,
                        videoId = videoId,
                        thumbnailUrl = thumb,
                        bpmHint = 124,
                        isDemoStream = false
                    )
                }
            }
        }

    private fun decodeHtml(input: String): String {
        return Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
    }
}
