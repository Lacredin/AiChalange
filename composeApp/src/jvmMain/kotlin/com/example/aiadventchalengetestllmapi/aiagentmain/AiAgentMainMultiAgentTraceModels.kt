package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal const val TRACE_GROUP_TYPE_SUBAGENT_RUN = "subagent_run"

internal enum class MultiAgentTracePhase(val wireValue: String) {
    PROMPT("prompt"),
    STEP_START("step_start"),
    TOOL_SELECTION("tool_selection"),
    TOOL_CALL_REQUEST("tool_call_request"),
    TOOL_CALL_RESPONSE("tool_call_response"),
    SUBAGENT_OUTPUT("subagent_output"),
    STEP_FINISH("step_finish"),
    ERROR("error");

    companion object {
        fun fromWire(raw: String?): MultiAgentTracePhase? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.wireValue == raw.trim().lowercase() }
        }
    }
}

internal data class MultiAgentTraceEventRecord(
    val id: Long?,
    val runId: Long?,
    val chatId: Long,
    val channel: MultiAgentEventChannel,
    val actorType: String,
    val actorKey: String,
    val role: String,
    val message: String,
    val metadataJson: String,
    val createdAt: Long
)

internal data class MultiAgentTraceMetadata(
    val traceGroupType: String?,
    val traceGroupId: String?,
    val tracePhase: MultiAgentTracePhase?,
    val tracePhaseRaw: String?,
    val traceGroupTitle: String?,
    val stepIndex: Int?
)

internal data class MultiAgentTraceSubagentGroup(
    val traceGroupId: String,
    val title: String,
    val events: List<MultiAgentTraceEventRecord>
)

internal sealed interface MultiAgentTraceTimelineItem {
    data class Group(val group: MultiAgentTraceSubagentGroup) : MultiAgentTraceTimelineItem
    data class Event(val event: MultiAgentTraceEventRecord) : MultiAgentTraceTimelineItem
}

private val traceJson = Json { ignoreUnknownKeys = true }

internal fun parseMultiAgentTraceMetadata(metadataJson: String): MultiAgentTraceMetadata {
    val raw = metadataJson.trim()
    if (raw.isBlank() || raw == "{}") {
        return MultiAgentTraceMetadata(
            traceGroupType = null,
            traceGroupId = null,
            tracePhase = null,
            tracePhaseRaw = null,
            traceGroupTitle = null,
            stepIndex = null
        )
    }
    val obj = runCatching { traceJson.parseToJsonElement(raw).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    val phaseRaw = obj["trace_phase"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { null }
    return MultiAgentTraceMetadata(
        traceGroupType = obj["trace_group_type"]?.jsonPrimitive?.contentOrNull,
        traceGroupId = obj["trace_group_id"]?.jsonPrimitive?.contentOrNull,
        tracePhase = MultiAgentTracePhase.fromWire(phaseRaw),
        tracePhaseRaw = phaseRaw,
        traceGroupTitle = obj["trace_group_title"]?.jsonPrimitive?.contentOrNull,
        stepIndex = obj["step_index"]?.jsonPrimitive?.intOrNull
    )
}

internal fun buildSubagentTraceMetadata(
    traceGroupId: String,
    traceGroupTitle: String,
    phase: MultiAgentTracePhase,
    stepIndex: Int?,
    extra: Map<String, String> = emptyMap()
): String {
    return buildJsonObject {
        put("trace_group_type", TRACE_GROUP_TYPE_SUBAGENT_RUN)
        put("trace_group_id", traceGroupId)
        put("trace_group_title", traceGroupTitle)
        put("trace_phase", phase.wireValue)
        if (stepIndex != null) put("step_index", stepIndex)
        extra.forEach { (key, value) -> put(key, value) }
    }.toString()
}

internal fun buildMultiAgentTraceTimeline(events: List<MultiAgentTraceEventRecord>): List<MultiAgentTraceTimelineItem> {
    val groupsById = linkedMapOf<String, MutableList<MultiAgentTraceEventRecord>>()
    val groupTitles = mutableMapOf<String, String>()
    val timeline = mutableListOf<Any>()

    events.forEach { event ->
        val metadata = parseMultiAgentTraceMetadata(event.metadataJson)
        val isSubagentGroup = metadata.traceGroupType == TRACE_GROUP_TYPE_SUBAGENT_RUN
        val groupId = metadata.traceGroupId
        if (isSubagentGroup && !groupId.isNullOrBlank()) {
            val current = groupsById[groupId]
            if (current == null) {
                groupsById[groupId] = mutableListOf(event)
                groupTitles[groupId] = metadata.traceGroupTitle ?: event.actorKey
                timeline += groupId
            } else {
                current += event
            }
        } else {
            timeline += event
        }
    }

    return timeline.map { item ->
        when (item) {
            is String -> {
                val groupedEvents = groupsById[item].orEmpty()
                MultiAgentTraceTimelineItem.Group(
                    MultiAgentTraceSubagentGroup(
                        traceGroupId = item,
                        title = groupTitles[item].orEmpty().ifBlank { "subagent" },
                        events = groupedEvents
                    )
                )
            }

            is MultiAgentTraceEventRecord -> MultiAgentTraceTimelineItem.Event(item)
            else -> error("Unexpected timeline item type: ${item::class.simpleName}")
        }
    }
}
