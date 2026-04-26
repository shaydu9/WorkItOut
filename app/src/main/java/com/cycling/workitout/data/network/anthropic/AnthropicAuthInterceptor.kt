package com.cycling.workitout.data.network.anthropic

import okhttp3.Interceptor
import okhttp3.Response

// Stamps x-api-key and anthropic-version headers on every request.
class AnthropicAuthInterceptor(
    private val apiKeyProvider: () -> String,
    private val apiVersion: String = "2023-06-01"
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("x-api-key", apiKeyProvider())
            .header("anthropic-version", apiVersion)
            .header("content-type", "application/json")
            .build()
        return chain.proceed(request)
    }
}
