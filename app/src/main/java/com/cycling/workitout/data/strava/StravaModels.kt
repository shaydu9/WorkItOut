package com.cycling.workitout.data.strava

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Strava OAuth token response. Shape is identical for the authorization-code
 * exchange and the refresh-token flows — on refresh, `athlete` is omitted.
 */
@Serializable
data class StravaTokenResponse(
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("expires_in") val expiresIn: Long = 0,
    val athlete: StravaAthlete? = null
)

@Serializable
data class StravaAthlete(
    val id: Long,
    val firstname: String? = null,
    val lastname: String? = null
)

/** Response from POST /api/v3/uploads and subsequent GET /uploads/{id}. */
@Serializable
data class StravaUploadResponse(
    val id: Long = 0,
    @SerialName("id_str") val idStr: String? = null,
    @SerialName("external_id") val externalId: String? = null,
    val error: String? = null,
    val status: String? = null,
    @SerialName("activity_id") val activityId: Long? = null
)
