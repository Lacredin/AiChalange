package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.serialization.json.JsonObject

internal enum class MultiAgentDecisionType {
    DIRECT_ANSWER,
    DELEGATE,
    NEED_CLARIFICATION,
    IMPOSSIBLE
}

internal enum class MultiAgentRunStatus {
    RUNNING,
    DONE,
    FAILED,
    WAITING_USER
}

internal enum class MultiAgentResolutionType {
    DIRECT_ANSWER,
    DELEGATED_DONE,
    NEED_CLARIFICATION,
    IMPOSSIBLE,
    FAILED
}

internal enum class MultiAgentStepStatus {
    planned,
    running,
    done,
    failed,
    needs_rework,
    waiting_user
}

internal data class MultiAgentSubagentDefinition(
    val key: String,
    val title: String,
    val description: String,
    val systemPrompt: String,
    val isEnabled: Boolean
)

internal data class MultiAgentPlanStep(
    val index: Int,
    val title: String,
    val assigneeKey: String,
    val taskInput: String
)

internal data class MultiAgentStepExecution(
    val step: MultiAgentPlanStep,
    val status: MultiAgentStepStatus,
    val output: String,
    val validationNote: String = "",
    val toolCallRefs: List<Long> = emptyList()
)

internal enum class MultiAgentToolKind {
    RAG_QUERY,
    MCP_CALL,
    PROJECT_FS_SUMMARY
}

internal enum class MultiAgentToolFallbackPolicy {
    DEGRADE,
    FAIL
}

internal data class MultiAgentToolPlanItem(
    val toolKind: MultiAgentToolKind,
    val reason: String,
    val paramsJson: String,
    val stepIndex: Int? = null
)

internal data class MultiAgentToolPlan(
    val requiresTools: Boolean,
    val tools: List<MultiAgentToolPlanItem>,
    val fallbackPolicy: MultiAgentToolFallbackPolicy,
    val fallbackPolicyDefaulted: Boolean = false
)

internal data class MultiAgentPlanningDecision(
    val action: MultiAgentDecisionType,
    val reason: String,
    val directAnswer: String?,
    val clarificationQuestion: String?,
    val impossibleReason: String?,
    val planSteps: List<MultiAgentPlanStep>,
    val toolPlan: MultiAgentToolPlan?
)

internal enum class MultiAgentValidationOutcome {
    COMPLETE,
    REWORK,
    NEED_CLARIFICATION,
    IMPOSSIBLE
}

internal data class MultiAgentValidationDecision(
    val outcome: MultiAgentValidationOutcome,
    val finalAnswer: String?,
    val reworkInstruction: String?,
    val clarificationQuestion: String?,
    val impossibleReason: String?,
    val toolCallIds: List<Long>,
    val ragEvidence: List<String>
)

internal data class MultiAgentModelCall(
    val messages: List<DeepSeekMessage>,
    val responseAsJson: Boolean,
    val temperature: Double,
    val topP: Double,
    val maxTokens: Int
)

internal data class MultiAgentRequest(
    val userRequest: String,
    val projectFolderPath: String,
    val subagents: List<MultiAgentSubagentDefinition>,
    val maxReworkAttempts: Int = 1,
    val conversationContext: String = "",
    val pendingQuestion: String? = null,
    val isContinuation: Boolean = false,
    val mcpToolsCatalog: String = ""
)

internal enum class MultiAgentMcpSelectionAction {
    MCP_CALL,
    NEED_CLARIFICATION,
    IMPOSSIBLE
}

internal data class MultiAgentMcpSelectionDecision(
    val action: MultiAgentMcpSelectionAction,
    val reason: String,
    val toolName: String?,
    val endpoint: String?,
    val arguments: JsonObject?,
    val clarificationQuestions: List<String>,
    val impossibleReason: String?
)

internal enum class MultiAgentEventChannel {
    USER,
    TRACE
}

internal data class MultiAgentEvent(
    val channel: MultiAgentEventChannel,
    val actorType: String,
    val actorKey: String,
    val role: String,
    val message: String,
    val metadataJson: String = "{}"
)

internal data class MultiAgentRunSummary(
    val runStatus: MultiAgentRunStatus,
    val resolutionType: MultiAgentResolutionType,
    val finalUserMessage: String,
    val planningDecision: MultiAgentPlanningDecision?,
    val steps: List<MultiAgentStepExecution>
)

internal data class MultiAgentToolCallLog(
    val runId: Long,
    val stepId: Long? = null,
    val toolKind: MultiAgentToolKind,
    val requestPayload: String,
    val responsePayload: String,
    val status: String,
    val errorCode: String,
    val errorMessage: String,
    val latencyMs: Long
)
