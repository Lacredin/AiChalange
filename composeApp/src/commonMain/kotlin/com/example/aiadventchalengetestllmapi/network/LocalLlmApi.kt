package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val localLlmJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

class LocalLlmApi(
    private val httpClient: HttpClient = NetworkClient.httpClient,
    private val chatUrl: String = "http://localhost:11434/api/chat"
) {
    suspend fun createChatCompletion(request: DeepSeekChatRequest): DeepSeekChatResponse {
        val response = httpClient.post(chatUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                LocalLlmChatRequest(
                    model = request.model,
                    messages = request.messages,
                    stream = true
                )
            )
        }
        val payload = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("Local LLM request failed (${response.status.value}): $payload")
        }

        val lines = payload
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) {
            error("Local LLM returned empty payload")
        }

        val text = buildString {
            lines.forEach { line ->
                val chunk = localLlmJson.decodeFromString(LocalLlmChatChunk.serializer(), line)
                append(chunk.message?.content.orEmpty())
            }
        }
        val lastChunk = localLlmJson.decodeFromString(LocalLlmChatChunk.serializer(), lines.last())

        return DeepSeekChatResponse(
            id = "local-llm",
            model = lastChunk.model ?: request.model,
            choices = listOf(
                DeepSeekChoice(
                    index = 0,
                    message = DeepSeekMessage(role = "assistant", content = text),
                    finishReason = lastChunk.doneReason
                )
            ),
            usage = null
        )
    }
}

@Serializable
private data class LocalLlmChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val stream: Boolean = true
)

@Serializable
private data class LocalLlmChatChunk(
    val model: String? = null,
    val message: DeepSeekMessage? = null,
    val done: Boolean = false,
    @SerialName("done_reason")
    val doneReason: String? = null
)
