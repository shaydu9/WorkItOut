package com.cycling.workitout.data.firestore

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class RideRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private fun uid(): String? = auth.currentUser?.uid

    fun getRides(): Flow<List<Ride>> = callbackFlow {
        val uid = uid() ?: run { trySend(emptyList()); awaitClose(); return@callbackFlow }
        val reg = firestore.collection("users").document(uid).collection("rides")
            .orderBy("startedAtMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList()); return@addSnapshotListener
                }
                trySend(snap.documents.mapNotNull { it.toRide() })
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveRide(ride: Ride): String {
        val uid = uid() ?: error("Not signed in")
        val data = mapOf(
            "name" to ride.name,
            "startedAtMillis" to ride.startedAtMillis,
            "durationSeconds" to ride.durationSeconds,
            "avgPowerWatts" to ride.avgPowerWatts,
            "maxPowerWatts" to ride.maxPowerWatts,
            "avgHeartRate" to ride.avgHeartRate,
            "maxHeartRate" to ride.maxHeartRate,
            "avgCadence" to ride.avgCadence,
            "normalizedPowerWatts" to ride.normalizedPowerWatts,
            "ftpWatts" to ride.ftpWatts,
            "dataPointsJson" to ride.dataPointsJson,
        )
        val ref = firestore.collection("users")
            .document(uid)
            .collection("rides")
            .add(data)
            .await()

        return ref.id
    }

    suspend fun getRideById(id: String): Ride? {
        val uid = uid() ?: return null
        val snap =
            firestore.collection("users")
                .document(uid)
                .collection("rides")
                .document(id)
                .get()
                .await()
        return snap.toRide()
    }

    suspend fun markStravaUploaded(rideId: String, activityId: Long, uploadedAtMillis: Long) {
        val uid = uid() ?: return
        firestore.collection("users")
            .document(uid)
            .collection("rides")
            .document(rideId)
            .set(mapOf(
                "stravaActivityId" to activityId,
                "stravaUploadedAtMillis" to uploadedAtMillis),
                SetOptions.merge())
            .await()
    }

    suspend fun deleteRide(id: String) {
        val uid = uid() ?: return

        firestore.collection("users")
            .document(uid)
            .collection("rides")
            .document(id)
            .delete()
            .await()
    }

    private fun DocumentSnapshot.toRide(): Ride? {
        val d = data ?: return null
        return Ride(
            id = id,
            name = d["name"] as? String ?: return null,
            startedAtMillis = (d["startedAtMillis"] as? Long) ?: return null,
            durationSeconds = (d["durationSeconds"] as? Long)?.toInt() ?: 0,
            avgPowerWatts = (d["avgPowerWatts"] as? Long)?.toInt() ?: 0,
            maxPowerWatts = (d["maxPowerWatts"] as? Long)?.toInt() ?: 0,
            avgHeartRate = (d["avgHeartRate"] as? Long)?.toInt() ?: 0,
            maxHeartRate = (d["maxHeartRate"] as? Long)?.toInt() ?: 0,
            avgCadence = (d["avgCadence"] as? Long)?.toInt() ?: 0,
            normalizedPowerWatts = (d["normalizedPowerWatts"] as? Long)?.toInt() ?: 0,
            ftpWatts = (d["ftpWatts"] as? Long)?.toInt() ?: 0,
            dataPointsJson = d["dataPointsJson"] as? String ?: "",
            stravaActivityId = d["stravaActivityId"] as? Long,
            stravaUploadedAtMillis = d["stravaUploadedAtMillis"] as? Long,
        )
    }
}