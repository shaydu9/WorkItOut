package com.cycling.workitout.data.network.strava

import com.cycling.workitout.BuildConfig
import com.cycling.workitout.data.network.core.NetworkModule
import com.cycling.workitout.data.strava.StravaClient
import com.cycling.workitout.data.strava.StravaTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber

// Handles 401s from Strava by refreshing the access token and retrying once.
class StravaAuthenticator(
    private val tokenStore: StravaTokenStore,
    @Suppress("UNUSED_PARAMETER") stravaApiProvider: () -> StravaApi
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Already retried once → give up rather than loop forever.
        if (responseCount(response) >= 2) {
            Timber.tag("STRAVA").w("Strava auth: already retried once, giving up")
            return null
        }

        // The refresh endpoint returning 401 means the refresh token itself
        // is bad — no point asking it to refresh itself.
        if (response.request.url.encodedPath.startsWith("/oauth/")) {
            return null
        }

        val refresh = tokenStore.refreshToken
        if (refresh.isNullOrBlank()) {
            Timber.tag("STRAVA").w("Strava auth: no refresh token stored — user must reconnect")
            return null
        }

        val newAccessToken = try {
            runBlocking {
                val idToken = StravaClient.firebaseIdToken()
                val resp = NetworkModule.stravaTokenRefresh(
                    clientId = BuildConfig.STRAVA_CLIENT_ID,
                    refreshToken = refresh,
                    idToken = idToken
                )
                tokenStore.accessToken = resp.accessToken
                tokenStore.refreshToken = resp.refreshToken
                tokenStore.expiresAt = resp.expiresAt
                resp.accessToken
            }
        } catch (t: Throwable) {
            Timber.tag("STRAVA").e(t, "Strava token refresh failed")
            return null
        }

        Timber.tag("STRAVA").d("Strava auth: refreshed access token, retrying original request")
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccessToken")
            .build()
    }

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
