package com.cycling.workitout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.ui.navigation.WorkItOutNavigation
import com.cycling.workitout.ui.theme.WorkItOutTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private lateinit var bleManager: BleManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hide status bar and make fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            // Hide the status bar
            hide(WindowInsetsCompat.Type.statusBars())
            // Set behavior for immersive mode (swipe to temporarily show system bars)
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Initialize BLE Manager
        bleManager = BleManager(applicationContext)
        
        // Load power smoothing preference
        lifecycleScope.launch {
            val powerSmoothingSeconds = WorkItOutApplication.themePreferences.powerSmoothingSeconds.first()
            bleManager.setPowerSmoothingWindow(powerSmoothingSeconds)
        }
        
        setContent {
            val themeMode by WorkItOutApplication.themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            
            WorkItOutTheme(themeMode = themeMode) {
                WorkItOutNavigation(bleManager = bleManager)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }
}
