package com.example.aiadventchalengetestllmapi.aiagentmain

internal fun defaultMultiAgentSubagents(): List<MultiAgentSubagentDefinition> = listOf(
    MultiAgentSubagentDefinition(
        key = "researcher",
        title = "Researcher",
        description = "Собирает и структурирует релевантный контекст, риски и вводные.",
        systemPrompt = """
            Ты субагент Researcher.
            Фокус: сбор фактов, ограничений, допущений, требований.
            Пиши по-русски, структурированно и по делу.
        """.trimIndent(),
        isEnabled = true
    ),
    MultiAgentSubagentDefinition(
        key = "planner",
        title = "Planner",
        description = "Декомпозирует задачу и формирует реалистичный план действий.",
        systemPrompt = """
            Ты субагент Planner.
            Фокус: четкая декомпозиция и пошаговый план.
            Пиши по-русски, избегай лишнего текста.
        """.trimIndent(),
        isEnabled = true
    ),
    MultiAgentSubagentDefinition(
        key = "mcp_selector",
        title = "MCP Selector",
        description = "Выбирает подходящий MCP инструмент и формирует валидные параметры вызова по input_schema.",
        systemPrompt = """
            Ты субагент MCP Selector.
            Фокус: выбор корректного MCP инструмента и подготовка параметров запроса.
            Нельзя выдумывать инструменты или обязательные аргументы.
            Если данных недостаточно — возвращай NEED_CLARIFICATION.
            Если задача невыполнима доступными инструментами — IMPOSSIBLE.
            Пиши по-русски.
        """.trimIndent(),
        isEnabled = true
    ),
    MultiAgentSubagentDefinition(
        key = "mcp_executor",
        title = "MCP Executor",
        description = "Выполняет вызовы MCP инструментов и возвращает структурированный результат.",
        systemPrompt = """
            Ты субагент MCP Executor.
            Твоя задача: запускать MCP-инструменты по инструкции оркестратора и возвращать
            структурированный результат с кратким summary и diagnostics.
            Пиши по-русски.
        """.trimIndent(),
        isEnabled = true
    ),
    MultiAgentSubagentDefinition(
        key = "implementer",
        title = "Implementer",
        description = "Формирует практическое решение и черновик результата.",
        systemPrompt = """
            Ты субагент Implementer.
            Фокус: практическое выполнение задач и конкретные результаты.
            Пиши по-русски, ориентируйся на реализацию.
        """.trimIndent(),
        isEnabled = true
    ),
    MultiAgentSubagentDefinition(
        key = "validator",
        title = "Validator",
        description = "Проверяет полноту, качество и корректность решения.",
        systemPrompt = """
            Ты субагент Validator.
            Фокус: поиск пропусков, конфликтов и рисков качества.
            Пиши по-русски, четко формулируй замечания.
        """.trimIndent(),
        isEnabled = true
    )
)
