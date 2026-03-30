package com.example.aiadventchalengetestllmapi.aiagentmain

internal data class AgentCommandSuggestion(
    val command: String,
    val hint: String,
    val description: String
)

internal sealed interface AgentSlashCommand {
    data class Help(val question: String?) : AgentSlashCommand
}

internal sealed interface AgentSlashParseResult {
    data object NotSlash : AgentSlashParseResult
    data class Parsed(val command: AgentSlashCommand) : AgentSlashParseResult
    data class Error(val message: String) : AgentSlashParseResult
}

internal object AgentSlashCommandParser {
    private val commandPattern = Regex("""^/([A-Za-z][\w-]*)(?:\s+(.*))?$""")
    private val anotherCommandPattern = Regex("""(?:^|\s)/[A-Za-z][\w-]*""")

    fun parse(input: String): AgentSlashParseResult {
        val trimmed = input.trim()
        if (!trimmed.startsWith("/")) return AgentSlashParseResult.NotSlash
        if (!input.startsWith("/")) {
            return AgentSlashParseResult.Error("Slash-команда должна начинаться с первого символа строки.")
        }

        val match = commandPattern.matchEntire(trimmed)
            ?: return AgentSlashParseResult.Error("Некорректный формат команды. Используйте: /help [вопрос].")

        val command = match.groupValues.getOrNull(1)?.lowercase().orEmpty()
        val rawArgs = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (rawArgs.isNotBlank() && anotherCommandPattern.containsMatchIn(rawArgs)) {
            return AgentSlashParseResult.Error("В одном сообщении допускается только одна slash-команда.")
        }

        return when (command) {
            "help" -> AgentSlashParseResult.Parsed(
                AgentSlashCommand.Help(question = rawArgs.ifBlank { null })
            )

            else -> AgentSlashParseResult.Error("Неизвестная команда: /$command. Доступно: /help.")
        }
    }
}

internal val agentCommandSuggestions = listOf(
    AgentCommandSuggestion(
        command = "/help",
        hint = "/help [вопрос о проекте]",
        description = "Задать вопрос о проекте по документации, MCP и RAG"
    )
)
