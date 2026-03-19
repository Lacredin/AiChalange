package com.example.aiadventchalengetestllmapi.embedding

import com.example.aiadventchalengetestllmapi.network.NetworkClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import java.util.prefs.Preferences

object EmbeddingGeneratorStub {
    private const val EMBEDDING_URL = "http://localhost:11434/api/embeddings"
    private const val EMBEDDING_PREFS_NODE = "com.example.aiadventchalengetestllmapi.embedinggeneration"
    private const val EMBEDDING_MODEL_KEY = "selected_embedding_model"
    private const val DEFAULT_EMBEDDING_MODEL = "nomic-embed-text"
    private const val QWEN_EMBEDDING_MODEL = "hf.co/Qwen/Qwen3-Embedding-0.6B-GGUF:Q8_0"

    val availableModels: List<String> = listOf(
        DEFAULT_EMBEDDING_MODEL,
        QWEN_EMBEDDING_MODEL
    )

    fun loadSelectedModel(): String {
        val raw = Preferences.userRoot()
            .node(EMBEDDING_PREFS_NODE)
            .get(EMBEDDING_MODEL_KEY, DEFAULT_EMBEDDING_MODEL)
            .trim()
        return raw.takeIf { it in availableModels } ?: DEFAULT_EMBEDDING_MODEL
    }

    fun saveSelectedModel(model: String) {
        val value = model.takeIf { it in availableModels } ?: DEFAULT_EMBEDDING_MODEL
        Preferences.userRoot().node(EMBEDDING_PREFS_NODE).put(EMBEDDING_MODEL_KEY, value)
    }

    suspend fun createEmbedding(input: String, model: String = loadSelectedModel()): Result<List<Double>> {
        if (input.isBlank()) {
            return Result.failure(IllegalArgumentException("Пустой текст для эмбеддинга"))
        }

        val selectedModel = model.takeIf { it in availableModels } ?: DEFAULT_EMBEDDING_MODEL
        return runCatching {
            val response: OllamaEmbeddingResponse = NetworkClient.httpClient.post(EMBEDDING_URL) {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaEmbeddingRequest(
                        model = selectedModel,
                        prompt = input,
                        input = input
                    )
                )
            }.body()

            if (response.embedding.isEmpty()) {
                throw IllegalStateException("Пустой embedding в ответе")
            }

            response.embedding
        }
    }
}

@Serializable
private data class OllamaEmbeddingRequest(
    val model: String,
    val prompt: String,
    val input: String
)

@Serializable
private data class OllamaEmbeddingResponse(
    val embedding: List<Double> = emptyList()
)
