package com.cycling.workitout.data.network.strava

import com.cycling.workitout.data.strava.StravaTokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <accessToken>` to every Strava API request,
 * except OAuth endpoints which authenticate via form-body client credentials.
 *
 * When the access token is blank (user not connected yet), the header is
 * omitted — the server returns 401 and the caller surfaces "not connected."
 *
 * Token freshness is **not** this class's concern. Expired tokens trigger a
 * 401, which [StravaAuthenticator] catches and handles transparently.
 */
class StravaAuthInterceptor(
    private val tokenStore: StravaTokenStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // OAuth endpoints don't take a Bearer token.
        if (request.url.encodedPath.startsWith("/oauth/")) {
            return chain.proceed(request)
        }

        val token = tokenStore.accessToken
        val augmented = if (!token.isNullOrBlank()) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        return chain.proceed(augmented)
    }
}
