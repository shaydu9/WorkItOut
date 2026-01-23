package com.cycling.workitout.ui.connection

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.R
import com.cycling.workitout.data.BleDevice
import com.cycling.workitout.data.DeviceType
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToLiveData: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isHeartRateConnected by viewModel.isHeartRateConnected.collectAsStateWithLifecycle()
    val isPowerMeterConnected by viewModel.isPowerMeterConnected.collectAsStateWithLifecycle()
    
    // State for dialogs
    var showRenameDialog by remember { mutableStateOf(false) }
    var deviceToRename by remember { mutableStateOf<Pair<String, String>?>(null) } // MAC, current name
    var showProfileDialog by remember { mutableStateOf(false) }
    var deviceToAssign by remember { mutableStateOf<String?>(null) } // MAC address
    
    // Request Bluetooth permissions
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connect_devices)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToProfiles) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profiles")
                    }
                    IconButton(
                        onClick = onNavigateToLiveData,
                        enabled = isHeartRateConnected || isPowerMeterConnected
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Go to Live Data")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Connection status cards
            ConnectionStatusCard(
                title = "Heart Rate Monitor",
                isConnected = isHeartRateConnected,
                onDisconnect = { viewModel.disconnectHeartRate() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ConnectionStatusCard(
                title = "Power Meter",
                isConnected = isPowerMeterConnected,
                onDisconnect = { viewModel.disconnectPowerMeter() }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Scan button
            Button(
                onClick = {
                    if (isScanning) {
                        viewModel.stopScan()
                    } else {
                        if (permissionsState.allPermissionsGranted) {
                            viewModel.startScan()
                        } else {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.isBluetoothEnabled() && permissionsState.allPermissionsGranted
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isScanning) 
                        stringResource(R.string.stop_scan) 
                    else 
                        stringResource(R.string.scan_devices)
                )
            }
            
            if (!viewModel.isBluetoothEnabled()) {
                Text(
                    text = stringResource(R.string.bluetooth_not_enabled),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            if (!permissionsState.allPermissionsGranted) {
                Text(
                    text = stringResource(R.string.permissions_required),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Scanning indicator
            if (isScanning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.scanning))
                }
            }
            
            // Main content in LazyColumn
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Your Devices section
                if (savedDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = "Your Devices",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(savedDevices) { device ->
                        SavedDeviceCard(
                            device = device,
                            onConnect = {
                                viewModel.reconnectDevice(device)
                            },
                            onRename = {
                                deviceToRename = Pair(device.macAddress, device.getDisplayName())
                                showRenameDialog = true
                            },
                            onAssign = {
                                deviceToAssign = device.macAddress
                                showProfileDialog = true
                            },
                            onForget = {
                                viewModel.forgetDevice(device.macAddress)
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                
                // Available Devices section
                item {
                    Text(
                        text = if (savedDevices.isNotEmpty()) "Available Devices" else "Discovered Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                if (discoveredDevices.isEmpty() && !isScanning) {
                    item {
                        Text(
                            text = stringResource(R.string.no_devices_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                }
                
                items(discoveredDevices) { device ->
                    DeviceCard(
                        device = device,
                        onConnect = { viewModel.connectDevice(device) }
                    )
                }
            }
        }
        
        // Rename Dialog
        if (showRenameDialog && deviceToRename != null) {
            RenameDeviceDialog(
                currentName = deviceToRename!!.second,
                onDismiss = {
                    showRenameDialog = false
                    deviceToRename = null
                },
                onConfirm = { newName ->
                    viewModel.renameDevice(deviceToRename!!.first, newName)
                    showRenameDialog = false
                    deviceToRename = null
                }
            )
        }
        
        // Profile Assignment Dialog
        if (showProfileDialog && deviceToAssign != null) {
            ProfileAssignmentDialog(
                profiles = profiles,
                currentProfileId = null, // TODO: Support multiple profiles per device
                onDismiss = {
                    showProfileDialog = false
                    deviceToAssign = null
                },
                onConfirm = { profileId ->
                    if (profileId != null) {
                        viewModel.assignDeviceToProfile(deviceToAssign!!, profileId)
                    }
                    showProfileDialog = false
                    deviceToAssign = null
                },
                onNavigateToProfiles = {
                    showProfileDialog = false
                    deviceToAssign = null
                    onNavigateToProfiles()
                }
            )
        }
    }
}

@Composable
fun ConnectionStatusCard(
    title: String,
    isConnected: Boolean,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isConnected) 
                        stringResource(R.string.connected) 
                    else 
                        stringResource(R.string.disconnected),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isConnected) {
                IconButton(onClick = onDisconnect) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.disconnect)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BleDevice,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (device.deviceType) {
                        DeviceType.HEART_RATE_MONITOR -> Icons.Default.Favorite
                        DeviceType.POWER_METER -> Icons.Default.Speed
                        DeviceType.SMART_TRAINER -> Icons.Default.DirectionsBike
                        DeviceType.UNKNOWN -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = device.deviceType.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedDeviceCard(
    device: com.cycling.workitout.data.database.SavedDeviceEntity,
    onConnect: () -> Unit,
    onRename: () -> Unit,
    onAssign: () -> Unit,
    onForget: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = when (device.deviceType) {
                        DeviceType.HEART_RATE_MONITOR -> Icons.Default.Favorite
                        DeviceType.POWER_METER -> Icons.Default.Speed
                        DeviceType.SMART_TRAINER -> Icons.Default.DirectionsBike
                        DeviceType.UNKNOWN -> Icons.Default.Bluetooth
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = device.getDisplayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    if (device.customName != null) {
                        Text(
                            text = device.manufacturerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = device.deviceType.name.replace("_", " "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Options menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Options"
                    )
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Assign to Profile") },
                        onClick = {
                            showMenu = false
                            onAssign()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.AccountCircle, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Forget Device") },
                        onClick = {
                            showMenu = false
                            onForget()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDeviceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Device") },
        text = {
            Column {
                Text(
                    text = "Enter a new name for this device",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Device Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProfileAssignmentDialog(
    profiles: List<com.cycling.workitout.data.database.EquipmentProfileEntity>,
    currentProfileId: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    onNavigateToProfiles: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign to Profile") },
        text = {
            Column {
                if (profiles.isEmpty()) {
                    Text(
                        text = "No profiles available. Create a profile first.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = onNavigateToProfiles,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Profile")
                    }
                } else {
                    Text(
                        text = "Select a profile for this device",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // None option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConfirm(null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentProfileId == null,
                            onClick = { onConfirm(null) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("None (Unassigned)")
                    }
                    
                    HorizontalDivider()
                    
                    // Profile options
                    profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(profile.profileId) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentProfileId == profile.profileId,
                                onClick = { onConfirm(profile.profileId) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.icon, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (profiles.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        dismissButton = null
    )
}
