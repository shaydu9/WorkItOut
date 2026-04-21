package com.cycling.workitout.data.network.anthropic.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the Anthropic Messages API (v1).
 *
 * These are internal to the network layer — repositories translate between
 * them and domain types (`WorkoutDefinition`, etc.). Don't leak them upward.
 *
 * See: https://docs.anthropic.com/en/api/messages
 */

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

/** Prompt caching hint — "ephemeral" = 5-minute TTL on the API's cache. */
@Serializable
data class CacheControl(val type: String = "ephemeral")

@Serializable
data class Message(
    val role: String,     // "user" | "assistant"
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

/** Claude's output is a list of blocks; we only care about `text` blocks today. */
@Serializable
data class ContentBlock(
    val type: String,        // "text" | "tool_use" | …
    val text: String = ""
)
