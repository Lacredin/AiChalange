package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.embedding.EmbeddingGeneratorStub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAIN_RAG_DEFAULT_TOP_K_BEFORE = 12
private const val MAIN_RAG_DEFAULT_TOP_K_AFTER = 5
private const val MAIN_RAG_DEFAULT_MIN_SCORE = 0.30
private const val MAIN_RAG_MAX_CHUNK_TEXT = 1400

private val mainRagJson = Json { ignoreUnknownKeys = true }

internal data class AiAgentMainEmbeddingChunkRecord(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: Long,
    val strategy: String,
    val chunkText: String,
    val embeddingJson: String
)

internal data class AiAgentMainRetrievedChunk(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: Long,
    val strategy: String,
    val chunkText: String,
    val score: Double
)

internal data class AiAgentMainRagConfig(
    val topKBefore: Int = MAIN_RAG_DEFAULT_TOP_K_BEFORE,
    val topKAfter: Int = MAIN_RAG_DEFAULT_TOP_K_AFTER,
    val threshold: Double = MAIN_RAG_DEFAULT_MIN_SCORE,
    val useFilter: Boolean = true,
    val useRewrite: Boolean = false
)

internal data class AiAgentMainRagPayload(
    val promptContext: String,
    val retrievalInfo: String,
    val selectedChunks: List<AiAgentMainRetrievedChunk>
)

internal suspend fun buildAiAgentMainRagPayload(
    query: String,
    chunks: List<AiAgentMainEmbeddingChunkRecord>,
    config: AiAgentMainRagConfig = AiAgentMainRagConfig()
): AiAgentMainRagPayload {
    val retrievalQuery = if (config.useRewrite) heuristicRewriteQueryForMainRag(query) else query
    val queryEmbedding = EmbeddingGeneratorStub.createEmbedding(retrievalQuery).getOrElse { throw it }

    val ranked = chunks.mapNotNull { row ->
        val emb = parseEmbeddingVectorForMainRag(row.embeddingJson)
        if (emb.size != queryEmbedding.size) return@mapNotNull null
        val score = cosineSimilarityForMainRag(queryEmbedding, emb)
        if (!score.isFinite()) return@mapNotNull null
        AiAgentMainRetrievedChunk(
            source = row.source,
            title = row.title,
            section = row.section,
            chunkId = row.chunkId,
            strategy = row.strategy,
            chunkText = row.chunkText,
            score = score
        )
    }.sortedByDescending { it.score }

    val topBefore = ranked.take(config.topKBefore)
    val afterFilter = if (config.useFilter) topBefore.filter { it.score >= config.threshold } else topBefore
    val topAfter = afterFilter.take(config.topKAfter)

    val retrievalInfo = buildString {
        append("rewrite=${if (config.useRewrite) "on" else "off"}")
        append(" | filter=${if (config.useFilter) "on" else "off"}")
        append(" | threshold=${"%.2f".format(config.threshold)}")
        append(" | candidates=${ranked.size}")
        append(" | topK-before=${topBefore.size}")
        append(" | after-filter=${afterFilter.size}")
        append(" | topK-after=${topAfter.size}")
    }

    val contextBlock = if (topAfter.isEmpty()) {
        "RAG context is empty: no chunks passed retrieval."
    } else {
        topAfter.mapIndexed { index, chunk ->
            val chunkText = chunk.chunkText.trim().take(MAIN_RAG_MAX_CHUNK_TEXT)
            "[S${index + 1}] title=${chunk.title}\n" +
                "section=${chunk.section}\n" +
                "source=${chunk.source}\n" +
                "score=${"%.4f".format(chunk.score)}\n" +
                "chunk=$chunkText"
        }.joinToString("\n\n")
    }

    val promptContext = buildString {
        appendLine("RAG context (local embeddings DB):")
        appendLine("retrieval_query: $retrievalQuery")
        appendLine("retrieval_info: $retrievalInfo")
        appendLine()
        append("Use this context for analysis and answers. If context is empty, say data is insufficient.")
        appendLine()
        appendLine()
        append("CONTEXT:\n$contextBlock")
    }.trim()

    return AiAgentMainRagPayload(
        promptContext = promptContext,
        retrievalInfo = retrievalInfo,
        selectedChunks = topAfter
    )
}

private fun parseEmbeddingVectorForMainRag(raw: String): List<Double> {
    val parsed = runCatching { mainRagJson.parseToJsonElement(raw.trim()) }.getOrNull()
    val array = when (parsed) {
        is JsonObject -> parsed["embedding"] as? JsonArray
        is JsonArray -> parsed
        else -> null
    } ?: return emptyList()
    return array.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
}

private fun cosineSimilarityForMainRag(left: List<Double>, right: List<Double>): Double {
    if (left.isEmpty() || right.isEmpty() || left.size != right.size) return 0.0
    var dot = 0.0
    var l2 = 0.0
    var r2 = 0.0
    for (i in left.indices) {
        dot += left[i] * right[i]
        l2 += left[i] * left[i]
        r2 += right[i] * right[i]
    }
    if (l2 == 0.0 || r2 == 0.0) return 0.0
    return dot / (kotlin.math.sqrt(l2) * kotlin.math.sqrt(r2))
}

private fun heuristicRewriteQueryForMainRag(original: String): String {
    val normalized = original.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isEmpty()) return original
    val keywords = normalized.split(" ")
        .filter { it.length >= 4 }
        .distinct()
        .take(16)
    return if (keywords.isEmpty()) original else keywords.joinToString(" ")
}
