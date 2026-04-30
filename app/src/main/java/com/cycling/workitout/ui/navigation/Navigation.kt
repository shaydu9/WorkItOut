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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cycling.workitout.WorkItOutApplication
import com.cycling.workitout.ble.BleManager
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.ui.auth.LoginScreen
import com.cycling.workitout.ui.auth.LoginViewModel
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
import timber.log.Timber

sealed class Screen(val route: String) {
    object FirstRunPairing : Screen("first_run_pairing")
    object Home : Screen("home")
    object ActiveWorkout : Screen("active_workout")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Library : Screen("library")
    object RideDetail : Screen("ride_detail/{rideId}") {
        fun withId(id: String) = "ride_detail/$id"
    }
}

// In-memory handoff to avoid serializing the full WorkoutDefinition through nav args.
object WorkoutSession {
    var pendingWorkout: WorkoutDefinition? = null
}

@Composable
fun WorkItOutNavigation(bleManager: BleManager) {
    val navController = rememberNavController()
    val prefs = WorkItOutApplication.themePreferences

    val authRepository = WorkItOutApplication.authRepository
    val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()

    if (currentUser == null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val loginViewModel = remember { LoginViewModel(authRepository) }

        val googleSignInClient = remember {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken("596435170722-t9firjj1ib56vsdttkqo24sae04re9uu.apps.googleusercontent.com")
                .requestEmail()
                .build()
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
        }

        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            try {
                val account = com.google.android.gms.auth.api.signin.GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(com.google.android.gms.common.api.ApiException::class.java)
                account.idToken?.let { loginViewModel.signInWithGoogle(it) }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Timber.w(e, "Google sign-in failed")
            }
        }

        LoginScreen(
            viewModel = loginViewModel,
            onGoogleSignIn = { launcher.launch(googleSignInClient.signInIntent) }
        )
        return
    }

    var startDestination by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val completed = prefs.hasCompletedFirstRun.first()
        startDestination = if (completed) Screen.Home.route else Screen.FirstRunPairing.route
    }

    val resolvedStart = startDestination
    if (resolvedStart == null) {
        // Spinner while DataStore resolves the first-run flag.
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
                    // launchSingleTop + popUpTo(Home) keeps exactly one ActiveWorkout entry in
                    // the back stack — defends against a stale WorkoutViewModel still driving
                    // the trainer if the user starts a second workout via a different path.
                    navController.navigate(Screen.ActiveWorkout.route) {
                        launchSingleTop = true
                        popUpTo(Screen.Home.route)
                    }
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

        composable(Screen.ActiveWorkout.route) { backStackEntry ->
            val workout = WorkoutSession.pendingWorkout
            // Scope the VM to the NavBackStackEntry: when this entry is popped (Stop → ride
            // saved → popUpTo, or onNavigateBack), Compose-Navigation calls onCleared(),
            // which releases the BLE ERG token and unwires engine callbacks. With the
            // previous `remember` pattern, onCleared never fired and the VM (plus its
            // FE-C resender state) leaked across navigation, causing two workouts to
            // fight over the trainer's resistance.
            val viewModel: WorkoutViewModel = viewModel(
                viewModelStoreOwner = backStackEntry,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        WorkoutViewModel(bleManager, workout) as T
                }
            )
            // Navigate to ride detail once saved; pop workout so Back goes Home, not back into a stopped workout.
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
                    navController.navigate(Screen.ActiveWorkout.route) {
                        launchSingleTop = true
                        popUpTo(Screen.Home.route)
                    }
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
            arguments = listOf(navArgument("rideId") { type = NavType.StringType })
        ) { backStack ->
            val rideId = backStack.arguments?.getString("rideId") ?: return@composable
            val viewModel = remember(rideId) { RideDetailViewModel(rideId) }
            RideDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
