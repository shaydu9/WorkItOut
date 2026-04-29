package com.cycling.workitout.data.network.anthropic

import com.cycling.workitout.data.network.anthropic.dto.MessagesRequest
import com.cycling.workitout.data.network.anthropic.dto.MessagesResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AnthropicApi {

    // Throws HttpException on non-2xx after retries are exhausted.
    @POST("anthropicMessages")
    suspend fun createMessage(@Body request: MessagesRequest): MessagesResponse
}
