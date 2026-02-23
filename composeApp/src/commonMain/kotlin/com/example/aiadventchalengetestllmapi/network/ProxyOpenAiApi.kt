package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

private val proxyApiJson = Json { ignoreUnknownKeys = true }

class ProxyOpenAiApi(
    private val httpClient: HttpClient = NetworkClient.httpClient,
    private val baseUrl: String = "https://openai.api.proxyapi.ru/v1"
) {
    suspend fun createChatCompletion(
        apiKey: String,
        request: DeepSeekChatRequest
    ): DeepSeekChatResponse {
        val response = httpClient.post("$baseUrl/chat/completions") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("ProxyAPI request failed (${response.status.value}): $payload")
        }

        return try {
            proxyApiJson.decodeFromString(DeepSeekChatResponse.serializer(), payload)
        } catch (e: Exception) {
            error("ProxyAPI returned unexpected payload: $payload")
        }
    }
}
