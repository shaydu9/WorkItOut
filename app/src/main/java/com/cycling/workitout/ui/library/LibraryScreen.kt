package com.cycling.workitout.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cycling.workitout.data.database.SavedWorkoutEntity
import com.cycling.workitout.ui.home.toWorkoutDefinition
import kotlin.math.roundToInt

/**
 * Render an interval's target as either watts (`"180 W"`) or a percent of FTP
 * (`"90%"`), driven by the user's global display preference.
 */
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

    var deleteTarget by remember { mutableStateOf<SavedWorkoutEntity?>(null) }

    // Saved workout_ids, so the heart button on starter cards reflects saved state.
    val savedIds = remember(savedWorkouts) { savedWorkouts.map { it.workoutId }.toSet() }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ── Starter workouts ────────────────────────────────────────
            if (defaultWorkouts.isNotEmpty()) {
                item {
                    SectionHeader("Starter workouts")
                }

                // Group by duration — one subheader + 4 cards per group.
                val byDuration = defaultWorkouts.groupBy { it.totalDurationSeconds / 60 }
                    .toSortedMap()

                byDuration.forEach { (minutes, group) ->
                    item {
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
                            onClick = { viewModel.selectDefaultWorkout(workout) },
                            onSave = { viewModel.saveToLibrary(workout) }
                        )
                    }
                }
            }

            // ── Your library ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(12.dp))
                SectionHeader("Your library")
            }

            if (savedWorkouts.isEmpty()) {
                item {
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
                        onClick = { viewModel.selectSavedWorkout(entity) },
                        onDelete = { deleteTarget = entity }
                    )
                }
            }
        }

        // Preview sheet for selected workout
        if (selectedWorkout != null) {
            LibraryPreviewSheet(
                workout = selectedWorkout!!,
                displayAsPercent = displayAsPercent,
                onToggleDisplay = { viewModel.setDisplayAsPercent(it) },
                onStart = {
                    val w = selectedWorkout!!
                    viewModel.dismissPreview()
                    onStartWorkout(w)
                },
                onDismiss = { viewModel.dismissPreview() }
            )
        }

        // Delete confirmation
        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Delete workout?") },
                text = { Text("\"${deleteTarget!!.name}\" will be removed from your library.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteWorkout(deleteTarget!!)
                        deleteTarget = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
                }
            )
        }
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
    entity: SavedWorkoutEntity,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPreviewSheet(
    workout: WorkoutDefinition,
    displayAsPercent: Boolean,
    onToggleDisplay: (Boolean) -> Unit,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    workout.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                WattsPercentToggle(
                    asPercent = displayAsPercent,
                    onChange = onToggleDisplay
                )
            }

            val totalMin = workout.totalDurationSeconds / 60
            Text(
                "${totalMin} min · ${workout.intervals.size} intervals",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (workout.description.isNotBlank()) {
                Text(workout.description, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(workout.intervals) { interval ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(interval.zone.colorHex).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 28.dp)
                                .background(Color(interval.zone.colorHex), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                interval.name,
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                interval.zone.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val mins = interval.durationSeconds / 60
                        val secs = interval.durationSeconds % 60
                        Text(
                            "%d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Text(
                            formatTarget(interval, displayAsPercent),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Close") }
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start")
                }
            }
        }
    }
}

/**
 * W | % segmented chip. Single source of truth for the display toggle — used on
 * the library preview sheet, home preview sheet, and active workout screen.
 */
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
