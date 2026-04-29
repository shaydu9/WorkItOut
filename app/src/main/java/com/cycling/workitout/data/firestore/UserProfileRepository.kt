package com.cycling.workitout.data.firestore

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cycling.workitout.data.preferences.ThemePreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.ByteArrayOutputStream

class UserProfileRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val themePreferences: ThemePreferences,
) {
    private fun uid(): String? = auth.currentUser?.uid

    // Snapshot listener → Flow. Emits UserProfile.defaults when signed out.
    val profile: Flow<UserProfile> = callbackFlow {
        val uid = uid()
        if (uid == null) {
            trySend(UserProfile())
            awaitClose()
            return@callbackFlow
        }
        val reg = firestore.collection("users").document(uid)
            .addSnapshotListener { snap, err ->
                if (err != null) { Timber.e(err, "profile listener error"); return@addSnapshotListener }
                if (snap == null || !snap.exists()) {
                    trySend(UserProfile())
                    // Seed Firestore from local prefs on first sign-in
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        patch(mapOf(
                            "ftpWatts" to themePreferences.userFtpWatts.first(),
                            "weightKg" to themePreferences.userWeightKg.first(),
                            "maxHr" to themePreferences.userMaxHeartRate.first()
                        ))
                    }
                    return@addSnapshotListener
                }
                val d = snap.data ?: emptyMap<String, Any>()
                trySend(UserProfile(
                    displayName = d["displayName"] as? String,
                    ftpWatts = (d["ftpWatts"] as? Long)?.toInt() ?: ThemePreferences.DEFAULT_FTP_WATTS,
                    weightKg = (d["weightKg"] as? Long)?.toInt() ?: ThemePreferences.DEFAULT_WEIGHT_KG,
                    maxHr = (d["maxHr"] as? Long)?.toInt() ?: ThemePreferences.DEFAULT_MAX_HR,
                    units = Units.valueOf((d["units"] as? String) ?: Units.METRIC.name),
                    photoUrl = d["photoUrl"] as? String,
                ))
            }
        awaitClose { reg.remove() }
    }.distinctUntilChanged()

    suspend fun setFtp(watts: Int) = patch(mapOf("ftpWatts" to watts))
    suspend fun setWeightKg(kg: Int) = patch(mapOf("weightKg" to kg))
    suspend fun setMaxHr(bpm: Int) = patch(mapOf("maxHr" to bpm))
    suspend fun setDisplayName(name: String) = patch(mapOf("displayName" to name))
    suspend fun setUnits(units: Units) = patch(mapOf("units" to units.name))

    suspend fun hasExistingProfile(): Boolean {
        val uid = uid() ?: return false
        return firestore
            .collection("users")
            .document(uid)
            .get()
            .await()
            .exists()
    }

    /** Resize photo to 512×512, upload to Storage, write photoUrl back to user doc. */
    suspend fun uploadProfilePhoto(context: Context, uri: Uri): String {
        val uid = uid() ?: error("Not signed in")
        val resized = resizeBitmap(context, uri, 512)
        val bytes = ByteArrayOutputStream().also { resized.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        val ref = storage.reference.child("profile_photos/$uid.jpg")
        ref.putBytes(bytes).await()
        val url = ref.downloadUrl.await().toString()
        patch(mapOf("photoUrl" to url))
        return url
    }

    private suspend fun patch(fields: Map<String, Any>) {
        val uid = uid() ?: return
        try {
            firestore.collection("users").document(uid).set(fields, SetOptions.merge()).await()
        } catch (t: Throwable) {
            Timber.e(t, "profile patch failed")
        }
    }

    private fun resizeBitmap(context: Context, uri: Uri, size: Int): Bitmap {
        val raw = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        return Bitmap.createScaledBitmap(raw, size, size, true)
    }
}
