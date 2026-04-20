package com.cycling.workitout.data.network.anthropic

import com.cycling.workitout.data.network.anthropic.dto.MessagesRequest
import com.cycling.workitout.data.network.anthropic.dto.MessagesResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit interface for the Anthropic Messages API.
 *
 * Reading this file tells you every call the app can make to Anthropic.
 * Base URL: `https://api.anthropic.com/` — set in [com.cycling.workitout.data.network.core.NetworkModule].
 */
interface AnthropicApi {

    /**
     * Create a message — the core synchronous LLM call. Retry, auth headers,
     * and JSON encoding/decoding are all handled by the OkHttp + Retrofit
     * pipeline; this function just declares the shape.
     *
     * Throws retrofit2.HttpException on non-2xx (after [RetryInterceptor]
     * exhausts its attempts for transient errors).
     */
    @POST("v1/messages")
    suspend fun createMessage(@Body request: MessagesRequest): MessagesResponse
}
