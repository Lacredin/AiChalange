package com.example.aiadventchalengetestllmapi.aiagentmain

internal object MultiAgentTraceFormatter {
    private const val maxFieldChars = 2_500
    private val secretRegexes = listOf(
        Regex("(?i)(api[_-]?key\\s*[:=]\\s*)([^\\s,;\\n]+)"),
        Regex("(?i)(token\\s*[:=]\\s*)([^\\s,;\\n]+)"),
        Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+)([^\\s,;\\n]+)"),
        Regex("(?i)(password\\s*[:=]\\s*)([^\\s,;\\n]+)")
    )

    fun formatSubagentPrompt(systemPrompt: String, userPrompt: String): String {
        return buildString {
            appendLine("system_prompt:")
            appendLine(safeText(systemPrompt))
            appendLine()
            appendLine("task_prompt:")
            append(safeText(userPrompt))
        }.trim()
    }

    fun formatToolSelection(tools: List<MultiAgentToolPlanItem>): String {
        if (tools.isEmpty()) return "Инструменты для шага не назначены."
        return buildString {
            appendLine("selected_tools:")
            tools.forEachIndexed { index, tool ->
                appendLine(
                    "${index + 1}. kind=${tool.toolKind.name} step=${tool.stepIndex ?: "-"} reason=${
                        safeText(tool.reason)
                    } capability=${tool.capability ?: "-"} operation=${tool.operationType ?: "-"}"
                )
            }
        }.trim()
    }

    fun formatToolCallRequest(tool: MultiAgentToolPlanItem): String {
        return buildString {
            appendLine("tool_kind: ${tool.toolKind.name}")
            appendLine("step_index: ${tool.stepIndex ?: "-"}")
            appendLine("capability: ${tool.capability ?: "-"}")
            appendLine("operation: ${tool.operationType ?: "-"}")
            appendLine("reason: ${safeText(tool.reason)}")
            appendLine("params:")
            append(safeText(tool.paramsJson))
        }.trim()
    }

    fun formatToolCallResponse(result: ToolGatewayResult): String {
        return buildString {
            appendLine("result_status: ${if (result.success) "success" else "error"}")
            appendLine("error_code: ${result.errorCode.ifBlank { "-" }}")
            appendLine("error_message: ${safeText(result.errorMessage).ifBlank { "-" }}")
            appendLine("latency_ms: ${result.latencyMs}")
            appendLine("normalized_output:")
            appendLine(safeText(result.normalizedOutput).ifBlank { "-" })
            appendLine()
            appendLine("raw_output:")
            append(safeText(result.rawOutput).ifBlank { "-" })
        }.trim()
    }

    fun formatSubagentOutput(output: String): String = safeText(output).ifBlank { "-" }

    fun safeText(raw: String): String {
        if (raw.isBlank()) return ""
        val masked = secretRegexes.fold(raw) { acc, regex ->
            regex.replace(acc) { match ->
                val prefix = match.groups[1]?.value.orEmpty()
                "$prefix***"
            }
        }
        return if (masked.length <= maxFieldChars) {
            masked
        } else {
            masked.take(maxFieldChars) + "\n...[truncated ${masked.length - maxFieldChars} chars]"
        }
    }
}
