package com.cycling.workitout.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.strava.StravaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val bleManager: BleManager? = null,
    private val themePreferences: ThemePreferences = WorkItOutApplication.themePreferences,
    private val stravaRepository: StravaRepository = WorkItOutApplication.stravaRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val powerSmoothingSeconds: StateFlow<Int> = themePreferences.powerSmoothingSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val ftp: StateFlow<Int> = themePreferences.userFtpWatts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_FTP_WATTS)

    val weightKg: StateFlow<Int> = themePreferences.userWeightKg
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_WEIGHT_KG)

    val maxHeartRate: StateFlow<Int> = themePreferences.userMaxHeartRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemePreferences.DEFAULT_MAX_HR)

    val stravaConnected: StateFlow<Boolean> = stravaRepository.isConnected
    val stravaAthleteName: StateFlow<String?> = stravaRepository.athleteName

    /** Launches Strava OAuth in a Custom Tab. The callback comes back through MainActivity. */
    fun connectStrava(context: Context) {
        stravaRepository.beginConnect(context)
    }

    fun disconnectStrava() {
        stravaRepository.disconnect()
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    fun setPowerSmoothingSeconds(seconds: Int) {
        viewModelScope.launch {
            themePreferences.setPowerSmoothingSeconds(seconds)
            bleManager?.setPowerSmoothingWindow(seconds)
        }
    }

    fun setFtp(watts: Int) {
        viewModelScope.launch {
            themePreferences.setUserFtpWatts(watts)
        }
    }

    fun setWeightKg(kg: Int) {
        viewModelScope.launch {
            themePreferences.setUserWeightKg(kg)
        }
    }

    fun setMaxHeartRate(bpm: Int) {
        viewModelScope.launch {
            themePreferences.setUserMaxHeartRate(bpm)
        }
    }

    /** Clear the first-run flag so Navigation routes back to FirstRunPairing. */
    fun resetFirstRun() {
        viewModelScope.launch {
            themePreferences.setHasCompletedFirstRun(false)
        }
    }
}
