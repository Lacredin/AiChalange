package com.example.aiadventchalengetestllmapi.aiagentmain

internal object MultiAgentPromptFactory {
    fun orchestratorPlanningPrompt(
        userRequest: String,
        globalUserRequest: MultiAgentGlobalUserRequest?,
        projectFolderPath: String,
        subagents: List<MultiAgentSubagentDefinition>,
        conversationContext: String,
        pendingQuestion: String?,
        isContinuation: Boolean
    ): String = buildString {
        appendLine("Ты оркестратор мультиагентной системы.")
        appendLine("Режим: ${if (isContinuation) "продолжение текущего run" else "новый run"}")
        appendLine("Проанализируй задачу и выбери одно действие: DIRECT_ANSWER | DELEGATE | NEED_CLARIFICATION | IMPOSSIBLE.")
        appendLine()
        appendLine("Верни только JSON по контракту (без markdown):")
        appendLine(
            """{"action":"DIRECT_ANSWER|DELEGATE|NEED_CLARIFICATION|IMPOSSIBLE","reason":"...","direct_answer":"...","clarification_question":"...","impossible_reason":"...","global_user_request":{"objective":"...","constraints":["..."],"clarifications":[{"question":"...","answer":"..."}],"assumptions":["..."],"task_messages":["..."],"clarification_messages":["..."],"agent_questions":["..."]},"execution_plan":{"plan_steps":[{"index":1,"title":"...","assignee_key":"...","task_input":"..."}],"tool_plan":{"requires_tools":true,"tools":[{"tool_kind":"RAG_QUERY|MCP_CALL","tool_scope":"SINGLE_TARGET|MULTI_TARGET","reason":"...","params":{"...":"..."},"step_index":1}],"fallback_policy":"DEGRADE|FAIL"}},"tooling_notes":["..."]}"""
        )
        appendLine("Правила:")
        appendLine("1) Если action != DELEGATE, execution_plan.plan_steps должен быть [].")
        appendLine("2) Для DELEGATE каждый шаг должен быть атомарным и исполнимым одним вызовом инструмента.")
        appendLine("3) Для tool_scope=SINGLE_TARGET нельзя объединять несколько целей в один шаг. Разворачивай шаги по одной цели.")
        appendLine("4) Для tool_scope=MULTI_TARGET допустим один агрегированный шаг.")
        appendLine("5) Добавь финальный шаг валидации результата по всем целям.")
        appendLine("6) Явно указывай tool_scope для каждого инструмента в tool_plan.tools.")
        appendLine("7) Если данных недостаточно, используй NEED_CLARIFICATION.")
        appendLine("8) Если задача невыполнима в рамках условий, используй IMPOSSIBLE.")
        appendLine("9) Указывай fallback_policy явно: DEGRADE или FAIL.")
        appendLine()
        appendLine("Папка проекта: $projectFolderPath")
        appendLine()
        appendLine("Незакрытый вопрос оркестратора:")
        appendLine(pendingQuestion?.ifBlank { "(нет)" } ?: "(нет)")
        appendLine()
        appendLine("История текущего run:")
        appendLine(conversationContext.ifBlank { "(история пустая)" })
        appendLine()
        appendLine("Enabled субагенты:")
        if (subagents.isEmpty()) {
            appendLine("- нет")
        } else {
            subagents.forEach { appendLine("- ${it.key}: ${it.description}") }
        }
        appendLine()
        appendLine("Структурированный global_user_request:")
        appendLine(formatGlobalUserRequestJson(globalUserRequest, fallbackRequest = userRequest))
        appendLine()
        appendLine("Сырой последний пользовательский ввод:")
        append(userRequest)
    }.trim()

    fun orchestratorReplanPrompt(
        userRequest: String,
        globalUserRequest: MultiAgentGlobalUserRequest?,
        currentPlan: MultiAgentPlanningDecision,
        availableTools: List<String>,
        unavailableTools: List<String>
    ): String = buildString {
        appendLine("Ты оркестратор мультиагентной системы.")
        appendLine("Нужен replanning: часть инструментов недоступна после preflight.")
        appendLine("Верни JSON по тому же контракту, что и в planning (global_user_request + execution_plan + tooling_notes).")
        appendLine("Сохраняй правила атомарности шагов и разворачивания single-target по целям.")
        appendLine()
        appendLine("Исходный запрос пользователя:")
        appendLine(userRequest)
        appendLine()
        appendLine("Структурированный global_user_request:")
        appendLine(formatGlobalUserRequestJson(globalUserRequest, fallbackRequest = userRequest))
        appendLine()
        appendLine("Текущий план (до replanning):")
        appendLine(currentPlan.toString())
        appendLine()
        appendLine("Доступные инструменты:")
        if (availableTools.isEmpty()) appendLine("- нет") else availableTools.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Недоступные инструменты:")
        if (unavailableTools.isEmpty()) appendLine("- нет") else unavailableTools.forEach { appendLine("- $it") }
    }.trim()

    fun subagentTaskPrompt(
        userRequest: String,
        step: MultiAgentPlanStep,
        reworkInstruction: String?,
        previousOutput: String?,
        sharedContext: String?
    ): String = buildString {
        appendLine("Задача от оркестратора.")
        appendLine("Глобальный запрос пользователя: $userRequest")
        appendLine("Шаг #${step.index}: ${step.title}")
        appendLine("Назначенный агент: ${step.assigneeKey}")
        appendLine()
        appendLine("Инструкция шага:")
        appendLine(step.taskInput)
        if (!reworkInstruction.isNullOrBlank()) {
            appendLine()
            appendLine("Инструкция на доработку:")
            appendLine(reworkInstruction)
        }
        if (!previousOutput.isNullOrBlank()) {
            appendLine()
            appendLine("Предыдущий ответ этого шага:")
            appendLine(previousOutput)
        }
        if (!sharedContext.isNullOrBlank()) {
            appendLine()
            appendLine("Контекст предыдущих шагов:")
            appendLine(sharedContext)
        }
        appendLine()
        append("Дай полный результат по шагу на русском языке.")
    }.trim()

    fun validationPrompt(
        userRequest: String,
        planSteps: List<MultiAgentPlanStep>,
        stepOutputs: List<MultiAgentStepExecution>
    ): String = buildString {
        appendLine("Ты валидатор результата мультиагентного выполнения.")
        appendLine("Оцени полноту, непротиворечивость и выполнимость итогового ответа.")
        appendLine("Верни только JSON без markdown по контракту:")
        appendLine(
            """{"outcome":"COMPLETE|REWORK|NEED_CLARIFICATION|IMPOSSIBLE","final_answer":"...","rework_instruction":"...","clarification_question":"...","impossible_reason":"...","tool_call_ids":[1,2],"rag_evidence":["..."]}"""
        )
        appendLine("Правила:")
        appendLine("1) COMPLETE: final_answer обязателен.")
        appendLine("2) REWORK: rework_instruction обязателен.")
        appendLine("3) NEED_CLARIFICATION: clarification_question обязателен.")
        appendLine("4) IMPOSSIBLE: impossible_reason обязателен.")
        appendLine("5) Если в задаче использовались инструменты, для COMPLETE укажи tool_call_ids и/или rag_evidence.")
        appendLine()
        appendLine("Запрос пользователя:")
        appendLine(userRequest)
        appendLine()
        appendLine("План:")
        if (planSteps.isEmpty()) {
            appendLine("- пустой")
        } else {
            planSteps.forEach { step ->
                appendLine("${step.index}. [${step.assigneeKey}] ${step.title}")
                appendLine("task_input: ${step.taskInput}")
            }
        }
        appendLine()
        appendLine("Результаты шагов:")
        if (stepOutputs.isEmpty()) {
            appendLine("- пусто")
        } else {
            stepOutputs.forEach { output ->
                appendLine("STEP #${output.step.index} (${output.step.assigneeKey}) status=${output.status}")
                val validToolRefs = output.toolCallRefs.filter { it > 0L }
                if (validToolRefs.isNotEmpty()) appendLine("tool_call_refs=${validToolRefs.joinToString(",")}")
                appendLine(output.output)
                appendLine()
            }
        }
        appendLine("Критические итоговые данные (используй как приоритетный источник для final_answer):")
        if (stepOutputs.isEmpty()) {
            appendLine("- нет итоговых данных")
        } else {
            stepOutputs.forEach { output ->
                appendLine("STEP #${output.step.index} final_data:")
                val highlights = extractCriticalStepHighlights(output.output)
                if (highlights.isEmpty()) {
                    appendLine("- (не выделено)")
                } else {
                    highlights.forEach { line -> appendLine("- $line") }
                }
            }
        }
    }.trim()

    fun mcpToolSelectionPrompt(
        userRequest: String,
        step: MultiAgentPlanStep?,
        toolReason: String,
        currentToolParamsJson: String,
        mcpToolsCatalog: String,
        conversationContext: String
    ): String = buildString {
        appendLine("Ты субагент выбора MCP инструмента и подготовки параметров вызова.")
        appendLine("Верни только JSON без markdown по контракту:")
        appendLine(
            """{"action":"MCP_CALL|NEED_CLARIFICATION|IMPOSSIBLE","reason":"...","mcp_call":{"toolName":"...","endpoint":"...","arguments":{},"output_filter":"optional filter rule"},"clarification_questions":["..."],"impossible_reason":"..."}"""
        )
        appendLine("Правила:")
        appendLine("1) Если выбираешь MCP_CALL, поле mcp_call.toolName обязательно и непустое.")
        appendLine("2) arguments должны соответствовать input_schema выбранного tool.")
        appendLine("3) Если данных не хватает для обязательных аргументов, верни NEED_CLARIFICATION.")
        appendLine("4) Если ни один доступный tool не подходит, верни IMPOSSIBLE.")
        appendLine("5) Не придумывай инструменты, которых нет в каталоге.")
        appendLine("6) Приоритет автономности: сначала попробуй best-effort через общие инструменты из каталога (например, explore_directory, git_list_files, project_read_file).")
        appendLine("7) Если узкоспециализированного инструмента нет, выбери ближайший общий инструмент и сформируй MCP_CALL вместо NEED_CLARIFICATION.")
        appendLine("8) NEED_CLARIFICATION используй только если даже общий best-effort невозможен из-за отсутствия обязательных входных данных.")
        appendLine()
        appendLine("Запрос пользователя:")
        appendLine(userRequest)
        appendLine()
        appendLine("Шаг оркестратора:")
        if (step == null) {
            appendLine("- step not provided")
        } else {
            appendLine("index=${step.index}")
            appendLine("title=${step.title}")
            appendLine("assignee_key=${step.assigneeKey}")
            appendLine("task_input=${step.taskInput}")
        }
        appendLine()
        appendLine("Причина вызова инструмента (от оркестратора):")
        appendLine(toolReason.ifBlank { "(пусто)" })
        appendLine()
        appendLine("Текущие params из tool_plan:")
        appendLine(currentToolParamsJson.ifBlank { "{}" })
        appendLine()
        appendLine("Каталог MCP инструментов (фактическая доступность):")
        appendLine(mcpToolsCatalog.ifBlank { "(каталог пуст)" })
        appendLine()
        appendLine("Контекст диалога:")
        appendLine(conversationContext.ifBlank { "(пусто)" })
    }.trim()

    fun mcpChunkFilterPrompt(
        userRequest: String,
        step: MultiAgentPlanStep,
        outputFilter: String,
        chunkIndex: Int,
        totalChunks: Int,
        chunkText: String
    ): String = buildString {
        appendLine("Ты обрабатываешь chunk результата MCP-инструмента.")
        appendLine("Верни только данные, соответствующие фильтру.")
        appendLine("Если подходящих данных нет, верни ровно: NO_MATCH")
        appendLine()
        appendLine("Запрос пользователя: $userRequest")
        appendLine("Шаг #${step.index}: ${step.title}")
        appendLine("Фильтр: $outputFilter")
        appendLine("Chunk: $chunkIndex/$totalChunks")
        appendLine()
        appendLine("Данные chunk:")
        appendLine(chunkText)
    }.trim()

    fun mcpFilteredMergePrompt(
        userRequest: String,
        step: MultiAgentPlanStep,
        outputFilter: String,
        filteredChunks: List<String>
    ): String = buildString {
        appendLine("Объедини уже отфильтрованные фрагменты и убери дубли.")
        appendLine("Верни только итоговые отфильтрованные данные без пояснений.")
        appendLine()
        appendLine("Запрос пользователя: $userRequest")
        appendLine("Шаг #${step.index}: ${step.title}")
        appendLine("Фильтр: $outputFilter")
        appendLine()
        filteredChunks.forEachIndexed { index, text ->
            appendLine("=== FILTERED_PART_${index + 1} ===")
            appendLine(text)
        }
    }.trim()

    private fun formatGlobalUserRequestJson(
        globalUserRequest: MultiAgentGlobalUserRequest?,
        fallbackRequest: String
    ): String {
        val request = globalUserRequest ?: MultiAgentGlobalUserRequest(objective = fallbackRequest)
        val clarifications = request.clarifications.joinToString(",") { pair ->
            """{"question":"${escapeJson(pair.question)}","answer":"${escapeJson(pair.answer)}"}"""
        }
        val constraints = request.constraints.joinToString(",") { "\"${escapeJson(it)}\"" }
        val assumptions = request.assumptions.joinToString(",") { "\"${escapeJson(it)}\"" }
        val taskMessages = request.taskMessages.joinToString(",") { "\"${escapeJson(it)}\"" }
        val clarificationMessages = request.clarificationMessages.joinToString(",") { "\"${escapeJson(it)}\"" }
        val agentQuestions = request.agentQuestions.joinToString(",") { "\"${escapeJson(it)}\"" }
        return """{"objective":"${escapeJson(request.objective)}","constraints":[$constraints],"clarifications":[$clarifications],"assumptions":[$assumptions],"task_messages":[$taskMessages],"clarification_messages":[$clarificationMessages],"agent_questions":[$agentQuestions]}"""
    }

    private fun escapeJson(raw: String): String {
        return raw
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    private fun extractCriticalStepHighlights(output: String): List<String> {
        val lines = output.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { it.equals("TOOL_CONTEXT:", ignoreCase = true) }
            .filterNot { it.startsWith("summary:", ignoreCase = true) }
            .filterNot { it.startsWith("diagnostics:", ignoreCase = true) }
            .filterNot { it.startsWith("tool_call_refs:", ignoreCase = true) }
            .filterNot { it.startsWith("- TOOL ", ignoreCase = true) }

        if (lines.isEmpty()) return emptyList()
        return lines.take(4)
    }
}
