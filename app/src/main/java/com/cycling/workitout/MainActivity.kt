package com.cycling.workitout

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.ui.navigation.WorkItOutNavigation
import com.cycling.workitout.ui.theme.WorkItOutTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var bleManager: BleManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize BLE Manager
        bleManager = BleManager(applicationContext)
        
        setContent {
            WorkItOutTheme {
                WorkItOutNavigation(bleManager = bleManager)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        bleManager.cleanup()
    }
}
