package com.cycling.workitout.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cycling.workitout.data.database.ActiveWorkoutEntity
import com.cycling.workitout.ui.workout.CompactDataPoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Composable
fun WorkoutRecoveryDialog(
    checkpoint: ActiveWorkoutEntity,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val minutes = remember(checkpoint) {
        try {
            val points = Json.decodeFromString<List<CompactDataPoint>>(checkpoint.dataPointsJson)
            (points.lastOrNull()?.t ?: 0) / 60
        } catch (e: Exception) { 0 }
    }

    AppAlertDialog(
        onDismiss = onDiscard,
        title = "Unfinished Ride",
        confirmText = "Save",
        onConfirm = onSave,
        dismissText = "Discard"
    ) {
        Text("Your last workout didn't finish. Save the $minutes min you recorded?")
    }
}