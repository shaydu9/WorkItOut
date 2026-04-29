package com.cycling.workitout.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.auth.AuthRepository
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.strava.StravaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val bleManager: BleManager? = null,
    private val themePreferences: ThemePreferences = WorkItOutApplication.themePreferences,
    private val stravaRepository: StravaRepository = WorkItOutApplication.stravaRepository,
    private val authRepository: AuthRepository = WorkItOutApplication.authRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            themePreferences.themeMode,
            themePreferences.powerSmoothingSeconds,
            themePreferences.userFtpWatts,
            themePreferences.userWeightKg,
            themePreferences.userMaxHeartRate
        ) { theme, smoothing, ftp, weight, maxHr ->
            SettingsUiState(
                themeMode = theme,
                powerSmoothingSeconds = smoothing,
                ftp = ftp,
                weightKg = weight,
                maxHeartRate = maxHr
            )
        },
        stravaRepository.isConnected,
        stravaRepository.athleteName,
        themePreferences.autoUploadToStravaOnFinish,
        authRepository.currentUser
    ) { partial, connected, athleteName, autoUpload, user ->
        partial.copy(
            stravaConnected = connected,
            stravaAthleteName = athleteName,
            autoUploadToStravaOnFinish = autoUpload,
            currentUser = user
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    //Actions
    fun setAutoUploadToStravaOnFinish(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.setAutoUploadToStravaOnFinish(enabled)
        }
    }

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

    fun signOut() {
        authRepository.signOut()
    }

    fun resetFirstRun() {
        viewModelScope.launch {
            themePreferences.setHasCompletedFirstRun(false)
        }
    }
}