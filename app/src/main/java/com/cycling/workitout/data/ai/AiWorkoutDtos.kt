package com.cycling.workitout.data.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiWorkoutDto(
    val name: String,
    val description: String = "",
    val intervals: List<AiIntervalDto>
)

@Serializable
data class AiIntervalDto(
    val name: String,
    @SerialName("durationSec") val durationSec: Int,
    @SerialName("targetPct") val targetPct: Double,
    val zone: String = "Z2"
)
