package com.cycling.workitout.data.network.strava

import com.cycling.workitout.BuildConfig
import com.cycling.workitout.data.strava.StravaTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber

/**
 * OkHttp [Authenticator] — the idiomatic place to handle token rotation.
 *
 * Runs **only** when a response comes back 401 Unauthorized. Preferred over
 * preemptive "is the token expired yet?" checks on every request: it sidesteps
 * clock-skew bugs and only refreshes when the server actually says so.
 *
 * Flow:
 *  1. Original request → 401.
 *  2. OkHttp invokes [authenticate] on its dispatcher thread.
 *  3. We check if we've already retried once (to prevent infinite loops) and
 *     that the failing URL isn't itself the refresh endpoint.
 *  4. Refresh the token synchronously via [stravaApiProvider]. A lambda is
 *     used rather than a direct [StravaApi] reference so [NetworkModule] can
 *     construct the API lazily — breaks the circular dep between Retrofit
 *     and its own Authenticator.
 *  5. Return the original request with the new Bearer — OkHttp retries it.
 *
 * Returning `null` tells OkHttp "give up" — the original 401 bubbles to the
 * caller and surfaces as a clean error in the UI.
 */
class StravaAuthenticator(
    private val tokenStore: StravaTokenStore,
    private val stravaApiProvider: () -> StravaApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Already retried once → give up rather than loop forever.
        if (responseCount(response) >= 2) {
            Timber.w("Strava auth: already retried once, giving up")
            return null
        }

        // The refresh endpoint returning 401 means the refresh token itself
        // is bad — no point asking it to refresh itself.
        if (response.request.url.encodedPath.startsWith("/oauth/")) {
            return null
        }

        val refresh = tokenStore.refreshToken
        if (refresh.isNullOrBlank()) {
            Timber.w("Strava auth: no refresh token stored — user must reconnect")
            return null
        }

        val newAccessToken = try {
            runBlocking {
                val resp = stravaApiProvider().refreshAccessToken(
                    clientId = BuildConfig.STRAVA_CLIENT_ID,
                    clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
                    refreshToken = refresh
                )
                tokenStore.accessToken = resp.accessToken
                tokenStore.refreshToken = resp.refreshToken
                tokenStore.expiresAt = resp.expiresAt
                resp.accessToken
            }
        } catch (t: Throwable) {
            Timber.e(t, "Strava token refresh failed")
            return null
        }

        Timber.d("Strava auth: refreshed access token, retrying original request")
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
    }

    /**
     * Count how many times this response chain has been tried. Each retry adds
     * one link via `priorResponse`. If we've already retried once and still got
     * 401, the refresh didn't stick — bail out.
     */
    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
