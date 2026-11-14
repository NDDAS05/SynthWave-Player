package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.SynthWaveApplication

class SynthWaveViewModelFactory(
    private val app: SynthWaveApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(app.musicRepository) as T
            }
            modelClass.isAssignableFrom(YouTubeViewModel::class.java) -> {
                YouTubeViewModel(app.youtubeRepository, app.settingsRepository) as T
            }
            modelClass.isAssignableFrom(NowPlayingViewModel::class.java) -> {
                NowPlayingViewModel(
                    playbackCoordinator = app.playbackCoordinator,
                    visualizerEngine = app.visualizerEngine,
                    lyricsResolver = app.lyricsResolver,
                    musicRepository = app.musicRepository
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
