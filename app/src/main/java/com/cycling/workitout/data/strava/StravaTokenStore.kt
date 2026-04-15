package com.cycling.workitout.data.strava

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

/**
 * Strava OAuth tokens live here. Backed by [EncryptedSharedPreferences] so the
 * refresh token isn't sitting in plain preferences. We keep:
 *  - accessToken    — short-lived bearer (6 hours)
 *  - refreshToken   — long-lived; used to mint new access tokens silently
 *  - expiresAt      — epoch seconds when the current access token goes stale
 *  - athleteId      — numeric Strava id (handy for logs)
 *  - athleteName    — "First Last", cached so UI can show "Connected as …"
 */
class StravaTokenStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        // Fallback to plain prefs only if the keystore is unusable (shouldn't happen
        // on real hardware). Better than crashing in Settings.
        Timber.e(t, "EncryptedSharedPreferences init failed; falling back to plain")
        context.getSharedPreferences(FILE_NAME + "_plain", Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(v) { prefs.edit().putString(KEY_ACCESS, v).apply() }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(v) { prefs.edit().putString(KEY_REFRESH, v).apply() }

    /** Epoch *seconds* (not millis) — matches Strava's `expires_at` field. */
    var expiresAt: Long
        get() = prefs.getLong(KEY_EXPIRES, 0L)
        set(v) { prefs.edit().putLong(KEY_EXPIRES, v).apply() }

    var athleteId: Long
        get() = prefs.getLong(KEY_ATHLETE_ID, 0L)
        set(v) { prefs.edit().putLong(KEY_ATHLETE_ID, v).apply() }

    var athleteName: String?
        get() = prefs.getString(KEY_ATHLETE_NAME, null)
        set(v) { prefs.edit().putString(KEY_ATHLETE_NAME, v).apply() }

    val hasTokens: Boolean get() = !refreshToken.isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE_NAME = "strava_tokens"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_ATHLETE_ID = "athlete_id"
        private const val KEY_ATHLETE_NAME = "athlete_name"
    }
}
