package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal class MultiAgentOrchestrator(
    private val parser: MultiAgentParser = MultiAgentParser
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(
        request: MultiAgentRequest,
        callModel: suspend (MultiAgentModelCall) -> String,
        executeTool: suspend (ToolGatewayRequest) -> ToolGatewayResult,
        onEvent: (MultiAgentEvent) -> Unit,
        onPlanningReady: (MultiAgentPlanningDecision) -> Unit,
        onStepReady: (MultiAgentStepExecution) -> Unit
    ): MultiAgentRunSummary {
        val enabledSubagents = request.subagents.filter { it.isEnabled }
        if (enabledSubagents.isEmpty()) {
            return MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.FAILED,
                resolutionType = MultiAgentResolutionType.FAILED,
                finalUserMessage = "Мультиагентный режим включен, но нет активных субагентов.",
                planningDecision = null,
                steps = emptyList()
            )
        }

        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.USER,
                actorType = "orchestrator",
                actorKey = "orchestrator",
                role = "assistant",
                message = "Оркестратор анализирует задачу."
            )
        )

        val planningPrompt = MultiAgentPromptFactory.orchestratorPlanningPrompt(
            userRequest = request.userRequest,
            projectFolderPath = request.projectFolderPath,
            subagents = enabledSubagents,
            conversationContext = request.conversationContext,
            pendingQuestion = request.pendingQuestion,
            isContinuation = request.isContinuation
        )
        val planningRaw = callModel(
            MultiAgentModelCall(
                messages = listOf(DeepSeekMessage(role = "user", content = planningPrompt)),
                responseAsJson = true,
                temperature = 0.1,
                topP = 0.3,
                maxTokens = 2500
            )
        )
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.TRACE,
                actorType = "orchestrator",
                actorKey = "orchestrator",
                role = "assistant",
                message = "PLANNING_PROMPT:\n$planningPrompt\n\nPLANNING_RAW:\n$planningRaw"
            )
        )
        val planningDecision = parser.parsePlanning(planningRaw)
            ?: return MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.FAILED,
                resolutionType = MultiAgentResolutionType.FAILED,
                finalUserMessage = "Оркестратор вернул невалидный план. Повторите запрос.",
                planningDecision = null,
                steps = emptyList()
            )

        onPlanningReady(planningDecision)

        when (planningDecision.action) {
            MultiAgentDecisionType.DIRECT_ANSWER -> {
                val direct = planningDecision.directAnswer?.takeIf { it.isNotBlank() }
                    ?: buildFallbackDirectAnswer(request.userRequest, callModel, onEvent)
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.DONE,
                    resolutionType = MultiAgentResolutionType.DIRECT_ANSWER,
                    finalUserMessage = direct,
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }

            MultiAgentDecisionType.NEED_CLARIFICATION -> {
                val question = planningDecision.clarificationQuestion
                    ?: "Нужны уточнения по задаче. Уточните желаемый результат."
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.WAITING_USER,
                    resolutionType = MultiAgentResolutionType.NEED_CLARIFICATION,
                    finalUserMessage = question,
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }

            MultiAgentDecisionType.IMPOSSIBLE -> {
                val reason = planningDecision.impossibleReason
                    ?: "Не могу выполнить задачу в текущих ограничениях."
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.DONE,
                    resolutionType = MultiAgentResolutionType.IMPOSSIBLE,
                    finalUserMessage = reason,
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }

            MultiAgentDecisionType.DELEGATE -> Unit
        }

        if (planningDecision.planSteps.isEmpty()) {
            return MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.FAILED,
                resolutionType = MultiAgentResolutionType.FAILED,
                finalUserMessage = "Оркестратор выбрал делегирование, но не построил шаги.",
                planningDecision = planningDecision,
                steps = emptyList()
            )
        }

        val toolPlanPreflight = preflightToolPlan(
            toolPlan = planningDecision.toolPlan,
            executeTool = executeTool,
            onEvent = onEvent
        )
        when (toolPlanPreflight.kind) {
            PreflightResultKind.CONTINUE -> Unit
            PreflightResultKind.NEED_CLARIFICATION -> {
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.WAITING_USER,
                    resolutionType = MultiAgentResolutionType.NEED_CLARIFICATION,
                    finalUserMessage = toolPlanPreflight.message
                        ?: "Нужны уточнения для продолжения работы.",
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }

            PreflightResultKind.IMPOSSIBLE -> {
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.DONE,
                    resolutionType = MultiAgentResolutionType.IMPOSSIBLE,
                    finalUserMessage = toolPlanPreflight.message
                        ?: "Инструменты недоступны, задача не может быть выполнена.",
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }
        }

        val subagentsByKey = enabledSubagents.associateBy { it.key.lowercase() }
        val stepsState = mutableListOf<MultiAgentStepExecution>()
        val evidenceToolCallIds = mutableSetOf<Long>()

        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.USER,
                actorType = "orchestrator",
                actorKey = "orchestrator",
                role = "assistant",
                message = "План построен: ${planningDecision.planSteps.size} шаг(ов). Начинаю выполнение."
            )
        )

        planningDecision.planSteps.forEachIndexed { idx, step ->
            val subagent = subagentsByKey[step.assigneeKey.lowercase()]
            if (subagent == null) {
                val failed = MultiAgentStepExecution(
                    step = step,
                    status = MultiAgentStepStatus.failed,
                    output = "Субагент '${step.assigneeKey}' не найден среди активных."
                )
                stepsState += failed
                onStepReady(failed)
                return@forEachIndexed
            }

            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.USER,
                    actorType = "orchestrator",
                    actorKey = "orchestrator",
                    role = "assistant",
                    message = "Шаг ${idx + 1}/${planningDecision.planSteps.size}: ${step.title} (${subagent.title})."
                )
            )
            onStepReady(
                MultiAgentStepExecution(
                    step = step,
                    status = MultiAgentStepStatus.running,
                    output = ""
                )
            )

            val stepTools = toolPlanPreflight.toolPlan
                ?.tools
                .orEmpty()
                .filter { it.stepIndex == null || it.stepIndex == step.index }
            val stepToolCallRefs = mutableListOf<Long>()
            val stepToolOutputs = mutableListOf<String>()
            val isMcpExecutor = subagent.key.equals("mcp_executor", ignoreCase = true)

            stepTools.forEach { tool ->
                if (isMcpExecutor && tool.toolKind != MultiAgentToolKind.MCP_CALL) return@forEach
                val toolResult = executeTool(
                    ToolGatewayRequest(
                        toolKind = tool.toolKind,
                        paramsJson = tool.paramsJson,
                        reason = tool.reason,
                        preflight = false,
                        stepIndex = step.index
                    )
                )
                val callId = extractToolCallId(toolResult)
                if (callId != null) {
                    stepToolCallRefs += callId
                    evidenceToolCallIds += callId
                }
                val line = buildString {
                    append("TOOL ${tool.toolKind}: ")
                    if (toolResult.success) append(toolResult.normalizedOutput)
                    else append("error=${toolResult.errorCode}: ${toolResult.errorMessage}")
                }
                stepToolOutputs += line
                onEvent(
                    MultiAgentEvent(
                        channel = MultiAgentEventChannel.TRACE,
                        actorType = "tool",
                        actorKey = tool.toolKind.name.lowercase(),
                        role = "assistant",
                        message = "STEP=${step.index}\n$line"
                    )
                )
            }

            if (isMcpExecutor) {
                val mcpOutput = if (stepToolOutputs.isEmpty()) {
                    "mcp_executor: инструменты MCP для шага не назначены."
                } else {
                    buildString {
                        appendLine("summary: MCP шаг выполнен.")
                        appendLine("tool_call_refs: ${stepToolCallRefs.joinToString(",")}")
                        appendLine("diagnostics:")
                        stepToolOutputs.forEach { appendLine("- $it") }
                    }.trim()
                }
                val done = MultiAgentStepExecution(
                    step = step,
                    status = MultiAgentStepStatus.done,
                    output = mcpOutput,
                    toolCallRefs = stepToolCallRefs
                )
                stepsState += done
                onStepReady(done)
                return@forEachIndexed
            }

            val prompt = MultiAgentPromptFactory.subagentTaskPrompt(
                userRequest = request.userRequest,
                step = step,
                reworkInstruction = null,
                previousOutput = stepToolOutputs.joinToString("\n").ifBlank { null }
            )
            val subagentRaw = callModel(
                MultiAgentModelCall(
                    messages = listOf(
                        DeepSeekMessage(role = "system", content = subagent.systemPrompt),
                        DeepSeekMessage(role = "user", content = prompt)
                    ),
                    responseAsJson = false,
                    temperature = 0.2,
                    topP = 0.8,
                    maxTokens = 3500
                )
            )
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.TRACE,
                    actorType = "subagent",
                    actorKey = subagent.key,
                    role = "assistant",
                    message = "SUBAGENT_PROMPT:\n$prompt\n\nSUBAGENT_RAW:\n$subagentRaw"
                )
            )
            val finalOutput = buildString {
                if (stepToolOutputs.isNotEmpty()) {
                    appendLine("TOOL_CONTEXT:")
                    stepToolOutputs.forEach { appendLine(it) }
                    appendLine()
                }
                append(subagentRaw.trim().ifBlank { "Пустой ответ субагента ${subagent.key}" })
            }
            val done = MultiAgentStepExecution(
                step = step,
                status = MultiAgentStepStatus.done,
                output = finalOutput,
                toolCallRefs = stepToolCallRefs
            )
            stepsState += done
            onStepReady(done)
        }

        val toolEvidenceRequired = toolPlanPreflight.toolPlan?.requiresTools == true
        val firstValidation = validate(
            userRequest = request.userRequest,
            planSteps = planningDecision.planSteps,
            stepResults = stepsState,
            callModel = callModel,
            onEvent = onEvent,
            toolEvidenceRequired = toolEvidenceRequired,
            knownToolCallIds = evidenceToolCallIds
        )
        if (firstValidation.outcome != MultiAgentValidationOutcome.REWORK) {
            return buildSummaryFromValidation(firstValidation, planningDecision, stepsState)
        }

        val reworkInstruction = firstValidation.reworkInstruction.orEmpty()
        var attempts = 0
        while (attempts < request.maxReworkAttempts && reworkInstruction.isNotBlank()) {
            attempts++
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.USER,
                    actorType = "orchestrator",
                    actorKey = "orchestrator",
                    role = "assistant",
                    message = "Требуется доработка результатов. Выполняю повтор по шагам."
                )
            )
            val updatedSteps = mutableListOf<MultiAgentStepExecution>()
            for (oldStep in stepsState) {
                val subagent = subagentsByKey[oldStep.step.assigneeKey.lowercase()]
                if (subagent == null) {
                    updatedSteps += oldStep.copy(status = MultiAgentStepStatus.failed)
                    continue
                }
                onStepReady(
                    oldStep.copy(
                        status = MultiAgentStepStatus.needs_rework,
                        validationNote = "validator_rework"
                    )
                )
                val prompt = MultiAgentPromptFactory.subagentTaskPrompt(
                    userRequest = request.userRequest,
                    step = oldStep.step,
                    reworkInstruction = reworkInstruction,
                    previousOutput = oldStep.output
                )
                val raw = callModel(
                    MultiAgentModelCall(
                        messages = listOf(
                            DeepSeekMessage(role = "system", content = subagent.systemPrompt),
                            DeepSeekMessage(role = "user", content = prompt)
                        ),
                        responseAsJson = false,
                        temperature = 0.2,
                        topP = 0.8,
                        maxTokens = 3500
                    )
                )
                onEvent(
                    MultiAgentEvent(
                        channel = MultiAgentEventChannel.TRACE,
                        actorType = "subagent",
                        actorKey = subagent.key,
                        role = "assistant",
                        message = "SUBAGENT_REWORK_PROMPT:\n$prompt\n\nSUBAGENT_REWORK_RAW:\n$raw"
                    )
                )
                val done = oldStep.copy(
                    status = MultiAgentStepStatus.done,
                    output = raw.trim().ifBlank { "Пустой ответ субагента ${subagent.key}" },
                    validationNote = "reworked"
                )
                updatedSteps += done
                onStepReady(done)
            }
            stepsState.clear()
            stepsState += updatedSteps
            val nextValidation = validate(
                userRequest = request.userRequest,
                planSteps = planningDecision.planSteps,
                stepResults = stepsState,
                callModel = callModel,
                onEvent = onEvent,
                toolEvidenceRequired = toolEvidenceRequired,
                knownToolCallIds = evidenceToolCallIds
            )
            if (nextValidation.outcome != MultiAgentValidationOutcome.REWORK) {
                return buildSummaryFromValidation(nextValidation, planningDecision, stepsState)
            }
        }

        return MultiAgentRunSummary(
            runStatus = MultiAgentRunStatus.FAILED,
            resolutionType = MultiAgentResolutionType.FAILED,
            finalUserMessage = "Не удалось завершить задачу после доработки.",
            planningDecision = planningDecision,
            steps = stepsState
        )
    }

    private suspend fun preflightToolPlan(
        toolPlan: MultiAgentToolPlan?,
        executeTool: suspend (ToolGatewayRequest) -> ToolGatewayResult,
        onEvent: (MultiAgentEvent) -> Unit
    ): PreflightResult {
        if (toolPlan == null || !toolPlan.requiresTools || toolPlan.tools.isEmpty()) {
            return PreflightResult(
                kind = PreflightResultKind.CONTINUE,
                toolPlan = toolPlan
            )
        }
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.USER,
                actorType = "orchestrator",
                actorKey = "orchestrator",
                role = "assistant",
                message = "Проверяю доступность инструментов (preflight)."
            )
        )
        val availableTools = mutableListOf<MultiAgentToolPlanItem>()
        val failedTools = mutableListOf<Pair<MultiAgentToolPlanItem, ToolGatewayResult>>()
        toolPlan.tools.forEach { tool ->
            val result = executeTool(
                ToolGatewayRequest(
                    toolKind = tool.toolKind,
                    paramsJson = tool.paramsJson,
                    reason = tool.reason,
                    preflight = true,
                    stepIndex = null
                )
            )
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.TRACE,
                    actorType = "orchestrator",
                    actorKey = "preflight",
                    role = "assistant",
                    message = "PREFLIGHT ${tool.toolKind}: success=${result.success}, error=${result.errorCode}:${result.errorMessage}"
                )
            )
            if (result.success) availableTools += tool else failedTools += (tool to result)
        }
        if (failedTools.isEmpty()) {
            return PreflightResult(
                kind = PreflightResultKind.CONTINUE,
                toolPlan = toolPlan.copy(tools = availableTools)
            )
        }
        val projectMissing = failedTools.any { (tool, result) ->
            tool.toolKind == MultiAgentToolKind.PROJECT_FS_SUMMARY &&
                result.errorCode == "PROJECT_FS_UNAVAILABLE"
        }
        if (projectMissing) {
            return PreflightResult(
                kind = PreflightResultKind.NEED_CLARIFICATION,
                message = "Укажите папку проекта для анализа и повторите запрос.",
                toolPlan = toolPlan.copy(tools = availableTools, requiresTools = availableTools.isNotEmpty())
            )
        }
        return when (toolPlan.fallbackPolicy) {
            MultiAgentToolFallbackPolicy.DEGRADE -> {
                onEvent(
                    MultiAgentEvent(
                        channel = MultiAgentEventChannel.USER,
                        actorType = "orchestrator",
                        actorKey = "orchestrator",
                        role = "assistant",
                        message = "Часть инструментов недоступна. Продолжаю в деградированном режиме."
                    )
                )
                PreflightResult(
                    kind = PreflightResultKind.CONTINUE,
                    toolPlan = toolPlan.copy(tools = availableTools, requiresTools = availableTools.isNotEmpty())
                )
            }

            MultiAgentToolFallbackPolicy.FAIL -> {
                onEvent(
                    MultiAgentEvent(
                        channel = MultiAgentEventChannel.USER,
                        actorType = "orchestrator",
                        actorKey = "orchestrator",
                        role = "assistant",
                        message = "Preflight не пройден. Требуемые инструменты недоступны."
                    )
                )
                PreflightResult(
                    kind = PreflightResultKind.IMPOSSIBLE,
                    message = "Preflight не пройден. Требуемые инструменты недоступны.",
                    toolPlan = toolPlan
                )
            }
        }
    }

    private suspend fun validate(
        userRequest: String,
        planSteps: List<MultiAgentPlanStep>,
        stepResults: List<MultiAgentStepExecution>,
        callModel: suspend (MultiAgentModelCall) -> String,
        onEvent: (MultiAgentEvent) -> Unit,
        toolEvidenceRequired: Boolean,
        knownToolCallIds: Set<Long>
    ): MultiAgentValidationDecision {
        val prompt = MultiAgentPromptFactory.validationPrompt(
            userRequest = userRequest,
            planSteps = planSteps,
            stepOutputs = stepResults
        )
        val raw = callModel(
            MultiAgentModelCall(
                messages = listOf(DeepSeekMessage(role = "user", content = prompt)),
                responseAsJson = true,
                temperature = 0.1,
                topP = 0.2,
                maxTokens = 1800
            )
        )
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.TRACE,
                actorType = "validator",
                actorKey = "validator",
                role = "assistant",
                message = "VALIDATION_PROMPT:\n$prompt\n\nVALIDATION_RAW:\n$raw"
            )
        )
        val parsed = parser.parseValidation(raw)
            ?: MultiAgentValidationDecision(
                outcome = MultiAgentValidationOutcome.REWORK,
                finalAnswer = null,
                reworkInstruction = "Невалидный ответ валидатора. Повтори шаги, добавь доказательства.",
                clarificationQuestion = null,
                impossibleReason = null,
                toolCallIds = emptyList(),
                ragEvidence = emptyList()
            )

        if (parsed.outcome == MultiAgentValidationOutcome.COMPLETE && toolEvidenceRequired) {
            val hasToolEvidence = parsed.toolCallIds.any { knownToolCallIds.contains(it) }
            val hasRagEvidence = parsed.ragEvidence.isNotEmpty()
            if (!hasToolEvidence && !hasRagEvidence) {
                return parsed.copy(
                    outcome = MultiAgentValidationOutcome.REWORK,
                    reworkInstruction = "Недостаточно доказательств. Добавь tool_call_ids и/или rag_evidence."
                )
            }
        }
        return parsed
    }

    private suspend fun buildFallbackDirectAnswer(
        userRequest: String,
        callModel: suspend (MultiAgentModelCall) -> String,
        onEvent: (MultiAgentEvent) -> Unit
    ): String {
        val prompt = "Дай краткий и точный ответ пользователю на русском языке.\n\nЗапрос:\n$userRequest"
        val raw = callModel(
            MultiAgentModelCall(
                messages = listOf(DeepSeekMessage(role = "user", content = prompt)),
                responseAsJson = false,
                temperature = 0.2,
                topP = 0.8,
                maxTokens = 1200
            )
        )
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.TRACE,
                actorType = "orchestrator",
                actorKey = "orchestrator",
                role = "assistant",
                message = "DIRECT_FALLBACK_PROMPT:\n$prompt\n\nDIRECT_FALLBACK_RAW:\n$raw"
            )
        )
        return raw.trim().ifBlank { "Не удалось сформировать прямой ответ." }
    }

    private fun buildSummaryFromValidation(
        validation: MultiAgentValidationDecision,
        planningDecision: MultiAgentPlanningDecision,
        steps: List<MultiAgentStepExecution>
    ): MultiAgentRunSummary {
        return when (validation.outcome) {
            MultiAgentValidationOutcome.COMPLETE -> MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.DONE,
                resolutionType = MultiAgentResolutionType.DELEGATED_DONE,
                finalUserMessage = validation.finalAnswer ?: "Готово. Результат получен.",
                planningDecision = planningDecision,
                steps = steps
            )

            MultiAgentValidationOutcome.NEED_CLARIFICATION -> MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.WAITING_USER,
                resolutionType = MultiAgentResolutionType.NEED_CLARIFICATION,
                finalUserMessage = validation.clarificationQuestion
                    ?: "Нужны уточнения для продолжения. Уточните задачу.",
                planningDecision = planningDecision,
                steps = steps
            )

            MultiAgentValidationOutcome.IMPOSSIBLE -> MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.DONE,
                resolutionType = MultiAgentResolutionType.IMPOSSIBLE,
                finalUserMessage = validation.impossibleReason
                    ?: "Не удалось выполнить задачу в текущих условиях.",
                planningDecision = planningDecision,
                steps = steps
            )

            MultiAgentValidationOutcome.REWORK -> MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.FAILED,
                resolutionType = MultiAgentResolutionType.FAILED,
                finalUserMessage = "Потребовалась доработка, но автоматический лимит попыток исчерпан.",
                planningDecision = planningDecision,
                steps = steps
            )
        }
    }

    private fun extractToolCallId(result: ToolGatewayResult): Long? {
        val metadata = result.metadataJson.trim()
        if (metadata.isBlank() || metadata == "{}") return null
        return runCatching {
            json.parseToJsonElement(metadata).jsonObject["toolCallId"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    private enum class PreflightResultKind {
        CONTINUE,
        NEED_CLARIFICATION,
        IMPOSSIBLE
    }

    private data class PreflightResult(
        val kind: PreflightResultKind,
        val toolPlan: MultiAgentToolPlan?,
        val message: String? = null
    )
}
