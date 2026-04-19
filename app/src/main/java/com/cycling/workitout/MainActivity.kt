package com.cycling.workitout

import android.content.Intent
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
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.preferences.ThemeMode
import com.cycling.workitout.data.strava.StravaClient
import com.cycling.workitout.ui.navigation.WorkItOutNavigation
import com.cycling.workitout.ui.theme.WorkItOutTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // If the Activity was launched cold via the Strava OAuth redirect, the
        // callback intent is already sitting on us. onNewIntent handles the
        // warm-start case (singleTask re-entry after Custom Tabs completes).
        handleStravaCallbackIfPresent(intent)
        
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

        // Auto-reconnect previously paired devices
        lifecycleScope.launch {
            val saved = WorkItOutApplication.deviceRepository.getAllDevices().first()
            saved.forEach { device ->
                Timber.d("Auto-reconnecting ${device.deviceType} ${device.macAddress}")
                when (device.deviceType) {
                    DeviceType.HEART_RATE_MONITOR -> bleManager.reconnectHeartRateMonitor(device.macAddress)
                    DeviceType.SMART_TRAINER -> bleManager.reconnectTrainer(device.macAddress)
                    DeviceType.POWER_METER -> bleManager.reconnectPowerMeter(device.macAddress)
                    else -> Timber.d("No reconnect path for ${device.deviceType}")
                }
            }
        }
        
        setContent {
            val themeMode by WorkItOutApplication.themePreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            
            WorkItOutTheme(themeMode = themeMode) {
                WorkItOutNavigation(bleManager = bleManager)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStravaCallbackIfPresent(intent)
    }

    /**
     * If [intent] is the `workitout://workitout/strava-callback?code=…` deep link
     * from the Custom Tab, hand it to the Strava repository to exchange for tokens.
     * Host is `workitout` to match Strava's Authorization Callback Domain; the
     * `/strava-callback` path distinguishes this from any future deep links.
     */
    private fun handleStravaCallbackIfPresent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "workitout" &&
            data.host == "workitout" &&
            data.path?.startsWith("/strava-callback") == true
        ) {
            Timber.i("Strava OAuth callback received")
            WorkItOutApplication.stravaRepository.handleAuthCallback(data)
        } else if (intent.action == Intent.ACTION_VIEW) {
            // Sanity log — we got a VIEW action but the URI isn't ours. Rare.
            Timber.d("Ignoring non-Strava VIEW intent: $data (expected ${StravaClient.REDIRECT_URI})")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }
}
