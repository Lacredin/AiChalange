package com.example.aiadventchalengetestllmapi.aiagentmain

import java.io.File
import kotlin.math.max

private const val PR_RAG_DOC_MAX_FILES = 80
private const val PR_RAG_DOC_MAX_CHARS_PER_FILE = 45_000
private const val PR_RAG_CHUNK_SIZE = 1_400
private const val PR_RAG_MAX_CHUNKS = 14

internal data class AgentPrLexicalRagChunk(
    val sourceType: String,
    val sourcePath: String,
    val score: Double,
    val text: String
)

internal data class AgentPrLexicalRagPayload(
    val promptContext: String,
    val retrievalInfo: String,
    val selectedChunks: List<AgentPrLexicalRagChunk>,
    val fallbackUsed: Boolean
)

internal fun buildAgentPrLexicalRagPayload(
    projectFolderPath: String,
    gitContext: AgentGitPrContext,
    queries: List<String>
): AgentPrLexicalRagPayload {
    val queryText = queries.joinToString("\n") + "\n" + gitContext.title + "\n" + gitContext.body
    val queryTokens = tokenizeForPrRag(queryText)

    val docChunks = loadDocsChunks(projectFolderPath)
    val changedFileChunks = loadChangedFileChunks(gitContext)
    val allChunks = docChunks + changedFileChunks

    val ranked = allChunks
        .map { candidate ->
            val score = lexicalScore(candidate.text, queryTokens)
            candidate.copy(score = score)
        }
        .filter { it.score > 0.0 }
        .sortedByDescending { it.score }

    val selected = ranked.take(PR_RAG_MAX_CHUNKS)
    val fallbackUsed = selected.isNotEmpty()
    val info = buildString {
        append("query_tokens=${queryTokens.size}")
        append(" | candidates=${allChunks.size}")
        append(" | selected=${selected.size}")
        append(" | source_docs=${docChunks.size}")
        append(" | source_changed_files=${changedFileChunks.size}")
    }

    val context = if (selected.isEmpty()) {
        "Lexical PR-RAG context is empty."
    } else {
        selected.mapIndexed { index, chunk ->
            buildString {
                appendLine("[L${index + 1}] source=${chunk.sourceType}:${chunk.sourcePath}")
                appendLine("score=${"%.4f".format(chunk.score)}")
                append(chunk.text.take(1_600))
            }
        }.joinToString("\n\n")
    }

    return AgentPrLexicalRagPayload(
        promptContext = buildString {
            appendLine("PR lexical RAG context (docs + changed files):")
            appendLine("retrieval_info: $info")
            appendLine()
            append(context)
        }.trim(),
        retrievalInfo = info,
        selectedChunks = selected,
        fallbackUsed = fallbackUsed
    )
}

private fun loadDocsChunks(projectFolderPath: String): List<AgentPrLexicalRagChunk> {
    val docsRoot = File(projectFolderPath, "Документация")
    if (!docsRoot.exists() || !docsRoot.isDirectory) return emptyList()

    val files = docsRoot.walkTopDown()
        .filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
        .take(PR_RAG_DOC_MAX_FILES)
        .toList()

    return files.flatMap { file ->
        val text = runCatching { file.readText() }.getOrDefault("").take(PR_RAG_DOC_MAX_CHARS_PER_FILE)
        val chunks = chunkTextForPrRag(text, PR_RAG_CHUNK_SIZE)
        chunks.mapIndexed { idx, chunk ->
            AgentPrLexicalRagChunk(
                sourceType = "docs",
                sourcePath = "${file.name}#${idx + 1}",
                score = 0.0,
                text = chunk
            )
        }
    }
}

private fun loadChangedFileChunks(gitContext: AgentGitPrContext): List<AgentPrLexicalRagChunk> {
    return gitContext.files.flatMap { file ->
        val chunks = chunkTextForPrRag(file.content, PR_RAG_CHUNK_SIZE)
        chunks.mapIndexed { idx, chunk ->
            AgentPrLexicalRagChunk(
                sourceType = "changed_file",
                sourcePath = "${file.path}#${idx + 1}",
                score = 0.0,
                text = chunk
            )
        }
    }
}

private fun chunkTextForPrRag(text: String, chunkSize: Int): List<String> {
    val normalized = text
        .replace("\r\n", "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    if (normalized.isBlank()) return emptyList()
    if (normalized.length <= chunkSize) return listOf(normalized)

    val chunks = mutableListOf<String>()
    var index = 0
    while (index < normalized.length) {
        val endExclusive = max(index + 1, minOf(normalized.length, index + chunkSize))
        chunks += normalized.substring(index, endExclusive).trim()
        index = endExclusive
    }
    return chunks
}

private fun lexicalScore(text: String, queryTokens: Set<String>): Double {
    if (queryTokens.isEmpty()) return 0.0
    val chunkTokens = tokenizeForPrRag(text)
    if (chunkTokens.isEmpty()) return 0.0
    val overlap = queryTokens.intersect(chunkTokens).size
    val coverage = overlap.toDouble() / queryTokens.size.toDouble()
    val density = overlap.toDouble() / chunkTokens.size.toDouble()
    return coverage * 0.75 + density * 0.25
}

private fun tokenizeForPrRag(text: String): Set<String> {
    return text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}_./-]+"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 3 }
        .toSet()
}
