package com.cycling.workitout.data.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

// Facade for Strava OAuth + upload; exposes isConnected, athleteName, and uploadState as StateFlows.
class StravaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val tokens = StravaTokenStore(appContext)
    private val client = StravaClient(tokens)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isConnected = MutableStateFlow(tokens.hasTokens)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _athleteName = MutableStateFlow(tokens.athleteName)
    val athleteName: StateFlow<String?> = _athleteName.asStateFlow()

    sealed class UploadState {
        object Idle : UploadState()
        object Uploading : UploadState()
        data class Success(val activityId: Long) : UploadState()
        data class Failed(val message: String) : UploadState()
    }
    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    // ── OAuth ───────────────────────────────────────────────────────────

    /** Open the Strava authorize page in a Custom Tab. */
    fun beginConnect(context: Context) {
        val intent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        // Custom tabs need FLAG_ACTIVITY_NEW_TASK when launched from non-activity context.
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.launchUrl(context, Uri.parse(client.buildAuthorizeUrl()))
    }

    /**
     * Called by MainActivity when it receives the `workitout://strava-callback`
     * deep link. Exchanges the `code` for tokens and flips [isConnected] on.
     */
    fun handleAuthCallback(uri: Uri) {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            Timber.w("Strava auth cancelled/denied: $error")
            return
        }
        if (code.isNullOrBlank()) {
            Timber.w("Strava callback missing code: $uri")
            return
        }
        scope.launch {
            try {
                client.exchangeCode(code)
                _isConnected.value = true
                _athleteName.value = tokens.athleteName
                Timber.i("Strava connected as ${tokens.athleteName}")
            } catch (t: Throwable) {
                Timber.e(t, "Strava code exchange failed")
            }
        }
    }

    fun disconnect() {
        tokens.clear()
        _isConnected.value = false
        _athleteName.value = null
        _uploadState.value = UploadState.Idle
    }

    // ── Upload ──────────────────────────────────────────────────────────

    /**
     * Upload a .fit file. Idempotent per call — if already uploading, no-op.
     * Updates [uploadState] as it progresses.
     */
    fun uploadFit(file: File, workoutName: String) {
        if (_uploadState.value is UploadState.Uploading) return
        if (!tokens.hasTokens) {
            _uploadState.value = UploadState.Failed("Strava not connected")
            return
        }
        _uploadState.value = UploadState.Uploading
        scope.launch {
            try {
                val activityId = client.uploadFit(file, workoutName)
                _uploadState.value = UploadState.Success(activityId)
            } catch (t: Throwable) {
                Timber.e(t, "Strava upload failed")
                _uploadState.value = UploadState.Failed(t.message ?: "Upload failed")
            }
        }
    }

    /** Reset upload UI state — used when the post-workout screen is dismissed. */
    fun resetUploadState() {
        _uploadState.value = UploadState.Idle
    }

    /**
     * Upload a .fit and return the new Strava activity id. Throws on failure.
     *
     * This deliberately does NOT touch [uploadState] — that StateFlow is reserved
     * for the live post-workout flow, which uses [uploadFit]. The history "sync
     * past ride" feature owns its own per-ride state and just needs the activity
     * id back so it can stamp the database row.
     */
    suspend fun uploadFitForHistory(
        file: File,
        workoutName: String,
        description: String
    ): Long {
        if (!tokens.hasTokens) throw IllegalStateException("Strava not connected")
        return client.uploadFit(file, workoutName, description)
    }
}
