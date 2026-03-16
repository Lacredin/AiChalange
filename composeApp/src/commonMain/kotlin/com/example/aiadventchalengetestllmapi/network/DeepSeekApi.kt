package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class DeepSeekApi(
    private val httpClient: HttpClient = NetworkClient.httpClient,
    private val baseUrl: String = "https://api.deepseek.com/v1"
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

    suspend fun createEmbedding(
        apiKey: String,
        request: DeepSeekEmbeddingRequest
    ): DeepSeekEmbeddingResponse {
        return httpClient.post("$baseUrl/embeddings") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}

@Serializable
data class DeepSeekEmbeddingRequest(
    val model: String,
    val input: String,
    @SerialName("encoding_format")
    val encodingFormat: String? = null
)

@Serializable
data class DeepSeekEmbeddingResponse(
    val data: List<DeepSeekEmbeddingData> = emptyList(),
    val model: String? = null,
    val usage: DeepSeekEmbeddingUsage? = null
)

@Serializable
data class DeepSeekEmbeddingData(
    val embedding: List<Double> = emptyList(),
    val index: Int? = null
)

@Serializable
data class DeepSeekEmbeddingUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null
)

@Serializable
data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("top_p")
    val topP: Double? = null,
    @SerialName("presence_penalty")
    val presencePenalty: Double? = null,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Double? = null,
    val stop: List<String>? = null,
    @SerialName("response_format")
    val responseFormat: DeepSeekResponseFormat? = null
)

@Serializable
data class DeepSeekResponseFormat(val type: String)

@Serializable
data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
data class DeepSeekChatResponse(
    val id: String,
    val model: String,
    val choices: List<DeepSeekChoice>,
    val usage: DeepSeekUsage? = null
)

@Serializable
data class DeepSeekUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
    @SerialName("total_tokens")
    val totalTokens: Int? = null,
    @SerialName("prompt_cache_hit_tokens")
    val promptCacheHitTokens: Int? = null,
    @SerialName("prompt_cache_miss_tokens")
    val promptCacheMissTokens: Int? = null
)

@Serializable
data class DeepSeekChoice(
    val index: Int,
    val message: DeepSeekMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null
)
