package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal class MultiAgentOrchestrator(
    private val parser: MultiAgentParser = MultiAgentParser,
    private val maxReplanAttempts: Int = 2
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

        var planningDecision = parser.parsePlanning(planningRaw)
            ?: return MultiAgentRunSummary(
                runStatus = MultiAgentRunStatus.FAILED,
                resolutionType = MultiAgentResolutionType.FAILED,
                finalUserMessage = "Оркестратор вернул невалидный план. Повторите запрос.",
                planningDecision = null,
                steps = emptyList()
            )
        onPlanningReady(planningDecision)
        emitFallbackPolicyTrace(planningDecision.toolPlan, onEvent, "initial")
        val mcpSelector = enabledSubagents.firstOrNull { it.key.equals("mcp_selector", ignoreCase = true) }

        when (val early = resolveEarlyAction(planningDecision, request, callModel, onEvent)) {
            null -> Unit
            else -> return early
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

        val initialSelection = resolveMcpCallsBySelector(
            request = request,
            planningDecision = planningDecision,
            mcpSelector = mcpSelector,
            callModel = callModel,
            onEvent = onEvent
        )
        when (initialSelection.kind) {
            McpSelectionKind.CONTINUE -> {
                if (initialSelection.toolPlan != planningDecision.toolPlan) {
                    planningDecision = planningDecision.copy(toolPlan = initialSelection.toolPlan)
                    onPlanningReady(planningDecision)
                }
            }
            McpSelectionKind.NEED_CLARIFICATION -> {
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.WAITING_USER,
                    resolutionType = MultiAgentResolutionType.NEED_CLARIFICATION,
                    finalUserMessage = initialSelection.message ?: "Нужны уточнения для выбора MCP инструмента.",
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }
            McpSelectionKind.IMPOSSIBLE -> {
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.DONE,
                    resolutionType = MultiAgentResolutionType.IMPOSSIBLE,
                    finalUserMessage = initialSelection.message ?: "Не удалось подобрать MCP инструмент для задачи.",
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }
        }

        var preflight = preflightToolPlan(
            toolPlan = planningDecision.toolPlan,
            executeTool = executeTool,
            onEvent = onEvent
        )

        var replanAttempt = 0
        while (preflight.unavailable.isNotEmpty() && replanAttempt < maxReplanAttempts) {
            replanAttempt++
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.USER,
                    actorType = "orchestrator",
                    actorKey = "orchestrator",
                    role = "assistant",
                    message = "Часть инструментов недоступна, запускаю replanning ($replanAttempt/$maxReplanAttempts)."
                )
            )
            val replanDecision = replanForAvailableTools(
                request = request,
                currentPlan = planningDecision,
                preflight = preflight,
                callModel = callModel,
                onEvent = onEvent
            ) ?: break

            planningDecision = replanDecision
            onPlanningReady(planningDecision)
            emitFallbackPolicyTrace(planningDecision.toolPlan, onEvent, "replan#$replanAttempt")

            when (val early = resolveEarlyAction(planningDecision, request, callModel, onEvent)) {
                null -> Unit
                else -> return early
            }

            if (planningDecision.planSteps.isEmpty()) {
                return MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.FAILED,
                    resolutionType = MultiAgentResolutionType.FAILED,
                    finalUserMessage = "После replanning оркестратор не построил шаги.",
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }

            val replanSelection = resolveMcpCallsBySelector(
                request = request,
                planningDecision = planningDecision,
                mcpSelector = mcpSelector,
                callModel = callModel,
                onEvent = onEvent
            )
            when (replanSelection.kind) {
                McpSelectionKind.CONTINUE -> {
                    if (replanSelection.toolPlan != planningDecision.toolPlan) {
                        planningDecision = planningDecision.copy(toolPlan = replanSelection.toolPlan)
                        onPlanningReady(planningDecision)
                    }
                }
                McpSelectionKind.NEED_CLARIFICATION -> {
                    return MultiAgentRunSummary(
                        runStatus = MultiAgentRunStatus.WAITING_USER,
                        resolutionType = MultiAgentResolutionType.NEED_CLARIFICATION,
                        finalUserMessage = replanSelection.message ?: "Нужны уточнения для выбора MCP инструмента.",
                        planningDecision = planningDecision,
                        steps = emptyList()
                    )
                }
                McpSelectionKind.IMPOSSIBLE -> {
                    return MultiAgentRunSummary(
                        runStatus = MultiAgentRunStatus.DONE,
                        resolutionType = MultiAgentResolutionType.IMPOSSIBLE,
                        finalUserMessage = replanSelection.message ?: "Не удалось подобрать MCP инструмент для задачи.",
                        planningDecision = planningDecision,
                        steps = emptyList()
                    )
                }
            }

            preflight = preflightToolPlan(
                toolPlan = planningDecision.toolPlan,
                executeTool = executeTool,
                onEvent = onEvent
            )
        }

        if (preflight.unavailable.isNotEmpty()) {
            val finalReason = buildUnavailableToolsReason(preflight.unavailable)
            val needClarification = preflight.unavailable.any {
                it.result.errorCode == "PROJECT_FS_UNAVAILABLE" || it.result.errorCode == "INVALID_ARGUMENT"
            }
            val resolutionType = if (needClarification) {
                MultiAgentResolutionType.NEED_CLARIFICATION
            } else {
                MultiAgentResolutionType.IMPOSSIBLE
            }
            val runStatus = if (needClarification) {
                MultiAgentRunStatus.WAITING_USER
            } else {
                MultiAgentRunStatus.DONE
            }
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.TRACE,
                    actorType = "orchestrator",
                    actorKey = "replan",
                    role = "assistant",
                    message = "REPLAN_FINAL: unresolved_tools=${preflight.unavailable.size}, resolution=$resolutionType, reason=$finalReason"
                )
            )
            return MultiAgentRunSummary(
                runStatus = runStatus,
                resolutionType = resolutionType,
                finalUserMessage = if (needClarification) {
                    "Нужны уточнения для продолжения: $finalReason"
                } else {
                    "Задача невыполнима с текущей доступностью инструментов: $finalReason"
                },
                planningDecision = planningDecision,
                steps = emptyList()
            )
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
            onStepReady(MultiAgentStepExecution(step = step, status = MultiAgentStepStatus.running, output = ""))
            val traceGroupId = buildSubagentTraceGroupId(step = step, subagent = subagent)
            emitSubagentTraceEvent(
                onEvent = onEvent,
                subagent = subagent,
                step = step,
                traceGroupId = traceGroupId,
                phase = MultiAgentTracePhase.STEP_START,
                message = "step_start: ${step.title}"
            )

            val stepTools = preflight.toolPlan
                ?.tools
                .orEmpty()
                .filter { it.stepIndex == null || it.stepIndex == step.index }
            val stepToolCallRefs = mutableListOf<Long>()
            val stepToolOutputs = mutableListOf<String>()
            val isMcpExecutor = subagent.key.equals("mcp_executor", ignoreCase = true)
            emitSubagentTraceEvent(
                onEvent = onEvent,
                subagent = subagent,
                step = step,
                traceGroupId = traceGroupId,
                phase = MultiAgentTracePhase.TOOL_SELECTION,
                message = MultiAgentTraceFormatter.formatToolSelection(stepTools)
            )

            stepTools.forEach { tool ->
                if (isMcpExecutor && tool.toolKind != MultiAgentToolKind.MCP_CALL) return@forEach
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = step,
                    traceGroupId = traceGroupId,
                    phase = MultiAgentTracePhase.TOOL_CALL_REQUEST,
                    message = MultiAgentTraceFormatter.formatToolCallRequest(tool)
                )
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
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = step,
                    traceGroupId = traceGroupId,
                    phase = MultiAgentTracePhase.TOOL_CALL_RESPONSE,
                    message = MultiAgentTraceFormatter.formatToolCallResponse(toolResult)
                )
                if (!toolResult.success) {
                    emitSubagentTraceEvent(
                        onEvent = onEvent,
                        subagent = subagent,
                        step = step,
                        traceGroupId = traceGroupId,
                        phase = MultiAgentTracePhase.ERROR,
                        message = "tool_error: ${toolResult.errorCode.ifBlank { "UNKNOWN" }} ${toolResult.errorMessage}"
                    )
                }
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
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = step,
                    traceGroupId = traceGroupId,
                    phase = MultiAgentTracePhase.SUBAGENT_OUTPUT,
                    message = MultiAgentTraceFormatter.formatSubagentOutput(mcpOutput)
                )
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = step,
                    traceGroupId = traceGroupId,
                    phase = MultiAgentTracePhase.STEP_FINISH,
                    message = "step_finish: status=done tool_call_refs=${stepToolCallRefs.joinToString(",")}"
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
            emitSubagentTraceEvent(
                onEvent = onEvent,
                subagent = subagent,
                step = step,
                traceGroupId = traceGroupId,
                phase = MultiAgentTracePhase.PROMPT,
                message = MultiAgentTraceFormatter.formatSubagentPrompt(
                    systemPrompt = subagent.systemPrompt,
                    userPrompt = prompt
                )
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
            emitSubagentTraceEvent(
                onEvent = onEvent,
                subagent = subagent,
                step = step,
                traceGroupId = traceGroupId,
                phase = MultiAgentTracePhase.SUBAGENT_OUTPUT,
                message = MultiAgentTraceFormatter.formatSubagentOutput(finalOutput)
            )
            val done = MultiAgentStepExecution(
                step = step,
                status = MultiAgentStepStatus.done,
                output = finalOutput,
                toolCallRefs = stepToolCallRefs
            )
            emitSubagentTraceEvent(
                onEvent = onEvent,
                subagent = subagent,
                step = step,
                traceGroupId = traceGroupId,
                phase = MultiAgentTracePhase.STEP_FINISH,
                message = "step_finish: status=done tool_call_refs=${stepToolCallRefs.joinToString(",")}"
            )
            stepsState += done
            onStepReady(done)
        }

        val toolEvidenceRequired = preflight.toolPlan?.requiresTools == true
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
                val reworkTraceGroupId = buildSubagentTraceGroupId(step = oldStep.step, subagent = subagent)
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = oldStep.step,
                    traceGroupId = reworkTraceGroupId,
                    phase = MultiAgentTracePhase.STEP_START,
                    message = "step_start: rework#$attempts ${oldStep.step.title}"
                )
                onStepReady(oldStep.copy(status = MultiAgentStepStatus.needs_rework, validationNote = "validator_rework"))
                val prompt = MultiAgentPromptFactory.subagentTaskPrompt(
                    userRequest = request.userRequest,
                    step = oldStep.step,
                    reworkInstruction = reworkInstruction,
                    previousOutput = oldStep.output
                )
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = oldStep.step,
                    traceGroupId = reworkTraceGroupId,
                    phase = MultiAgentTracePhase.PROMPT,
                    message = MultiAgentTraceFormatter.formatSubagentPrompt(
                        systemPrompt = subagent.systemPrompt,
                        userPrompt = prompt
                    )
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
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = oldStep.step,
                    traceGroupId = reworkTraceGroupId,
                    phase = MultiAgentTracePhase.SUBAGENT_OUTPUT,
                    message = MultiAgentTraceFormatter.formatSubagentOutput(raw)
                )
                val done = oldStep.copy(
                    status = MultiAgentStepStatus.done,
                    output = raw.trim().ifBlank { "Пустой ответ субагента ${subagent.key}" },
                    validationNote = "reworked"
                )
                emitSubagentTraceEvent(
                    onEvent = onEvent,
                    subagent = subagent,
                    step = oldStep.step,
                    traceGroupId = reworkTraceGroupId,
                    phase = MultiAgentTracePhase.STEP_FINISH,
                    message = "step_finish: status=done validation_note=reworked"
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

    private suspend fun replanForAvailableTools(
        request: MultiAgentRequest,
        currentPlan: MultiAgentPlanningDecision,
        preflight: PreflightResult,
        callModel: suspend (MultiAgentModelCall) -> String,
        onEvent: (MultiAgentEvent) -> Unit
    ): MultiAgentPlanningDecision? {
        val available = preflight.available.map { it.describe() }
        val unavailable = preflight.unavailable.map { it.describe(includeError = true) }
        val prompt = MultiAgentPromptFactory.orchestratorReplanPrompt(
            userRequest = request.userRequest,
            currentPlan = currentPlan,
            availableTools = available,
            unavailableTools = unavailable
        )
        val raw = callModel(
            MultiAgentModelCall(
                messages = listOf(DeepSeekMessage(role = "user", content = prompt)),
                responseAsJson = true,
                temperature = 0.1,
                topP = 0.2,
                maxTokens = 2200
            )
        )
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.TRACE,
                actorType = "orchestrator",
                actorKey = "replan",
                role = "assistant",
                message = "REPLAN_PROMPT:\n$prompt\n\nREPLAN_RAW:\n$raw"
            )
        )
        return parser.parsePlanning(raw)
    }

    private suspend fun resolveMcpCallsBySelector(
        request: MultiAgentRequest,
        planningDecision: MultiAgentPlanningDecision,
        mcpSelector: MultiAgentSubagentDefinition?,
        callModel: suspend (MultiAgentModelCall) -> String,
        onEvent: (MultiAgentEvent) -> Unit
    ): McpSelectionResult {
        val toolPlan = planningDecision.toolPlan ?: return McpSelectionResult(kind = McpSelectionKind.CONTINUE, toolPlan = null)
        if (toolPlan.tools.none { it.toolKind == MultiAgentToolKind.MCP_CALL }) {
            return McpSelectionResult(kind = McpSelectionKind.CONTINUE, toolPlan = toolPlan)
        }
        if (mcpSelector == null) {
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.TRACE,
                    actorType = "orchestrator",
                    actorKey = "mcp_selector",
                    role = "assistant",
                    message = "MCP selector subagent is not configured; keeping original MCP params from tool_plan."
                )
            )
            return McpSelectionResult(kind = McpSelectionKind.CONTINUE, toolPlan = toolPlan)
        }

        val stepByIndex = planningDecision.planSteps.associateBy { it.index }
        val rewrittenTools = mutableListOf<MultiAgentToolPlanItem>()
        for (tool in toolPlan.tools) {
            if (tool.toolKind != MultiAgentToolKind.MCP_CALL) {
                rewrittenTools += tool
                continue
            }
            val step = tool.stepIndex?.let { stepByIndex[it] }
            val selectorPrompt = MultiAgentPromptFactory.mcpToolSelectionPrompt(
                userRequest = request.userRequest,
                step = step,
                toolReason = tool.reason,
                currentToolParamsJson = tool.paramsJson,
                mcpToolsCatalog = request.mcpToolsCatalog,
                conversationContext = request.conversationContext
            )
            val selectorRaw = callModel(
                MultiAgentModelCall(
                    messages = listOf(
                        DeepSeekMessage(role = "system", content = mcpSelector.systemPrompt),
                        DeepSeekMessage(role = "user", content = selectorPrompt)
                    ),
                    responseAsJson = true,
                    temperature = 0.1,
                    topP = 0.2,
                    maxTokens = 1800
                )
            )
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.TRACE,
                    actorType = "subagent",
                    actorKey = mcpSelector.key,
                    role = "assistant",
                    message = "MCP_SELECTOR_PROMPT:\n$selectorPrompt\n\nMCP_SELECTOR_RAW:\n$selectorRaw"
                )
            )
            val selection = parser.parseMcpSelection(selectorRaw)
            if (selection == null) {
                rewrittenTools += tool
                continue
            }
            when (selection.action) {
                MultiAgentMcpSelectionAction.NEED_CLARIFICATION -> {
                    val question = selection.clarificationQuestions.firstOrNull()
                        ?: "Нужны уточнения для выбора MCP инструмента."
                    return McpSelectionResult(
                        kind = McpSelectionKind.NEED_CLARIFICATION,
                        toolPlan = toolPlan,
                        message = question
                    )
                }
                MultiAgentMcpSelectionAction.IMPOSSIBLE -> {
                    return McpSelectionResult(
                        kind = McpSelectionKind.IMPOSSIBLE,
                        toolPlan = toolPlan,
                        message = selection.impossibleReason ?: "Не удалось подобрать MCP инструмент."
                    )
                }
                MultiAgentMcpSelectionAction.MCP_CALL -> {
                    val selectedToolName = selection.toolName
                    if (selectedToolName.isNullOrBlank()) {
                        rewrittenTools += tool
                        continue
                    }
                    val params = buildJsonObject {
                        put("toolName", selectedToolName)
                        if (!selection.endpoint.isNullOrBlank()) put("endpoint", selection.endpoint)
                        if (selection.arguments != null) {
                            put("arguments", selection.arguments)
                        } else {
                            putJsonObject("arguments") { }
                        }
                    }
                    rewrittenTools += tool.copy(paramsJson = params.toString())
                }
            }
        }
        return McpSelectionResult(
            kind = McpSelectionKind.CONTINUE,
            toolPlan = toolPlan.copy(tools = rewrittenTools)
        )
    }

    private fun emitFallbackPolicyTrace(
        toolPlan: MultiAgentToolPlan?,
        onEvent: (MultiAgentEvent) -> Unit,
        phase: String
    ) {
        if (toolPlan == null) return
        val source = if (toolPlan.fallbackPolicyDefaulted) "default" else "model"
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.TRACE,
                actorType = "orchestrator",
                actorKey = "fallback_policy",
                role = "assistant",
                message = "FALLBACK_POLICY phase=$phase value=${toolPlan.fallbackPolicy} source=$source"
            )
        )
    }

    private suspend fun preflightToolPlan(
        toolPlan: MultiAgentToolPlan?,
        executeTool: suspend (ToolGatewayRequest) -> ToolGatewayResult,
        onEvent: (MultiAgentEvent) -> Unit
    ): PreflightResult {
        if (toolPlan == null || !toolPlan.requiresTools || toolPlan.tools.isEmpty()) {
            return PreflightResult(toolPlan = toolPlan, available = emptyList(), unavailable = emptyList())
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

        val availableTools = mutableListOf<PreflightToolResult>()
        val failedTools = mutableListOf<PreflightToolResult>()
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
            val preflightTool = PreflightToolResult(tool = tool, result = result)
            val source = extractMetadataField(result.metadataJson, "availability_source")
            val reason = extractMetadataField(result.metadataJson, "availability_reason")
            val details = extractMetadataField(result.metadataJson, "availability_details")
            onEvent(
                MultiAgentEvent(
                    channel = MultiAgentEventChannel.TRACE,
                    actorType = "orchestrator",
                    actorKey = "preflight",
                    role = "assistant",
                    message = "PREFLIGHT ${tool.toolKind} success=${result.success} error=${result.errorCode}:${result.errorMessage} source=$source reason=$reason details=$details"
                )
            )
            if (result.success) availableTools += preflightTool else failedTools += preflightTool
        }

        val filtered = toolPlan.copy(tools = availableTools.map { it.tool })
        return PreflightResult(toolPlan = filtered, available = availableTools, unavailable = failedTools)
    }

    private suspend fun resolveEarlyAction(
        planningDecision: MultiAgentPlanningDecision,
        request: MultiAgentRequest,
        callModel: suspend (MultiAgentModelCall) -> String,
        onEvent: (MultiAgentEvent) -> Unit
    ): MultiAgentRunSummary? {
        return when (planningDecision.action) {
            MultiAgentDecisionType.DIRECT_ANSWER -> {
                val direct = planningDecision.directAnswer?.takeIf { it.isNotBlank() }
                    ?: buildFallbackDirectAnswer(request.userRequest, callModel, onEvent)
                MultiAgentRunSummary(
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
                MultiAgentRunSummary(
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
                MultiAgentRunSummary(
                    runStatus = MultiAgentRunStatus.DONE,
                    resolutionType = MultiAgentResolutionType.IMPOSSIBLE,
                    finalUserMessage = reason,
                    planningDecision = planningDecision,
                    steps = emptyList()
                )
            }

            MultiAgentDecisionType.DELEGATE -> null
        }
    }

    private fun buildUnavailableToolsReason(unavailable: List<PreflightToolResult>): String {
        if (unavailable.isEmpty()) return "нет недоступных инструментов"
        return unavailable.joinToString("; ") {
            val err = "${it.result.errorCode}:${it.result.errorMessage}".trim(':')
            "${it.describe()} ($err)"
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

    private fun emitSubagentTraceEvent(
        onEvent: (MultiAgentEvent) -> Unit,
        subagent: MultiAgentSubagentDefinition,
        step: MultiAgentPlanStep,
        traceGroupId: String,
        phase: MultiAgentTracePhase,
        message: String
    ) {
        onEvent(
            MultiAgentEvent(
                channel = MultiAgentEventChannel.TRACE,
                actorType = "subagent",
                actorKey = subagent.key,
                role = "assistant",
                message = message,
                metadataJson = buildSubagentTraceMetadata(
                    traceGroupId = traceGroupId,
                    traceGroupTitle = subagent.title,
                    phase = phase,
                    stepIndex = step.index,
                    extra = mapOf("step_title" to step.title)
                )
            )
        )
    }

    private fun buildSubagentTraceGroupId(
        step: MultiAgentPlanStep,
        subagent: MultiAgentSubagentDefinition
    ): String {
        val suffix = System.currentTimeMillis()
        return "subagent_run:${step.index}:${subagent.key}:$suffix"
    }

    private fun extractToolCallId(result: ToolGatewayResult): Long? {
        val metadata = result.metadataJson.trim()
        if (metadata.isBlank() || metadata == "{}") return null
        return runCatching {
            json.parseToJsonElement(metadata).jsonObject["toolCallId"]?.jsonPrimitive?.longOrNull
        }.getOrNull()
    }

    private fun extractMetadataField(metadataJson: String, key: String): String {
        val raw = metadataJson.trim()
        if (raw.isBlank() || raw == "{}") return "-"
        return runCatching {
            json.parseToJsonElement(raw).jsonObject[key]?.jsonPrimitive?.contentOrNull ?: "-"
        }.getOrDefault("-")
    }

    private data class PreflightToolResult(
        val tool: MultiAgentToolPlanItem,
        val result: ToolGatewayResult
    ) {
        fun describe(includeError: Boolean = false): String {
            val base = "${tool.toolKind} step=${tool.stepIndex ?: "-"} reason=${tool.reason}"
            if (!includeError) return base
            return "$base error=${result.errorCode}:${result.errorMessage}"
        }
    }

    private data class PreflightResult(
        val toolPlan: MultiAgentToolPlan?,
        val available: List<PreflightToolResult>,
        val unavailable: List<PreflightToolResult>
    )

    private enum class McpSelectionKind {
        CONTINUE,
        NEED_CLARIFICATION,
        IMPOSSIBLE
    }

    private data class McpSelectionResult(
        val kind: McpSelectionKind,
        val toolPlan: MultiAgentToolPlan?,
        val message: String? = null
    )
}
