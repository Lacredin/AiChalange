package com.example.aiadventchalengetestllmapi.embedding

object EmbeddingGeneratorStub {
    fun createEmbedding(input: String): Result<List<Double>> {
        if (input.isBlank()) {
            return Result.failure(IllegalArgumentException("Пустой текст для эмбеддинга"))
        }
        return Result.failure(IllegalStateException("Создание эмбеддингов в разработке"))
    }
}
