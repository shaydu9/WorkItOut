package com.cycling.workitout.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
        private val HAS_COMPLETED_FIRST_RUN_KEY = booleanPreferencesKey("has_completed_first_run")
        private val USER_FTP_WATTS_KEY = intPreferencesKey("user_ftp_watts")
        private val USER_WEIGHT_KG_KEY = intPreferencesKey("user_weight_kg")
        private val USER_MAX_HR_KEY = intPreferencesKey("user_max_hr")
        const val DEFAULT_FTP_WATTS = 200
        const val DEFAULT_WEIGHT_KG = 75
        const val DEFAULT_MAX_HR = 190
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

    val hasCompletedFirstRun: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_COMPLETED_FIRST_RUN_KEY] ?: false
        }

    val userFtpWatts: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[USER_FTP_WATTS_KEY] ?: DEFAULT_FTP_WATTS
        }

    val userWeightKg: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[USER_WEIGHT_KG_KEY] ?: DEFAULT_WEIGHT_KG
        }

    val userMaxHeartRate: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[USER_MAX_HR_KEY] ?: DEFAULT_MAX_HR
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

    suspend fun setHasCompletedFirstRun(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_FIRST_RUN_KEY] = completed
        }
    }

    suspend fun setUserFtpWatts(watts: Int) {
        context.dataStore.edit { preferences ->
            preferences[USER_FTP_WATTS_KEY] = watts.coerceIn(50, 600)
        }
    }

    suspend fun setUserWeightKg(kg: Int) {
        context.dataStore.edit { preferences ->
            preferences[USER_WEIGHT_KG_KEY] = kg.coerceIn(30, 200)
        }
    }

    suspend fun setUserMaxHeartRate(bpm: Int) {
        context.dataStore.edit { preferences ->
            preferences[USER_MAX_HR_KEY] = bpm.coerceIn(120, 230)
        }
    }
}
