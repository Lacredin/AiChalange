package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.aiadventchalengetestllmapi.BuildSecrets

internal enum class AiAgentApi(
    val label: String,
    val envVar: String,
    val defaultModel: String,
    val supportedModels: List<String>
) {
    DeepSeek(
        label = "DeepSeek",
        envVar = "DEEPSEEK_API_KEY",
        defaultModel = "deepseek-chat",
        supportedModels = listOf("deepseek-chat", "deepseek-reasoner")
    ),
    OpenAI(
        label = "OpenAI",
        envVar = "OPENAI_API_KEY",
        defaultModel = "gpt-4o-mini",
        supportedModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1", "o3-mini")
    ),
    GigaChat(
        label = "GigaChat",
        envVar = "GIGACHAT_ACCESS_TOKEN",
        defaultModel = "GigaChat-2",
        supportedModels = listOf("GigaChat-2", "GigaChat-2-Pro", "GigaChat-2-Max")
    ),
    ProxyOpenAI(
        label = "ProxyAPI (OpenAI)",
        envVar = "PROXYAPI_API_KEY",
        defaultModel = "openai/gpt-4o-mini",
        supportedModels = listOf(
            "openai/gpt-5.2",
            "openai/gpt-4o-mini",
            "openai/gpt-4o",
            "openai/gpt-4.1-mini",
            "openai/gpt-4.1",
            "openai/o3-mini",
            "anthropic/claude-sonnet-4-6",
            "anthropic/claude-sonnet-4-5",
            "anthropic/claude-opus-4-6",
            "anthropic/claude-3-7-sonnet-20250219"
        )
    ),
    LocalLlm(
        label = "Local LLM",
        envVar = "LOCAL_LLM_API_KEY",
        defaultModel = "llama3.1:8b",
        supportedModels = listOf("llama3.1:8b", "gemma2:2b", "qwen2.5:7b")
    )
}

internal enum class AgentState { Idle, Planning, Execution, Checking, Done }

internal data class ValidationFailureInfo(
    val problem: String,
    val failedStepId: Int,
    val retryFromStepId: Int,
    val proposedSolution: String,
    val stepDetectionSource: String
)

internal data class ExecutionToolRequest(
    val toolName: String,
    val endpoint: String?,
    val reason: String,
    val arguments: Map<String, Any?>
)

internal data class AiAgentMessage(
    val text: String,
    val isUser: Boolean,
    val paramsInfo: String,
    val stream: AiAgentStream,
    val epoch: Int,
    val createdAt: Long
)

internal data class LongTermMemoryEntry(
    val id: Long,
    val key: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long
)

internal data class InvariantEntry(
    val id: Long,
    val key: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long
)

internal enum class AiAgentStream { Real, Raw }

internal data class AiAgentChatItem(val id: Long, val title: String)

internal data class AiAgentBranchItem(
    val number: Int,
    val messages: SnapshotStateList<AiAgentMessage>
)

internal data class McpServerOption(
    val title: String,
    val url: String
)

internal val mcpServerOptions = listOf(
    McpServerOption(
        title = "Exa MCP",
        url = "https://mcp.exa.ai/mcp"
    ),
    McpServerOption(
        title = "Local MCP (127.0.0.1)",
        url = "http://127.0.0.1:8080/mcp"
    )
)

private val streamStripRegex = Regex("""\s*\|\s*stream=(real|raw)""")
private val epochStripRegex = Regex("""\s*\|\s*epoch=\d+""")

internal fun AiAgentMessage.displayParamsInfo(): String =
    paramsInfo
        .replace(streamStripRegex, "")
        .replace(epochStripRegex, "")

internal fun aiAgentReadApiKey(envVar: String): String {
    if (envVar == "LOCAL_LLM_API_KEY") return "local-llm"
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}
