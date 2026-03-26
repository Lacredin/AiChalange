package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val streamJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

internal suspend fun parseStreamingChatResponse(
    response: HttpResponse,
    request: DeepSeekChatRequest,
    providerName: String,
    onChunk: (String) -> Unit
): DeepSeekChatResponse {
    if (!response.status.isSuccess()) {
        val payload = response.bodyAsText()
        error("$providerName request failed (${response.status.value}): $payload")
    }

    val channel = response.bodyAsChannel()
    val content = StringBuilder()
    var streamModel: String? = null
    var finishReason: String? = null
    var fullResponse: DeepSeekChatResponse? = null

    while (true) {
        val line = channel.readUTF8Line() ?: break
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        val payload = when {
            trimmed.startsWith("data:") -> trimmed.removePrefix("data:").trim()
            else -> trimmed
        }
        if (payload.isEmpty() || payload == "[DONE]") continue

        fullResponse = runCatching { streamJson.decodeFromString(DeepSeekChatResponse.serializer(), payload) }.getOrNull()
        if (fullResponse != null) {
            break
        }

        val streamChunk = runCatching { streamJson.decodeFromString(OpenAiStreamChunk.serializer(), payload) }.getOrNull() ?: continue
        if (streamModel == null) streamModel = streamChunk.model
        val choice = streamChunk.choices.firstOrNull()
        if (choice != null) {
            finishReason = choice.finishReason ?: finishReason
            val delta = choice.delta?.content.orEmpty()
            if (delta.isNotEmpty()) {
                content.append(delta)
                onChunk(delta)
            }
        }
    }

    if (fullResponse != null) return fullResponse

    return DeepSeekChatResponse(
        id = "stream-$providerName",
        model = streamModel ?: request.model,
        choices = listOf(
            DeepSeekChoice(
                index = 0,
                message = DeepSeekMessage(role = "assistant", content = content.toString()),
                finishReason = finishReason
            )
        ),
        usage = null
    )
}

@Serializable
private data class OpenAiStreamChunk(
    val model: String? = null,
    val choices: List<OpenAiStreamChoice> = emptyList()
)

@Serializable
private data class OpenAiStreamChoice(
    val index: Int? = null,
    val delta: OpenAiStreamDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
private data class OpenAiStreamDelta(
    val content: String? = null
)
