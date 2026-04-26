package com.cycling.workitout.data.network.core

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.math.min
import kotlin.random.Random

// Exponential backoff for 408/429/5xx and IOExceptions; honors Retry-After on 429.
class RetryInterceptor(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var lastError: Throwable? = null

        for (attempt in 0 until maxAttempts) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || !isRetryable(response.code) || attempt == maxAttempts - 1) {
                    return response
                }
                val delayMs = retryDelayMs(response, attempt)
                Timber.w("HTTP ${response.code} on ${request.url.encodedPath} — retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxAttempts)")
                response.close()
                sleep(delayMs)
            } catch (e: IOException) {
                lastError = e
                if (attempt == maxAttempts - 1) throw e
                val delayMs = backoffMs(attempt)
                Timber.w(e, "Network error on ${request.url.encodedPath} — retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxAttempts)")
                sleep(delayMs)
            }
        }
        // Unreachable unless maxAttempts == 0; defensive throw.
        throw IOException("RetryInterceptor exhausted after $maxAttempts attempts", lastError)
    }

    private fun isRetryable(status: Int): Boolean =
        status == 408 || status == 429 || status in 500..599

    private fun retryDelayMs(response: Response, attempt: Int): Long {
        if (response.code == 429) {
            response.header("Retry-After")?.toLongOrNull()?.let { seconds ->
                return (seconds * 1000L).coerceIn(500L, MAX_BACKOFF_MS)
            }
        }
        return backoffMs(attempt)
    }

    private fun backoffMs(attempt: Int): Long {
        val base = 500L shl attempt        // 500, 1000, 2000, 4000…
        val jitter = Random.nextLong(0, 400L)
        return min(base + jitter, MAX_BACKOFF_MS)
    }

    // Extracted for test overrides; plain Thread.sleep is fine since OkHttp's
    // interceptor chain already runs on a background dispatcher thread.
    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 4   // 1 initial + 3 retries
        const val MAX_BACKOFF_MS = 8_000L    // cap any single sleep at 8s
    }
}
