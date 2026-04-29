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

// One shared OkHttpClient + one JSON converter; Retrofit instances are built per-service on top.
object NetworkModule {

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

    private val sharedClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)  // Strava .fit uploads can be chunky
            .addInterceptor(RetryInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val anthropicApi: AnthropicApi by lazy {
        val client = sharedClient.newBuilder()
            .addInterceptor(AnthropicAuthInterceptor())
            .build()

        Retrofit.Builder()
            .baseUrl("https://us-central1-workitout-7cce5.cloudfunctions.net/")
            .client(client)
            .addConverterFactory(jsonConverter)
            .build()
            .create(AnthropicApi::class.java)
    }

    // Lambda breaks the circular dep: Authenticator needs the API, but API isn't built yet.
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
