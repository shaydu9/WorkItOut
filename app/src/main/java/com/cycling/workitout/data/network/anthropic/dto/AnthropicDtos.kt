package com.cycling.workitout.data.network.anthropic.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Anthropic Messages API wire types — internal to the network layer.

@Serializable
data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: List<SystemBlock>,
    val messages: List<Message>
)

@Serializable
data class SystemBlock(
    val type: String,
    val text: String,
    @SerialName("cache_control") val cacheControl: CacheControl? = null
)

// Prompt cache hint — "ephemeral" gives a 5-minute TTL.
@Serializable
data class CacheControl(val type: String = "ephemeral")

@Serializable
data class Message(
    val role: String,
    val content: String
)

@Serializable
data class MessagesResponse(
    val id: String,
    val type: String = "message",
    val role: String = "assistant",
    val model: String = "",
    val content: List<ContentBlock>,
    @SerialName("stop_reason") val stopReason: String? = null
)

@Serializable
data class ContentBlock(
    val type: String,
    val text: String = ""
)
