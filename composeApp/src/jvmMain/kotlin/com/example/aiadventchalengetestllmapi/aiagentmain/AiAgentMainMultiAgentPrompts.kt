package com.example.aiadventchalengetestllmapi.aiagentmain

internal object MultiAgentPromptFactory {
    fun orchestratorPlanningPrompt(
        userRequest: String,
        projectFolderPath: String,
        subagents: List<MultiAgentSubagentDefinition>
    ): String = buildString {
        appendLine("Ты оркестратор мультиагентной системы.")
        appendLine("Проанализируй запрос и выбери одно действие:")
        appendLine("- DIRECT_ANSWER")
        appendLine("- DELEGATE")
        appendLine("- NEED_CLARIFICATION")
        appendLine("- IMPOSSIBLE")
        appendLine()
        appendLine("Верни только JSON без markdown по контракту:")
        appendLine(
            """{"action":"DIRECT_ANSWER|DELEGATE|NEED_CLARIFICATION|IMPOSSIBLE","reason":"...","direct_answer":"...","clarification_question":"...","impossible_reason":"...","plan_steps":[{"title":"...","assignee_key":"...","task_input":"..."}]}"""
        )
        appendLine("Правила:")
        appendLine("1) Если action != DELEGATE, plan_steps должен быть []")
        appendLine("2) При DELEGATE составь пошаговый план и назначь assignee_key только из enabled субагентов.")
        appendLine("3) Если данных не хватает, используй NEED_CLARIFICATION.")
        appendLine("4) Если задача невыполнима в заданных рамках, используй IMPOSSIBLE.")
        appendLine()
        appendLine("Папка проекта: $projectFolderPath")
        appendLine()
        appendLine("Enabled субагенты:")
        if (subagents.isEmpty()) {
            appendLine("- нет")
        } else {
            subagents.forEach {
                appendLine("- ${it.key}: ${it.description}")
            }
        }
        appendLine()
        appendLine("Запрос пользователя:")
        append(userRequest)
    }.trim()

    fun subagentTaskPrompt(
        userRequest: String,
        step: MultiAgentPlanStep,
        reworkInstruction: String?,
        previousOutput: String?
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
            """{"outcome":"COMPLETE|REWORK|NEED_CLARIFICATION|IMPOSSIBLE","final_answer":"...","rework_instruction":"...","clarification_question":"...","impossible_reason":"..."}"""
        )
        appendLine("Правила:")
        appendLine("1) COMPLETE: final_answer обязателен.")
        appendLine("2) REWORK: rework_instruction обязателен.")
        appendLine("3) NEED_CLARIFICATION: clarification_question обязателен.")
        appendLine("4) IMPOSSIBLE: impossible_reason обязателен.")
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
                appendLine(output.output)
                appendLine()
            }
        }
    }.trim()
}
