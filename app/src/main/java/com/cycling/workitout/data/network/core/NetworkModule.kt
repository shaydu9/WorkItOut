package com.cycling.workitout.data.network.core

import com.cycling.workitout.BuildConfig
import com.cycling.workitout.data.network.anthropic.AnthropicApi
import com.cycling.workitout.data.network.anthropic.AnthropicAuthInterceptor
import com.cycling.workitout.data.network.strava.StravaApi
import com.cycling.workitout.data.network.strava.StravaAuthInterceptor
import com.cycling.workitout.data.network.strava.StravaAuthenticator
import com.cycling.workitout.data.strava.StravaTokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Singleton network graph. Holds one shared [OkHttpClient] (connection pool
 * reuse) + one converter factory, and exposes typed Retrofit APIs per
 * service.
 *
 * Pattern choices:
 *  - **Plain `object`, not Hilt.** Keeps the codebase framework-light for now;
 *    migration to Hilt later is a mechanical swap — every `@Provides` reads
 *    exactly the same bodies as the `by lazy` blocks below.
 *  - **Shared `OkHttpClient`.** Each [Retrofit] instance wraps a `.newBuilder()`
 *    copy so per-service interceptors (auth, logging overrides) don't leak
 *    across services. The underlying connection pool + dispatcher + cache is
 *    still shared.
 *  - **Factory for Strava, lazy for Anthropic.** Strava's auth interceptor
 *    needs a [StravaTokenStore] passed in by the repository, so it's built
 *    via [createStravaApi]. Anthropic reads its key from BuildConfig so there's
 *    nothing to inject — it's a plain `by lazy`.
 */
object NetworkModule {

    // ── Shared foundation ───────────────────────────────────────────────

    private val jsonConverter by lazy {
        NetworkJson.asConverterFactory("application/json".toMediaType())
    }

    /** Debug-only request/response logging. Release builds emit nothing. */
    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor { message -> Timber.tag("HTTP").d(message) }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /**
     * Shared client. Every service's Retrofit is built from a `.newBuilder()`
     * copy of this, inheriting timeouts + retry + logging while layering its
     * own auth on top.
     */
    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)  // Strava .fit uploads can be chunky
            .addInterceptor(RetryInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    // ── Anthropic ───────────────────────────────────────────────────────

    val anthropicApi: AnthropicApi by lazy {
        val client = sharedClient.newBuilder()
            .addInterceptor(AnthropicAuthInterceptor(apiKeyProvider = ::readAnthropicKey))
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.anthropic.com/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(AnthropicApi::class.java)
    }

    /**
     * Read `ANTHROPIC_API_KEY` from BuildConfig via reflection so changes to
     * `local.properties` + incremental builds are picked up without a clean —
     * Kotlin's static-final-String inlining otherwise caches the stale value.
     */
    private fun readAnthropicKey(): String = try {
        val cls = Class.forName("com.cycling.workitout.BuildConfig")
        cls.getField("ANTHROPIC_API_KEY").get(null) as? String ?: ""
    } catch (t: Throwable) {
        Timber.e(t, "Failed to read ANTHROPIC_API_KEY from BuildConfig")
        ""
    }

    // ── Strava ──────────────────────────────────────────────────────────

    /**
     * Build a [StravaApi] bound to a specific token store. Called once by
     * [com.cycling.workitout.data.strava.StravaRepository] on construction.
     *
     * The Authenticator takes a `() -> StravaApi` provider rather than a
     * direct reference because it needs to call the API instance it lives
     * inside — the lambda breaks that cycle: we capture it before the API
     * is constructed, and it's only invoked later, after init completes.
     */
    fun createStravaApi(tokenStore: StravaTokenStore): StravaApi {
        lateinit var stravaApi: StravaApi

        val client = sharedClient.newBuilder()
            .addInterceptor(StravaAuthInterceptor(tokenStore))
            .authenticator(StravaAuthenticator(tokenStore) { stravaApi })
            .build()

        stravaApi = Retrofit.Builder()
            .baseUrl("https://www.strava.com/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(StravaApi::class.java)

        return stravaApi
    }
}
