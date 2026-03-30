package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.mcp.McpToolInfo
import com.example.aiadventchalengetestllmapi.mcp.RemoteMcpService
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.DeepSeekResponseFormat
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

internal const val AGENT_NO_CONTEXT_MESSAGE = "Нет контекста для выполнения запроса. Переформулируйте."

internal data class AgentModeState(
    val isEnabled: Boolean = false,
    val projectFolderPath: String = ""
)

internal data class AgentMcpServerSnapshot(
    val title: String,
    val url: String,
    val tools: List<McpToolInfo>,
    val error: String? = null
)

internal data class AgentMcpContext(
    val snapshots: List<AgentMcpServerSnapshot>,
    val promptContext: String
)

internal data class AgentProjectContext(
    val rootPath: String,
    val treePreview: String,
    val snippetsPreview: String
)

internal data class AgentRagSourceMeta(
    val title: String,
    val section: String,
    val source: String
)

internal data class AgentRagCatalog(
    val sources: List<AgentRagSourceMeta>
)

internal data class AgentHelpRequest(
    val projectFolderPath: String,
    val userQuestion: String
)

internal data class AgentMcpExecutionResult(
    val request: AgentMcpRequestPlan,
    val output: String?,
    val error: String? = null
)

internal data class AgentRagExecutionResult(
    val query: String,
    val payload: AiAgentMainRagPayload?
)

internal data class AgentModelRequestOptions(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val responseFormat: DeepSeekResponseFormat? = null
)

internal class AgentProjectContextProvider {
    fun load(projectFolderPath: String): AgentProjectContext {
        val root = File(projectFolderPath)
        require(root.exists() && root.isDirectory) {
            "Выбранная папка проекта недоступна: $projectFolderPath"
        }

        val treeLines = mutableListOf<String>()
        buildTreePreview(root, root, depth = 0, maxDepth = 3, maxEntries = 120, into = treeLines)
        val snippets = collectImportantSnippets(root)

        return AgentProjectContext(
            rootPath = root.absolutePath,
            treePreview = treeLines.joinToString("\n").ifBlank { "(пусто)" },
            snippetsPreview = snippets.ifBlank { "Значимые файлы не найдены или недоступны." }
        )
    }

    private fun buildTreePreview(
        root: File,
        dir: File,
        depth: Int,
        maxDepth: Int,
        maxEntries: Int,
        into: MutableList<String>
    ) {
        if (depth > maxDepth || into.size >= maxEntries) return
        val children = dir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .orEmpty()

        for (child in children) {
            if (into.size >= maxEntries) return
            val relative = child.relativeTo(root).invariantSeparatorsPath
            val prefix = "  ".repeat(depth)
            into += if (child.isDirectory) "$prefix- $relative/" else "$prefix- $relative"
            if (child.isDirectory) buildTreePreview(root, child, depth + 1, maxDepth, maxEntries, into)
        }
    }

    private fun collectImportantSnippets(root: File): String {
        val candidates = mutableListOf<File>()
        val preferredNames = setOf(
            "README.md",
            "README",
            "settings.gradle.kts",
            "build.gradle.kts",
            "AGENTS.md"
        )
        preferredNames.forEach { name ->
            val direct = File(root, name)
            if (direct.exists() && direct.isFile) candidates += direct
        }

        val docsRoot = File(root, "Документация")
        if (docsRoot.exists() && docsRoot.isDirectory) {
            docsRoot.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in setOf("md", "txt") }
                ?.sortedBy { it.name.lowercase() }
                ?.take(6)
                ?.let(candidates::addAll)
        }

        val sb = StringBuilder()
        candidates.distinctBy { it.absolutePath }.take(10).forEach { file ->
            val text = runCatching { file.readText() }.getOrDefault("")
            if (text.isBlank()) return@forEach
            val normalized = text.replace(Regex("\\s+"), " ").trim().take(700)
            sb.appendLine("FILE: ${file.relativeTo(root).invariantSeparatorsPath}")
            sb.appendLine(normalized)
            sb.appendLine()
        }
        return sb.toString().trim()
    }
}

internal class AgentHelpCommandUseCase(
    private val projectContextProvider: AgentProjectContextProvider = AgentProjectContextProvider()
) {
    suspend fun execute(
        request: AgentHelpRequest,
        mcpContext: AgentMcpContext,
        ragCatalog: AgentRagCatalog,
        runMcpTool: suspend (request: AgentMcpRequestPlan) -> AgentMcpExecutionResult,
        runRagQuery: suspend (query: String) -> AgentRagExecutionResult,
        callPlanningModel: suspend (messages: List<DeepSeekMessage>) -> String,
        callAnsweringModel: suspend (messages: List<DeepSeekMessage>) -> String
    ): String {
        val projectContext = projectContextProvider.load(request.projectFolderPath)

        val planningPrompt = AgentPlanningPromptFactory.create(
            AgentPlanningPromptInput(
                userRequest = request.userQuestion,
                projectFolderPath = projectContext.rootPath,
                mcpSummary = buildPlanningMcpSummaryForPrompt(mcpContext),
                ragSourcesSummary = buildPlanningRagSummary(ragCatalog)
            )
        )

        val planningRaw = callPlanningModel(
            listOf(DeepSeekMessage(role = "user", content = planningPrompt))
        )
        val decision = AgentPlanningParser.parse(planningRaw) ?: return AGENT_NO_CONTEXT_MESSAGE
        if (!decision.hasContext || decision.strategy == AgentPlanningStrategy.NONE) return AGENT_NO_CONTEXT_MESSAGE
        if (decision.strategy == AgentPlanningStrategy.MCP && decision.mcpRequests.isEmpty()) return AGENT_NO_CONTEXT_MESSAGE
        if (decision.strategy == AgentPlanningStrategy.RAG && decision.ragQueries.isEmpty()) return AGENT_NO_CONTEXT_MESSAGE
        if (decision.strategy == AgentPlanningStrategy.MCP_AND_RAG &&
            decision.mcpRequests.isEmpty() &&
            decision.ragQueries.isEmpty()
        ) {
            return AGENT_NO_CONTEXT_MESSAGE
        }

        val mcpResults: List<AgentMcpExecutionResult>
        val ragResults: List<AgentRagExecutionResult>
        when (decision.strategy) {
            AgentPlanningStrategy.MCP -> {
                mcpResults = executeMcpBranch(decision, runMcpTool)
                ragResults = emptyList()
            }

            AgentPlanningStrategy.RAG -> {
                mcpResults = emptyList()
                ragResults = executeRagBranch(decision, runRagQuery)
            }

            AgentPlanningStrategy.MCP_AND_RAG -> {
                val parallelResults = supervisorScope {
                    val mcpDeferred = async { executeMcpBranch(decision, runMcpTool) }
                    val ragDeferred = async { executeRagBranch(decision, runRagQuery) }
                    mcpDeferred.await() to ragDeferred.await()
                }
                mcpResults = parallelResults.first
                ragResults = parallelResults.second
            }

            AgentPlanningStrategy.NONE -> return AGENT_NO_CONTEXT_MESSAGE
        }

        val mcpContextText = buildMcpExecutionContext(mcpResults)
        val ragContextText = buildRagExecutionContext(ragResults)
        val hasUsefulMcp = mcpResults.any { !it.output.isNullOrBlank() }
        val hasUsefulRag = ragResults.any { !it.payload?.selectedChunks.isNullOrEmpty() }
        if (!hasUsefulMcp && !hasUsefulRag) return AGENT_NO_CONTEXT_MESSAGE

        val answerPrompt = AgentAnswerPromptFactory.create(
            AgentAnswerPromptInput(
                userRequest = request.userQuestion,
                projectFolderPath = projectContext.rootPath,
                planningReason = decision.reason,
                mcpContext = mcpContextText,
                ragContext = ragContextText
            )
        )

        val answer = callAnsweringModel(listOf(DeepSeekMessage(role = "user", content = answerPrompt))).trim()
        return answer.ifEmpty { AGENT_NO_CONTEXT_MESSAGE }
    }

    private suspend fun executeMcpBranch(
        decision: AgentRoutingDecision,
        runMcpTool: suspend (request: AgentMcpRequestPlan) -> AgentMcpExecutionResult
    ): List<AgentMcpExecutionResult> {
        if (decision.mcpRequests.isEmpty()) return emptyList()
        return decision.mcpRequests.map { request -> runMcpTool(request) }
    }

    private suspend fun executeRagBranch(
        decision: AgentRoutingDecision,
        runRagQuery: suspend (query: String) -> AgentRagExecutionResult
    ): List<AgentRagExecutionResult> {
        if (decision.ragQueries.isEmpty()) return emptyList()
        return decision.ragQueries.distinct().map { query -> runRagQuery(query) }
    }

    private fun buildPlanningMcpSummary(context: AgentMcpContext): String {
        if (context.snapshots.isEmpty()) return "MCP недоступен: серверы не настроены."
        val lines = mutableListOf<String>()
        context.snapshots.forEach { snapshot ->
            if (snapshot.error != null) {
                lines += "Server ${snapshot.title}: недоступен (${snapshot.error})"
            } else if (snapshot.tools.isEmpty()) {
                lines += "Server ${snapshot.title}: инструментов нет"
            } else {
                val tools = snapshot.tools.joinToString(", ") { it.name }
                lines += "Server ${snapshot.title}: $tools"
            }
        }
        return lines.joinToString("\n")
    }

    private fun buildPlanningMcpSummaryForPrompt(context: AgentMcpContext): String {
        if (context.snapshots.isEmpty()) return "MCP недоступен: серверы не настроены."
        return buildString {
            context.snapshots.forEach { snapshot ->
                appendLine("server.title: ${snapshot.title}")
                appendLine("server.endpoint: ${snapshot.url}")
                if (snapshot.error != null) {
                    appendLine("server.status: unavailable")
                    appendLine("server.error: ${snapshot.error}")
                    appendLine()
                    return@forEach
                }
                if (snapshot.tools.isEmpty()) {
                    appendLine("server.status: available")
                    appendLine("server.tools: []")
                    appendLine()
                    return@forEach
                }
                appendLine("server.status: available")
                snapshot.tools.forEach { tool ->
                    appendLine("tool.name: ${tool.name}")
                    appendLine("tool.description: ${tool.description.ifBlank { "(empty)" }}")
                    appendLine("tool.supportsWebSocket: ${tool.supportsWebSocket}")
                    appendLine("tool.input_schema: ${tool.inputSchema}")
                    appendLine()
                }
            }
        }.trim()
    }

    private fun buildPlanningRagSummary(catalog: AgentRagCatalog): String {
        if (catalog.sources.isEmpty()) return "RAG недоступен: индекс пустой."
        return catalog.sources
            .distinctBy { "${it.title}|${it.section}|${it.source}" }
            .take(60)
            .joinToString("\n") { src -> "${src.title} | ${src.section} | ${src.source}" }
    }

    private fun buildMcpExecutionContext(results: List<AgentMcpExecutionResult>): String {
        if (results.isEmpty()) return ""
        return results.joinToString("\n\n") { result ->
            buildString {
                appendLine("tool=${result.request.toolName}")
                result.request.endpoint?.let { appendLine("endpoint=$it") }
                if (!result.error.isNullOrBlank()) appendLine("error=${result.error}")
                if (!result.output.isNullOrBlank()) append("output=${result.output}")
            }.trim()
        }
    }

    private fun buildRagExecutionContext(results: List<AgentRagExecutionResult>): String {
        if (results.isEmpty()) return ""
        return results.joinToString("\n\n") { result ->
            val payload = result.payload
            if (payload == null) {
                "query=${result.query}\ncontext=empty"
            } else {
                buildString {
                    appendLine("query=${result.query}")
                    appendLine("retrieval_info=${payload.retrievalInfo}")
                    append("context=${payload.promptContext}")
                }
            }
        }
    }
}

internal suspend fun collectAgentMcpContext(
    remoteMcpService: RemoteMcpService,
    servers: List<McpServerOption>
): AgentMcpContext {
    val snapshots = servers.map { server ->
        runCatching { remoteMcpService.listAvailableTools(server.url) }
            .fold(
                onSuccess = { tools ->
                    AgentMcpServerSnapshot(
                        title = server.title,
                        url = server.url,
                        tools = tools,
                        error = null
                    )
                },
                onFailure = { error ->
                    AgentMcpServerSnapshot(
                        title = server.title,
                        url = server.url,
                        tools = emptyList(),
                        error = error.message ?: error::class.simpleName ?: "unknown"
                    )
                }
            )
    }
    val promptContext = buildString {
        appendLine("Контекст MCP-серверов для режима Агент:")
        if (snapshots.isEmpty()) {
            append("MCP серверы не настроены.")
        } else {
            snapshots.forEach { snapshot ->
                appendLine()
                appendLine("Server: ${snapshot.title} (${snapshot.url})")
                if (snapshot.error != null) {
                    appendLine("Ошибка подключения: ${snapshot.error}")
                } else if (snapshot.tools.isEmpty()) {
                    appendLine("Инструменты не найдены.")
                } else {
                    snapshot.tools.forEach { tool ->
                        val wsSuffix = if (tool.supportsWebSocket) " [WebSocket]" else ""
                        val description = tool.description.ifBlank { "без описания" }
                        appendLine("- ${tool.name}$wsSuffix: $description")
                    }
                }
            }
        }
    }.trim()
    return AgentMcpContext(
        snapshots = snapshots,
        promptContext = promptContext
    )
}

internal fun pickProjectDirectory(initialPath: String?): String? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isMultiSelectionEnabled = false
        initialPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.isDirectory }
            ?.let { currentDirectory = it }
        dialogTitle = "Выберите папку проекта для режима Агент"
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return null
    return chooser.selectedFile?.absolutePath
}

internal suspend fun callAiAgentMainApi(
    requestApi: AiAgentApi,
    model: String,
    apiKey: String,
    requestMessages: List<DeepSeekMessage>,
    deepSeekApi: com.example.aiadventchalengetestllmapi.network.DeepSeekApi,
    openAiApi: com.example.aiadventchalengetestllmapi.network.OpenAiApi,
    gigaChatApi: com.example.aiadventchalengetestllmapi.network.GigaChatApi,
    proxyOpenAiApi: com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi,
    localLlmApi: com.example.aiadventchalengetestllmapi.network.LocalLlmApi,
    options: AgentModelRequestOptions = AgentModelRequestOptions()
): String {
    val request = DeepSeekChatRequest(
        model = model,
        messages = requestMessages,
        temperature = options.temperature,
        topP = options.topP,
        maxTokens = options.maxTokens,
        responseFormat = options.responseFormat
    )
    val response = when (requestApi) {
        AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletionStreaming(apiKey = apiKey, request = request, onChunk = {})
        AiAgentApi.OpenAI -> openAiApi.createChatCompletionStreaming(apiKey = apiKey, request = request, onChunk = {})
        AiAgentApi.GigaChat -> gigaChatApi.createChatCompletionStreaming(accessToken = apiKey, request = request, onChunk = {})
        AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletionStreaming(apiKey = apiKey, request = request, onChunk = {})
        AiAgentApi.LocalLlm -> localLlmApi.createChatCompletionStreaming(request = request, onChunk = {})
    }
    return response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        .ifEmpty { "Пустой ответ от ${requestApi.label}." }
}
