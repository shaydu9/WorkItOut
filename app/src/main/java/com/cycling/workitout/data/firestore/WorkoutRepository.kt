package com.cycling.workitout.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class WorkoutRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private fun uid(): String? = auth.currentUser?.uid

    fun getSavedWorkouts(): Flow<List<SavedWorkout>> = callbackFlow {
        val uid = uid() ?: run { trySend(emptyList()); awaitClose(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid).collection("savedWorkouts")
            .orderBy("savedAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snap.documents.mapNotNull { it.toSavedWorkout() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveWorkout(workout: SavedWorkout) {
        val uid = uid() ?: return
        val data = mapOf(
            "name" to workout.name,
            "description" to workout.description,
            "totalDurationSeconds" to workout.totalDurationSeconds,
            "savedAtMillis" to workout.savedAtMillis,
            "intervalsJson" to workout.intervalsJson,
        )
        firestore.collection("users").document(uid).collection("savedWorkouts")
            .document(workout.id)
            .set(data)
            .await()
    }

    suspend fun existsByWorkoutId(id: String): Boolean {
        val uid = uid() ?: return false
        val snap = firestore
            .collection("users")
            .document(uid)
            .collection("savedWorkouts")
            .document(id)
            .get()
            .await()
        return snap.exists()
    }

    suspend fun deleteWorkout(id: String) {
        val uid = uid() ?: return
        firestore
            .collection("users")
            .document(uid)
            .collection("savedWorkouts")
            .document(id)
            .delete()
            .await()
    }

    private fun DocumentSnapshot.toSavedWorkout(): SavedWorkout? {
        val d = data ?: return null
        return SavedWorkout(
            id = id,
            name = d["name"] as? String ?: return null,
            description = d["description"] as? String ?: "",
            totalDurationSeconds = (d["totalDurationSeconds"] as? Long)?.toInt() ?: 0,
            savedAtMillis = (d["savedAtMillis"] as? Long) ?: return null,
            intervalsJson = d["intervalsJson"] as? String ?: "",
        )
    }
}