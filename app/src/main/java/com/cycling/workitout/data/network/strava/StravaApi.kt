package com.cycling.workitout.data.network.strava

import com.cycling.workitout.data.network.strava.dto.StravaTokenResponse
import com.cycling.workitout.data.network.strava.dto.StravaUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Retrofit interface for the Strava REST API.
 *
 * Base URL: `https://www.strava.com/` — OAuth endpoints and versioned API
 * endpoints share a host, just different path prefixes (`oauth/` vs `api/v3/`).
 *
 * Auth:
 *  - `/oauth/token` is **unauthenticated** — callers present client credentials
 *    in the form body. [StravaAuthInterceptor] skips it; [StravaAuthenticator]
 *    skips it too (no point trying to refresh the thing that does the refreshing).
 *  - `/api/v3/...` requires `Authorization: Bearer <token>` — handled by
 *    [StravaAuthInterceptor]; if the access token has expired, [StravaAuthenticator]
 *    transparently refreshes and retries.
 */
interface StravaApi {

    /** Authorization-code grant — runs once, when the user returns from Custom Tabs. */
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeAuthCode(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): StravaTokenResponse

    /** Refresh-token grant — called by [StravaAuthenticator] on 401. */
    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): StravaTokenResponse

    /**
     * Upload a .fit file. Returns an upload record with an id; Strava processes
     * the file asynchronously — callers poll [pollUpload] until `activityId`
     * is non-null or `error` is populated.
     */
    @Multipart
    @POST("api/v3/uploads")
    suspend fun uploadActivity(
        @Part("name") name: RequestBody,
        @Part("description") description: RequestBody,
        @Part("trainer") trainer: RequestBody,
        @Part("commute") commute: RequestBody,
        @Part("data_type") dataType: RequestBody,
        @Part file: MultipartBody.Part
    ): StravaUploadResponse

    @GET("api/v3/uploads/{id}")
    suspend fun pollUpload(@Path("id") id: Long): StravaUploadResponse
}
