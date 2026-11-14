package com.example

import android.app.Application
import com.example.audio.PlaybackCoordinator
import com.example.audio.VisualizerEngine
import com.example.data.local.SynthWaveDatabase
import com.example.data.preferences.SettingsRepository
import com.example.data.repository.MusicRepository
import com.example.data.scanner.LyricsResolver
import com.example.data.scanner.MediaStoreAudioScanner
import com.example.data.youtube.YouTubeApiService
import com.example.data.youtube.YouTubeRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class SynthWaveApplication : Application() {

    val database: SynthWaveDatabase by lazy {
        SynthWaveDatabase.getInstance(this)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(this)
    }

    val audioScanner: MediaStoreAudioScanner by lazy {
        MediaStoreAudioScanner(this)
    }

    val lyricsResolver: LyricsResolver by lazy {
        LyricsResolver(this, database.synthWaveDao())
    }

    val musicRepository: MusicRepository by lazy {
        MusicRepository(database.synthWaveDao(), audioScanner)
    }

    val youtubeRepository: YouTubeRepository by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        YouTubeRepository(retrofit.create(YouTubeApiService::class.java))
    }

    val visualizerEngine: VisualizerEngine by lazy {
        VisualizerEngine()
    }

    val playbackCoordinator: PlaybackCoordinator by lazy {
        PlaybackCoordinator(
            context = this,
            musicRepository = musicRepository,
            settingsRepository = settingsRepository,
            visualizerEngine = visualizerEngine
        )
    }
}
