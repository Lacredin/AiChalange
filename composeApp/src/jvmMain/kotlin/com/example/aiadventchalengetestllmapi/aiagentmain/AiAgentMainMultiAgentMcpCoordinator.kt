package com.example.aiadventchalengetestllmapi.aiagentmain

internal data class MultiAgentMcpRefreshReport(
    val didLiveRefresh: Boolean,
    val lines: List<String>
)

internal class AiAgentMainMultiAgentMcpCoordinator(
    private val allServersProvider: () -> List<McpServerOption>,
    private val toolsProvider: (serverUrl: String) -> List<String>,
    private val toolInfosProvider: (serverUrl: String) -> List<com.example.aiadventchalengetestllmapi.mcp.McpToolInfo>,
    private val errorProvider: (serverUrl: String) -> String?,
    private val refreshServerTools: suspend (serverUrl: String) -> Unit,
    private val endpointMatcher: (endpoint: String, serverUrl: String) -> Boolean,
    private val ragChunkCountProvider: () -> Int,
    private val isSessionRefreshed: () -> Boolean,
    private val markSessionRefreshed: (Boolean) -> Unit
) {
    fun currentRagChunkCount(): Int = ragChunkCountProvider()

    suspend fun ensureMcpCacheFresh(): MultiAgentMcpRefreshReport {
        val traceLines = mutableListOf<String>()
        var didRefresh = false
        val servers = allServersProvider()

        if (!isSessionRefreshed()) {
            servers.forEach { server ->
                refreshServerTools(server.url)
                traceLines += "live-refresh server=${server.title} reason=session_bootstrap"
            }
            markSessionRefreshed(true)
            didRefresh = servers.isNotEmpty()
        } else {
            servers.forEach { server ->
                val tools = toolsProvider(server.url)
                val error = errorProvider(server.url)
                val refreshReason = when {
                    error != null -> "previous_error=$error"
                    tools.isEmpty() -> "empty_cache"
                    else -> null
                }
                if (refreshReason != null) {
                    refreshServerTools(server.url)
                    traceLines += "live-refresh server=${server.title} reason=$refreshReason"
                    didRefresh = true
                }
            }
        }

        if (!didRefresh) {
            traceLines += "live-refresh skipped reason=cache_ok"
        }
        return MultiAgentMcpRefreshReport(didLiveRefresh = didRefresh, lines = traceLines)
    }

    fun findServerForTool(toolName: String, endpoint: String?): McpServerOption? {
        val endpointNormalized = endpoint?.trim()?.ifBlank { null }
        val servers = allServersProvider()
        return servers.firstOrNull { server ->
            toolsProvider(server.url).any { it.equals(toolName, ignoreCase = true) } &&
                (endpointNormalized == null || endpointMatcher(endpointNormalized, server.url))
        } ?: servers.firstOrNull { server ->
            toolsProvider(server.url).any { it.equals(toolName, ignoreCase = true) }
        }
    }

    fun buildRagAvailability(): ToolGatewayAvailability {
        val chunks = currentRagChunkCount()
        return ToolGatewayAvailability(
            available = chunks > 0,
            source = "index availability",
            reason = if (chunks > 0) "" else "RAG index has no chunks",
            details = "RAG source = index availability; chunks=$chunks"
        )
    }

    fun buildMcpAvailability(toolName: String): ToolGatewayAvailability {
        val servers = allServersProvider()
        val matching = servers.filter { server ->
            toolsProvider(server.url).any { it.equals(toolName, ignoreCase = true) }
        }
        val serverErrors = servers.mapNotNull { server ->
            val error = errorProvider(server.url)
            if (error == null) null else "${server.title}: $error"
        }
        return ToolGatewayAvailability(
            available = matching.isNotEmpty(),
            source = "all configured servers",
            reason = if (matching.isNotEmpty()) {
                ""
            } else if (serverErrors.isNotEmpty()) {
                "tool '$toolName' not found; server errors: ${serverErrors.joinToString("; ")}"
            } else {
                "tool '$toolName' not found in discovered MCP cache"
            },
            details = "MCP source = all configured servers; checked=${servers.size}; matched=${matching.joinToString(",") { it.title }}"
        )
    }

    fun buildMcpToolsCatalogForSelector(): String {
        val servers = allServersProvider()
        if (servers.isEmpty()) return "no configured MCP servers"
        return buildString {
            servers.forEach { server ->
                appendLine("server: ${server.title} (${server.url})")
                val error = errorProvider(server.url)
                if (error != null) {
                    appendLine("  status: error=$error")
                    appendLine()
                    return@forEach
                }
                val infos = toolInfosProvider(server.url)
                if (infos.isNotEmpty()) {
                    infos.forEach { tool ->
                        appendLine("  - tool: ${tool.name}")
                        appendLine("    description: ${tool.description.ifBlank { "no description" }}")
                        appendLine("    input_schema: ${tool.inputSchema}")
                    }
                } else {
                    val names = toolsProvider(server.url)
                    if (names.isEmpty()) {
                        appendLine("  - tools: none")
                    } else {
                        names.forEach { name ->
                            appendLine("  - tool: $name")
                            appendLine("    description: unknown")
                            appendLine("    input_schema: {}")
                        }
                    }
                }
                appendLine()
            }
        }.trim()
    }
}
