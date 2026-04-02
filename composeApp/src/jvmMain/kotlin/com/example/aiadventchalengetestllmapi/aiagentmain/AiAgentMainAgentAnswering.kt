package com.example.aiadventchalengetestllmapi.aiagentmain

internal data class AgentAnswerPromptInput(
    val userRequest: String,
    val projectFolderPath: String,
    val planningReason: String,
    val mcpContext: String,
    val ragContext: String
)

internal object AgentAnswerPromptFactory {
    fun create(input: AgentAnswerPromptInput): String = buildString {
        appendLine("Ты отвечаешь пользователю по команде /help в режиме Агент.")
        appendLine("Используй только переданный контекст MCP и RAG. Не выдумывай факты.")
        appendLine("Запрещено использовать собственные знания модели, если их нет в MCP/RAG контексте.")
        appendLine("Если контекста недостаточно, ответь строго: Нет контекста для выполнения запроса. Переформулируйте.")
        appendLine("Ответ должен быть кратким, по делу, на русском языке.")
        appendLine()
        appendLine("КРИТИЧЕСКИЕ ПРАВИЛА:")
        appendLine("1) MCP и RAG равнозначны: используй оба источника как единый контекст.")
        appendLine("2) RAG рассматривай как справочник: опирайся на него дословно и по смыслу, без добавления внешних знаний.")
        appendLine("3) Если в MCP контексте есть блок '=== КРИТИЧЕСКИЙ РЕЗУЛЬТАТ ИНСТРУМЕНТА ===', считай его приоритетным фактом от инструмента.")
        appendLine("4) Фразу 'Ответ, полученный от инструмента ... равен:' трактуй как прямое значение результата вызова инструмента.")
        appendLine("5) Не подменяй и не нормализуй это значение (например branch name, id, path, commit hash).")
        appendLine("6) Если вопрос просит одно конкретное значение (например текущая ветка), верни это значение в первой строке ответа.")
        appendLine("7) Если в MCP и RAG есть конфликтующие факты, явно укажи конфликт и перечисли оба факта без домыслов.")
        appendLine("8) Не добавляй предположения и альтернативы, если контекст уже содержит точный ответ.")
        appendLine("9) Если по MCP/RAG нельзя надежно ответить, верни строго: Нет контекста для выполнения запроса. Переформулируйте.")
        appendLine()
        appendLine("Запрос пользователя:")
        appendLine(input.userRequest)
        appendLine()
        appendLine("Папка проекта:")
        appendLine(input.projectFolderPath)
        appendLine()
        appendLine("Причина выбора стратегии:")
        appendLine(input.planningReason)
        appendLine()
        appendLine("MCP контекст:")
        appendLine(input.mcpContext.ifBlank { "нет" })
        appendLine()
        appendLine("RAG контекст:")
        appendLine(input.ragContext.ifBlank { "нет" })
    }.trim()
}
