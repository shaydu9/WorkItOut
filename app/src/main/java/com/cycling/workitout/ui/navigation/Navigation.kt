package com.cycling.workitout.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.ui.firstrun.FirstRunPairingScreen
import com.cycling.workitout.ui.firstrun.FirstRunPairingViewModel
import com.cycling.workitout.ui.history.HistoryScreen
import com.cycling.workitout.ui.history.HistoryViewModel
import com.cycling.workitout.ui.history.RideDetailScreen
import com.cycling.workitout.ui.history.RideDetailViewModel
import com.cycling.workitout.ui.home.HomeScreen
import com.cycling.workitout.ui.home.HomeViewModel
import com.cycling.workitout.ui.library.LibraryScreen
import com.cycling.workitout.ui.library.LibraryViewModel
import com.cycling.workitout.ui.settings.SettingsScreen
import com.cycling.workitout.ui.workout.WorkoutScreen
import com.cycling.workitout.ui.workout.WorkoutViewModel
import kotlinx.coroutines.flow.first

sealed class Screen(val route: String) {
    object FirstRunPairing : Screen("first_run_pairing")
    object Home : Screen("home")
    object ActiveWorkout : Screen("active_workout")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Library : Screen("library")
    object RideDetail : Screen("ride_detail/{rideId}") {
        fun withId(id: Long) = "ride_detail/$id"
    }
}

/**
 * In-memory handoff of the generated workout from Home → ActiveWorkout.
 * Avoids serializing the full WorkoutDefinition through nav arguments.
 */
object WorkoutSession {
    var pendingWorkout: WorkoutDefinition? = null
}

@Composable
fun WorkItOutNavigation(bleManager: BleManager) {
    val navController = rememberNavController()
    val prefs = WorkItOutApplication.themePreferences

    // Determine start destination once, based on first-run flag.
    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val completed = prefs.hasCompletedFirstRun.first()
        startDestination = if (completed) Screen.Home.route else Screen.FirstRunPairing.route
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        // Brief splash while we read DataStore
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStart
    ) {
        composable(Screen.FirstRunPairing.route) {
            val viewModel = remember { FirstRunPairingViewModel(bleManager, prefs) }
            FirstRunPairingScreen(
                viewModel = viewModel,
                onPairingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.FirstRunPairing.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val viewModel = remember { HomeViewModel(bleManager, prefs) }
            HomeScreen(
                viewModel = viewModel,
                onStartWorkout = { workout ->
                    WorkoutSession.pendingWorkout = workout
                    navController.navigate(Screen.ActiveWorkout.route)
                },
                onOpenSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onRepairDevices = {
                    navController.navigate(Screen.FirstRunPairing.route)
                },
                onOpenHistory = {
                    navController.navigate(Screen.History.route)
                },
                onOpenLibrary = {
                    navController.navigate(Screen.Library.route)
                }
            )
        }

        composable(Screen.ActiveWorkout.route) {
            val workout = WorkoutSession.pendingWorkout
            val viewModel = remember(workout) {
                WorkoutViewModel(bleManager, workout)
            }
            // Once the ride has been persisted, jump straight to the same detail
            // screen the user would see from History — that's the unified
            // post-workout summary. Pop the live workout off the stack so Back
            // from the detail goes Home, not back into a stopped workout.
            val savedRideId by viewModel.savedRideId.collectAsStateWithLifecycle()
            LaunchedEffect(savedRideId) {
                savedRideId?.let { id ->
                    WorkoutSession.pendingWorkout = null
                    navController.navigate(Screen.RideDetail.withId(id)) {
                        popUpTo(Screen.ActiveWorkout.route) { inclusive = true }
                    }
                }
            }
            WorkoutScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    WorkoutSession.pendingWorkout = null
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel = com.cycling.workitout.ui.settings.SettingsViewModel(bleManager)
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRepairDevices = {
                    navController.navigate(Screen.FirstRunPairing.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Library.route) {
            val viewModel = remember { LibraryViewModel() }
            LibraryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartWorkout = { workout ->
                    WorkoutSession.pendingWorkout = workout
                    navController.navigate(Screen.ActiveWorkout.route)
                }
            )
        }

        composable(Screen.History.route) {
            val viewModel = remember { HistoryViewModel() }
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onRideClick = { rideId ->
                    navController.navigate(Screen.RideDetail.withId(rideId))
                }
            )
        }

        composable(
            route = Screen.RideDetail.route,
            arguments = listOf(navArgument("rideId") { type = NavType.LongType })
        ) { backStack ->
            val rideId = backStack.arguments?.getLong("rideId") ?: return@composable
            val viewModel = remember(rideId) { RideDetailViewModel(rideId) }
            RideDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
