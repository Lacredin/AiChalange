package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val multiAgentJson = Json { ignoreUnknownKeys = true }

internal object MultiAgentParser {
    fun parsePlanning(raw: String): MultiAgentPlanningDecision? {
        val root = parseJsonObject(raw) ?: return null
        val action = when (root["action"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()) {
            "DIRECT_ANSWER" -> MultiAgentDecisionType.DIRECT_ANSWER
            "DELEGATE" -> MultiAgentDecisionType.DELEGATE
            "NEED_CLARIFICATION" -> MultiAgentDecisionType.NEED_CLARIFICATION
            "IMPOSSIBLE" -> MultiAgentDecisionType.IMPOSSIBLE
            else -> return null
        }
        val steps = root["plan_steps"]?.jsonArray.orEmpty().mapIndexedNotNull { index, item ->
            val obj = item as? JsonObject ?: return@mapIndexedNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val assignee = obj["assignee_key"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val taskInput = obj["task_input"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank() || assignee.isBlank() || taskInput.isBlank()) return@mapIndexedNotNull null
            MultiAgentPlanStep(
                index = obj["index"]?.jsonPrimitive?.intOrNull ?: (index + 1),
                title = title,
                assigneeKey = assignee,
                taskInput = taskInput
            )
        }
        return MultiAgentPlanningDecision(
            action = action,
            reason = root["reason"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            directAnswer = root["direct_answer"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
            clarificationQuestion = root["clarification_question"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
            impossibleReason = root["impossible_reason"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
            planSteps = steps
        )
    }

    fun parseValidation(raw: String): MultiAgentValidationDecision? {
        val root = parseJsonObject(raw) ?: return null
        val outcome = when (root["outcome"]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()) {
            "COMPLETE" -> MultiAgentValidationOutcome.COMPLETE
            "REWORK" -> MultiAgentValidationOutcome.REWORK
            "NEED_CLARIFICATION" -> MultiAgentValidationOutcome.NEED_CLARIFICATION
            "IMPOSSIBLE" -> MultiAgentValidationOutcome.IMPOSSIBLE
            else -> return null
        }
        return MultiAgentValidationDecision(
            outcome = outcome,
            finalAnswer = root["final_answer"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
            reworkInstruction = root["rework_instruction"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
            clarificationQuestion = root["clarification_question"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
            impossibleReason = root["impossible_reason"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
        )
    }

    private fun parseJsonObject(raw: String): JsonObject? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return runCatching { multiAgentJson.parseToJsonElement(trimmed).jsonObject }.getOrNull()
    }
}
