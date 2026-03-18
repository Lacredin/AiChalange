package com.example.aiadventchalengetestllmapi.chunking

internal fun semanticKeywords(text: String): Set<String> =
    text.lowercase()
        .split(Regex("[^a-zа-я0-9]+"))
        .filter { it.length > 2 }
        .toSet()

internal fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val intersection = a.intersect(b).size.toDouble()
    val union = a.union(b).size.toDouble()
    if (union == 0.0) return 0.0
    return intersection / union
}

private data class SemanticChunk(
    val text: String,
    val keywords: Set<String>
)

internal fun chunkSemantic(
    text: String,
    targetSize: Int = 500,
    similarityThreshold: Double = 0.2
): List<String> {
    if (text.isBlank()) return emptyList()
    val sentences = text
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return emptyList()

    val minPreferredSize = (targetSize * 0.85).toInt().coerceAtLeast(600)
    val chunks = mutableListOf<SemanticChunk>()
    var currentChunk = ""
    var currentKeywords = emptySet<String>()

    fun flush() {
        val textValue = currentChunk.trim()
        if (textValue.isNotEmpty()) {
            chunks += SemanticChunk(text = textValue, keywords = semanticKeywords(textValue))
        }
        currentChunk = ""
        currentKeywords = emptySet()
    }

    sentences.forEach { sentence ->
        if (sentence.length > targetSize) {
            flush()
            sentence.chunked(targetSize).forEach { part ->
                val partText = part.trim()
                if (partText.isNotEmpty()) {
                    chunks += SemanticChunk(text = partText, keywords = semanticKeywords(partText))
                }
            }
            return@forEach
        }

        if (currentChunk.isEmpty()) {
            currentChunk = sentence
            currentKeywords = semanticKeywords(sentence)
            return@forEach
        }

        val sentenceKeywords = semanticKeywords(sentence)
        val combinedSize = currentChunk.length + 1 + sentence.length
        if (combinedSize <= targetSize) {
            val closeByMeaning = jaccard(currentKeywords, sentenceKeywords) >= similarityThreshold
            val shouldAttach = closeByMeaning || currentChunk.length < minPreferredSize
            if (shouldAttach) {
                currentChunk += " $sentence"
                currentKeywords = semanticKeywords(currentChunk)
            } else {
                flush()
                currentChunk = sentence
                currentKeywords = sentenceKeywords
            }
        } else {
            flush()
            currentChunk = sentence
            currentKeywords = sentenceKeywords
        }
    }
    flush()

    if (chunks.size <= 1) return chunks.map { it.text }

    // Secondary packing pass: maximize chunk size near target, while still preferring semantic proximity.
    val packed = mutableListOf<SemanticChunk>()
    var current = chunks.first()
    chunks.drop(1).forEach { next ->
        val combinedSize = current.text.length + 1 + next.text.length
        if (combinedSize <= targetSize) {
            val closeByMeaning = jaccard(current.keywords, next.keywords) >= similarityThreshold
            val shouldAttach = closeByMeaning || current.text.length < minPreferredSize
            if (shouldAttach) {
                val combinedText = "${current.text} ${next.text}".trim()
                current = SemanticChunk(combinedText, semanticKeywords(combinedText))
            } else {
                packed += current
                current = next
            }
        } else {
            packed += current
            current = next
        }
    }
    packed += current

    return packed.map { it.text }
}
