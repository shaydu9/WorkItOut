package com.cycling.workitout.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

class ThemePreferences(private val context: Context) {
    
    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val POWER_SMOOTHING_SECONDS_KEY = intPreferencesKey("power_smoothing_seconds")
    }
    
    val themeMode: Flow<ThemeMode> = context.dataStore.data
        .map { preferences ->
            val themeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
            ThemeMode.valueOf(themeName)
        }
    
    val powerSmoothingSeconds: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[POWER_SMOOTHING_SECONDS_KEY] ?: 3 // Default to 3 seconds
        }
    
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
    
    suspend fun setPowerSmoothingSeconds(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[POWER_SMOOTHING_SECONDS_KEY] = seconds
        }
    }
}
