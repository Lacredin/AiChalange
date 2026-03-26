package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.preparePost
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class OpenAiApi(
    private val httpClient: HttpClient = NetworkClient.httpClient,
    private val baseUrl: String = "https://api.openai.com/v1"
) {
    suspend fun createChatCompletion(
        apiKey: String,
        request: DeepSeekChatRequest
    ): DeepSeekChatResponse {
        return httpClient.post("$baseUrl/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun createChatCompletionStreaming(
        apiKey: String,
        request: DeepSeekChatRequest,
        onChunk: (String) -> Unit
    ): DeepSeekChatResponse {
        return httpClient.preparePost("$baseUrl/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request.copy(stream = true))
        }.execute { response ->
            parseStreamingChatResponse(response, request, providerName = "OpenAI", onChunk = onChunk)
        }
    }
}
