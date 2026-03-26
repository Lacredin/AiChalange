package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
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

    suspend fun createChatCompletionStreaming(
        request: DeepSeekChatRequest,
        onChunk: (String) -> Unit
    ): DeepSeekChatResponse {
        return httpClient.preparePost(chatUrl) {
            contentType(ContentType.Application.Json)
            setBody(
                LocalLlmChatRequest(
                    model = request.model,
                    messages = request.messages,
                    stream = true
                )
            )
        }.execute { response ->
            if (!response.status.isSuccess()) {
                val payload = response.bodyAsText()
                error("Local LLM request failed (${response.status.value}): $payload")
            }

            val channel = response.bodyAsChannel()
            var lastChunk: LocalLlmChatChunk? = null
            val text = buildString {
                while (true) {
                    val line = channel.readUTF8Line() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val chunk = localLlmJson.decodeFromString(LocalLlmChatChunk.serializer(), trimmed)
                    lastChunk = chunk
                    val delta = chunk.message?.content.orEmpty()
                    if (delta.isNotEmpty()) {
                        append(delta)
                        onChunk(delta)
                    }
                }
            }

            val finalChunk = lastChunk ?: error("Local LLM returned empty payload")
            DeepSeekChatResponse(
                id = "local-llm",
                model = finalChunk.model ?: request.model,
                choices = listOf(
                    DeepSeekChoice(
                        index = 0,
                        message = DeepSeekMessage(role = "assistant", content = text),
                        finishReason = finalChunk.doneReason
                    )
                ),
                usage = null
            )
        }
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
