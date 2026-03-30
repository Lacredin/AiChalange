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
        appendLine("Используй только переданный контекст. Не выдумывай факты.")
        appendLine("Если контекста недостаточно, ответь: Нет контекста для выполнения запроса. Переформулируйте.")
        appendLine("Пиши кратко и по делу на русском языке.")
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
