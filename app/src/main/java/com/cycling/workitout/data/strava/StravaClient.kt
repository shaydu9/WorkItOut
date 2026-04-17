package com.cycling.workitout.data.strava

import com.cycling.workitout.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Thin REST wrapper around the bits of Strava's API we care about:
 *   - OAuth authorize URL builder   (Custom Tabs target)
 *   - authorization-code exchange   (one-shot, after the deep-link returns)
 *   - refresh-token flow            (silent, whenever [TokenStore.expiresAt] is stale)
 *   - uploads + polling             (the actual "push a .fit to Strava" call)
 *
 * All network I/O runs on Dispatchers.IO. Callers see suspend functions that
 * throw on hard failure.
 */
class StravaClient(private val tokens: StravaTokenStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS) // uploads can be chunky
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // ── OAuth ───────────────────────────────────────────────────────────

    /**
     * Build the mobile OAuth authorize URL. Strava's `/oauth/mobile/authorize`
     * variant is the one that plays nicely with custom-scheme redirects; the
     * web variant rejects non-http redirects in some flows.
     */
    fun buildAuthorizeUrl(): String {
        val clientId = BuildConfig.STRAVA_CLIENT_ID
        return "https://www.strava.com/oauth/mobile/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=code" +
                "&approval_prompt=auto" +
                "&scope=activity:write,read"
    }

    /** Exchange the authorization code we just got back via deep-link for tokens. */
    suspend fun exchangeCode(code: String): StravaTokenResponse = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.STRAVA_CLIENT_ID)
            .add("client_secret", BuildConfig.STRAVA_CLIENT_SECRET)
            .add("code", code)
            .add("grant_type", "authorization_code")
            .build()
        val resp = postForTokens(body)
        saveTokens(resp)
        resp
    }

    /** Refresh if the access token is missing or within the 2-minute skew window. */
    suspend fun ensureFreshToken(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis() / 1000
        val current = tokens.accessToken
        if (!current.isNullOrBlank() && tokens.expiresAt - 120 > now) {
            return@withContext current
        }
        val refresh = tokens.refreshToken
            ?: throw IllegalStateException("Strava not connected — no refresh token")
        Timber.d("Refreshing Strava access token")
        val body = FormBody.Builder()
            .add("client_id", BuildConfig.STRAVA_CLIENT_ID)
            .add("client_secret", BuildConfig.STRAVA_CLIENT_SECRET)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        val resp = postForTokens(body)
        saveTokens(resp)
        resp.accessToken
    }

    private fun postForTokens(body: okhttp3.RequestBody): StravaTokenResponse {
        val req = Request.Builder()
            .url("https://www.strava.com/oauth/token")
            .post(body)
            .build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("Strava token endpoint ${response.code}: $text")
            }
            return json.decodeFromString(StravaTokenResponse.serializer(), text)
        }
    }

    private fun saveTokens(resp: StravaTokenResponse) {
        tokens.accessToken = resp.accessToken
        tokens.refreshToken = resp.refreshToken
        tokens.expiresAt = resp.expiresAt
        resp.athlete?.let { a ->
            tokens.athleteId = a.id
            tokens.athleteName = listOfNotNull(a.firstname, a.lastname)
                .joinToString(" ")
                .ifBlank { "Strava athlete #${a.id}" }
        }
    }

    // ── Uploads ─────────────────────────────────────────────────────────

    /**
     * Upload a .fit file and poll until Strava finishes processing it.
     * Returns the new activity id on success, throws on failure.
     */
    suspend fun uploadFit(
        file: File,
        name: String,
        description: String = "Recorded with WorkItOut"
    ): Long = withContext(Dispatchers.IO) {
        val bearer = ensureFreshToken()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("name", name)
            .addFormDataPart("description", description)
            .addFormDataPart("trainer", "1")
            .addFormDataPart("commute", "0")
            .addFormDataPart("data_type", "fit")
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            )
            .build()

        val req = Request.Builder()
            .url("https://www.strava.com/api/v3/uploads")
            .header("Authorization", "Bearer $bearer")
            .post(body)
            .build()

        val initial: StravaUploadResponse = http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("Strava upload ${response.code}: $text")
            }
            json.decodeFromString(StravaUploadResponse.serializer(), text)
        }
        initial.error?.let { throw RuntimeException("Strava rejected upload: $it") }

        // Poll /uploads/{id} until Strava is done chewing on it.
        val uploadId = initial.id
        repeat(15) { attempt ->
            delay(2000)
            val poll = pollUpload(uploadId, bearer)
            poll.error?.let { throw RuntimeException("Strava upload failed: $it") }
            if (poll.activityId != null && poll.activityId > 0) {
                Timber.i("Strava upload complete → activity ${poll.activityId}")
                return@withContext poll.activityId
            }
            Timber.d("Strava upload pending (attempt ${attempt + 1}): ${poll.status}")
        }
        throw RuntimeException("Strava upload timed out after 30s")
    }

    private fun pollUpload(uploadId: Long, bearer: String): StravaUploadResponse {
        val req = Request.Builder()
            .url("https://www.strava.com/api/v3/uploads/$uploadId")
            .header("Authorization", "Bearer $bearer")
            .get()
            .build()
        http.newCall(req).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw RuntimeException("Strava poll ${response.code}: $text")
            }
            return json.decodeFromString(StravaUploadResponse.serializer(), text)
        }
    }

    companion object {
        /**
         * Must match the deep link registered in AndroidManifest.xml *and*
         * match the Authorization Callback Domain configured in the Strava
         * app settings (`workitout`).
         */
        const val REDIRECT_URI = "workitout://strava-callback"
    }
}
