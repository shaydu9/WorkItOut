package com.cycling.workitout.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.auth.AuthRepository
import com.cycling.workitout.data.firestore.UserProfileRepository
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.data.preferences.ThemePreferences
import com.cycling.workitout.data.strava.StravaRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class SettingsViewModel(
    private val bleManager: BleManager? = null,
    private val themePreferences: ThemePreferences = WorkItOutApplication.themePreferences,
    private val stravaRepository: StravaRepository = WorkItOutApplication.stravaRepository,
    private val authRepository: AuthRepository = WorkItOutApplication.authRepository,
    private val userProfileRepository: UserProfileRepository = WorkItOutApplication.userProfileRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            themePreferences.themeMode,
            themePreferences.powerSmoothingSeconds,
            userProfileRepository.profile
        ) { theme, smoothing, profile ->
            SettingsUiState(
                themeMode = theme,
                powerSmoothingSeconds = smoothing,
                ftp = profile.ftpWatts,
                weightKg = profile.weightKg,
                maxHeartRate = profile.maxHr,
                photoUrl = profile.photoUrl
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
            userProfileRepository.setFtp(watts)
        }
    }

    fun setWeightKg(kg: Int) {
        viewModelScope.launch {
            userProfileRepository.setWeightKg(kg)
        }
    }

    fun setMaxHeartRate(bpm: Int) {
        viewModelScope.launch {
            userProfileRepository.setMaxHr(bpm)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            FirebaseFirestore.getInstance()
                .terminate()
                .await()
            FirebaseFirestore.getInstance()
                .clearPersistence()
                .await()
            authRepository.signOut()
        }
    }

    fun resetFirstRun() {
        viewModelScope.launch {
            themePreferences.setHasCompletedFirstRun(false)
        }
    }

    fun uploadProfilePhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                userProfileRepository.uploadProfilePhoto(context, uri)
            } catch (t: Throwable) {
                Timber.e(t, "Photo upload failed")
            }
        }
    }
}