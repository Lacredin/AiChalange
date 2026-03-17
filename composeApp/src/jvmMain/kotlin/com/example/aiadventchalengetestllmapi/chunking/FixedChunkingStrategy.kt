package com.example.aiadventchalengetestllmapi.chunking

internal fun chunkFixed(text: String, size: Int = 10000): List<String> {
    if (text.isBlank()) return emptyList()
    return text.trim().chunked(size)
}
