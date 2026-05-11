package com.cycling.workitout.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.workitout.data.WorkoutDefinition
import com.cycling.workitout.data.WorkoutIntervalDef
import com.cycling.workitout.data.firestore.SavedWorkout
import com.cycling.workitout.data.scaledByIntensity
import com.cycling.workitout.ui.components.WorkoutPreviewSheet
import com.cycling.workitout.ui.home.toWorkoutDefinition
import kotlin.math.roundToInt

// Renders an interval target as watts or %FTP based on the global display toggle.
internal fun formatTarget(interval: WorkoutIntervalDef, asPercent: Boolean): String =
    if (asPercent) "${(interval.targetPowerPercentFtp * 100).roundToInt()}%"
    else "${interval.targetPowerWatts} W"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit,
    onStartWorkout: (WorkoutDefinition) -> Unit
) {
    val savedWorkouts by viewModel.savedWorkouts.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedWorkout by viewModel.selectedWorkout.collectAsStateWithLifecycle()
    val defaultWorkouts by viewModel.defaultWorkouts.collectAsStateWithLifecycle()
    val displayAsPercent by viewModel.displayAsPercent.collectAsStateWithLifecycle()
    val currentFtp by viewModel.ftp.collectAsStateWithLifecycle()
    val intensityScale by viewModel.intensityScale.collectAsStateWithLifecycle()

    var deleteTarget by remember { mutableStateOf<SavedWorkout?>(null) }

    // Saved workout_ids, so the heart button on starter cards reflects saved state.
    val savedIds = remember(savedWorkouts) { savedWorkouts.map { it.id }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Library") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LibraryScreenContent(
            savedWorkouts = savedWorkouts,
            defaultWorkouts = defaultWorkouts,
            selectedWorkout = selectedWorkout,
            intensityScale = intensityScale,
            displayAsPercent = displayAsPercent,
            currentFtp = currentFtp,
            savedIds = savedIds,
            deleteTarget = deleteTarget,
            onSetDeleteTarget = { deleteTarget = it },
            onSelectDefault = viewModel::selectDefaultWorkout,
            onSaveDefault = viewModel::saveToLibrary,
            onSelectSaved = viewModel::selectSavedWorkout,
            onDeleteSaved = viewModel::deleteWorkout,
            onAdjustIntensity = viewModel::adjustIntensity,
            onToggleDisplay = viewModel::setDisplayAsPercent,
            onDismissPreview = viewModel::dismissPreview,
            onStartWorkout = onStartWorkout,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun LibraryScreenContent(
    savedWorkouts: List<SavedWorkout>,
    defaultWorkouts: List<WorkoutDefinition>,
    selectedWorkout: WorkoutDefinition?,
    intensityScale: Float,
    displayAsPercent: Boolean,
    currentFtp: Int,
    savedIds: Set<String>,
    deleteTarget: SavedWorkout?,
    onSetDeleteTarget: (SavedWorkout?) -> Unit,
    onSelectDefault: (WorkoutDefinition) -> Unit,
    onSaveDefault: (WorkoutDefinition) -> Unit,
    onSelectSaved: (SavedWorkout) -> Unit,
    onDeleteSaved: (SavedWorkout) -> Unit,
    onAdjustIntensity: (Int) -> Unit,
    onToggleDisplay: (Boolean) -> Unit,
    onDismissPreview: () -> Unit,
    onStartWorkout: (WorkoutDefinition) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val columns = if (isTablet) 2 else 1

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        if (defaultWorkouts.isNotEmpty()) {
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                SectionHeader("Starter workouts")
            }

            val byDuration = defaultWorkouts.groupBy { it.totalDurationSeconds / 60 }
                .toSortedMap()

            byDuration.forEach { (minutes, group) ->
                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                    Text(
                        "$minutes min",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(group, key = { it.id }) { workout ->
                    DefaultWorkoutCard(
                        workout = workout,
                        alreadySaved = workout.id in savedIds,
                        onClick = { onSelectDefault(workout) },
                        onSave = { onSaveDefault(workout) }
                    )
                }
            }
        }

        item(span = { GridItemSpan(maxCurrentLineSpan) }) {
            Spacer(Modifier.height(12.dp))
            SectionHeader("Your library")
        }

        if (savedWorkouts.isEmpty()) {
            item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                Text(
                    "Tap the heart on a starter above, or generate a workout from Home and save it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(savedWorkouts, key = { it.id }) { entity ->
                SavedWorkoutCard(
                    entity = entity,
                    currentFtp = currentFtp,
                    onClick = { onSelectSaved(entity) },
                    onDelete = { onSetDeleteTarget(entity) }
                )
            }
        }
    }

    if (selectedWorkout != null) {
        WorkoutPreviewSheet(
            workout = selectedWorkout,
            intensityScale = intensityScale,
            isGenerating = false,
            displayAsPercent = displayAsPercent,
            onAdjustIntensity = onAdjustIntensity,
            onRegenerate = null,
            onSave = null,
            onStart = {
                val scaled = selectedWorkout.scaledByIntensity(intensityScale)
                onDismissPreview()
                onStartWorkout(scaled)
            },
            onDismiss = onDismissPreview,
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { onSetDeleteTarget(null) },
            title = { Text("Delete workout?") },
            text = { Text("\"${deleteTarget.name}\" will be removed from your library.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSaved(deleteTarget)
                    onSetDeleteTarget(null)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onSetDeleteTarget(null) }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun DefaultWorkoutCard(
    workout: WorkoutDefinition,
    alreadySaved: Boolean,
    onClick: () -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onSave,
                    enabled = !alreadySaved,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (alreadySaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (alreadySaved) "Saved" else "Save to library",
                        tint = if (alreadySaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (workout.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    workout.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(8.dp))
            ZoneStrip(workout)
        }
    }
}

@Composable
private fun SavedWorkoutCard(
    entity: SavedWorkout,
    currentFtp: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val totalMin = entity.totalDurationSeconds / 60
    // Parse intervals for the zone summary strip — re-resolve watts against current FTP.
    val workout = remember(entity.id, currentFtp) { entity.toWorkoutDefinition(currentFtp) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entity.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "${totalMin} min · ${workout.intervals.size} intervals",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (entity.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    entity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(8.dp))

            ZoneStrip(workout)
        }
    }
}

@Composable
private fun ZoneStrip(workout: WorkoutDefinition) {
    val total = workout.totalDurationSeconds.toFloat().coerceAtLeast(1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(3.dp)
            )
    ) {
        workout.intervals.forEach { interval ->
            val fraction = interval.durationSeconds / total
            Box(
                modifier = Modifier
                    .weight(fraction)
                    .fillMaxHeight()
                    .background(Color(interval.zone.colorHex))
            )
        }
    }
}


// W | % segmented chip — shared by the library, home, and active workout screens.
@Composable
internal fun WattsPercentToggle(
    asPercent: Boolean,
    onChange: (Boolean) -> Unit
) {
    SingleChoiceSegmentedButtonRow {
        SegmentedButton(
            selected = !asPercent,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("W") }
        SegmentedButton(
            selected = asPercent,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("%") }
    }
}
