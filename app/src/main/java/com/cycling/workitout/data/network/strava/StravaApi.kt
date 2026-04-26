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

interface StravaApi {

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun exchangeAuthCode(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): StravaTokenResponse

    @FormUrlEncoded
    @POST("oauth/token")
    suspend fun refreshAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): StravaTokenResponse

    // Strava processes async — poll until activityId is non-null or error is set.
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
