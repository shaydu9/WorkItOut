package com.cycling.workitout.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cycling.workitout.ble.BleManager
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.cycling.workitout.ui.connection.ConnectionScreen
import com.cycling.workitout.ui.connection.ConnectionViewModel
import com.cycling.workitout.ui.livedata.LiveDataScreen
import com.cycling.workitout.ui.livedata.LiveDataViewModel
import com.cycling.workitout.ui.profiledetail.ProfileDetailScreen
import com.cycling.workitout.ui.profiledetail.ProfileDetailViewModel
import com.cycling.workitout.ui.profiles.ProfilesScreen
import com.cycling.workitout.ui.profiles.ProfilesViewModel
import com.cycling.workitout.ui.settings.SettingsScreen
import com.cycling.workitout.ui.user.UserProfileScreen
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Connection : Screen("connection")
    object LiveData : Screen("live_data")
    object Profiles : Screen("profiles")
    object ProfileDetail : Screen("profile_detail/{profileId}") {
        fun createRoute(profileId: String) = "profile_detail/$profileId"
    }
    object Settings : Screen("settings")
    object UserProfile : Screen("user_profile")
}

data class DrawerItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkItOutNavigation(bleManager: BleManager) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    val drawerItems = listOf(
        DrawerItem(
            route = Screen.Connection.route,
            title = "Connect Devices",
            icon = Icons.Default.Bluetooth,
            description = "Manage BLE connections"
        ),
        DrawerItem(
            route = Screen.Profiles.route,
            title = "Profiles",
            icon = Icons.Default.DirectionsBike,
            description = "Bike setups & equipment"
        ),
        DrawerItem(
            route = Screen.UserProfile.route,
            title = "User Profile",
            icon = Icons.Default.Person,
            description = "Account & statistics"
        ),
        DrawerItem(
            route = Screen.Settings.route,
            title = "Settings",
            icon = Icons.Default.Settings,
            description = "App preferences"
        )
    )
    
    var currentRoute by remember { mutableStateOf(Screen.Connection.route) }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    drawerItems = drawerItems,
                    currentRoute = currentRoute,
                    onItemClick = { route ->
                        scope.launch {
                            drawerState.close()
                        }
                        currentRoute = route
                        navController.navigate(route) {
                            // Pop up to the start destination to avoid building up a large stack
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            // Don't restore state for Profiles - always start fresh at profiles list
                            restoreState = (route != Screen.Profiles.route)
                        }
                    },
                    onCloseDrawer = {
                        scope.launch {
                            drawerState.close()
                        }
                    }
                )
            }
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Connection.route
        ) {
            composable(Screen.Connection.route) {
                currentRoute = Screen.Connection.route
                val viewModel = ConnectionViewModel(bleManager)
                ConnectionScreen(
                    viewModel = viewModel,
                    onNavigateToLiveData = {
                        navController.navigate(Screen.LiveData.route)
                    },
                    onNavigateToProfiles = {
                        navController.navigate(Screen.Profiles.route)
                    },
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
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
            
            composable(Screen.Profiles.route) {
                currentRoute = Screen.Profiles.route
                val viewModel = ProfilesViewModel(bleManager)
                ProfilesScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    },
                    onNavigateToProfileDetail = { profileId ->
                        navController.navigate(Screen.ProfileDetail.createRoute(profileId))
                    }
                )
            }
            
            composable(
                route = Screen.ProfileDetail.route,
                arguments = listOf(navArgument("profileId") { type = NavType.StringType })
            ) { backStackEntry ->
                val profileId = backStackEntry.arguments?.getString("profileId") ?: return@composable
                val viewModel = ProfileDetailViewModel(bleManager)
                ProfileDetailScreen(
                    profileId = profileId,
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onOpenDrawer = {
                        scope.launch {
                            drawerState.open()
                        }
                    }
                )
            }
            
            composable(Screen.Settings.route) {
                currentRoute = Screen.Settings.route
                val viewModel = com.cycling.workitout.ui.settings.SettingsViewModel(bleManager)
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            
            composable(Screen.UserProfile.route) {
                currentRoute = Screen.UserProfile.route
                UserProfileScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}

@Composable
fun DrawerContent(
    drawerItems: List<DrawerItem>,
    currentRoute: String,
    onItemClick: (String) -> Unit,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "WorkItOut",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Cycling Companion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        
        // Navigation Items
        drawerItems.forEach { item ->
            NavigationDrawerItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null
                    )
                },
                label = {
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (item.description.isNotEmpty()) {
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                selected = currentRoute == item.route,
                onClick = { onItemClick(item.route) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // App Version
        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}
