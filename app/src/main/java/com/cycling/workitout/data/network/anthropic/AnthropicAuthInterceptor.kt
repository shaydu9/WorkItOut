package com.cycling.workitout.data.network.anthropic

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

// Stamps Authorization: Bearer <Firebase ID token> on every request to the proxy function.
class AnthropicAuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            try {
                FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
            } catch (t: Throwable) {
                Timber.tag("AI").e(t, "Failed to fetch Firebase ID token")
                null
            }
        }

        val request = chain.request().newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .header("content-type", "application/json")
            .build()

        return chain.proceed(request)
    }
}
