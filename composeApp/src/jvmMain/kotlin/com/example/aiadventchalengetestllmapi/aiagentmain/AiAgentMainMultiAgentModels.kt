package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage

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
    val validationNote: String = ""
)

internal data class MultiAgentPlanningDecision(
    val action: MultiAgentDecisionType,
    val reason: String,
    val directAnswer: String?,
    val clarificationQuestion: String?,
    val impossibleReason: String?,
    val planSteps: List<MultiAgentPlanStep>
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
    val impossibleReason: String?
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
    val maxReworkAttempts: Int = 1
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
