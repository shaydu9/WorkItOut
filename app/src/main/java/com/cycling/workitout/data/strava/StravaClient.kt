package com.cycling.workitout.data.strava

import com.cycling.workitout.BuildConfig
import com.cycling.workitout.data.network.core.NetworkModule
import com.cycling.workitout.data.network.strava.StravaApi
import com.cycling.workitout.data.network.strava.dto.StravaTokenResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File

// Handles OAuth code exchange, token storage, and the upload+poll loop for .fit files.
class StravaClient(
    private val tokens: StravaTokenStore,
    private val api: StravaApi = NetworkModule.createStravaApi(tokens)
) {

    // /oauth/mobile/authorize is used because the web variant rejects non-http redirects.
    fun buildAuthorizeUrl(): String {
        val clientId = BuildConfig.STRAVA_CLIENT_ID
        return "https://www.strava.com/oauth/mobile/authorize" +
                "?client_id=$clientId" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=code" +
                "&approval_prompt=auto" +
                "&scope=activity:write,read"
    }

    suspend fun exchangeCode(code: String): StravaTokenResponse = withContext(Dispatchers.IO) {
        val resp = api.exchangeAuthCode(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            code = code
        )
        saveTokens(resp)
        resp
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

    suspend fun uploadFit(
        file: File,
        name: String,
        description: String = "Recorded with WorkItOut"
    ): Long = withContext(Dispatchers.IO) {
        val textType = "text/plain".toMediaTypeOrNull()
        val filePart = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody("application/octet-stream".toMediaTypeOrNull())
        )

        val initial = api.uploadActivity(
            name = name.toRequestBody(textType),
            description = description.toRequestBody(textType),
            trainer = "1".toRequestBody(textType),
            commute = "0".toRequestBody(textType),
            dataType = "fit".toRequestBody(textType),
            file = filePart
        )
        initial.error?.let { throw RuntimeException("Strava rejected upload: $it") }

        // Poll /uploads/{id} until Strava is done chewing on it.
        val uploadId = initial.id
        repeat(15) { attempt ->
            delay(2000)
            val poll = api.pollUpload(uploadId)
            poll.error?.let { throw RuntimeException("Strava upload failed: $it") }
            if (poll.activityId != null && poll.activityId > 0) {
                Timber.i("Strava upload complete → activity ${poll.activityId}")
                return@withContext poll.activityId
            }
            Timber.d("Strava upload pending (attempt ${attempt + 1}): ${poll.status}")
        }
        throw RuntimeException("Strava upload timed out after 30s")
    }

    companion object {
        /**
         * Must match the deep link registered in AndroidManifest.xml *and*
         * match the Authorization Callback Domain configured in the Strava
         * app settings (`workitout`).
         *
         * Strava validates `redirect_uri` by comparing the URI's HOST (not
         * scheme) against the configured callback domain. So with domain
         * `workitout`, the host segment must also be `workitout` — hence the
         * slightly odd-looking `workitout://workitout/strava-callback` shape
         * (scheme=workitout, host=workitout, path=/strava-callback).
         */
        const val REDIRECT_URI = "workitout://workitout/strava-callback"
    }
}
