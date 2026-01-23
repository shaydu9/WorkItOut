package com.cycling.workitout.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.data.preferences.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val bleManager: BleManager? = null,
    private val themePreferences: ThemePreferences = WorkItOutApplication.themePreferences
) : ViewModel() {
    
    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)
    
    val powerSmoothingSeconds: StateFlow<Int> = themePreferences.powerSmoothingSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    
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
}
