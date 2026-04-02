package com.example.aiadventchalengetestllmapi.aiagentmain

internal enum class MultiAgentConversationRole {
    USER,
    AGENT
}

internal data class MultiAgentConversationMessage(
    val role: MultiAgentConversationRole,
    val text: String
)

internal object MultiAgentUserRequestExtractor {
    fun extract(
        conversation: List<MultiAgentConversationMessage>,
        fallbackObjective: String
    ): MultiAgentGlobalUserRequest {
        val taskMessages = mutableListOf<String>()
        val clarificationMessages = mutableListOf<String>()
        val agentQuestions = mutableListOf<String>()
        val clarifications = mutableListOf<MultiAgentClarificationPair>()
        var pendingQuestion: String? = null

        conversation.forEach { message ->
            val text = message.text.trim()
            if (text.isBlank()) return@forEach
            when (message.role) {
                MultiAgentConversationRole.AGENT -> {
                    if (looksLikeQuestion(text)) {
                        pendingQuestion = text
                        agentQuestions += text
                    }
                }

                MultiAgentConversationRole.USER -> {
                    val question = pendingQuestion
                    if (!question.isNullOrBlank()) {
                        clarificationMessages += text
                        clarifications += MultiAgentClarificationPair(question = question, answer = text)
                        pendingQuestion = null
                    } else {
                        taskMessages += text
                    }
                }
            }
        }

        val objective = taskMessages.lastOrNull()
            ?: fallbackObjective.trim()

        return MultiAgentGlobalUserRequest(
            objective = objective.ifBlank { fallbackObjective.trim() },
            constraints = extractConstraints(taskMessages + clarificationMessages),
            clarifications = clarifications,
            assumptions = buildAssumptions(taskMessages, clarifications, fallbackObjective),
            taskMessages = taskMessages,
            clarificationMessages = clarificationMessages,
            agentQuestions = agentQuestions
        )
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.endsWith("?") || trimmed.contains("уточни", ignoreCase = true)
    }

    private fun extractConstraints(messages: List<String>): List<String> {
        if (messages.isEmpty()) return emptyList()
        val constraintHints = listOf(
            "не ",
            "нельзя",
            "только",
            "обязательно",
            "сохрани",
            "огранич",
            "без "
        )
        return messages
            .asSequence()
            .flatMap { it.lines().asSequence() }
            .map { it.trim() }
            .filter { line -> constraintHints.any { hint -> line.contains(hint, ignoreCase = true) } }
            .distinct()
            .take(6)
            .toList()
    }

    private fun buildAssumptions(
        taskMessages: List<String>,
        clarifications: List<MultiAgentClarificationPair>,
        fallbackObjective: String
    ): List<String> {
        if (taskMessages.isEmpty() && clarifications.isEmpty()) {
            return listOf("Цель взята из последнего сообщения: $fallbackObjective")
        }
        if (taskMessages.size == 1 && clarifications.isEmpty()) return emptyList()
        return listOf("Приоритет отдан последнему task-сообщению как основной цели.")
    }
}
