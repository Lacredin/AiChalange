package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal enum class AgentPlanningStrategy {
    NONE,
    MCP,
    RAG,
    MCP_AND_RAG
}

internal data class AgentMcpRequestPlan(
    val toolName: String,
    val endpoint: String?,
    val arguments: Map<String, Any?>
)

internal data class AgentRoutingDecision(
    val hasContext: Boolean,
    val strategy: AgentPlanningStrategy,
    val reason: String,
    val mcpRequests: List<AgentMcpRequestPlan>,
    val ragQueries: List<String>
)

internal data class AgentPlanningPromptInput(
    val userRequest: String,
    val projectFolderPath: String,
    val mcpSummary: String,
    val ragSourcesSummary: String
)

internal object AgentPlanningPromptFactory {
    fun create(input: AgentPlanningPromptInput): String = buildString {
        appendLine("Ты router/planner для команды /help. Ты не отвечаешь пользователю.")
        appendLine("Твоя задача: выбрать стратегию контекста и вернуть только JSON.")
        appendLine()
        appendLine("Правила:")
        appendLine("1) Верни только JSON, без markdown и пояснений.")
        appendLine("2) Структура JSON строго:")
        appendLine(
            """{"hasContext":boolean,"strategy":"NONE|MCP|RAG|MCP_AND_RAG","reason":"...","mcpRequests":[{"toolName":"...","endpoint":"...","arguments":{}}],"ragQueries":["..."]}"""
        )
        appendLine("3) Если контекст не подходит: hasContext=false, strategy=NONE.")
        appendLine("4) Нельзя выдумывать MCP инструменты, которых нет в списке.")
        appendLine("5) Нельзя выбирать MCP, если MCP недоступен.")
        appendLine("6) Нельзя выбирать RAG, если RAG-источники отсутствуют.")
        appendLine("7) strategy=MCP => ragQueries пустой.")
        appendLine("8) strategy=RAG => mcpRequests пустой.")
        appendLine("9) strategy=NONE => mcpRequests и ragQueries пустые.")
        appendLine("10) Всегда учитывай путь проекта из projectFolderPath как корневой путь.")
        appendLine("11) Если выбранный MCP инструмент принимает путь в arguments, обязательно передай projectFolderPath в arguments.")
        appendLine("12) Для пути используй подходящий ключ из схемы инструмента: path, projectPath, rootPath, workspacePath, directory, filePath.")
        appendLine("13) Если MCP стратегия требует путь, но путь не передан в arguments, это невалидный план: верни hasContext=false и strategy=NONE.")
        appendLine("14) mcpRequests.arguments формируй строго по input_schema инструмента от MCP сервера: типы, required-поля, структура объекта.")
        appendLine("15) Не добавляй аргументы, которых нет в input_schema, и не нарушай типы полей.")
        appendLine("16) Если невозможно сформировать валидные arguments по input_schema, не выбирай MCP: верни hasContext=false и strategy=NONE.")
        appendLine("17) При выборе MCP используй endpoint строго из списка серверов MCP (или null), не выдумывай endpoint.")
        appendLine()
        appendLine("Запрос пользователя:")
        appendLine(input.userRequest)
        appendLine()
        appendLine("Папка проекта:")
        appendLine(input.projectFolderPath)
        appendLine()
        appendLine("MCP инструменты:")
        appendLine(input.mcpSummary)
        appendLine()
        appendLine("RAG источники:")
        appendLine(input.ragSourcesSummary)
    }.trim()
}

internal object AgentPlanningParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): AgentRoutingDecision? {
        val element = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val root = element as? JsonObject ?: return null

        val hasContext = root["hasContext"]?.jsonPrimitive?.booleanOrNull ?: return null
        val strategy = root["strategy"]?.jsonPrimitive?.contentOrNull
            ?.let { value -> runCatching { AgentPlanningStrategy.valueOf(value.uppercase()) }.getOrNull() }
            ?: return null
        val reason = root["reason"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "no reason" }

        val mcpRequests = root["mcpRequests"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val toolName = obj["toolName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (toolName.isEmpty()) return@mapNotNull null
            val endpoint = obj["endpoint"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
            val arguments = parseArguments(obj["arguments"] as? JsonObject)
            AgentMcpRequestPlan(
                toolName = toolName,
                endpoint = endpoint,
                arguments = arguments
            )
        }

        val ragQueries = root["ragQueries"]?.jsonArray.orEmpty().mapNotNull { item ->
            item.jsonPrimitive.contentOrNull?.trim()?.ifBlank { null }
        }.distinct()

        return AgentRoutingDecision(
            hasContext = hasContext,
            strategy = strategy,
            reason = reason,
            mcpRequests = mcpRequests,
            ragQueries = ragQueries
        )
    }

    private fun parseArguments(obj: JsonObject?): Map<String, Any?> {
        if (obj == null) return emptyMap()
        return obj.mapValues { (_, value) -> value.toPrimitiveAnyOrJson() }
    }

    private fun JsonElement.toPrimitiveAnyOrJson(): Any? = when (this) {
        is JsonPrimitive -> {
            booleanOrNull ?: contentOrNull?.toLongOrNull() ?: contentOrNull?.toDoubleOrNull() ?: contentOrNull
        }

        is JsonObject -> toString()
        else -> toString()
    }
}
