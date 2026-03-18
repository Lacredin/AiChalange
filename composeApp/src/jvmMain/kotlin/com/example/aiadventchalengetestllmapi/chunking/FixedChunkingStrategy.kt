package com.example.aiadventchalengetestllmapi.chunking

internal fun chunkFixed(text: String, size: Int = 500): List<String> {
    if (text.isBlank()) return emptyList()
    return text.trim().chunked(size)
}
