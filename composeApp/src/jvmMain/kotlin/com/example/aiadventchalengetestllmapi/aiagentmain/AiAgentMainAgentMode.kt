package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.mcp.McpToolInfo
import com.example.aiadventchalengetestllmapi.mcp.RemoteMcpService
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import java.io.File
import javax.swing.JFileChooser

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
            if (child.isDirectory) {
                buildTreePreview(root, child, depth + 1, maxDepth, maxEntries, into)
            }
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
            val normalized = text.replace(Regex("\\s+"), " ").trim()
            val preview = normalized.take(700)
            sb.appendLine("FILE: ${file.relativeTo(root).invariantSeparatorsPath}")
            sb.appendLine(preview)
            sb.appendLine()
        }
        return sb.toString().trim()
    }
}

internal class AgentHelpCommandUseCase(
    private val projectContextProvider: AgentProjectContextProvider = AgentProjectContextProvider()
) {
    suspend fun execute(
        projectFolderPath: String,
        userQuestion: String?,
        ragPayload: AiAgentMainRagPayload?,
        mcpContext: AgentMcpContext?,
        callModel: suspend (List<DeepSeekMessage>) -> String
    ): String {
        val question = userQuestion?.trim().orEmpty()
        val effectiveQuestion = if (question.isBlank()) {
            "Дай общий обзор проекта: назначение, основные модули, точки входа и где искать ключевую бизнес-логику."
        } else {
            question
        }
        val projectContext = projectContextProvider.load(projectFolderPath)
        val messages = buildList {
            add(
                DeepSeekMessage(
                    role = "system",
                    content = AGENT_HELP_SYSTEM_PROMPT
                )
            )
            add(
                DeepSeekMessage(
                    role = "system",
                    content = buildProjectContextPrompt(projectContext)
                )
            )
            mcpContext?.promptContext
                ?.takeIf { it.isNotBlank() }
                ?.let { add(DeepSeekMessage(role = "system", content = it)) }
            ragPayload?.promptContext
                ?.takeIf { it.isNotBlank() }
                ?.let { add(DeepSeekMessage(role = "system", content = it)) }
            add(DeepSeekMessage(role = "user", content = effectiveQuestion))
        }
        return callModel(messages)
    }

    private fun buildProjectContextPrompt(context: AgentProjectContext): String = buildString {
        appendLine("Контекст выбранного проекта (локальная папка):")
        appendLine("project_root: ${context.rootPath}")
        appendLine()
        appendLine("Структура каталогов/файлов (ограниченный превью):")
        appendLine(context.treePreview)
        appendLine()
        appendLine("Фрагменты ключевых файлов:")
        appendLine(context.snippetsPreview)
    }.trim()
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
    localLlmApi: com.example.aiadventchalengetestllmapi.network.LocalLlmApi
): String {
    val request = DeepSeekChatRequest(model = model, messages = requestMessages)
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

private const val AGENT_HELP_SYSTEM_PROMPT = """
Ты работаешь в режиме "Агент" для локального проекта.
Твоя задача: отвечать строго по контексту выбранной папки проекта, RAG-контексту и MCP-контексту.
Если данных недостаточно, явно скажи об этом и укажи, какие данные нужны.
Отвечай на русском языке, структурировано и кратко.
"""
