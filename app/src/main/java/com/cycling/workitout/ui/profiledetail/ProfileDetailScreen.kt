package com.cycling.workitout.ui.profiledetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.ui.components.*
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileDetailScreen(
    profileId: String,
    viewModel: ProfileDetailViewModel,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val allDevices by viewModel.allDevices.collectAsStateWithLifecycle()
    val heartRateData by viewModel.heartRateData.collectAsStateWithLifecycle()
    val powerData by viewModel.powerData.collectAsStateWithLifecycle()
    val isHeartRateConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val isPowerMeterConnected by viewModel.isPowerMeterConnected.collectAsStateWithLifecycle()
    val isDemoMode by viewModel.isDemoMode.collectAsStateWithLifecycle()
    val isDemoProfile by viewModel.isDemoProfile.collectAsStateWithLifecycle()
    
    var showManageDevicesDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    
    // Load profile when profileId changes
    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }
    
    // Cleanup when leaving screen or profileId changes
    DisposableEffect(profileId) {
        onDispose {
            // ViewModel will handle cleanup in onCleared(), but this ensures
            // we clean up when switching between profiles
            Timber.d("Disposing profile: $profileId")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile?.icon ?: "🚴",
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = profile?.name ?: "Profile",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    if (!isDemoProfile) {
                        IconButton(onClick = { showManageDevicesDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Manage Devices")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Top Section - Live Data Metrics
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Demo Mode Banner
                if (isDemoMode) {
                    DemoModeBanner(
                        phase = viewModel.getDemoPhaseDescription(),
                        elapsedTime = viewModel.getDemoElapsedTime()
                    )
                }
                
                // Live Data Section
                Text(
                    text = "Live Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Heart Rate Card
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Favorite,
                        label = "Heart Rate",
                        value = if (isHeartRateConnected) "${heartRateData.heartRate}" else "--",
                        unit = "bpm",
                        isConnected = isHeartRateConnected
                    )
                    
                    // Power Card
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        label = "Power",
                        value = if (isPowerMeterConnected) "${powerData.power}" else "--",
                        unit = "W",
                        isConnected = isPowerMeterConnected
                    )
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Cadence Card
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Loop,
                        label = "Cadence",
                        value = if (isPowerMeterConnected) "${powerData.cadence}" else "--",
                        unit = "rpm",
                        isConnected = isPowerMeterConnected
                    )
                    
                    // Placeholder for future metric
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        label = "Duration",
                        value = "--",
                        unit = "min",
                        isConnected = false
                    )
                }
            }
            
            // Bottom Section - Tabs with Map and Graphs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Tab Row
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        text = { Text("Map") },
                        icon = { Icon(Icons.Default.Map, contentDescription = "Map") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(1)
                            }
                        },
                        text = { Text("Graphs") },
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = "Graphs") }
                    )
                }
                
                // Horizontal Pager for swipeable content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    when (page) {
                        0 -> MapTab()
                        1 -> GraphsTab(
                            isDemoMode = isDemoMode,
                            currentPower = powerData.power,
                            elapsedSeconds = viewModel.getDemoElapsedSeconds()
                        )
                    }
                }
            }
        }
        
        // Manage Devices Dialog
        if (showManageDevicesDialog) {
            ManageDevicesDialog(
                profileDevices = devices,
                allDevices = allDevices,
                onDismiss = { showManageDevicesDialog = false },
                onAddDevice = { deviceMac ->
                    viewModel.assignDeviceToProfile(deviceMac)
                },
                onRemoveDevice = { deviceMac ->
                    viewModel.removeDeviceFromProfile(deviceMac)
                }
            )
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String,
    isConnected: Boolean
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isConnected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = if (isConnected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = if (isConnected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isConnected)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DemoModeBanner(
    phase: String,
    elapsedTime: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Demo Mode",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "🎮 Demo Mode Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Simulated workout data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = phase,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = elapsedTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDevicesDialog(
    profileDevices: List<com.cycling.workitout.data.database.SavedDeviceEntity>,
    allDevices: List<com.cycling.workitout.data.database.SavedDeviceEntity>,
    onDismiss: () -> Unit,
    onAddDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        title = { 
            Text(
                "Manage Devices",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // Available Devices to Add
                val unassignedDevices = allDevices.filter { device ->
                    profileDevices.none { it.macAddress == device.macAddress }
                }
                
                if (unassignedDevices.isNotEmpty()) {
                    Text(
                        text = "Available Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unassignedDevices) { device ->
                            DeviceManagementCard(
                                device = device,
                                isAssigned = false,
                                onToggle = { onAddDevice(device.macAddress) }
                            )
                        }
                    }
                    
                    if (profileDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Currently Assigned Devices
                if (profileDevices.isNotEmpty()) {
                    Text(
                        text = "Assigned Devices (${profileDevices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(profileDevices) { device ->
                            DeviceManagementCard(
                                device = device,
                                isAssigned = true,
                                onToggle = { onRemoveDevice(device.macAddress) }
                            )
                        }
                    }
                }
                
                // Empty state
                if (unassignedDevices.isEmpty() && profileDevices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Saved Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Go to Connect Devices to pair your sensors",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun DeviceManagementCard(
    device: com.cycling.workitout.data.database.SavedDeviceEntity,
    isAssigned: Boolean,
    onToggle: () -> Unit
) {
    val (deviceIcon, deviceColor) = when (device.deviceType) {
        DeviceType.HEART_RATE_MONITOR -> Pair(Icons.Default.Favorite, androidx.compose.ui.graphics.Color(0xFFE91E63)) // Pink/Red
        DeviceType.POWER_METER -> Pair(Icons.Default.Bolt, androidx.compose.ui.graphics.Color(0xFFFFC107)) // Amber/Yellow
        DeviceType.SMART_TRAINER -> Pair(Icons.AutoMirrored.Filled.DirectionsBike, androidx.compose.ui.graphics.Color(0xFF2196F3)) // Blue
        DeviceType.UNKNOWN -> Pair(Icons.Default.Bluetooth, androidx.compose.ui.graphics.Color(0xFF9E9E9E)) // Gray
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAssigned)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Colored icon background
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = deviceColor.copy(alpha = 0.2f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = null,
                            tint = deviceColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = device.getDisplayName(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (isAssigned)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when (device.deviceType) {
                            DeviceType.HEART_RATE_MONITOR -> "❤️ Heart Rate Monitor"
                            DeviceType.POWER_METER -> "⚡ Power Meter"
                            DeviceType.SMART_TRAINER -> "🚴 Smart Trainer"
                            DeviceType.UNKNOWN -> "📡 Unknown Device"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAssigned)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isAssigned) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                    contentDescription = if (isAssigned) "Remove" else "Add",
                    tint = if (isAssigned)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MapTab() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Map,
                contentDescription = "Map",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Map View",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Route tracking coming soon",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GraphsTab(
    isDemoMode: Boolean,
    currentPower: Int,
    elapsedSeconds: Int
) {
    if (isDemoMode) {
        // Generate workout data
        val workoutIntervals = remember { generateDemoWorkout() }
        
        // Generate power data points (simulate recording)
        val powerDataPoints = remember(elapsedSeconds, currentPower) {
            generatePowerDataPoints(elapsedSeconds, currentPower, workoutIntervals)
        }
        
        // Get current target power based on elapsed time
        val currentTarget = remember(elapsedSeconds) {
            getCurrentTargetPower(elapsedSeconds, workoutIntervals)
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Workout Structure Graph
            WorkoutStructureGraph(
                intervals = workoutIntervals,
                currentTimeSeconds = elapsedSeconds,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Real-time Power Graph
            RealTimePowerGraph(
                powerDataPoints = powerDataPoints,
                targetPower = currentTarget,
                currentTimeSeconds = elapsedSeconds,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Workout info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "TrainerRoad-Style Workout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mixed intervals with varying intensity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    } else {
        // Show placeholder for non-demo profiles
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShowChart,
                    contentDescription = "Graphs",
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Workout Graphs",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Available in Demo Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Generate historical power data points
 */
private fun generatePowerDataPoints(
    elapsedSeconds: Int,
    currentPower: Int,
    workoutIntervals: List<WorkoutInterval>
): List<PowerDataPoint> {
    val points = mutableListOf<PowerDataPoint>()
    
    // Generate a point every second up to current time
    for (time in 0..elapsedSeconds) {
        val targetPower = getCurrentTargetPower(time, workoutIntervals)
        
        // Add some realistic variance around target
        val variance = (kotlin.random.Random.nextInt(-30, 31))
        val power = (targetPower + variance).coerceIn(0, 450)
        
        points.add(PowerDataPoint(time, power))
    }
    
    // Override last point with actual current power
    if (points.isNotEmpty() && elapsedSeconds > 0) {
        points[points.lastIndex] = PowerDataPoint(elapsedSeconds, currentPower)
    }
    
    return points
}

/**
 * Get current target power based on workout intervals
 */
private fun getCurrentTargetPower(
    elapsedSeconds: Int,
    workoutIntervals: List<WorkoutInterval>
): Int {
    var cumulativeTime = 0
    for (interval in workoutIntervals) {
        cumulativeTime += interval.durationSeconds
        if (elapsedSeconds < cumulativeTime) {
            return interval.targetPower
        }
    }
    return workoutIntervals.lastOrNull()?.targetPower ?: 150
}
