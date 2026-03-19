package com.example.aiadventchalengetestllmapi.aiagentrag

object AiAgentRagPrompts {
    const val NO_ANSWER = "не знаю. Пожалуйста, уточните вопрос."

    const val SYSTEM =
        "Отвечай только на основе предоставленного контекста. " +
            "Если нет релевантных фрагментов, " +
            "обязан ответить строго: \"$NO_ANSWER\""

    const val EMPTY_CONTEXT = "Релевантный контекст выше порога не найден."

    fun buildUserPrompt(question: String, retrievalQuery: String, context: String): String =
        "Вопрос:\n$question\n\n" +
            "Поисковый запрос:\n$retrievalQuery\n\n" +
            "Контекст:\n$context\n\n" +
            "Правило: если нет фрагментов выше порога, ответь строго: \"$NO_ANSWER\""
}
