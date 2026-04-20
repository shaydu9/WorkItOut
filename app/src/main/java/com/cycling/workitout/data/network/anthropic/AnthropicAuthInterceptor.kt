package com.cycling.workitout.data.network.anthropic

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Stamps Anthropic's required headers on every outbound request:
 *  - `x-api-key`       — the account API key
 *  - `anthropic-version` — API version pin (matches the docs we're coding against)
 *
 * The key is read lazily via [apiKeyProvider] so tests can inject a fake and
 * so `BuildConfig.ANTHROPIC_API_KEY` changes in `local.properties` are picked
 * up across incremental builds without stale inlining.
 */
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
