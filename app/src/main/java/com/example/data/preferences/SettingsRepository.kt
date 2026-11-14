package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.RepeatMode
import com.example.data.model.VisualizerMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "synthwave_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val YOUTUBE_API_KEY = stringPreferencesKey("youtube_api_key")
        val VISUALIZER_MODE = stringPreferencesKey("visualizer_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
    }

    val youtubeApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.YOUTUBE_API_KEY].orEmpty()
    }

    val visualizerMode: Flow<VisualizerMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.VISUALIZER_MODE]
        VisualizerMode.entries.firstOrNull { it.name == raw } ?: VisualizerMode.TWISTING_RING
    }

    val shuffleEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHUFFLE_ENABLED] ?: false
    }

    val repeatMode: Flow<RepeatMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.REPEAT_MODE]
        RepeatMode.entries.firstOrNull { it.name == raw } ?: RepeatMode.OFF
    }

    suspend fun saveYouTubeApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.YOUTUBE_API_KEY] = apiKey.trim()
        }
    }

    suspend fun saveVisualizerMode(mode: VisualizerMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VISUALIZER_MODE] = mode.name
        }
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHUFFLE_ENABLED] = enabled
        }
    }

    suspend fun saveRepeatMode(mode: RepeatMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REPEAT_MODE] = mode.name
        }
    }
}
