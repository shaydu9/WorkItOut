package com.cycling.workitout.data.network.strava

import com.cycling.workitout.data.strava.StravaTokenStore
import okhttp3.Interceptor
import okhttp3.Response

// Attaches Bearer token to every Strava request; skips OAuth endpoints and blank tokens.
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
