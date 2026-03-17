package com.example.aiadventchalengetestllmapi.embedding

import com.example.aiadventchalengetestllmapi.network.NetworkClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

object EmbeddingGeneratorStub {
    private const val EMBEDDING_URL = "http://localhost:11434/api/embeddings"
    private const val EMBEDDING_MODEL = "nomic-embed-text"

    suspend fun createEmbedding(input: String): Result<List<Double>> {
        if (input.isBlank()) {
            return Result.failure(IllegalArgumentException("Пустой текст для эмбеддинга"))
        }

        return runCatching {
            val response: OllamaEmbeddingResponse = NetworkClient.httpClient.post(EMBEDDING_URL) {
                contentType(ContentType.Application.Json)
                setBody(
                    OllamaEmbeddingRequest(
                        model = EMBEDDING_MODEL,
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
