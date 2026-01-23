package com.cycling.workitout.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.DeviceType
import com.cycling.workitout.data.database.EquipmentProfileEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToProfileDetail: (String) -> Unit
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfile by viewModel.activeProfile.collectAsStateWithLifecycle()
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    
    // State for create/edit dialog
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<EquipmentProfileEntity?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profiles") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        profileToEdit = null
                        showProfileDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Profile")
                    }
                }
            )
        },
        floatingActionButton = {
            if (profiles.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        profileToEdit = null
                        showProfileDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New Profile") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (profiles.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Profiles Yet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create a profile for each bike or setup.\nDevices will auto-connect when you select a profile.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                profileToEdit = null
                                showProfileDialog = true
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Profile")
                        }
                    }
                }
            } else {
                // Profiles list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Select a profile to activate it and auto-connect its devices",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // Separate regular profiles from demo profile
                    val regularProfiles = profiles.filter { !viewModel.isDemoProfile(it.profileId) }
                    val demoProfile = profiles.find { viewModel.isDemoProfile(it.profileId) }
                    
                    // Show regular profiles with reorder buttons
                    items(regularProfiles.size) { index ->
                        val profile = regularProfiles[index]
                        val devicesForProfile by viewModel.getDevicesForProfile(profile.profileId)
                            .collectAsStateWithLifecycle()
                        
                        ProfileCard(
                            profile = profile,
                            isActive = activeProfile?.profileId == profile.profileId,
                            deviceCount = devicesForProfile.size,
                            onClick = {
                                // Navigate to profile detail screen
                                onNavigateToProfileDetail(profile.profileId)
                            },
                            onActivate = {
                                if (activeProfile?.profileId == profile.profileId) {
                                    viewModel.deactivateAllProfiles()
                                } else {
                                    viewModel.setActiveProfile(profile.profileId)
                                }
                            },
                            onEdit = {
                                profileToEdit = profile
                                showProfileDialog = true
                            },
                            onDelete = {
                                viewModel.deleteProfile(profile.profileId)
                            },
                            showReorderButtons = regularProfiles.size > 1,
                            canMoveUp = index > 0,
                            canMoveDown = index < regularProfiles.size - 1,
                            onMoveUp = {
                                viewModel.reorderProfiles(index, index - 1)
                            },
                            onMoveDown = {
                                viewModel.reorderProfiles(index, index + 1)
                            },
                            isDemoProfile = false
                        )
                    }
                    
                    // Show demo profile at the bottom (no reorder buttons)
                    demoProfile?.let { demo ->
                        item {
                            val devicesForProfile by viewModel.getDevicesForProfile(demo.profileId)
                                .collectAsStateWithLifecycle()
                            
                            ProfileCard(
                                profile = demo,
                                isActive = activeProfile?.profileId == demo.profileId,
                                deviceCount = devicesForProfile.size,
                                onClick = {
                                    // Navigate to profile detail screen
                                    onNavigateToProfileDetail(demo.profileId)
                                },
                                onActivate = {
                                    if (activeProfile?.profileId == demo.profileId) {
                                        viewModel.deactivateAllProfiles()
                                    } else {
                                        viewModel.setActiveProfile(demo.profileId)
                                    }
                                },
                                onEdit = {
                                    // Demo profile cannot be edited
                                },
                                onDelete = {
                                    // Demo profile cannot be deleted
                                },
                                showReorderButtons = false,
                                canMoveUp = false,
                                canMoveDown = false,
                                onMoveUp = {},
                                onMoveDown = {},
                                isDemoProfile = true
                            )
                        }
                    }
                }
            }
        }
        
        // Create/Edit Profile Dialog
        if (showProfileDialog) {
            CreateProfileDialog(
                existingProfile = profileToEdit,
                onDismiss = {
                    showProfileDialog = false
                    profileToEdit = null
                },
                onConfirm = { name, icon ->
                    if (profileToEdit != null) {
                        viewModel.updateProfile(profileToEdit!!.profileId, name, icon)
                    } else {
                        viewModel.createProfile(name, icon)
                    }
                    showProfileDialog = false
                    profileToEdit = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileCard(
    profile: EquipmentProfileEntity,
    isActive: Boolean,
    deviceCount: Int,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showReorderButtons: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    isDemoProfile: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon/Emoji
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.icon,
                    style = MaterialTheme.typography.headlineMedium,
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize * 1.2
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Profile info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = "$deviceCount device${if (deviceCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Reorder buttons (if enabled)
            if (showReorderButtons) {
                Column {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = canMoveUp
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move up",
                            tint = if (canMoveUp) 
                                MaterialTheme.colorScheme.onSurfaceVariant 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = canMoveDown
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move down",
                            tint = if (canMoveDown) 
                                MaterialTheme.colorScheme.onSurfaceVariant 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            
            // Options menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isActive) "Deactivate" else "Activate") },
                        onClick = {
                            showMenu = false
                            onActivate()
                        },
                        leadingIcon = {
                            Icon(
                                if (isActive) Icons.Default.RadioButtonUnchecked else Icons.Default.CheckCircle,
                                contentDescription = null
                            )
                        }
                    )
                    
                    // Hide Edit and Delete options for demo profile
                    if (!isDemoProfile) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
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
}

@Composable
fun CreateProfileDialog(
    existingProfile: EquipmentProfileEntity?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf(existingProfile?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(existingProfile?.icon ?: "🚴") }
    
    val iconOptions = listOf(
        "🚴", "🚵", "🏠", "🏔️", "⚡", "🔥",
        "💪", "🎯", "⭐", "🏆", "🚀", "⚙️"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingProfile != null) "Edit Profile" else "Create Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g., Specialized Aethos, Indoor Setup") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Select Icon",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Icon grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconOptions.forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (icon == selectedIcon)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, selectedIcon) },
                enabled = name.isNotBlank()
            ) {
                Text(if (existingProfile != null) "Save" else "Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
