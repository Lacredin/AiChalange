package com.example.aiadventchalengetestllmapi.chunking

private data class StructuredBlock(
    val type: String,
    val text: String
)

private fun isHeadingLineForStructured(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("#")) return true
    if (trimmed.matches(Regex("^\\d+(\\.\\d+)*[.)]?\\s+.+$"))) return true
    return false
}

private fun parseStructuredBlocks(text: String): List<StructuredBlock> {
    val lines = text.lines()
    if (lines.isEmpty()) return emptyList()

    val blocks = mutableListOf<StructuredBlock>()
    val paragraph = StringBuilder()
    var inCodeFence = false
    val codeFence = StringBuilder()

    fun flushParagraph() {
        val content = paragraph.toString().trim()
        if (content.isNotEmpty()) {
            blocks += StructuredBlock(type = "paragraph", text = content)
        }
        paragraph.clear()
    }

    fun flushCodeFence() {
        val content = codeFence.toString().trim()
        if (content.isNotEmpty()) {
            blocks += StructuredBlock(type = "code", text = content)
        }
        codeFence.clear()
    }

    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        val isHeading = isHeadingLineForStructured(trimmed)
        val isList = trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") ||
            trimmed.matches(Regex("^\\d+[.)]\\s+.+$"))
        val isFence = trimmed.startsWith("```")

        if (isFence) {
            flushParagraph()
            inCodeFence = !inCodeFence
            codeFence.appendLine(line)
            if (!inCodeFence) {
                flushCodeFence()
            }
            return@forEach
        }

        if (inCodeFence) {
            codeFence.appendLine(line)
            return@forEach
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            return@forEach
        }

        if (isHeading) {
            flushParagraph()
            blocks += StructuredBlock(type = "heading", text = trimmed)
            return@forEach
        }

        if (isList) {
            flushParagraph()
            blocks += StructuredBlock(type = "list", text = trimmed)
            return@forEach
        }

        if (paragraph.isNotEmpty()) paragraph.appendLine()
        paragraph.append(trimmed)
    }

    flushParagraph()
    flushCodeFence()
    return blocks
}

private fun splitLongTextBySentences(text: String, hardLimit: Int): List<String> {
    if (text.length <= hardLimit) return listOf(text)
    val sentences = text
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return text.chunked(hardLimit)

    val parts = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        val value = current.toString().trim()
        if (value.isNotEmpty()) parts += value
        current.clear()
    }
    sentences.forEach { sentence ->
        if (sentence.length > hardLimit) {
            flush()
            parts += sentence.chunked(hardLimit)
            return@forEach
        }
        if (current.isEmpty()) {
            current.append(sentence)
        } else if (current.length + 1 + sentence.length <= hardLimit) {
            current.append(" ").append(sentence)
        } else {
            flush()
            current.append(sentence)
        }
    }
    flush()
    return parts
}

internal fun chunkStructured(
    text: String,
    targetSize: Int = 10000,
    softLimit: Int = 8500,
    overlapChars: Int = 220
): List<String> {
    if (text.isBlank()) return emptyList()
    val hardLimit = targetSize
    val blocks = parseStructuredBlocks(text).flatMap { block ->
        splitLongTextBySentences(block.text, hardLimit).map { part ->
            StructuredBlock(type = block.type, text = part)
        }
    }
    if (blocks.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    var currentKeywords = emptySet<String>()

    fun chunkTailForOverlap(value: String): String {
        val paragraphs = value
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val tail = paragraphs.lastOrNull().orEmpty()
        if (tail.length <= overlapChars) return tail
        return tail.takeLast(overlapChars)
    }

    fun flush() {
        val content = current.toString().trim()
        if (content.isNotEmpty()) {
            chunks += content
        }
        current.clear()
        currentKeywords = emptySet()
    }

    blocks.forEach { block ->
        val blockText = block.text.trim()
        if (blockText.isEmpty()) return@forEach

        if (current.isEmpty()) {
            current.append(blockText)
            currentKeywords = semanticKeywords(current.toString())
            return@forEach
        }

        val separator = if (block.type == "heading") "\n\n" else "\n"
        val candidateSize = current.length + separator.length + blockText.length
        val blockKeywords = semanticKeywords(blockText)
        val closeByMeaning = jaccard(currentKeywords, blockKeywords) >= 0.12

        val shouldAttach = when {
            candidateSize > hardLimit -> false
            current.length < softLimit -> true
            else -> closeByMeaning
        }

        if (shouldAttach) {
            current.append(separator).append(blockText)
            currentKeywords = semanticKeywords(current.toString())
        } else {
            val previousChunk = current.toString()
            flush()
            val overlap = chunkTailForOverlap(previousChunk)
            if (overlap.isNotBlank()) {
                current.append(overlap).append("\n\n")
            }
            current.append(blockText)
            currentKeywords = semanticKeywords(current.toString())
        }
    }

    flush()
    return chunks
}
