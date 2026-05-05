package com.cycling.workitout.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.cycling.workitout.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.preferences.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = SettingsViewModel(),
    onNavigateBack: () -> Unit,
    onRepairDevices: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadProfilePhoto(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        SettingsScreenContent(
            state = state,
            onSetFtp = viewModel::setFtp,
            onSetWeight = viewModel::setWeightKg,
            onSetMaxHr = viewModel::setMaxHeartRate,
            onSetPowerSmoothing = viewModel::setPowerSmoothingSeconds,
            onSetThemeMode = viewModel::setThemeMode,
            onConnectStrava = { viewModel.connectStrava(context) },
            onDisconnectStrava = viewModel::disconnectStrava,
            onDismissStravaError = viewModel::dismissStravaConnectError,
            onSetAutoUploadToStrava = viewModel::setAutoUploadToStravaOnFinish,
            onSignOut = viewModel::signOut,
            onUploadPhoto = viewModel::uploadProfilePhoto,
            onRepairDevices = {
                viewModel.resetFirstRun()
                onRepairDevices()
            },
            onDeleteAccount = viewModel::deleteAccount,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
@Suppress("AssignedValueIsNeverRead")
private fun SettingsScreenContent(
    state: SettingsUiState,
    onSetFtp: (Int) -> Unit,
    onSetWeight: (Int) -> Unit,
    onSetMaxHr: (Int) -> Unit,
    onSetPowerSmoothing: (Int) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onConnectStrava: () -> Unit,
    onDisconnectStrava: () -> Unit,
    onDismissStravaError: () -> Unit,
    onSetAutoUploadToStrava: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onUploadPhoto: (android.content.Context, android.net.Uri) -> Unit,
    onRepairDevices: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onUploadPhoto(context, it) } }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showPowerSmoothingDialog by remember { mutableStateOf(false) }
    var showFtpDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }
    var showMaxHrDialog by remember { mutableStateOf(false) }
    var showDisconnectStravaDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    val isTablet = LocalConfiguration.current.screenWidthDp >= 600

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
    LazyColumn(
        modifier = (if (isTablet) Modifier.widthIn(max = 640.dp) else Modifier)
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionHeader("Training") }
        item {
            SettingsItem(
                icon = Icons.Default.FitnessCenter,
                title = "FTP",
                subtitle = "${state.ftp}W",
                onClick = { showFtpDialog = true }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.MonitorWeight,
                title = "Weight",
                subtitle = "${state.weightKg} kg",
                onClick = { showWeightDialog = true }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.Favorite,
                title = "Max Heart Rate",
                subtitle = "${state.maxHeartRate} bpm",
                onClick = { showMaxHrDialog = true }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.ShowChart,
                title = "Power Averaging",
                subtitle = "${state.powerSmoothingSeconds}s average",
                onClick = { showPowerSmoothingDialog = true }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Devices")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Bluetooth,
                title = "Re-pair devices",
                subtitle = "Run the pairing flow again",
                onClick = onRepairDevices
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Integrations")
        }
        item {
            if (state.stravaConnected) {
                SettingsItem(
                    icon = Icons.Default.CloudUpload,
                    title = "Strava",
                    subtitle = "Connected${state.stravaAthleteName?.let { " as $it" } ?: ""}",
                    iconTint = Color(0xFFFC4C02),
                    onClick = { showDisconnectStravaDialog = true }
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Strava",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFC4C02)
                        )
                        listOf(
                            "Auto-upload completed rides",
                            "Sync your heart rate, power, and cadence data",
                            "View your activity on Strava after every workout"
                        ).forEach { bullet ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                                Text(bullet, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        state.stravaConnectError?.let { msg ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    TextButton(
                                        onClick = onDismissStravaError,
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Dismiss") }
                                }
                            }
                        }
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.btn_strava_connect_with_orange),
                                contentDescription = "Connect with Strava",
                                modifier = Modifier
                                    .height(44.dp)
                                    .clickable(onClick = onConnectStrava)
                            )
                        }
                    }
                }
            }
        }
        if (state.stravaConnected) {
            item {
                SettingsToggleItem(
                    icon = Icons.Default.CloudSync,
                    title = "Auto-upload to Strava",
                    subtitle = "Send rides to Strava as soon as you finish",
                    iconTint = Color(0xFFFC4C02),
                    checked = state.autoUploadToStravaOnFinish,
                    onCheckedChange = onSetAutoUploadToStrava
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Display")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Brightness4,
                title = "Theme",
                subtitle = when (state.themeMode) {
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                    ThemeMode.SYSTEM -> "System default"
                },
                onClick = { showThemeDialog = true }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("Account")
        }
        item {
            SettingsItem(
                icon = Icons.Default.AccountCircle,
                title = "Profile photo",
                subtitle = "Tap to change",
                photoUrl = state.photoUrl,
                onClick = { photoPicker.launch("image/*") }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.AccountCircle,
                title = "Signed in as",
                subtitle = when {
                    state.currentUser?.isAnonymous == true -> "Anonymous"
                    state.currentUser?.email != null -> state.currentUser.email
                    else -> "—"
                },
                onClick = { }
            )
        }
        item {

        }
        item {
            SettingsItem(
                icon = Icons.Default.Logout,
                title = "Sign out",
                subtitle = if (state.currentUser?.isAnonymous == true) {
                    "Anonymous data won't transfer to a new account"
                } else {
                    "Sign out of WorkItOut"
                },
                iconTint = MaterialTheme.colorScheme.error,
                onClick = { showSignOutDialog = true }
            )
        }

        item {
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "Delete account",
                subtitle = "Permanently delete your account and all data",
                iconTint = MaterialTheme.colorScheme.error,
                onClick = {
                    showDeleteAccountDialog = true
                }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            SectionHeader("About")
        }
        item {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "WorkItOut",
                subtitle = "Version 1.0.0",
                onClick = { }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Default.PrivacyTip,
                title = "Privacy Policy",
                subtitle = "shaydu9.github.io/WorkItOut/privacy.html",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://shaydu9.github.io/WorkItOut/privacy.html".toUri())
                    ContextCompat.startActivity(context, intent, null)
                }
            )
        }
    }
    } // end Box

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = state.themeMode,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { mode ->
                onSetThemeMode(mode)
                showThemeDialog = false
            }
        )
    }

    if (showPowerSmoothingDialog) {
        PowerSmoothingDialog(
            currentSeconds = state.powerSmoothingSeconds,
            onDismiss = { showPowerSmoothingDialog = false },
            onSecondsSelected = { seconds ->
                onSetPowerSmoothing(seconds)
                showPowerSmoothingDialog = false
            }
        )
    }

    if (showFtpDialog) {
        FtpDialog(
            currentFtp = state.ftp,
            onDismiss = { showFtpDialog = false },
            onFtpSelected = { watts ->
                onSetFtp(watts)
                showFtpDialog = false
            }
        )
    }

    if (showWeightDialog) {
        NumericValueDialog(
            title = "Body weight",
            description = "Used with your power output to compute virtual speed and distance for Strava.",
            currentValue = state.weightKg,
            unit = "kg",
            range = 30..200,
            onDismiss = { showWeightDialog = false },
            onValueSelected = { kg ->
                onSetWeight(kg)
                showWeightDialog = false
            }
        )
    }

    if (showMaxHrDialog) {
        NumericValueDialog(
            title = "Max Heart Rate",
            description = "Your maximum heart rate. Used to compute %HRmax training zones.",
            currentValue = state.maxHeartRate,
            unit = "bpm",
            range = 120..230,
            onDismiss = { showMaxHrDialog = false },
            onValueSelected = { bpm ->
                onSetMaxHr(bpm)
                showMaxHrDialog = false
            }
        )
    }

    if (showDisconnectStravaDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectStravaDialog = false },
            title = { Text("Disconnect Strava?") },
            text = {
                Text("You'll need to sign in again to upload future workouts. Previously uploaded rides stay on Strava.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDisconnectStrava()
                        showDisconnectStravaDialog = false
                    }
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectStravaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    if (state.currentUser?.isAnonymous == true) {
                        "Your anonymous account will be lost. You'll need to sign in or create an account next time."
                    } else {
                        "You'll need to sign in again to use WorkItOut."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSignOut()
                        showSignOutDialog = false
                    }
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete account?") },
            text = {
                Text("This permanently deletes your account, rides, workouts, and profile. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAccount()
                        showDeleteAccountDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color? = null,
    photoUrl: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == mode,
                            onClick = { onThemeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.SYSTEM -> "System default"
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun PowerSmoothingDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSecondsSelected: (Int) -> Unit
) {
    val intervals = listOf(1, 3, 5, 10, 30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Power Averaging") },
        text = {
            Column {
                intervals.forEach { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSeconds == seconds,
                            onClick = { onSecondsSelected(seconds) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${seconds}s average", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun NumericValueDialog(
    title: String,
    description: String,
    currentValue: Int,
    unit: String,
    range: IntRange,
    onDismiss: () -> Unit,
    onValueSelected: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentValue.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("$title ($unit)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (!valid) Text("Enter a value between ${range.first} and ${range.last}")
                    },
                    isError = !valid
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onValueSelected) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun FtpDialog(
    currentFtp: Int,
    onDismiss: () -> Unit,
    onFtpSelected: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentFtp.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 50..600

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Functional Threshold Power") },
        text = {
            Column {
                Text(
                    "Your sustainable 1-hour power output. Used to scale AI-generated workouts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(3) },
                    label = { Text("FTP (watts)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        if (!valid) Text("Enter a value between 50 and 600")
                    },
                    isError = !valid
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onFtpSelected) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
