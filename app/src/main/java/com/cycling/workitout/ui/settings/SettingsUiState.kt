package com.cycling.workitout.ui.settings

import com.cycling.workitout.data.auth.AuthUser
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.data.preferences.ThemePreferences

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val powerSmoothingSeconds: Int = 3,
    val ftp: Int = ThemePreferences.DEFAULT_FTP_WATTS,
    val weightKg: Int = ThemePreferences.DEFAULT_WEIGHT_KG,
    val maxHeartRate: Int = ThemePreferences.DEFAULT_MAX_HR,
    val stravaConnected: Boolean = false,
    val stravaAthleteName: String? = null,
    val stravaConnectError: String? = null,
    val autoUploadToStravaOnFinish: Boolean = false,
    val currentUser: AuthUser? = null,
    val photoUrl: String? = null
)
