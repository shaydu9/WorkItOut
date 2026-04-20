package com.cycling.workitout.data.network.strava.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the Strava REST API.
 *
 * Same rule as Anthropic: these are internal to the network layer. The
 * domain surface lives in `StravaRepository`'s public API (`UploadState`,
 * etc.), which maps from these.
 */

/**
 * Response from `POST /oauth/token` for either the authorization-code or
 * refresh-token grant. On refresh the `athlete` object is omitted.
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

/**
 * Response from `POST /api/v3/uploads` and the subsequent `GET /api/v3/uploads/{id}`
 * polling calls. Strava processes uploads asynchronously; you post the .fit,
 * receive an upload id, and then poll until `activity_id` is populated or
 * `error` is non-null.
 */
@Serializable
data class StravaUploadResponse(
    val id: Long = 0,
    @SerialName("id_str") val idStr: String? = null,
    @SerialName("external_id") val externalId: String? = null,
    val error: String? = null,
    val status: String? = null,
    @SerialName("activity_id") val activityId: Long? = null
)
