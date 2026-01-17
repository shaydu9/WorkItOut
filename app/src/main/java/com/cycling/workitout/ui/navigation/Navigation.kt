package com.cycling.workitout.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.ui.connection.ConnectionScreen
import com.cycling.workitout.ui.connection.ConnectionViewModel
import com.cycling.workitout.ui.livedata.LiveDataScreen
import com.cycling.workitout.ui.livedata.LiveDataViewModel

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object LiveData : Screen("live_data")
}

@Composable
fun WorkItOutNavigation(bleManager: BleManager) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Connection.route
    ) {
        composable(Screen.Connection.route) {
            val viewModel = ConnectionViewModel(bleManager)
            ConnectionScreen(
                viewModel = viewModel,
                onNavigateToLiveData = {
                    navController.navigate(Screen.LiveData.route)
                }
            )
        }
        
        composable(Screen.LiveData.route) {
            val viewModel = LiveDataViewModel(bleManager)
            LiveDataScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
