package com.example.ui.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AudioTrack
import com.example.data.preferences.SettingsRepository
import com.example.data.youtube.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class YouTubeUiState(
    val searchQuery: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showingDemoResults: Boolean = true,
    val tracks: List<AudioTrack> = emptyList(),
    val demoStreams: List<AudioTrack> = emptyList()
)

class YouTubeViewModel(
    private val youTubeRepository: YouTubeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<AudioTrack>>(youTubeRepository.demoStreams)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _showingDemo = MutableStateFlow(true)

    val uiState: StateFlow<YouTubeUiState> = combine(
        _searchQuery,
        settingsRepository.youtubeApiKey,
        _isLoading,
        _errorMessage,
        combine(_showingDemo, _searchResults) { demo, results -> demo to results }
    ) { query, apiKey, loading, error, (showingDemo, results) ->
        YouTubeUiState(
            searchQuery = query,
            apiKey = apiKey,
            isLoading = loading,
            errorMessage = error,
            showingDemoResults = showingDemo,
            tracks = results,
            demoStreams = youTubeRepository.demoStreams
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubeUiState(
            tracks = youTubeRepository.demoStreams,
            demoStreams = youTubeRepository.demoStreams
        )
    )

    fun onQueryChange(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _showingDemo.value = true
            _errorMessage.value = null
            _searchResults.value = youTubeRepository.demoStreams
        }
    }

    fun performSearch() {
        val currentQuery = _searchQuery.value.trim()
        val apiKey = uiState.value.apiKey
        if (currentQuery.isEmpty()) {
            _showingDemo.value = true
            _errorMessage.value = null
            _searchResults.value = youTubeRepository.demoStreams
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = youTubeRepository.searchTracks(currentQuery, apiKey)
            result.onSuccess { list ->
                _showingDemo.value = apiKey.isBlank()
                _searchResults.value = list
                if (apiKey.isBlank() && list.isEmpty()) {
                    _errorMessage.value = "No demo streams matched \"$currentQuery\". Add a YouTube Data API v3 key in Settings to search all of YouTube."
                }
            }.onFailure { throwable ->
                _showingDemo.value = false
                _errorMessage.value = throwable.message ?: "YouTube API search failed. Check your API key and network."
            }
            _isLoading.value = false
        }
    }

    fun saveApiKey(newKey: String) {
        viewModelScope.launch {
            settingsRepository.saveYouTubeApiKey(newKey)
            _errorMessage.value = null
            if (_searchQuery.value.isNotBlank()) {
                performSearch()
            }
        }
    }

    fun resetToDemoStreams() {
        _searchQuery.value = ""
        _errorMessage.value = null
        _showingDemo.value = true
        _searchResults.value = youTubeRepository.demoStreams
    }
}
