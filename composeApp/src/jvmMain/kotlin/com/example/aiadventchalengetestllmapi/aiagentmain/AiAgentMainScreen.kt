package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.BuildSecrets
import com.example.aiadventchalengetestllmapi.RootScreen
import com.example.aiadventchalengetestllmapi.aiagentmaindb.AiAgentMainDatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.aiagentmaindb.createAiAgentMainDatabase
import com.example.aiadventchalengetestllmapi.embedinggenerationdb.EmbedingGenerationDatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.embedinggenerationdb.createEmbedingGenerationDatabase
import com.example.aiadventchalengetestllmapi.mcp.McpToolInfo
import com.example.aiadventchalengetestllmapi.mcp.RemoteMcpService
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.DeepSeekResponseFormat
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
import com.example.aiadventchalengetestllmapi.network.LocalLlmApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentMainScreen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    MaterialTheme(colorScheme = AiAgentMainScreenTheme.colorScheme()) {
        AiAgentMainChat(
            modifier = Modifier.fillMaxSize(),
            currentScreen = currentScreen,
            onSelectScreen = onSelectScreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAgentMainChat(
    modifier: Modifier = Modifier,
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val openAiApi = remember { OpenAiApi() }
    val gigaChatApi = remember { GigaChatApi() }
    val proxyOpenAiApi = remember { ProxyOpenAiApi() }
    val localLlmApi = remember { LocalLlmApi() }
    val remoteMcpService = remember { RemoteMcpService() }
    val database = remember { createAiAgentMainDatabase(AiAgentMainDatabaseDriverFactory()) }
    val queries = remember(database) { database.chatHistoryQueries }
    val embDb = remember { createEmbedingGenerationDatabase(EmbedingGenerationDatabaseDriverFactory()) }
    val embQueries = remember(embDb) { embDb.embeddingChunksQueries }

    val chats = remember { mutableStateListOf<AiAgentChatItem>() }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var isAgentCommandMenuExpanded by remember { mutableStateOf(false) }
    var screensMenuExpanded by remember { mutableStateOf(false) }
    var selectedApi by remember { mutableStateOf(AiAgentApi.DeepSeek) }
    var apiSelectorExpanded by remember { mutableStateOf(false) }
    var modelSelectorExpanded by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(AiAgentApi.DeepSeek.defaultModel) }
    var activeChatId by remember { mutableStateOf<Long?>(null) }
    var chatSessionId by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isMemoryPanelVisible by remember { mutableStateOf(false) }
    var isMcpPanelVisible by remember { mutableStateOf(false) }
    var isMcpEnabled by remember { mutableStateOf(false) }
    var isRagEnabled by remember { mutableStateOf(true) }
    val mcpServerEnabled = remember {
        mutableStateMapOf<String, Boolean>().apply {
            mcpServerOptions.forEach { put(it.url, false) }
        }
    }
    val mcpServerTools = remember {
        mutableStateMapOf<String, List<String>>().apply {
            mcpServerOptions.forEach { put(it.url, emptyList()) }
        }
    }
    val mcpServerToolInfos = remember {
        mutableStateMapOf<String, List<McpToolInfo>>().apply {
            mcpServerOptions.forEach { put(it.url, emptyList()) }
        }
    }
    val mcpServerWebSocketTools = remember {
        mutableStateMapOf<String, Set<String>>().apply {
            mcpServerOptions.forEach { put(it.url, emptySet()) }
        }
    }
    val mcpServerErrors = remember {
        mutableStateMapOf<String, String?>().apply {
            mcpServerOptions.forEach { put(it.url, null) }
        }
    }
    var mcpToolsSummary by remember { mutableStateOf("MCP выключен") }
    val websocketChatIdsByKey = remember { mutableStateMapOf<String, Long>() }

    // Branching
    val branchesByChat = remember { mutableStateMapOf<Long, SnapshotStateList<AiAgentBranchItem>>() }
    val regularMessagesByChat = remember { mutableStateMapOf<Long, SnapshotStateList<AiAgentMessage>>() }
    val branchCounterByChat = remember { mutableStateMapOf<Long, Int>() }
    val branchVisibilityByChat = remember { mutableStateMapOf<Long, Boolean>() }
    var selectedBranchNumber by remember { mutableStateOf<Int?>(null) }
    var isBranchingEnabled by remember { mutableStateOf(false) }
    val branchNames = remember { mutableStateMapOf<Int, String>() }
    val multiAgentTraceMessagesByChat = remember { mutableStateMapOf<Long, SnapshotStateList<AiAgentMessage>>() }

    // Long-term memory
    var selectedProfileId by remember { mutableStateOf<Long?>(null) }
    val longTermMemory = remember { mutableStateListOf<LongTermMemoryEntry>() }
    var isMemoryPanelExpanded by remember { mutableStateOf(true) }
    var memoryEditingId by remember { mutableStateOf<Long?>(null) }
    var memoryKeyInput by remember { mutableStateOf("") }
    var memoryValueInput by remember { mutableStateOf("") }
    var isMemoryFormVisible by remember { mutableStateOf(false) }

    // Invariants
    val invariants = remember { mutableStateListOf<InvariantEntry>() }
    var isInvariantPanelVisible by remember { mutableStateOf(false) }
    var isInvariantPanelExpanded by remember { mutableStateOf(true) }
    var invariantEditingId by remember { mutableStateOf<Long?>(null) }
    var invariantKeyInput by remember { mutableStateOf("") }
    var invariantValueInput by remember { mutableStateOf("") }
    var isInvariantFormVisible by remember { mutableStateOf(false) }
    var isInvariantsEnabled by remember { mutableStateOf(true) }
    var isStateMachineEnabled by remember { mutableStateOf(true) }
    var invariantViolations by remember { mutableStateOf<List<String>>(emptyList()) }
    var invariantViolationPreviousState by remember { mutableStateOf(AgentState.Idle) }
    var planInvariantCheckFailed by remember { mutableStateOf(false) }
    var planInvariantViolationByAi by remember { mutableStateOf(false) }
    var agentModeState by remember { mutableStateOf(AgentModeState()) }
    var isMultiAgentEnabled by remember { mutableStateOf(false) }
    var isMultiAgentTraceMode by remember { mutableStateOf(false) }
    var isMultiAgentSettingsVisible by remember { mutableStateOf(false) }
    var hasMultiAgentMcpRefreshedThisSession by remember { mutableStateOf(false) }
    val multiAgentSubagents = remember { mutableStateListOf<MultiAgentSubagentDefinition>() }
    val agentHelpCommandUseCase = remember { AgentHelpCommandUseCase() }
    val agentPrReviewUseCase = remember { AgentPrReviewUseCase() }
    val agentAuthTokenUseCase = remember { AgentAuthTokenUseCase(remoteMcpService) }
    val multiAgentOrchestrator = remember { MultiAgentOrchestrator() }

    // State machine
    var agentState by remember { mutableStateOf(AgentState.Idle) }
    var userRequestText by remember { mutableStateOf("") }
    var planText by remember { mutableStateOf("") }
    var executionText by remember { mutableStateOf("") }
    var isErrorState by remember { mutableStateOf(false) }
    var planEditInput by remember { mutableStateOf(TextFieldValue("")) }
    var lastPlanEditText by remember { mutableStateOf("") }
    val executionSteps = remember { mutableStateListOf<PlanStepJson>() }
    var executionCurrentStepIndex by remember { mutableStateOf(0) }
    val executionResults = remember { mutableStateMapOf<Int, String>() }
    var executionRecoveryInstruction by remember { mutableStateOf<String?>(null) }
    var validationPhase by remember { mutableStateOf(1) }
    var validationFailure by remember { mutableStateOf<ValidationFailureInfo?>(null) }
    var validationRecoveryBranchNumber by remember { mutableStateOf<Int?>(null) }
    var validationRecoveryAttempt by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var planClarificationNeeded by remember { mutableStateOf<String?>(null) }
    var startPlanningPhaseHandler: (() -> Unit)? = null

    // ─── DB helpers ────────────────────────────────────────────────────────────

    fun loadChatsFromDb(): List<AiAgentChatItem> =
        queries.selectChats().executeAsList().map { AiAgentChatItem(id = it.id, title = it.title) }

    fun appSettingBool(key: String, default: Boolean): Boolean {
        val row = queries.selectAppSettingByKey(setting_key = key).executeAsOneOrNull()
        return when (row?.setting_value?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            "0", "false", "no", "off" -> false
            else -> default
        }
    }

    fun saveAppSettingBool(key: String, value: Boolean) {
        queries.upsertAppSetting(
            setting_key = key,
            setting_value = if (value) "1" else "0"
        )
    }

    fun appSettingString(key: String, default: String = ""): String =
        queries.selectAppSettingByKey(setting_key = key).executeAsOneOrNull()?.setting_value ?: default

    fun saveAppSettingString(key: String, value: String) {
        queries.upsertAppSetting(
            setting_key = key,
            setting_value = value
        )
    }

    fun mcpServerSettingKey(url: String): String = "mcp_server_enabled::$url"

    fun loadBranchesForChat(chatId: Long): SnapshotStateList<AiAgentBranchItem> {
        val branchMap = linkedMapOf<Int, SnapshotStateList<AiAgentMessage>>()
        queries.selectBranchMessagesByChat(chatId).executeAsList().forEach { row ->
            val stream = if (row.stream == "real") AiAgentStream.Real else AiAgentStream.Raw
            val message = AiAgentMessage(
                text = row.message,
                isUser = row.role == "user",
                paramsInfo = row.params_info,
                stream = stream,
                epoch = row.epoch.toInt(),
                createdAt = row.created_at
            )
            branchMap.getOrPut(row.branch_number.toInt()) { mutableStateListOf() } += message
        }
        val result = mutableStateListOf<AiAgentBranchItem>()
        branchMap.entries.sortedBy { it.key }.forEach { (number, messages) ->
            result += AiAgentBranchItem(number = number, messages = messages)
        }
        return result
    }

    fun appendMessageToBranch(chatId: Long, branchNumber: Int, message: AiAgentMessage) {
        queries.insertBranchMessage(
            chat_id = chatId,
            branch_number = branchNumber.toLong(),
            role = if (message.isUser) "user" else "assistant",
            message = message.text,
            params_info = message.paramsInfo,
            stream = if (message.stream == AiAgentStream.Real) "real" else "raw",
            epoch = message.epoch.toLong(),
            created_at = message.createdAt
        )
    }

    fun loadRegularMessagesForChat(chatId: Long): SnapshotStateList<AiAgentMessage> {
        val messages = queries.selectMessagesByChat(chatId).executeAsList().map { row ->
            AiAgentMessage(
                text = row.message,
                isUser = row.role == "user",
                paramsInfo = row.params_info,
                stream = AiAgentStream.Raw,
                epoch = 0,
                createdAt = row.created_at
            )
        }
        return mutableStateListOf<AiAgentMessage>().also { it += messages }
    }

    fun appendMessageToRegularChat(chatId: Long, message: AiAgentMessage) {
        queries.insertMessage(
            chat_id = chatId,
            api = selectedApi.label,
            model = modelInput.trim().ifEmpty { selectedApi.defaultModel },
            role = if (message.isUser) "user" else "assistant",
            message = message.text,
            params_info = message.paramsInfo,
            created_at = message.createdAt
        )
    }

    fun loadMultiAgentTraceMessagesForChat(chatId: Long): SnapshotStateList<AiAgentMessage> {
        val rows = queries.selectMultiAgentEventsByChat(chat_id = chatId).executeAsList()
        val messages = rows.map { row ->
            AiAgentMessage(
                text = "[${row.actor_type}:${row.actor_key}] ${row.message}",
                isUser = row.role == "user",
                paramsInfo = "stage=multiagent|trace|channel=${row.channel}",
                stream = AiAgentStream.Raw,
                epoch = 0,
                createdAt = row.created_at
            )
        }
        return mutableStateListOf<AiAgentMessage>().also { it += messages }
    }

    fun refreshMultiAgentSubagents() {
        val fromDb = queries.selectAllMultiAgentSubagents().executeAsList().map { row ->
            MultiAgentSubagentDefinition(
                key = row.agent_key,
                title = row.title,
                description = row.description,
                systemPrompt = row.system_prompt,
                isEnabled = row.is_enabled == 1L
            )
        }
        multiAgentSubagents.clear()
        multiAgentSubagents += fromDb
    }

    fun ensureDefaultMultiAgentSubagents() {
        val existing = queries.selectAllMultiAgentSubagents().executeAsList()
        val existingKeys = existing.map { it.agent_key.lowercase() }.toSet()
        val now = System.currentTimeMillis()
        defaultMultiAgentSubagents().forEach { subagent ->
            if (existingKeys.contains(subagent.key.lowercase())) return@forEach
            queries.insertMultiAgentSubagent(
                agent_key = subagent.key,
                title = subagent.title,
                description = subagent.description,
                is_enabled = if (subagent.isEnabled) 1 else 0,
                system_prompt = subagent.systemPrompt,
                created_at = now,
                updated_at = now
            )
        }
    }

    fun updateMultiAgentSubagentEnabled(agentKey: String, enabled: Boolean) {
        queries.updateMultiAgentSubagentEnabledByKey(
            is_enabled = if (enabled) 1 else 0,
            updated_at = System.currentTimeMillis(),
            agent_key = agentKey
        )
        refreshMultiAgentSubagents()
    }

    fun appendMultiAgentEvent(
        chatId: Long,
        runId: Long?,
        event: MultiAgentEvent
    ) {
        val now = System.currentTimeMillis()
        queries.insertMultiAgentEvent(
            run_id = runId,
            chat_id = chatId,
            channel = event.channel.name.lowercase(),
            actor_type = event.actorType,
            actor_key = event.actorKey,
            role = event.role,
            message = event.message,
            metadata_json = event.metadataJson,
            created_at = now
        )
        val traceMessages = multiAgentTraceMessagesByChat.getOrPut(chatId) { mutableStateListOf() }
        val traceMessage = AiAgentMessage(
            text = "[${event.actorType}:${event.actorKey}] ${event.message}",
            isUser = event.role == "user",
            paramsInfo = "stage=multiagent|trace|channel=${event.channel.name.lowercase()}",
            stream = AiAgentStream.Raw,
            epoch = 0,
            createdAt = now
        )
        traceMessages += traceMessage
    }

    fun buildMultiAgentRunConversationContext(runId: Long): String {
        val events = queries.selectMultiAgentEventsByRun(run_id = runId).executeAsList()
        val steps = queries.selectMultiAgentStepsByRun(run_id = runId).executeAsList()
        return buildString {
            appendLine("Run #$runId")
            appendLine("Events:")
            if (events.isEmpty()) {
                appendLine("- нет событий")
            } else {
                events.takeLast(120).forEach { event ->
                    appendLine("[${event.channel}] ${event.actor_type}:${event.actor_key} (${event.role})")
                    appendLine(event.message)
                    appendLine()
                }
            }
            appendLine("Steps:")
            if (steps.isEmpty()) {
                appendLine("- нет шагов")
            } else {
                steps.forEach { step ->
                    appendLine("#${step.step_index} [${step.status}] ${step.assignee_agent_key}: ${step.title}")
                    if (step.input_payload.isNotBlank()) appendLine("input: ${step.input_payload}")
                    if (step.output_payload.isNotBlank()) appendLine("output: ${step.output_payload}")
                    if (step.validation_note.isNotBlank()) appendLine("note: ${step.validation_note}")
                    appendLine()
                }
            }
        }.trim()
    }

    fun buildRegularConversationContext(messages: List<AiAgentMessage>): String {
        return messages.takeLast(20).joinToString("\n") { msg ->
            val role = if (msg.isUser) "user" else "assistant"
            "$role: ${msg.text}"
        }
    }

    fun escapeJsonValue(raw: String): String =
        raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun insertMultiAgentToolCall(log: MultiAgentToolCallLog): Long {
        queries.insertMultiAgentToolCall(
            run_id = log.runId,
            step_id = log.stepId,
            tool_kind = log.toolKind.name,
            request_payload = log.requestPayload,
            response_payload = log.responsePayload,
            status = log.status,
            error_code = log.errorCode,
            error_message = log.errorMessage,
            latency_ms = log.latencyMs,
            created_at = System.currentTimeMillis()
        )
        return queries.selectLastInsertedMultiAgentToolCallId().executeAsOne()
    }

    fun websocketChatKey(serverUrl: String, toolName: String): String =
        "${serverUrl.trim().lowercase()}::${toolName.trim().lowercase()}"

    fun websocketChatTitle(serverTitle: String, toolName: String): String =
        "WebSocket • $serverTitle • $toolName"

    suspend fun buildRagPayloadForPrompt(query: String, forceEnabled: Boolean = false): AiAgentMainRagPayload? {
        if ((!isRagEnabled && !forceEnabled) || query.isBlank()) return null
        val rows = embQueries.selectAll().executeAsList().map { row ->
            AiAgentMainEmbeddingChunkRecord(
                source = row.source,
                title = row.title,
                section = row.section,
                chunkId = row.chunk_id,
                strategy = row.strategy,
                chunkText = row.chunk_text,
                embeddingJson = row.embedding_json
            )
        }
        return buildAiAgentMainRagPayload(query = query, chunks = rows)
    }

    fun loadAgentRagCatalog(): AgentRagCatalog {
        val sources = embQueries.selectAll().executeAsList().map { row ->
            AgentRagSourceMeta(
                title = row.title,
                section = row.section,
                source = row.source
            )
        }.distinctBy { "${it.title}|${it.section}|${it.source}" }
        return AgentRagCatalog(sources = sources)
    }

    fun ensureWebSocketLogChatId(server: McpServerOption, toolName: String): Long {
        val key = websocketChatKey(server.url, toolName)
        val existing = websocketChatIdsByKey[key]
        if (existing != null) {
            val stillExists = queries.selectChatById(existing).executeAsOneOrNull() != null
            if (stillExists) return existing
            websocketChatIdsByKey.remove(key)
        }

        val title = websocketChatTitle(server.title, toolName)
        val existingByTitle = loadChatsFromDb().firstOrNull { it.title == title }?.id
        if (existingByTitle != null) {
            websocketChatIdsByKey[key] = existingByTitle
            return existingByTitle
        }

        queries.insertChat(
            title = title,
            created_at = System.currentTimeMillis(),
            selected_profile_id = null
        )
        val chatId = queries.selectLastInsertedChatId().executeAsOne()
        websocketChatIdsByKey[key] = chatId
        chats.clear()
        chats += loadChatsFromDb()
        return chatId
    }

    fun ensureWebSocketLogBranch(chatId: Long): SnapshotStateList<AiAgentMessage> {
        val existing = branchesByChat[chatId]?.firstOrNull { it.number == 1 }?.messages
        if (existing != null) return existing

        val fromDb = loadBranchesForChat(chatId)
        if (fromDb.isNotEmpty()) {
            branchesByChat[chatId] = fromDb
            branchCounterByChat[chatId] = fromDb.maxOfOrNull { it.number } ?: 1
            branchVisibilityByChat.putIfAbsent(chatId, true)
            return fromDb.firstOrNull { it.number == 1 }?.messages ?: mutableStateListOf<AiAgentMessage>().also { fallback ->
                val items = mutableStateListOf(AiAgentBranchItem(number = 1, messages = fallback))
                branchesByChat[chatId] = items
                branchCounterByChat[chatId] = 1
                branchVisibilityByChat.putIfAbsent(chatId, true)
            }
        }

        val messages = mutableStateListOf<AiAgentMessage>()
        branchesByChat[chatId] = mutableStateListOf(AiAgentBranchItem(number = 1, messages = messages))
        branchCounterByChat[chatId] = 1
        branchVisibilityByChat.putIfAbsent(chatId, true)
        return messages
    }

    fun appendWebSocketLog(
        server: McpServerOption,
        toolName: String,
        direction: String,
        payload: String
    ) {
        val chatId = ensureWebSocketLogChatId(server, toolName)
        val messages = ensureWebSocketLogBranch(chatId)
        val message = AiAgentMessage(
            text = "[$direction] $payload",
            isUser = direction == "SEND",
            paramsInfo = "stage=websocket|server=${server.url}|tool=$toolName|direction=$direction",
            stream = AiAgentStream.Raw,
            epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        messages += message
        appendMessageToBranch(chatId, 1, message)
    }

    // ─── Long-term memory ──────────────────────────────────────────────────────

    fun loadLongTermMemoryForProfile(profileId: Long) {
        val entries = queries.selectMemoryEntriesByProfile(profile_id = profileId).executeAsList().map { row ->
            LongTermMemoryEntry(
                id = row.id,
                key = row.entry_key,
                value = row.entry_value,
                createdAt = row.created_at,
                updatedAt = row.updated_at
            )
        }
        longTermMemory.clear()
        longTermMemory += entries
    }

    fun insertLongTermEntry(key: String, value: String) {
        val profileId = selectedProfileId ?: return
        val now = System.currentTimeMillis()
        queries.insertMemoryEntry(
            profile_id = profileId,
            entry_key = key.trim(),
            entry_value = value.trim(),
            created_at = now,
            updated_at = now
        )
        loadLongTermMemoryForProfile(profileId)
    }

    fun updateLongTermEntry(id: Long, value: String) {
        val profileId = selectedProfileId ?: return
        queries.updateMemoryEntry(value.trim(), System.currentTimeMillis(), id)
        loadLongTermMemoryForProfile(profileId)
    }

    fun deleteLongTermEntry(id: Long) {
        val profileId = selectedProfileId ?: return
        queries.deleteMemoryEntry(id)
        loadLongTermMemoryForProfile(profileId)
    }

    // ─── Invariants ────────────────────────────────────────────────────────────

    fun loadInvariants() {
        val entries = queries.selectAllInvariantEntries().executeAsList().map { row ->
            InvariantEntry(
                id = row.id,
                key = row.entry_key,
                value = row.entry_value,
                createdAt = row.created_at,
                updatedAt = row.updated_at
            )
        }
        invariants.clear()
        invariants += entries
    }

    fun insertInvariantEntry(key: String, value: String) {
        val now = System.currentTimeMillis()
        queries.insertInvariantEntry(
            entry_key = key.trim(),
            entry_value = value.trim(),
            created_at = now,
            updated_at = now
        )
        loadInvariants()
    }

    fun updateInvariantEntry(id: Long, value: String) {
        queries.updateInvariantEntry(value.trim(), System.currentTimeMillis(), id)
        loadInvariants()
    }

    fun deleteInvariantEntry(id: Long) {
        queries.deleteInvariantEntry(id)
        loadInvariants()
    }

    fun ensureProfileAndLtm() {
        val profiles = queries.selectProfiles().executeAsList()
        val profileId = if (profiles.isNotEmpty()) {
            profiles.first().id
        } else {
            queries.insertProfile(
                name = "Профиль 1",
                is_long_term_memory_enabled = 1,
                is_system_prompt_enabled = 0,
                system_prompt_text = "",
                is_summarization_enabled = 0,
                summarize_after_tokens = "10000",
                is_sliding_window_enabled = 0,
                sliding_window_size = "12",
                is_sticky_facts_enabled = 0,
                sticky_facts_window_size = "12",
                sticky_facts_system_message = "",
                is_branching_enabled = 0,
                show_raw_history = 0
            )
            queries.selectProfiles().executeAsList().first().id
        }
        selectedProfileId = profileId
        loadLongTermMemoryForProfile(profileId)
        loadInvariants()
    }

    // ─── State machine helpers ─────────────────────────────────────────────────

    fun buildLtmSystemMessage(): DeepSeekMessage? {
        if (longTermMemory.isEmpty()) return null
        val content = buildString {
            longTermMemory.forEach { e -> appendLine("${e.key}: ${e.value}") }
            append("\nУчитывай эту информацию во всех ответах.")
        }
        return DeepSeekMessage(role = "system", content = content.trim())
    }

    fun buildInvariantsSystemMessage(): DeepSeekMessage? {
        if (!isInvariantsEnabled || invariants.isEmpty()) return null
        val content = buildString {
            appendLine("Инварианты (ограничения, которые ВСЕГДА должны соблюдаться):")
            invariants.forEach { e -> appendLine("- ${e.key}: ${e.value}") }
        }
        return DeepSeekMessage(role = "system", content = content.trim())
    }

    suspend fun buildMcpToolsContext(): String {
        if (!isMcpEnabled) {
            mcpToolsSummary = "MCP выключен"
            return "MCP отключен."
        }

        val enabledServers = mcpServerOptions.filter { mcpServerEnabled[it.url] == true }
        if (enabledServers.isEmpty()) {
            mcpToolsSummary = "MCP включен, серверы не выбраны"
            return "MCP включен, но не выбраны серверы."
        }

        val blocks = mutableListOf<String>()
        enabledServers.forEach { server ->
            runCatching { remoteMcpService.listAvailableTools(server.url) }
                .onSuccess { tools ->
                    mcpServerToolInfos[server.url] = tools
                    mcpServerTools[server.url] = tools.map { it.name }
                    mcpServerWebSocketTools[server.url] = tools
                        .filter { it.supportsWebSocket }
                        .map { it.name.lowercase() }
                        .toSet()
                    mcpServerErrors[server.url] = null
                    val toolsText = if (tools.isEmpty()) {
                        "инструменты не найдены"
                    } else {
                        tools.joinToString("\n") { tool ->
                            val description = tool.description.ifBlank { "без описания" }
                            val wsSuffix = if (tool.supportsWebSocket) " [WebSocket]" else ""
                            "- ${tool.name}$wsSuffix: $description"
                        }
                    }
                    blocks += "Сервер: ${server.title}\n$toolsText"
                }
                .onFailure { error ->
                    blocks += "Сервер: ${server.title}\nошибка подключения: ${error.message ?: error::class.simpleName ?: "unknown"}"
                }
        }
        val context = blocks.joinToString("\n\n")
        mcpToolsSummary = context
        return context
    }

    suspend fun buildMcpSystemMessage(): DeepSeekMessage? {
        if (!isMcpEnabled) return null
        val context = buildMcpToolsContext().trim()
        if (context.isBlank()) return null
        return DeepSeekMessage(
            role = "system",
            content = "Контекст MCP-серверов (актуальные инструменты):\n$context"
        )
    }

    fun enabledMcpServers(): List<McpServerOption> =
        mcpServerOptions.filter { mcpServerEnabled[it.url] == true }

    fun updateMcpSummaryFast() {
        if (!isMcpEnabled) {
            mcpToolsSummary = "MCP disabled"
            return
        }
        val enabledServers = enabledMcpServers()
        if (enabledServers.isEmpty()) {
            mcpToolsSummary = "MCP enabled, no servers selected"
            return
        }
        mcpToolsSummary = enabledServers.joinToString("\n\n") { server ->
            val tools = mcpServerTools[server.url].orEmpty()
            val wsTools = mcpServerWebSocketTools[server.url].orEmpty()
            val error = mcpServerErrors[server.url]
            val body = when {
                error != null -> "connection error: $error"
                tools.isEmpty() -> "no tools found"
                else -> tools.joinToString("\n") { name ->
                    val wsSuffix = if (wsTools.contains(name.lowercase())) " [WebSocket]" else ""
                    "- $name$wsSuffix"
                }
            }
            "Server: ${server.title}\n$body"
        }
    }

    suspend fun refreshMcpServerTools(serverUrl: String) {
        val server = mcpServerOptions.firstOrNull { it.url == serverUrl } ?: return
        runCatching { remoteMcpService.listAvailableTools(server.url) }
            .onSuccess { tools ->
                mcpServerToolInfos[server.url] = tools
                mcpServerTools[server.url] = tools.map { it.name }
                mcpServerWebSocketTools[server.url] = tools
                    .filter { it.supportsWebSocket }
                    .map { it.name.lowercase() }
                    .toSet()
                mcpServerErrors[server.url] = null
            }
            .onFailure { error ->
                mcpServerToolInfos[server.url] = emptyList()
                mcpServerTools[server.url] = emptyList()
                mcpServerWebSocketTools[server.url] = emptySet()
                mcpServerErrors[server.url] = error.message ?: error::class.simpleName ?: "unknown"
            }
        updateMcpSummaryFast()
    }

    suspend fun refreshEnabledMcpServerTools() {
        enabledMcpServers().forEach { server ->
            refreshMcpServerTools(server.url)
        }
        updateMcpSummaryFast()
    }

    fun updateMcpToolsSummaryCached() {
        if (!isMcpEnabled) {
            mcpToolsSummary = "MCP выключен"
            return
        }
        val enabledServers = enabledMcpServers()
        if (enabledServers.isEmpty()) {
            mcpToolsSummary = "MCP включен, серверы не выбраны"
            return
        }
        mcpToolsSummary = enabledServers.joinToString("\n\n") { server ->
            val tools = mcpServerTools[server.url].orEmpty()
            val wsTools = mcpServerWebSocketTools[server.url].orEmpty()
            val error = mcpServerErrors[server.url]
            val body = when {
                error != null -> "ошибка подключения: $error"
                tools.isEmpty() -> "инструменты не найдены"
                else -> tools.joinToString("\n") { name ->
                    val wsSuffix = if (wsTools.contains(name.lowercase())) " [WebSocket]" else ""
                    "- $name$wsSuffix"
                }
            }
            "Сервер: ${server.title}\n$body"
        }
    }

    fun buildMcpToolsContextCached(): String {
        updateMcpSummaryFast()
        return mcpToolsSummary
    }

    fun buildMcpToolsContextForPrompt(): String {
        if (!isMcpEnabled) return "MCP отключен."
        val enabledServers = enabledMcpServers()
        if (enabledServers.isEmpty()) return "MCP включен, но серверы не выбраны."

        return enabledServers.joinToString("\n\n") { server ->
            val toolInfos = mcpServerToolInfos[server.url].orEmpty()
            val error = mcpServerErrors[server.url]
            val body = when {
                error != null -> "ошибка подключения: $error"
                toolInfos.isEmpty() -> {
                    val names = mcpServerTools[server.url].orEmpty()
                    if (names.isEmpty()) "инструменты не найдены"
                    else names.joinToString("\n") { "- $it" }
                }
                else -> toolInfos.joinToString("\n\n") { tool ->
                    val wsSuffix = if (tool.supportsWebSocket) " [WebSocket]" else ""
                    val description = tool.description.ifBlank { "без описания" }
                    buildString {
                        appendLine("- ${tool.name}$wsSuffix")
                        appendLine("  description: $description")
                        append("  input_schema: ${tool.inputSchema}")
                    }
                }
            }
            "Server: ${server.title}\n$body"
        }
    }

    suspend fun buildMcpSystemMessageCached(): DeepSeekMessage? {
        if (!isMcpEnabled) return null
        val context = buildMcpToolsContextCached().trim()
        if (context.isBlank()) return null
        return DeepSeekMessage(
            role = "system",
            content = "Контекст MCP-серверов (кэш инструментов):\n$context"
        )
    }

    fun endpointMatchesServer(endpoint: String, serverUrl: String): Boolean {
        val endpointTrimmed = endpoint.trim()
        if (endpointTrimmed.equals(serverUrl, ignoreCase = true)) return true
        if (endpointTrimmed.equals(serverUrl.replace("http://", "ws://"), ignoreCase = true)) return true
        if (endpointTrimmed.equals(serverUrl.replace("https://", "wss://"), ignoreCase = true)) return true

        return if (endpointTrimmed.contains("://")) {
            runCatching {
                val endpointUri = java.net.URI(endpointTrimmed)
                val serverUri = java.net.URI(serverUrl)
                val endpointHost = endpointUri.host?.lowercase().orEmpty()
                val serverHost = serverUri.host?.lowercase().orEmpty()
                val endpointPort = if (endpointUri.port >= 0) endpointUri.port else endpointUri.toURL().defaultPort
                val serverPort = if (serverUri.port >= 0) serverUri.port else serverUri.toURL().defaultPort
                endpointHost == serverHost && endpointPort == serverPort
            }.getOrDefault(false)
        } else {
            true
        }
    }

    fun isMcpTransportEndpoint(endpoint: String, serverUrl: String): Boolean {
        val endpointTrimmed = endpoint.trim()
        return endpointTrimmed.equals(serverUrl, ignoreCase = true) ||
            endpointTrimmed.equals(serverUrl.replace("http://", "ws://"), ignoreCase = true) ||
            endpointTrimmed.equals(serverUrl.replace("https://", "wss://"), ignoreCase = true)
    }

    suspend fun runMcpToolForExecution(
        step: PlanStepJson,
        taskContext: String,
        previousResultsFormatted: String,
        requestedToolName: String? = null,
        requestedEndpoint: String? = null,
        requestedArguments: Map<String, Any?> = emptyMap()
    ): String? {
        if (!isMcpEnabled) return null
        val toolName = requestedToolName?.trim().orEmpty().ifEmpty { step.tool?.trim().orEmpty() }
        if (toolName.isEmpty()) return null

        val endpointNormalized = requestedEndpoint?.trim()?.takeIf { it.isNotBlank() }
        val enabledServers = enabledMcpServers()
        val targetServer = enabledServers.firstOrNull { server ->
            val hasTool = mcpServerTools[server.url].orEmpty().any { it.equals(toolName, ignoreCase = true) }
            if (!hasTool) return@firstOrNull false
            if (endpointNormalized == null) return@firstOrNull true
            endpointMatchesServer(endpointNormalized, server.url)
        } ?: return "MCP tool '$toolName' не найден среди активных серверов."

        val baseArguments = mapOf(
            "step_id" to step.stepId,
            "step_description" to step.description,
            "task_context" to taskContext,
            "previous_results" to previousResultsFormatted
        )
        val arguments = baseArguments + requestedArguments
        val isWebSocketTool = mcpServerWebSocketTools[targetServer.url]
            .orEmpty()
            .contains(toolName.lowercase())
        val useAuxWebSocket = isWebSocketTool &&
            endpointNormalized != null &&
            !isMcpTransportEndpoint(endpointNormalized, targetServer.url)

        return runCatching {
            if (useAuxWebSocket) {
                remoteMcpService.callToolWithAuxWebSocket(
                    serverUrl = targetServer.url,
                    webSocketEndpoint = endpointNormalized ?: targetServer.url,
                    toolName = toolName,
                    arguments = arguments,
                    onWebSocketLog = { direction, payload ->
                        appendWebSocketLog(
                            server = targetServer,
                            toolName = toolName,
                            direction = direction,
                            payload = payload
                        )
                    }
                )
            } else if (isWebSocketTool) {
                remoteMcpService.callToolViaWebSocket(
                    serverUrl = targetServer.url,
                    webSocketEndpoint = endpointNormalized,
                    toolName = toolName,
                    arguments = arguments,
                    onWebSocketLog = { direction, payload ->
                        appendWebSocketLog(
                            server = targetServer,
                            toolName = toolName,
                            direction = direction,
                            payload = payload
                        )
                    }
                )
            } else {
                remoteMcpService.callTool(targetServer.url, toolName, arguments)
            }
        }
            .fold(
                onSuccess = { output ->
                    val transport = when {
                        useAuxWebSocket -> "HTTP+AuxWebSocket"
                        isWebSocketTool -> "WebSocket"
                        else -> "HTTP"
                    }
                    val endpointSuffix = endpointNormalized?.let { ", endpoint=$it" }.orEmpty()
                    "MCP tool '$toolName' (${targetServer.title}, $transport$endpointSuffix) output:\n$output"
                },
                onFailure = { error -> "MCP tool '$toolName' (${targetServer.title}) error: ${error.message ?: error::class.simpleName ?: "unknown"}" }
            )
    }

    fun parseExecutionToolRequest(
        response: String,
        fallbackToolName: String?
    ): ExecutionToolRequest? {
        val root = tryParseJsonExecution(response)?.jsonObject ?: return null
        val requestObj = root["tool_request"]?.jsonObject ?: return null
        val shouldCall = requestObj["should_call"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!shouldCall) return null

        val toolName = requestObj["tool_name"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            .orEmpty()
            .ifEmpty { fallbackToolName?.trim().orEmpty() }
        if (toolName.isBlank()) return null

        val endpoint = requestObj["endpoint"]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: (requestObj["connection"] as? JsonObject)
                ?.get("endpoint")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        val reason = requestObj["reason"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val arguments = requestObj["arguments"]?.jsonObject
            ?.mapValues { (_, value) -> jsonElementToAny(value) }
            ?: emptyMap()

        return ExecutionToolRequest(
            toolName = toolName,
            endpoint = endpoint,
            reason = reason,
            arguments = arguments
        )
    }

    fun buildInvariantsPromptSuffix(): String {
        if (!isInvariantsEnabled || invariants.isEmpty()) return ""
        return buildString {
            appendLine()
            appendLine("Обязательно проверь выполнение этих ограничений:")
            invariants.forEach { e -> appendLine("- ${e.key}: ${e.value}") }
        }.trimEnd()
    }

    fun checkInvariantViolations(response: String): List<String> {
        if (!isInvariantsEnabled || invariants.isEmpty()) return emptyList()
        val violations = mutableListOf<String>()
        val responseLower = response.lowercase()
        try {
            val json = lenientJson.parseToJsonElement(response).jsonObject
            // Formal validation: failed criteria matching invariant keys
            json["criteria_results"]?.jsonArray?.forEach { item ->
                val obj = item.jsonObject
                if (obj["passed"]?.jsonPrimitive?.booleanOrNull == false) {
                    val criterion = obj["criterion"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    invariants.forEach { inv ->
                        if (criterion.contains(inv.key.lowercase()) || criterion.contains(inv.value.lowercase())) {
                            val entry = "${inv.key}: ${inv.value}"
                            if (entry !in violations) violations += entry
                        }
                    }
                }
            }
            // Execution: error result or not ready
            val resultType = json["result"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
            val nextStepReady = json["next_step_ready"]?.jsonPrimitive?.booleanOrNull
            if (resultType == "error" || nextStepReady == false) {
                val reasoning = json["reasoning"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                invariants.forEach { inv ->
                    if (reasoning.contains(inv.key.lowercase())) {
                        val entry = "${inv.key}: ${inv.value}"
                        if (entry !in violations) violations += entry
                    }
                }
            }
            // Semantic: overall_passed == false, check issues
            if (json["overall_passed"]?.jsonPrimitive?.booleanOrNull == false) {
                val issues = json["issues"]?.jsonArray?.joinToString(" ") {
                    it.jsonPrimitive.contentOrNull.orEmpty()
                }?.lowercase() ?: ""
                invariants.forEach { inv ->
                    if (issues.contains(inv.key.lowercase()) || issues.contains(inv.value.lowercase())) {
                        val entry = "${inv.key}: ${inv.value}"
                        if (entry !in violations) violations += entry
                    }
                }
            }
        } catch (_: Exception) {}
        // Text heuristic: invariant key/value near violation markers
        if (violations.isEmpty()) {
            val markers = listOf(
                "нарушено", "нарушение", "violated", "не соответствует",
                "не выполнено", "failed", "не пройден", "constraint violation"
            )
            invariants.forEach { inv ->
                if (markers.any { marker ->
                    val idx = responseLower.indexOf(marker)
                    if (idx < 0) return@any false
                    val window = responseLower.substring(maxOf(0, idx - 150), minOf(responseLower.length, idx + 150))
                    window.contains(inv.key.lowercase()) || window.contains(inv.value.lowercase())
                }) {
                    val entry = "${inv.key}: ${inv.value}"
                    if (entry !in violations) violations += entry
                }
            }
        }
        return violations
    }

    fun createStateBranch(chatId: Long, branchNumber: Int, name: String): SnapshotStateList<AiAgentMessage> {
        val branches = branchesByChat.getOrPut(chatId) { mutableStateListOf() }
        val messages = mutableStateListOf<AiAgentMessage>()
        branches += AiAgentBranchItem(number = branchNumber, messages = messages)
        branchCounterByChat[chatId] = branchNumber
        branchVisibilityByChat[chatId] = true
        branchNames[branchNumber] = name
        return messages
    }

    fun createAutoStateBranch(chatId: Long, name: String): Pair<Int, SnapshotStateList<AiAgentMessage>> {
        val nextBranchNumber = (branchCounterByChat[chatId] ?: 0) + 1
        return nextBranchNumber to createStateBranch(chatId, nextBranchNumber, name)
    }

    fun appendValidationRecoveryHistory(
        chatId: Long,
        text: String,
        paramsInfo: String
    ) {
        val branchNumber = validationRecoveryBranchNumber ?: return
        val messages = branchesByChat[chatId]?.firstOrNull { it.number == branchNumber }?.messages ?: return
        val message = AiAgentMessage(
            text = text,
            isUser = false,
            paramsInfo = paramsInfo,
            stream = AiAgentStream.Raw,
            epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        messages += message
        appendMessageToBranch(chatId, branchNumber, message)
    }

    suspend fun callPlanningApi(userQuery: String): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val ragPayload = buildRagPayloadForPrompt(userQuery)
        val basePrompt = PLANNING_PROMPT_TEMPLATE.replace("{user_query}", userQuery)
        val promptText = if (ragPayload == null) basePrompt else "$basePrompt\n\n${ragPayload.promptContext}"
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            ragPayload?.let { add(DeepSeekMessage(role = "system", content = it.promptContext)) }
            add(DeepSeekMessage(role = "user", content = promptText))
        }
        val request = DeepSeekChatRequest(
            model = model,
            messages = messages,
            temperature = 0.1,
            maxTokens = 2000,
            topP = 0.9,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0,
            responseFormat = DeepSeekResponseFormat(type = "json_object")
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

    suspend fun callClarificationPlanningApi(
        originalUserQuery: String,
        clarificationQuestion: String,
        userClarificationResponse: String
    ): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val ragPayload = buildRagPayloadForPrompt(
            listOf(originalUserQuery, clarificationQuestion, userClarificationResponse)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        val basePrompt = CLARIFICATION_FOLLOW_UP_PROMPT_TEMPLATE
            .replace("{original_user_query}", originalUserQuery)
            .replace("{clarification_question}", clarificationQuestion)
            .replace("{user_clarification_response}", userClarificationResponse)
            .replace("{available_tools_formatted}", buildMcpToolsContextCached())
        val promptText = if (ragPayload == null) basePrompt else "$basePrompt\n\n${ragPayload.promptContext}"
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            ragPayload?.let { add(DeepSeekMessage(role = "system", content = it.promptContext)) }
            add(DeepSeekMessage(role = "user", content = promptText))
        }
        val request = DeepSeekChatRequest(
            model = model,
            messages = messages,
            temperature = 0.1,
            maxTokens = 2000,
            topP = 0.9,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0,
            responseFormat = DeepSeekResponseFormat(type = "json_object")
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

    fun handleClarificationInPlanResult(
        result: String,
        chatId: Long,
        planningMessages: SnapshotStateList<AiAgentMessage>
    ) {
        val parsedPlan = try { lenientJson.decodeFromString<PlanJson>(result) } catch (_: Exception) { null }
        val clarification = parsedPlan?.clarificationNeeded?.takeIf { it.isNotBlank() }
        planClarificationNeeded = clarification
        if (clarification != null) {
            val clarMsg = AiAgentMessage(
                text = clarification, isUser = false,
                paramsInfo = "stage=planning|clarification", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += clarMsg
            appendMessageToBranch(chatId, 2, clarMsg)
        }
    }

    suspend fun callPlanEditApi(originalPlanJson: String, userEditText: String): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val promptText = PLAN_EDIT_PROMPT_TEMPLATE
            .replace("{original_plan_json}", originalPlanJson)
            .replace("{user_edit_text}", userEditText)
        val ragPayload = buildRagPayloadForPrompt(promptText)
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            ragPayload?.let { add(DeepSeekMessage(role = "system", content = it.promptContext)) }
            add(DeepSeekMessage(role = "user", content = promptText))
        }
        val request = DeepSeekChatRequest(
            model = model,
            messages = messages,
            temperature = 0.1,
            maxTokens = 2000,
            topP = 0.9,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0,
            responseFormat = DeepSeekResponseFormat(type = "json_object")
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

    suspend fun callApi(userMessageText: String): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val ragPayload = buildRagPayloadForPrompt(userMessageText)
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            ragPayload?.let { add(DeepSeekMessage(role = "system", content = it.promptContext)) }
            add(DeepSeekMessage(role = "user", content = userMessageText))
        }
        val request = DeepSeekChatRequest(model = model, messages = messages)
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

    suspend fun callApiWithHistory(history: List<AiAgentMessage>): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val latestUserQuery = history.lastOrNull { it.isUser }?.text.orEmpty()
        val ragPayload = buildRagPayloadForPrompt(latestUserQuery)
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            ragPayload?.let { add(DeepSeekMessage(role = "system", content = it.promptContext)) }
            history.forEach { message ->
                add(
                    DeepSeekMessage(
                        role = if (message.isUser) "user" else "assistant",
                        content = message.text
                    )
                )
            }
        }
        val request = DeepSeekChatRequest(model = model, messages = messages)
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

    fun updateInputText(next: TextFieldValue) {
        inputText = next
        isAgentCommandMenuExpanded = shouldShowAgentCommandMenu(
            text = next.text,
            isAgentModeEnabled = agentModeState.isEnabled
        )
    }

    fun appendRegularMessage(chatId: Long, message: AiAgentMessage) {
        val regularMessages = regularMessagesByChat.getOrPut(chatId) { loadRegularMessagesForChat(chatId) }
        regularMessages += message
        appendMessageToRegularChat(chatId, message)
    }

    fun sendRegularChatMessage(messageText: String = inputText.text.trim()) {
        val chatId = activeChatId ?: return
        val trimmed = messageText.trim()
        if (trimmed.isEmpty() || isLoading) return

        val regularMessages = regularMessagesByChat.getOrPut(chatId) { loadRegularMessagesForChat(chatId) }
        isBranchingEnabled = false
        selectedBranchNumber = null
        agentState = AgentState.Idle
        planClarificationNeeded = null

        val userMsg = AiAgentMessage(
            text = trimmed,
            isUser = true,
            paramsInfo = "stage=chat",
            stream = AiAgentStream.Raw,
            epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        regularMessages += userMsg
        appendMessageToRegularChat(chatId, userMsg)
        updateInputText(TextFieldValue(""))

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            val result = try {
                isErrorState = false
                callApiWithHistory(regularMessages.toList())
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }
            if (sessionId != chatSessionId) { isLoading = false; return@launch }

            val respMsg = AiAgentMessage(
                text = result,
                isUser = false,
                paramsInfo = "stage=chat",
                stream = AiAgentStream.Raw,
                epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            regularMessages += respMsg
            appendMessageToRegularChat(chatId, respMsg)
            isLoading = false
        }
    }

    val multiAgentMcpCoordinator = remember {
        AiAgentMainMultiAgentMcpCoordinator(
            allServersProvider = { mcpServerOptions },
            toolsProvider = { serverUrl -> mcpServerTools[serverUrl].orEmpty() },
            toolInfosProvider = { serverUrl -> mcpServerToolInfos[serverUrl].orEmpty() },
            errorProvider = { serverUrl -> mcpServerErrors[serverUrl] },
            refreshServerTools = { serverUrl -> refreshMcpServerTools(serverUrl) },
            endpointMatcher = { endpoint, serverUrl -> endpointMatchesServer(endpoint, serverUrl) },
            ragChunkCountProvider = { embQueries.selectAll().executeAsList().size },
            isSessionRefreshed = { hasMultiAgentMcpRefreshedThisSession },
            markSessionRefreshed = { refreshed -> hasMultiAgentMcpRefreshedThisSession = refreshed }
        )
    }

    fun sendMultiAgentMessage(messageText: String = inputText.text.trim()) {
        val chatId = activeChatId ?: return
        val trimmed = messageText.trim()
        if (trimmed.isEmpty() || isLoading) return

        val regularMessages = regularMessagesByChat.getOrPut(chatId) { loadRegularMessagesForChat(chatId) }
        isBranchingEnabled = false
        selectedBranchNumber = null
        agentState = AgentState.Idle
        planClarificationNeeded = null

        val userMsg = AiAgentMessage(
            text = trimmed,
            isUser = true,
            paramsInfo = "stage=multiagent|user",
            stream = AiAgentStream.Raw,
            epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        regularMessages += userMsg
        appendMessageToRegularChat(chatId, userMsg)
        updateInputText(TextFieldValue(""))

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            var runId: Long? = null
            var isContinuationRun = false
            var pendingQuestion: String? = null
            val stepRowIdByIndex = mutableMapOf<Int, Long>()
            val result = try {
                isErrorState = false
                val now = System.currentTimeMillis()
                val latestRun = queries.selectLatestMultiAgentRunByChat(chat_id = chatId).executeAsOneOrNull()
                val canResume = latestRun != null &&
                    latestRun.status.equals(MultiAgentRunStatus.WAITING_USER.name.lowercase(), ignoreCase = true) &&
                    latestRun.pending_question.isNotBlank()

                if (canResume) {
                    runId = latestRun!!.id
                    isContinuationRun = true
                    pendingQuestion = latestRun.pending_question
                    queries.updateMultiAgentRunStatus(
                        status = MultiAgentRunStatus.RUNNING.name.lowercase(),
                        resolution_type = "continuation",
                        pending_question = "",
                        state_json = """{"phase":"resume_started"}""",
                        updated_at = now,
                        id = runId!!
                    )
                    appendMultiAgentEvent(
                        chatId = chatId,
                        runId = runId,
                        event = MultiAgentEvent(
                            channel = MultiAgentEventChannel.USER,
                            actorType = "orchestrator",
                            actorKey = "orchestrator",
                            role = "assistant",
                            message = "Продолжаю текущий run #$runId по вашему уточнению."
                        )
                    )
                } else {
                    queries.insertMultiAgentRun(
                        chat_id = chatId,
                        parent_run_id = latestRun?.id,
                        user_request = trimmed,
                        status = MultiAgentRunStatus.RUNNING.name.lowercase(),
                        resolution_type = "pending",
                        pending_question = "",
                        state_json = """{"phase":"new"}""",
                        created_at = now,
                        updated_at = now
                    )
                    runId = queries.selectLastInsertedMultiAgentRunId().executeAsOne()
                }

                appendMultiAgentEvent(
                    chatId = chatId,
                    runId = runId,
                    event = MultiAgentEvent(
                        channel = MultiAgentEventChannel.TRACE,
                        actorType = "user",
                        actorKey = "user",
                        role = "user",
                        message = trimmed
                    )
                )

                val requestApi = selectedApi
                val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
                val apiKey = aiAgentReadApiKey(requestApi.envVar)
                if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
                val projectFolderPath = agentModeState.projectFolderPath.trim()
                val conversationContext = if (isContinuationRun && runId != null) {
                    buildMultiAgentRunConversationContext(runId!!)
                } else {
                    buildRegularConversationContext(regularMessages.toList())
                }
                val preflightRefresh = multiAgentMcpCoordinator.ensureMcpCacheFresh()
                preflightRefresh.lines.forEach { line ->
                    appendMultiAgentEvent(
                        chatId = chatId,
                        runId = runId,
                        event = MultiAgentEvent(
                            channel = MultiAgentEventChannel.TRACE,
                            actorType = "mcp",
                            actorKey = "refresh",
                            role = "assistant",
                            message = "MCP refresh before preflight: $line"
                        )
                    )
                }
                val projectContextProvider = AgentProjectContextProvider()
                val toolGateway = AiAgentMainToolGateway(
                    ragExecutor = { query ->
                        buildRagPayloadForPrompt(query = query, forceEnabled = true)?.promptContext.orEmpty()
                    },
                    mcpExecutor = { toolRequest ->
                        val callRefresh = multiAgentMcpCoordinator.ensureMcpCacheFresh()
                        callRefresh.lines.forEach { line ->
                            appendMultiAgentEvent(
                                chatId = chatId,
                                runId = runId,
                                event = MultiAgentEvent(
                                    channel = MultiAgentEventChannel.TRACE,
                                    actorType = "mcp",
                                    actorKey = "refresh",
                                    role = "assistant",
                                    message = "MCP refresh before call: $line"
                                )
                            )
                        }
                        val toolName = toolRequest.toolName.trim()
                        val endpoint = toolRequest.endpoint?.trim()
                        val targetServer = multiAgentMcpCoordinator.findServerForTool(toolName = toolName, endpoint = endpoint)
                        if (targetServer == null) {
                            error("MCP tool '$toolName' недоступен на активных серверах.")
                        }
                        remoteMcpService.callTool(
                            serverUrl = targetServer.url,
                            toolName = toolName,
                            arguments = toolRequest.arguments
                        )
                    },
                    projectFsSummaryExecutor = { folder ->
                        val ctx = projectContextProvider.load(folder)
                        buildString {
                            appendLine("root: ${ctx.rootPath}")
                            appendLine("tree:")
                            appendLine(ctx.treePreview)
                            appendLine()
                            appendLine("snippets:")
                            append(ctx.snippetsPreview)
                        }.trim()
                    },
                    ragAvailability = {
                        multiAgentMcpCoordinator.buildRagAvailability()
                    },
                    mcpAvailability = { toolName ->
                        multiAgentMcpCoordinator.buildMcpAvailability(toolName)
                    },
                    isProjectFsAvailable = { folder ->
                        runCatching {
                            val dir = java.io.File(folder)
                            dir.exists() && dir.isDirectory
                        }.getOrDefault(false)
                    }
                )

                val summary = multiAgentOrchestrator.execute(
                    request = MultiAgentRequest(
                        userRequest = trimmed,
                        projectFolderPath = projectFolderPath,
                        subagents = multiAgentSubagents.filter { it.isEnabled },
                        conversationContext = conversationContext,
                        pendingQuestion = pendingQuestion,
                        isContinuation = isContinuationRun,
                        mcpToolsCatalog = multiAgentMcpCoordinator.buildMcpToolsCatalogForSelector()
                    ),
                    callModel = { call ->
                        callAiAgentMainApi(
                            requestApi = requestApi,
                            model = model,
                            apiKey = apiKey,
                            requestMessages = call.messages,
                            deepSeekApi = deepSeekApi,
                            openAiApi = openAiApi,
                            gigaChatApi = gigaChatApi,
                            proxyOpenAiApi = proxyOpenAiApi,
                            localLlmApi = localLlmApi,
                            options = AgentModelRequestOptions(
                                temperature = call.temperature,
                                topP = call.topP,
                                maxTokens = call.maxTokens,
                                responseFormat = if (call.responseAsJson) {
                                    DeepSeekResponseFormat(type = "json_object")
                                } else {
                                    null
                                }
                            )
                        )
                    },
                    executeTool = { toolRequest ->
                        if (toolRequest.toolKind == MultiAgentToolKind.MCP_CALL) {
                            val refreshReport = multiAgentMcpCoordinator.ensureMcpCacheFresh()
                            refreshReport.lines.forEach { line ->
                                appendMultiAgentEvent(
                                    chatId = chatId,
                                    runId = runId,
                                    event = MultiAgentEvent(
                                        channel = MultiAgentEventChannel.TRACE,
                                        actorType = "mcp",
                                        actorKey = if (toolRequest.preflight) "preflight_refresh" else "call_refresh",
                                        role = "assistant",
                                        message = "MCP refresh around tool call: $line"
                                    )
                                )
                            }
                        }
                        val gatewayResult = toolGateway.execute(toolRequest)
                        val stepId = toolRequest.stepIndex?.let { stepRowIdByIndex[it] }
                        val requestPayload = buildString {
                            val safeReason = toolRequest.reason.replace("\"", "\\\"")
                            append("{")
                            append("\"preflight\":${toolRequest.preflight},")
                            append("\"reason\":\"$safeReason\",")
                            append("\"params\":${toolRequest.paramsJson}")
                            append("}")
                        }
                        val toolCallId = insertMultiAgentToolCall(
                            MultiAgentToolCallLog(
                                runId = runId ?: error("runId is null while logging tool call"),
                                stepId = stepId,
                                toolKind = toolRequest.toolKind,
                                requestPayload = requestPayload,
                                responsePayload = gatewayResult.rawOutput.ifBlank { gatewayResult.normalizedOutput },
                                status = if (gatewayResult.success) "success" else "error",
                                errorCode = gatewayResult.errorCode,
                                errorMessage = gatewayResult.errorMessage,
                                latencyMs = gatewayResult.latencyMs
                            )
                        )
                        val mergedMetadata = run {
                            val base = gatewayResult.metadataJson.trim()
                            if (base.startsWith("{") && base.endsWith("}")) {
                                val withoutTail = base.removeSuffix("}")
                                val separator = if (withoutTail.length <= 1) "" else ","
                                "$withoutTail${separator}\"toolCallId\":$toolCallId,\"preflight\":${toolRequest.preflight}}"
                            } else {
                                """{"toolCallId":$toolCallId,"preflight":${toolRequest.preflight}}"""
                            }
                        }
                        gatewayResult.copy(
                            metadataJson = mergedMetadata
                        )
                    },
                    onEvent = { event ->
                        appendMultiAgentEvent(chatId = chatId, runId = runId, event = event)
                        if (event.channel == MultiAgentEventChannel.USER) {
                            val msg = AiAgentMessage(
                                text = event.message,
                                isUser = false,
                                paramsInfo = "stage=multiagent|status",
                                stream = AiAgentStream.Raw,
                                epoch = 0,
                                createdAt = System.currentTimeMillis()
                            )
                            regularMessages += msg
                            appendMessageToRegularChat(chatId, msg)
                        }
                    },
                    onPlanningReady = { planning ->
                        if (runId == null) return@execute
                        val nowPlan = System.currentTimeMillis()
                        val existingRows = queries.selectMultiAgentStepsByRun(run_id = runId!!).executeAsList()
                        val existingByStepIndex = existingRows.associateBy { it.step_index.toInt() }
                        planning.planSteps.forEach { step ->
                            val existing = existingByStepIndex[step.index]
                            if (existing == null) {
                                queries.insertMultiAgentStep(
                                    run_id = runId!!,
                                    step_index = step.index.toLong(),
                                    title = step.title,
                                    assignee_agent_key = step.assigneeKey,
                                    status = MultiAgentStepStatus.planned.name,
                                    input_payload = step.taskInput,
                                    output_payload = "",
                                    validation_note = "",
                                    created_at = nowPlan,
                                    updated_at = nowPlan
                                )
                            } else {
                                queries.updateMultiAgentStepById(
                                    status = MultiAgentStepStatus.planned.name,
                                    output_payload = "",
                                    validation_note = "replanned",
                                    updated_at = nowPlan,
                                    id = existing.id
                                )
                            }
                        }
                        val rows = queries.selectMultiAgentStepsByRun(run_id = runId!!).executeAsList()
                        stepRowIdByIndex.clear()
                        rows.forEach { row -> stepRowIdByIndex[row.step_index.toInt()] = row.id }
                    },
                    onStepReady = { step ->
                        val rowId = stepRowIdByIndex[step.step.index] ?: return@execute
                        queries.updateMultiAgentStepById(
                            status = step.status.name,
                            output_payload = step.output,
                            validation_note = step.validationNote,
                            updated_at = System.currentTimeMillis(),
                            id = rowId
                        )
                    }
                )

                if (runId != null) {
                    val pendingQ = if (summary.runStatus == MultiAgentRunStatus.WAITING_USER) {
                        summary.finalUserMessage
                    } else {
                        ""
                    }
                    val stateJson = buildString {
                        append("{")
                        append("\"isContinuation\":$isContinuationRun,")
                        append("\"runStatus\":\"${summary.runStatus.name.lowercase()}\",")
                        append("\"resolution\":\"${summary.resolutionType.name.lowercase()}\",")
                        append("\"steps\":${summary.steps.size},")
                        append("\"lastMessage\":\"${escapeJsonValue(summary.finalUserMessage)}\"")
                        append("}")
                    }
                    queries.updateMultiAgentRunStatus(
                        status = summary.runStatus.name.lowercase(),
                        resolution_type = summary.resolutionType.name.lowercase(),
                        pending_question = pendingQ,
                        state_json = stateJson,
                        updated_at = System.currentTimeMillis(),
                        id = runId!!
                    )
                }
                summary.finalUserMessage
            } catch (e: Exception) {
                isErrorState = true
                if (runId != null) {
                    queries.updateMultiAgentRunStatus(
                        status = MultiAgentRunStatus.FAILED.name.lowercase(),
                        resolution_type = MultiAgentResolutionType.FAILED.name.lowercase(),
                        pending_question = "",
                        state_json = """{"error":"${escapeJsonValue(e.message ?: "unknown error")}"}""",
                        updated_at = System.currentTimeMillis(),
                        id = runId!!
                    )
                }
                "Request failed: ${e.message ?: "unknown error"}"
            }

            if (sessionId != chatSessionId) { isLoading = false; return@launch }
            val finalMsg = AiAgentMessage(
                text = result,
                isUser = false,
                paramsInfo = "stage=multiagent|final",
                stream = AiAgentStream.Raw,
                epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            regularMessages += finalMsg
            appendMessageToRegularChat(chatId, finalMsg)
            isLoading = false
        }
    }

    fun sendPrimaryInput() {
        val chatId = activeChatId ?: return
        val rawInput = inputText.text
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty() || isLoading) return

        if (!agentModeState.isEnabled) {
            if (isStateMachineEnabled) {
                startPlanningPhaseHandler?.invoke() ?: sendRegularChatMessage(trimmed)
            } else {
                sendRegularChatMessage(trimmed)
            }
            return
        }

        val parseResult = AgentSlashCommandParser.parse(rawInput)
        when (parseResult) {
            AgentSlashParseResult.NotSlash -> {
                if (isMultiAgentEnabled) sendMultiAgentMessage(trimmed)
                else sendRegularChatMessage(trimmed)
            }

            is AgentSlashParseResult.Error -> {
                isBranchingEnabled = false
                selectedBranchNumber = null
                agentState = AgentState.Idle
                planClarificationNeeded = null
                val now = System.currentTimeMillis()
                appendRegularMessage(
                    chatId = chatId,
                    message = AiAgentMessage(
                        text = trimmed,
                        isUser = true,
                        paramsInfo = "stage=agent|command",
                        stream = AiAgentStream.Raw,
                        epoch = 0,
                        createdAt = now
                    )
                )
                appendRegularMessage(
                    chatId = chatId,
                    message = AiAgentMessage(
                        text = parseResult.message,
                        isUser = false,
                        paramsInfo = "stage=agent|error",
                        stream = AiAgentStream.Raw,
                        epoch = 0,
                        createdAt = now + 1
                    )
                )
                updateInputText(TextFieldValue(""))
            }

            is AgentSlashParseResult.Parsed -> {
                when (val command = parseResult.command) {
                    is AgentSlashCommand.Help -> {
                        isBranchingEnabled = false
                        selectedBranchNumber = null
                        agentState = AgentState.Idle
                        planClarificationNeeded = null
                        val userMessage = AiAgentMessage(
                            text = trimmed,
                            isUser = true,
                            paramsInfo = "stage=agent|help",
                            stream = AiAgentStream.Raw,
                            epoch = 0,
                            createdAt = System.currentTimeMillis()
                        )
                        appendRegularMessage(chatId, userMessage)
                        updateInputText(TextFieldValue(""))

                        scope.launch {
                            val sessionId = chatSessionId
                            isLoading = true
                            val result = try {
                                val projectFolderPath = agentModeState.projectFolderPath.trim()
                                if (projectFolderPath.isEmpty()) {
                                    error("Для команды /help нужно выбрать папку проекта в режиме Агент.")
                                }
                                val ragQuery = listOfNotNull(
                                    command.question,
                                    "Контекст проекта: $projectFolderPath"
                                ).joinToString("\n")
                                val ragPayload = buildRagPayloadForPrompt(
                                    query = ragQuery,
                                    forceEnabled = true
                                )
                                val mcpContext = collectAgentMcpContext(
                                    remoteMcpService = remoteMcpService,
                                    servers = mcpServerOptions
                                )
                                val ragCatalog = loadAgentRagCatalog()
                                val requestApi = selectedApi
                                val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
                                val apiKey = aiAgentReadApiKey(requestApi.envVar)
                                if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
                                isErrorState = false
                                agentHelpCommandUseCase.execute(
                                    request = AgentHelpRequest(
                                        projectFolderPath = projectFolderPath,
                                        userQuestion = command.question?.trim().orEmpty().ifBlank {
                                            "Дай общий обзор проекта: назначение, основные модули и точки входа."
                                        }
                                    ),
                                    mcpContext = mcpContext,
                                    ragCatalog = ragCatalog,
                                    runMcpTool = { plan ->
                                        val target = mcpContext.snapshots
                                            .asSequence()
                                            .filter { it.error == null }
                                            .filter { snapshot ->
                                                snapshot.tools.any { it.name.equals(plan.toolName, ignoreCase = true) }
                                            }
                                            .firstOrNull { snapshot ->
                                                val endpoint = plan.endpoint?.trim()
                                                endpoint.isNullOrBlank() || endpointMatchesServer(endpoint, snapshot.url)
                                            }
                                        if (target == null) {
                                            AgentMcpExecutionResult(
                                                request = plan,
                                                output = null,
                                                error = "Инструмент недоступен в MCP контексте"
                                            )
                                        } else {
                                            runCatching {
                                                remoteMcpService.callTool(
                                                    serverUrl = target.url,
                                                    toolName = plan.toolName,
                                                    arguments = plan.arguments
                                                )
                                            }.fold(
                                                onSuccess = { output ->
                                                    AgentMcpExecutionResult(
                                                        request = plan,
                                                        output = output
                                                    )
                                                },
                                                onFailure = { error ->
                                                    AgentMcpExecutionResult(
                                                        request = plan,
                                                        output = null,
                                                        error = error.message ?: error::class.simpleName ?: "unknown"
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    runRagQuery = { query ->
                                        AgentRagExecutionResult(
                                            query = query,
                                            payload = buildRagPayloadForPrompt(query = query, forceEnabled = true)
                                        )
                                    },
                                    callPlanningModel = { requestMessages ->
                                        callAiAgentMainApi(
                                            requestApi = requestApi,
                                            model = model,
                                            apiKey = apiKey,
                                            requestMessages = requestMessages,
                                            deepSeekApi = deepSeekApi,
                                            openAiApi = openAiApi,
                                            gigaChatApi = gigaChatApi,
                                            proxyOpenAiApi = proxyOpenAiApi,
                                            localLlmApi = localLlmApi,
                                            options = AgentModelRequestOptions(
                                                temperature = 0.05,
                                                topP = 0.1,
                                                maxTokens = 1200,
                                                responseFormat = DeepSeekResponseFormat(type = "json_object")
                                            )
                                        )
                                    },
                                    callAnsweringModel = { requestMessages ->
                                        callAiAgentMainApi(
                                            requestApi = requestApi,
                                            model = model,
                                            apiKey = apiKey,
                                            requestMessages = requestMessages,
                                            deepSeekApi = deepSeekApi,
                                            openAiApi = openAiApi,
                                            gigaChatApi = gigaChatApi,
                                            proxyOpenAiApi = proxyOpenAiApi,
                                            localLlmApi = localLlmApi,
                                            options = AgentModelRequestOptions(
                                                temperature = 0.2,
                                                topP = 0.8,
                                                maxTokens = 4000
                                            )
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                isErrorState = true
                                "Request failed: ${e.message ?: "unknown error"}"
                            }
                            if (sessionId != chatSessionId) { isLoading = false; return@launch }

                            appendRegularMessage(
                                chatId = chatId,
                                message = AiAgentMessage(
                                    text = result,
                                    isUser = false,
                                    paramsInfo = "stage=agent|help",
                                    stream = AiAgentStream.Raw,
                                    epoch = 0,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            isLoading = false
                        }
                    }

                    is AgentSlashCommand.ReviewPr -> {
                        isBranchingEnabled = false
                        selectedBranchNumber = null
                        agentState = AgentState.Idle
                        planClarificationNeeded = null
                        val userMessage = AiAgentMessage(
                            text = trimmed,
                            isUser = true,
                            paramsInfo = "stage=agent|review-pr",
                            stream = AiAgentStream.Raw,
                            epoch = 0,
                            createdAt = System.currentTimeMillis()
                        )
                        appendRegularMessage(chatId, userMessage)
                        updateInputText(TextFieldValue(""))

                        scope.launch {
                            val sessionId = chatSessionId
                            isLoading = true
                            val result = try {
                                val projectFolderPath = agentModeState.projectFolderPath.trim()
                                if (projectFolderPath.isEmpty()) {
                                    error("Для команды /review-pr нужно выбрать папку проекта в режиме Агент.")
                                }

                                val requestApi = selectedApi
                                val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
                                val apiKey = aiAgentReadApiKey(requestApi.envVar)
                                if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
                                isErrorState = false

                                val reviewMcpServers = mcpServerOptions.filter { server ->
                                    mcpServerEnabled[server.url] == true
                                }.ifEmpty { mcpServerOptions }
                                val reviewMcpContext = collectAgentMcpContext(
                                    remoteMcpService = remoteMcpService,
                                    servers = reviewMcpServers
                                )
                                val githubTokenProvider = {
                                    BuildSecrets.apiKeyFor("GITHUB_PERSONAL_ACCESS_TOKEN").trim()
                                        .ifBlank {
                                            System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN")?.trim().orEmpty()
                                        }
                                        .ifBlank {
                                            System.getenv("GITHUB_TOKEN")?.trim().orEmpty()
                                        }
                                        .ifBlank {
                                            System.getenv("GH_TOKEN")?.trim().orEmpty()
                                        }
                                }

                                agentPrReviewUseCase.execute(
                                    request = AgentPrReviewRequest(
                                        projectFolderPath = projectFolderPath,
                                        commandArguments = command.arguments.orEmpty()
                                    ),
                                    gitGateway = AgentMcpPrReviewGitGateway(
                                        remoteMcpService = remoteMcpService,
                                        servers = reviewMcpContext.snapshots,
                                        githubTokenProvider = githubTokenProvider
                                    ),
                                    runRagQuery = { query ->
                                        AgentRagExecutionResult(
                                            query = query,
                                            payload = buildRagPayloadForPrompt(query = query, forceEnabled = true)
                                        )
                                    },
                                    callReviewModel = { requestMessages ->
                                        callAiAgentMainApi(
                                            requestApi = requestApi,
                                            model = model,
                                            apiKey = apiKey,
                                            requestMessages = requestMessages,
                                            deepSeekApi = deepSeekApi,
                                            openAiApi = openAiApi,
                                            gigaChatApi = gigaChatApi,
                                            proxyOpenAiApi = proxyOpenAiApi,
                                            localLlmApi = localLlmApi,
                                            options = AgentModelRequestOptions(
                                                temperature = 0.2,
                                                topP = 0.8,
                                                maxTokens = 4000
                                            )
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                isErrorState = true
                                "Request failed: ${e.message ?: "unknown error"}"
                            }
                            if (sessionId != chatSessionId) { isLoading = false; return@launch }

                            appendRegularMessage(
                                chatId = chatId,
                                message = AiAgentMessage(
                                    text = result,
                                    isUser = false,
                                    paramsInfo = "stage=agent|review-pr",
                                    stream = AiAgentStream.Raw,
                                    epoch = 0,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            isLoading = false
                        }
                    }

                    is AgentSlashCommand.AuthToken -> {
                        isBranchingEnabled = false
                        selectedBranchNumber = null
                        agentState = AgentState.Idle
                        planClarificationNeeded = null
                        val userMessage = AiAgentMessage(
                            text = trimmed,
                            isUser = true,
                            paramsInfo = "stage=agent|auth-token",
                            stream = AiAgentStream.Raw,
                            epoch = 0,
                            createdAt = System.currentTimeMillis()
                        )
                        appendRegularMessage(chatId, userMessage)
                        updateInputText(TextFieldValue(""))

                        scope.launch {
                            val sessionId = chatSessionId
                            isLoading = true
                            val result = try {
                                val authServers = mcpServerOptions.filter { server ->
                                    mcpServerEnabled[server.url] == true
                                }.ifEmpty { mcpServerOptions }
                                val authMcpContext = collectAgentMcpContext(
                                    remoteMcpService = remoteMcpService,
                                    servers = authServers
                                )
                                agentAuthTokenUseCase.execute(
                                    arguments = command.arguments.orEmpty(),
                                    servers = authMcpContext.snapshots
                                )
                            } catch (e: Exception) {
                                isErrorState = true
                                "Request failed: ${e.message ?: "unknown error"}"
                            }
                            if (sessionId != chatSessionId) { isLoading = false; return@launch }

                            appendRegularMessage(
                                chatId = chatId,
                                message = AiAgentMessage(
                                    text = result,
                                    isUser = false,
                                    paramsInfo = "stage=agent|auth-token",
                                    stream = AiAgentStream.Raw,
                                    epoch = 0,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            isLoading = false
                        }
                    }
                }
            }
        }
    }

    suspend fun callExecutionStepApi(
        taskContext: String,
        stepId: Int,
        stepDescription: String,
        previousResultsFormatted: String,
        stepToolName: String? = null,
        recoveryContext: String? = null
    ): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val ragPayload = buildRagPayloadForPrompt(
            listOf(taskContext, stepDescription, previousResultsFormatted)
                .filter { it.isNotBlank() }
                .joinToString("\n")
        )
        val promptText = EXECUTION_STEP_PROMPT_TEMPLATE
            .replace("{task_context}", taskContext)
            .replace("{recovery_context}", recoveryContext?.ifBlank { "нет" } ?: "нет")
            .replace("{step_id}", stepId.toString())
            .replace("{step_description}", stepDescription)
            .replace("{step_tool_name}", stepToolName ?: "null")
            .replace("{available_tools_formatted}", buildMcpToolsContextForPrompt())
            .replace("{previous_results_formatted}", previousResultsFormatted)
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            buildInvariantsSystemMessage()?.let { add(it) }
            ragPayload?.let { add(DeepSeekMessage(role = "system", content = it.promptContext)) }
            add(DeepSeekMessage(role = "user", content = promptText))
        }
        val request = DeepSeekChatRequest(
            model = model,
            messages = messages,
            temperature = 0.2,
            maxTokens = 4000,
            topP = 0.95,
            frequencyPenalty = 0.1,
            presencePenalty = null
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

    suspend fun callPlanInvariantCheckApi(planJson: String, invariantsList: String, userEditRequest: String = ""): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val promptText = INVARIANT_PLAN_CHECK_PROMPT_TEMPLATE
            .replace("{plan_json}", planJson)
            .replace("{invariants_list}", invariantsList)
            .replace("{user_edit_request}", userEditRequest.ifBlank { "(отсутствует — план создан ИИ)" })
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            add(DeepSeekMessage(role = "user", content = promptText))
        }
        val request = DeepSeekChatRequest(
            model = model,
            messages = messages,
            temperature = 0.1,
            maxTokens = 1500,
            topP = 0.9,
            responseFormat = DeepSeekResponseFormat(type = "json_object")
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

    fun checkPlanInvariantViolations(response: String): Pair<List<String>, Boolean> {
        val violations = mutableListOf<String>()
        var aiViolated = false
        try {
            val json = lenientJson.parseToJsonElement(response).jsonObject
            if (json["overall_passed"]?.jsonPrimitive?.booleanOrNull == true) return Pair(emptyList(), false)
            aiViolated = json["ai_violated"]?.jsonPrimitive?.booleanOrNull ?: true
            json["invariant_checks"]?.jsonArray?.forEach { item ->
                val obj = item.jsonObject
                if (obj["passed"]?.jsonPrimitive?.booleanOrNull == false) {
                    val key = obj["invariant_key"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val value = obj["invariant_value"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val details = obj["violation_details"]?.jsonPrimitive?.contentOrNull
                    val entry = if (!details.isNullOrBlank()) "$key: $value — $details" else "$key: $value"
                    if (entry !in violations) violations += entry
                }
            }
            if (violations.isEmpty()) {
                val summary = json["summary"]?.jsonPrimitive?.contentOrNull
                violations += summary ?: "Инварианты плана нарушены"
            }
        } catch (_: Exception) {
            violations += "Ошибка разбора ответа проверки инвариантов"
            aiViolated = true
        }
        return Pair(violations, aiViolated)
    }

    fun findStepIndexById(stepId: Int): Int =
        executionSteps.indexOfFirst { it.stepId == stepId }.takeIf { it >= 0 } ?: 0

    fun extractStepId(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        return Regex("""(?:step[_\s-]*id|шаг)\D{0,10}(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    fun findStepIdByDescriptionMatch(vararg texts: String?): Int? {
        val haystack = texts.filterNotNull().joinToString(" ").lowercase()
        if (haystack.isBlank()) return null

        return executionSteps
            .map { step ->
                val descriptionWords = Regex("""\p{L}[\p{L}\p{N}_-]*""")
                    .findAll(step.description.lowercase())
                    .map { it.value }
                    .filter { it.length >= 4 }
                    .distinct()
                    .toList()
                val matches = descriptionWords.count { haystack.contains(it) }
                step.stepId to matches
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    fun parseValidationFailure(response: String): ValidationFailureInfo? {
        val json = try { lenientJson.parseToJsonElement(response).jsonObject } catch (_: Exception) { return null }
        val overallPassed = json["overall_passed"]?.jsonPrimitive?.booleanOrNull
        val needsReplanning = json["needs_replanning"]?.jsonPrimitive?.booleanOrNull ?: false
        if (overallPassed == true && !needsReplanning) return null

        val directProblem = json["detected_problem"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val fallbackProblem = buildString {
            json["criteria_results"]?.jsonArray
                ?.firstOrNull { it.jsonObject["passed"]?.jsonPrimitive?.booleanOrNull == false }
                ?.jsonObject
                ?.let { item ->
                    val criterion = item["criterion"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val details = item["error_details"]?.jsonPrimitive?.contentOrNull
                    val actual = item["actual"]?.jsonPrimitive?.contentOrNull
                    append(criterion.ifBlank { "Проверка не пройдена" })
                    if (!details.isNullOrBlank()) append(": $details")
                    else if (!actual.isNullOrBlank()) append(": $actual")
                }
            if (isBlank()) {
                val issue = json["issues"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull
                val feedback = json["feedback_for_planning"]?.jsonPrimitive?.contentOrNull
                append(issue ?: feedback ?: "Валидация обнаружила проблему в результате выполнения")
            }
        }.trim()
        val problem = directProblem.ifBlank { fallbackProblem }

        val directSolution = json["proposed_solution"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val fallbackSolution = (
            json["feedback_for_planning"]?.jsonPrimitive?.contentOrNull
                ?: json["recommendation"]?.jsonPrimitive?.contentOrNull
                ?: "Вернуться к проблемному шагу, исправить результат и затем повторить проверку."
            ).trim()
        val solution = directSolution.ifBlank { fallbackSolution }

        val issuesText = json["issues"]?.jsonArray?.joinToString(" ") { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val feedbackText = json["feedback_for_planning"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val recommendationText = json["recommendation"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val explicitFailedStepId = json["failed_step_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: json["failed_step_id"]?.jsonPrimitive?.intOrNull
        val explicitRetryStepId = json["retry_from_step_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: json["retry_from_step_id"]?.jsonPrimitive?.intOrNull
        val parsedFromTextStepId = extractStepId(problem)
            ?: extractStepId(solution)
            ?: extractStepId(issuesText)
            ?: extractStepId(feedbackText)
            ?: extractStepId(recommendationText)
        val matchedByDescriptionStepId = findStepIdByDescriptionMatch(
            problem,
            solution,
            issuesText,
            feedbackText,
            recommendationText
        )
        val lastExecutedStepId = executionResults.keys.maxOrNull()
            ?: executionSteps.getOrNull(executionCurrentStepIndex)?.stepId
            ?: executionSteps.lastOrNull()?.stepId
        val fallbackFirstStepId = executionSteps.firstOrNull()?.stepId ?: 1

        val failedStepId = explicitFailedStepId
            ?: explicitRetryStepId
            ?: parsedFromTextStepId
            ?: matchedByDescriptionStepId
            ?: lastExecutedStepId
            ?: fallbackFirstStepId

        val retryFromStepId = explicitRetryStepId ?: failedStepId
        val stepDetectionSource = when {
            explicitFailedStepId != null -> "model.failed_step_id"
            explicitRetryStepId != null -> "model.retry_from_step_id"
            parsedFromTextStepId != null -> "parsed_from_text"
            matchedByDescriptionStepId != null -> "matched_step_description"
            lastExecutedStepId != null -> "last_executed_step"
            else -> "first_plan_step"
        }

        return ValidationFailureInfo(
            problem = problem,
            failedStepId = failedStepId,
            retryFromStepId = retryFromStepId,
            proposedSolution = solution,
            stepDetectionSource = stepDetectionSource
        )
    }


    suspend fun awaitUnpaused(sessionId: Int): Boolean {
        while (isPaused) {
            delay(150)
            if (sessionId != chatSessionId) return false
        }
        return sessionId == chatSessionId
    }

    suspend fun callValidationApi(promptText: String): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildMcpSystemMessageCached()?.let { add(it) }
            add(DeepSeekMessage(role = "user", content = promptText))
        }
        val request = DeepSeekChatRequest(
            model = model,
            messages = messages,
            temperature = 0.1,
            maxTokens = 1500,
            topP = 0.9,
            responseFormat = DeepSeekResponseFormat(type = "json_object")
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

    suspend fun runValidationLoop(
        chatId: Long,
        checkMessages: SnapshotStateList<AiAgentMessage>,
        sessionId: Int
    ) {
        val plan = try { lenientJson.decodeFromString<PlanJson>(planText) } catch (_: Exception) { PlanJson() }
        val originalGoal = plan.goal.ifBlank { userRequestText }
        val validationCriteria = plan.validationCriteria
            .joinToString("\n") { "- $it" }
            .ifBlank { "Нет формальных критериев. Оцени по смыслу." }
        val previousStepsResults = executionResults.entries.sortedBy { it.key }
            .joinToString("\n") { (id, res) -> "step_id=$id: $res" }
            .ifEmpty { "Нет промежуточных результатов." }

        // Фаза 1: формальная проверка
        if (validationPhase == 1) {
            if (!awaitUnpaused(sessionId)) { isLoading = false; return }

            val prompt1 = (VALIDATION_FORMAL_PROMPT_TEMPLATE
                .replace("{original_goal}", originalGoal)
                .replace("{validation_criteria}", validationCriteria)
                .replace("{execution_results}", executionText)) + buildInvariantsPromptSuffix()

            val phase1UserMsg = AiAgentMessage(
                text = "Формальная проверка\n\n▼ Запрос к ИИ:\n$prompt1",
                isUser = true,
                paramsInfo = "stage=checking|phase=1",
                stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            checkMessages += phase1UserMsg
            appendMessageToBranch(chatId, 4, phase1UserMsg)

            val result1 = try {
                val r = callValidationApi(prompt1)
                isErrorState = false
                r
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }

            if (sessionId != chatSessionId) { isLoading = false; return }

            val phase1RespMsg = AiAgentMessage(
                text = result1, isUser = false,
                paramsInfo = "stage=checking|phase=1",
                stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            checkMessages += phase1RespMsg
            appendMessageToBranch(chatId, 4, phase1RespMsg)

            if (isErrorState) { isLoading = false; return }

            val phase1Violations = checkInvariantViolations(result1)
            if (phase1Violations.isNotEmpty()) {
                invariantViolationPreviousState = AgentState.Execution
                invariantViolations = phase1Violations
                isLoading = false
                return
            }

            parseValidationFailure(result1)?.let {
                validationFailure = it
                if (validationRecoveryBranchNumber == null) {
                    validationRecoveryAttempt += 1
                    val (branchNumber, messages) = createAutoStateBranch(
                        chatId,
                        "Проверка: исправление #$validationRecoveryAttempt"
                    )
                    validationRecoveryBranchNumber = branchNumber
                    val failureMsg = AiAgentMessage(
                        text = buildString {
                            appendLine("Найдена проблема по итогам проверки")
                            appendLine("Попытка исправления: #$validationRecoveryAttempt")
                            appendLine("Проблема: ${it.problem}")
                            appendLine("Проблемный шаг: ${it.failedStepId}")
                            appendLine("Источник определения шага: ${it.stepDetectionSource}")
                            append("Предложенное решение: ${it.proposedSolution}")
                        },
                        isUser = false,
                        paramsInfo = "stage=checking|recovery|failure|phase=1",
                        stream = AiAgentStream.Raw,
                        epoch = 0,
                        createdAt = System.currentTimeMillis()
                    )
                    messages += failureMsg
                    appendMessageToBranch(chatId, branchNumber, failureMsg)
                }
                isLoading = false
                return
            }

            validationPhase = 2
        }

        // Фаза 2: смысловая проверка
        if (!awaitUnpaused(sessionId)) { isLoading = false; return }

        val prompt2 = VALIDATION_SEMANTIC_PROMPT_TEMPLATE
            .replace("{original_goal}", originalGoal)
            .replace("{task_context}", userRequestText)
            .replace("{validation_criteria}", validationCriteria)
            .replace("{execution_results}", executionText)
            .replace("{previous_steps_results}", previousStepsResults)

        val phase2UserMsg = AiAgentMessage(
            text = "Смысловая проверка\n\n▼ Запрос к ИИ:\n$prompt2",
            isUser = true,
            paramsInfo = "stage=checking|phase=2",
            stream = AiAgentStream.Raw, epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        checkMessages += phase2UserMsg
        appendMessageToBranch(chatId, 4, phase2UserMsg)

        val result2 = try {
            val r = callValidationApi(prompt2)
            isErrorState = false
            r
        } catch (e: Exception) {
            isErrorState = true
            "Request failed: ${e.message ?: "unknown error"}"
        }

        if (sessionId != chatSessionId) { isLoading = false; return }

        val phase2RespMsg = AiAgentMessage(
            text = result2, isUser = false,
            paramsInfo = "stage=checking|phase=2",
            stream = AiAgentStream.Raw, epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        checkMessages += phase2RespMsg
        appendMessageToBranch(chatId, 4, phase2RespMsg)

        val phase2Violations = checkInvariantViolations(result2)
        if (phase2Violations.isNotEmpty()) {
            invariantViolationPreviousState = AgentState.Execution
            val combined = (invariantViolations + phase2Violations).distinct()
            invariantViolations = combined
        }

        validationFailure = parseValidationFailure(result2)
        if (validationFailure != null) {
            val failure = validationFailure!!
            if (validationRecoveryBranchNumber == null) {
                validationRecoveryAttempt += 1
                val (branchNumber, messages) = createAutoStateBranch(
                    chatId,
                    "Проверка: исправление #$validationRecoveryAttempt"
                )
                validationRecoveryBranchNumber = branchNumber
                val failureMsg = AiAgentMessage(
                    text = buildString {
                        appendLine("Найдена проблема по итогам проверки")
                        appendLine("Попытка исправления: #$validationRecoveryAttempt")
                        appendLine("Проблема: ${failure.problem}")
                        appendLine("Проблемный шаг: ${failure.failedStepId}")
                        appendLine("Источник определения шага: ${failure.stepDetectionSource}")
                        append("Предложенное решение: ${failure.proposedSolution}")
                    },
                    isUser = false,
                    paramsInfo = "stage=checking|recovery|failure|phase=2",
                    stream = AiAgentStream.Raw,
                    epoch = 0,
                    createdAt = System.currentTimeMillis()
                )
                messages += failureMsg
                appendMessageToBranch(chatId, branchNumber, failureMsg)
            }
        } else if (validationRecoveryBranchNumber != null) {
            appendValidationRecoveryHistory(
                chatId = chatId,
                text = "Проверка после исправления завершилась успешно.",
                paramsInfo = "stage=checking|recovery|resolved"
            )
            validationRecoveryBranchNumber = null
        }

        isLoading = false
    }

    suspend fun executeStepsLoop(
        chatId: Long,
        execMessages: SnapshotStateList<AiAgentMessage>,
        sessionId: Int
    ) {
        val planJson = try {
            lenientJson.decodeFromString<PlanJson>(planText)
        } catch (e: Exception) {
            isErrorState = true
            val errMsg = AiAgentMessage(
                text = "Ошибка парсинга плана: ${e.message}",
                isUser = false,
                paramsInfo = "stage=execution|error",
                stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            execMessages += errMsg
            appendMessageToBranch(chatId, 3, errMsg)
            isLoading = false
            return
        }

        val taskContext = buildString {
            if (planJson.goal.isNotBlank()) appendLine("Цель: ${planJson.goal}")
            append("Задача: $userRequestText")
        }

        while (executionCurrentStepIndex < executionSteps.size) {
            if (!awaitUnpaused(sessionId)) { isLoading = false; return }

            val step = executionSteps[executionCurrentStepIndex]
            val previousResultsFormatted = if (executionResults.isEmpty()) {
                "Нет предыдущих результатов."
            } else {
                executionResults.entries.sortedBy { it.key }
                    .joinToString("\n") { (id, res) -> "step_id=$id: $res" }
            }

            val stepLabel = "Шаг ${step.stepId}${step.tool?.let { " [$it]" }.orEmpty()}: ${step.description}"
            val stepUserMsg = AiAgentMessage(
                text = stepLabel, isUser = true,
                paramsInfo = "stage=execution|step=${step.stepId}",
                stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            execMessages += stepUserMsg
            appendMessageToBranch(chatId, 3, stepUserMsg)

            val firstResult = try {
                val r = callExecutionStepApi(
                    taskContext = taskContext,
                    stepId = step.stepId,
                    stepDescription = step.description,
                    previousResultsFormatted = previousResultsFormatted,
                    stepToolName = step.tool
                )
                isErrorState = false
                r
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }

            if (sessionId != chatSessionId) { isLoading = false; return }

            val stepRespMsg = AiAgentMessage(
                text = firstResult, isUser = false,
                paramsInfo = "stage=execution|step=${step.stepId}",
                stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            execMessages += stepRespMsg
            appendMessageToBranch(chatId, 3, stepRespMsg)

            if (isErrorState) { isLoading = false; return }

            val toolRequest = parseExecutionToolRequest(
                response = firstResult,
                fallbackToolName = step.tool
            )
            val result = if (toolRequest != null) {
                val toolRequestMsg = AiAgentMessage(
                    text = buildString {
                        appendLine("Модель запросила вызов MCP-инструмента.")
                        appendLine("tool_name: ${toolRequest.toolName}")
                        toolRequest.endpoint?.let { appendLine("endpoint: $it") }
                        if (toolRequest.reason.isNotBlank()) appendLine("reason: ${toolRequest.reason}")
                        append("arguments: ${toolRequest.arguments}")
                    },
                    isUser = false,
                    paramsInfo = "stage=execution|mcp|request|step=${step.stepId}",
                    stream = AiAgentStream.Raw,
                    epoch = 0,
                    createdAt = System.currentTimeMillis()
                )
                execMessages += toolRequestMsg
                appendMessageToBranch(chatId, 3, toolRequestMsg)

                val mcpToolOutput = runMcpToolForExecution(
                    step = step,
                    taskContext = taskContext,
                    previousResultsFormatted = previousResultsFormatted,
                    requestedToolName = toolRequest.toolName,
                    requestedEndpoint = toolRequest.endpoint,
                    requestedArguments = toolRequest.arguments
                )

                val mcpOutputText = mcpToolOutput.orEmpty()
                val mcpMsg = AiAgentMessage(
                    text = mcpOutputText,
                    isUser = false,
                    paramsInfo = "stage=execution|mcp|step=${step.stepId}",
                    stream = AiAgentStream.Raw,
                    epoch = 0,
                    createdAt = System.currentTimeMillis()
                )
                execMessages += mcpMsg
                appendMessageToBranch(chatId, 3, mcpMsg)

                val secondResult = try {
                    val secondCallContext = buildString {
                        append(previousResultsFormatted)
                        appendLine()
                        appendLine()
                        appendLine("MCP_TOOL_OUTPUT:")
                        append(mcpOutputText.ifBlank { "Пустой ответ инструмента." })
                    }
                    val r = callExecutionStepApi(
                        taskContext = taskContext,
                        stepId = step.stepId,
                        stepDescription = step.description,
                        previousResultsFormatted = secondCallContext,
                        stepToolName = step.tool
                    )
                    isErrorState = false
                    r
                } catch (e: Exception) {
                    isErrorState = true
                    "Request failed: ${e.message ?: "unknown error"}"
                }

                if (sessionId != chatSessionId) { isLoading = false; return }

                val secondRespMsg = AiAgentMessage(
                    text = secondResult, isUser = false,
                    paramsInfo = "stage=execution|step=${step.stepId}|after_mcp",
                    stream = AiAgentStream.Raw, epoch = 0,
                    createdAt = System.currentTimeMillis()
                )
                execMessages += secondRespMsg
                appendMessageToBranch(chatId, 3, secondRespMsg)

                secondResult
            } else {
                firstResult
            }

            if (isErrorState) { isLoading = false; return }

            val stepViolations = checkInvariantViolations(result)
            if (stepViolations.isNotEmpty()) {
                invariantViolationPreviousState = AgentState.Planning
                invariantViolations = (invariantViolations + stepViolations).distinct()
                isLoading = false
                return
            }

            executionResults[step.stepId] = result
            executionCurrentStepIndex++
        }
        // все шаги выполнены
        executionRecoveryInstruction = null
        executionText = executionResults.entries.sortedBy { it.key }
            .joinToString("\n\n") { (id, res) -> "=== Шаг $id ===\n$res" }

        val summaryMsg = AiAgentMessage(
            text = executionText,
            isUser = false,
            paramsInfo = "stage=execution|summary",
            stream = AiAgentStream.Raw, epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        execMessages += summaryMsg
        appendMessageToBranch(chatId, 3, summaryMsg)
        isLoading = false
    }

    // ─── State machine transitions ─────────────────────────────────────────────

    fun startPlanningPhase() {
        val chatId = activeChatId ?: return
        val trimmed = inputText.text.trim()
        if (trimmed.isEmpty() || isLoading) return

        userRequestText = trimmed
        updateInputText(TextFieldValue(""))
        isBranchingEnabled = true

        val now = System.currentTimeMillis()

        // Branch 1: "Основная" — хранит запрос пользователя и итоговый результат
        val mainMessages = createStateBranch(chatId, 1, "Основная")
        val userMsg = AiAgentMessage(
            text = trimmed, isUser = true,
            paramsInfo = "stage=main", stream = AiAgentStream.Raw, epoch = 0, createdAt = now
        )
        mainMessages += userMsg
        appendMessageToBranch(chatId, 1, userMsg)

        val stateMsg = AiAgentMessage(
            text = "Планирование", isUser = false,
            paramsInfo = "stage=main", stream = AiAgentStream.Raw, epoch = 0, createdAt = now
        )
        mainMessages += stateMsg
        appendMessageToBranch(chatId, 1, stateMsg)

        // Branch 2: "Планирование"
        val planningMessages = createStateBranch(chatId, 2, "Планирование")
        selectedBranchNumber = 2
        agentState = AgentState.Planning

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true

            val planUserMsg = AiAgentMessage(
                text = trimmed, isUser = true,
                paramsInfo = "stage=planning", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += planUserMsg
            appendMessageToBranch(chatId, 2, planUserMsg)

            val result = try {
                val r = callPlanningApi(trimmed)
                isErrorState = false
                r
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }

            if (sessionId != chatSessionId) { isLoading = false; return@launch }

            planText = result
            val planRespMsg = AiAgentMessage(
                text = result, isUser = false,
                paramsInfo = "stage=planning", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += planRespMsg
            appendMessageToBranch(chatId, 2, planRespMsg)
            handleClarificationInPlanResult(result, chatId, planningMessages)
            isLoading = false
        }
    }

    startPlanningPhaseHandler = ::startPlanningPhase

    fun onPlanApproved() {
        val chatId = activeChatId ?: return
        if (isLoading) return

        val plan = try {
            lenientJson.decodeFromString<PlanJson>(planText)
        } catch (_: Exception) {
            isErrorState = true
            return
        }
        if (plan.steps.isEmpty()) { isErrorState = true; return }

        executionSteps.clear()
        executionSteps.addAll(plan.steps)
        executionCurrentStepIndex = 0
        executionResults.clear()
        executionRecoveryInstruction = null
        validationFailure = null
        validationRecoveryBranchNumber = null

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true

            if (isInvariantsEnabled && invariants.isNotEmpty()) {
                val invariantsList = invariants.joinToString("\n") { "- ${it.key}: ${it.value}" }
                val checkResult = try {
                    val r = callPlanInvariantCheckApi(planText, invariantsList, lastPlanEditText)
                    isErrorState = false
                    r
                } catch (_: Exception) {
                    isErrorState = true
                    isLoading = false
                    return@launch
                }
                if (sessionId != chatSessionId) { isLoading = false; return@launch }
                val (violations, aiViolated) = checkPlanInvariantViolations(checkResult)
                if (violations.isNotEmpty()) {
                    invariantViolationPreviousState = AgentState.Planning
                    invariantViolations = violations
                    if (aiViolated) {
                        // ИИ сам предложил нарушение — показываем баннер и автоматически исправляем
                        planInvariantViolationByAi = true
                        val planningMessages = branchesByChat[chatId]?.firstOrNull { it.number == 2 }?.messages
                        if (planningMessages == null) {
                            planInvariantCheckFailed = true
                            isLoading = false
                            return@launch
                        }
                        val correctionInstruction = buildString {
                            appendLine("Ты сам предложил план, нарушающий следующие инварианты:")
                            violations.forEach { appendLine("• $it") }
                            appendLine("Исправь план так, чтобы он строго соблюдал все эти ограничения.")
                        }
                        val correctedPlan = try {
                            val r = callPlanEditApi(planText, correctionInstruction)
                            isErrorState = false
                            r
                        } catch (_: Exception) {
                            isErrorState = true
                            planInvariantViolationByAi = false
                            planInvariantCheckFailed = true
                            isLoading = false
                            return@launch
                        }
                        if (sessionId != chatSessionId) { isLoading = false; return@launch }
                        planText = correctedPlan
                        val autoFixMsg = AiAgentMessage(
                            text = correctedPlan, isUser = false,
                            paramsInfo = "stage=planning|ai-invariant-autofix", stream = AiAgentStream.Raw, epoch = 0,
                            createdAt = System.currentTimeMillis()
                        )
                        planningMessages += autoFixMsg
                        appendMessageToBranch(chatId, 2, autoFixMsg)
                        invariantViolations = emptyList()
                        planInvariantViolationByAi = false
                    } else {
                        // Пользователь явно запросил нарушающее действие — стандартное поведение
                        planInvariantCheckFailed = true
                    }
                    isLoading = false
                    return@launch
                }
            }

            val execMessages = createStateBranch(chatId, 3, "Выполнение")
            selectedBranchNumber = 3
            agentState = AgentState.Execution
            executeStepsLoop(chatId, execMessages, sessionId)
        }
    }

    fun onExecutionDone() {
        val chatId = activeChatId ?: return
        if (isLoading) return

        validationPhase = 1
        validationFailure = null
        val checkMessages = createStateBranch(chatId, 4, "Проверка")
        selectedBranchNumber = 4
        agentState = AgentState.Checking

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            runValidationLoop(chatId, checkMessages, sessionId)
        }
    }

    fun onCheckingDone() {
        val chatId = activeChatId ?: return
        if (isLoading) return
        if (validationFailure != null) return

        val branches = branchesByChat[chatId] ?: return
        val mainBranch = branches.firstOrNull { it.number == 1 } ?: return

        val resultMsg = AiAgentMessage(
            text = executionText, isUser = false,
            paramsInfo = "stage=done", stream = AiAgentStream.Raw, epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        mainBranch.messages += resultMsg
        appendMessageToBranch(chatId, 1, resultMsg)

        selectedBranchNumber = 1
        agentState = AgentState.Done
    }

    // ─── Plan edit ─────────────────────────────────────────────────────────────

    fun sendPlanEdit() {
        val chatId = activeChatId ?: return
        val editText = planEditInput.text.trim()
        if (editText.isEmpty() || isLoading) return
        val planningMessages = branchesByChat[chatId]?.firstOrNull { it.number == 2 }?.messages ?: return

        lastPlanEditText = editText
        planEditInput = TextFieldValue("")
        planInvariantCheckFailed = false
        planInvariantViolationByAi = false
        invariantViolations = emptyList()

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true

            val userEditMsg = AiAgentMessage(
                text = editText, isUser = true,
                paramsInfo = "stage=planning|edit", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += userEditMsg
            appendMessageToBranch(chatId, 2, userEditMsg)

            val originalPlan = planText
            val result = try {
                val r = callPlanEditApi(originalPlan, editText)
                isErrorState = false
                r
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }
            if (sessionId != chatSessionId) { isLoading = false; return@launch }
            if (!isErrorState) planText = result
            val respMsg = AiAgentMessage(
                text = result, isUser = false,
                paramsInfo = "stage=planning|edit", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += respMsg
            appendMessageToBranch(chatId, 2, respMsg)
            isLoading = false
        }
    }

    fun sendClarificationResponse() {
        val chatId = activeChatId ?: return
        val responseText = inputText.text.trim()
        if (responseText.isEmpty() || isLoading) return
        val currentClarification = planClarificationNeeded ?: return
        val planningMessages = branchesByChat[chatId]?.firstOrNull { it.number == 2 }?.messages ?: return

        updateInputText(TextFieldValue(""))

        scope.launch {
            val sessionId = chatSessionId
            isLoading = true

            val userMsg = AiAgentMessage(
                text = responseText, isUser = true,
                paramsInfo = "stage=planning|clarification-response", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += userMsg
            appendMessageToBranch(chatId, 2, userMsg)

            val result = try {
                val r = callClarificationPlanningApi(userRequestText, currentClarification, responseText)
                isErrorState = false
                r
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }

            if (sessionId != chatSessionId) { isLoading = false; return@launch }

            planText = result
            val respMsg = AiAgentMessage(
                text = result, isUser = false,
                paramsInfo = "stage=planning|clarification-followup", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += respMsg
            appendMessageToBranch(chatId, 2, respMsg)

            if (!isErrorState) {
                handleClarificationInPlanResult(result, chatId, planningMessages)
            }
            isLoading = false
        }
    }

    // ─── Retry helpers ─────────────────────────────────────────────────────────

    fun restartPlanning(chatId: Long) {
        if (userRequestText.isBlank()) return
        val planningMessages = branchesByChat[chatId]?.firstOrNull { it.number == 2 }?.messages ?: return
        chatSessionId++
        val sessionId = chatSessionId
        isPaused = false
        isErrorState = false
        planText = ""
        planClarificationNeeded = null
        planInvariantCheckFailed = false
        planInvariantViolationByAi = false
        invariantViolations = emptyList()
        lastPlanEditText = ""
        // сбрасываем всё зависимое от плана: старые шаги и результаты выполнения устарели
        executionSteps.clear()
        executionCurrentStepIndex = 0
        executionResults.clear()
        executionRecoveryInstruction = null
        validationFailure = null
        validationRecoveryBranchNumber = null
        validationRecoveryAttempt = 0
        executionText = ""
        planningMessages.clear()
        agentState = AgentState.Planning
        selectedBranchNumber = 2
        isLoading = true
        scope.launch {
            val result = try {
                val r = callPlanningApi(userRequestText)
                isErrorState = false
                r
            } catch (_: Exception) {
                isErrorState = true
                isLoading = false
                return@launch
            }
            if (sessionId != chatSessionId) { isLoading = false; return@launch }
            planText = result
            val msg = AiAgentMessage(
                text = result, isUser = false,
                paramsInfo = "stage=planning|restart", stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += msg
            appendMessageToBranch(chatId, 2, msg)
            handleClarificationInPlanResult(result, chatId, planningMessages)
            isLoading = false
        }
    }

    fun restartChecking(chatId: Long) {
        if (executionText.isBlank()) return  // нечего проверять без результатов выполнения
        val checkMessages = branchesByChat[chatId]?.firstOrNull { it.number == 4 }?.messages ?: return
        chatSessionId++
        val sessionId = chatSessionId
        isPaused = false
        isErrorState = false
        validationPhase = 1
        checkMessages.clear()
        agentState = AgentState.Checking
        selectedBranchNumber = 4
        isLoading = true
        scope.launch {
            runValidationLoop(chatId, checkMessages, sessionId)
        }
    }

    fun restartExecution() {
        val chatId = activeChatId ?: return
        if (executionSteps.isEmpty()) {
            if (planText.isBlank()) return
            val plan = try { lenientJson.decodeFromString<PlanJson>(planText) } catch (_: Exception) { return }
            if (plan.steps.isEmpty()) return
            executionSteps.addAll(plan.steps)
        }
        val execMessages = branchesByChat[chatId]?.firstOrNull { it.number == 3 }?.messages ?: return
        chatSessionId++
        val sessionId = chatSessionId
        isPaused = false
        isErrorState = false
        executionCurrentStepIndex = 0
        executionResults.clear()
        executionRecoveryInstruction = null
        validationFailure = null
        execMessages.clear()
        agentState = AgentState.Execution
        selectedBranchNumber = 3
        isLoading = true
        scope.launch {
            executeStepsLoop(chatId, execMessages, sessionId)
        }
    }

    fun restartBranch(chatId: Long, branchNumber: Int) {
        when (branchNumber) {
            1, 3 -> restartExecution()
            2 -> restartPlanning(chatId)
            4 -> restartChecking(chatId)
        }
    }

    fun retryPlanning() {
        val chatId = activeChatId ?: return
        if (isLoading) return
        val planningMessages = branchesByChat[chatId]?.firstOrNull { it.number == 2 }?.messages ?: return
        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            // повторяем тот же тип вызова, что провалился последним
            val (result, paramsInfo) = try {
                val r = if (lastPlanEditText.isNotEmpty()) {
                    callPlanEditApi(planText, lastPlanEditText)
                } else {
                    callPlanningApi(userRequestText)
                }
                isErrorState = false
                Pair(r, if (lastPlanEditText.isNotEmpty()) "stage=planning|edit|retry" else "stage=planning|retry")
            } catch (e: Exception) {
                isErrorState = true
                Pair(
                    "Request failed: ${e.message ?: "unknown error"}",
                    if (lastPlanEditText.isNotEmpty()) "stage=planning|edit|retry" else "stage=planning|retry"
                )
            }
            if (sessionId != chatSessionId) { isLoading = false; return@launch }
            if (!isErrorState) planText = result
            val msg = AiAgentMessage(
                text = result, isUser = false,
                paramsInfo = paramsInfo, stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            planningMessages += msg
            appendMessageToBranch(chatId, 2, msg)
            isLoading = false
        }
    }

    fun retryExecution() {
        val chatId = activeChatId ?: return
        if (isLoading) return
        isErrorState = false
        val execMessages = branchesByChat[chatId]?.firstOrNull { it.number == 3 }?.messages ?: return
        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            executeStepsLoop(chatId, execMessages, sessionId)
        }
    }

    fun applyValidationFix() {
        val chatId = activeChatId ?: return
        val failure = validationFailure ?: return
        if (isLoading) return

        val retryIndex = findStepIndexById(failure.retryFromStepId)
        val retryStepIds = executionSteps.drop(retryIndex).map { it.stepId }.toSet()
        retryStepIds.forEach { executionResults.remove(it) }
        executionCurrentStepIndex = retryIndex
        executionText = executionResults.entries.sortedBy { it.key }
            .joinToString("\n\n") { (id, res) -> "Шаг $id:\n$res" }
        executionRecoveryInstruction = buildString {
            appendLine("Проверка обнаружила проблему после выполнения плана.")
            appendLine("Проблема: ${failure.problem}")
            appendLine("Проблемный шаг: ${failure.failedStepId}")
            append("Решение: ${failure.proposedSolution}")
        }
        validationFailure = null
        selectedBranchNumber = 3
        agentState = AgentState.Execution

        val execMessages = branchesByChat[chatId]?.firstOrNull { it.number == 3 }?.messages ?: return
        val resumeMsg = AiAgentMessage(
            text = buildString {
                appendLine("Исправление после проверки")
                appendLine("Возврат к шагу ${failure.retryFromStepId}")
                appendLine("Проблема: ${failure.problem}")
                append("Решение: ${failure.proposedSolution}")
            },
            isUser = true,
            paramsInfo = "stage=execution|recovery|step=${failure.retryFromStepId}",
            stream = AiAgentStream.Raw,
            epoch = 0,
            createdAt = System.currentTimeMillis()
        )
        execMessages += resumeMsg
        appendMessageToBranch(chatId, 3, resumeMsg)
        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            isErrorState = false
            executeStepsLoop(chatId, execMessages, sessionId)
        }

    }
    fun retryChecking() {
        val chatId = activeChatId ?: return
        if (isLoading) return
        isErrorState = false
        validationFailure = null
        val checkMessages = branchesByChat[chatId]?.firstOrNull { it.number == 4 }?.messages ?: return
        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            runValidationLoop(chatId, checkMessages, sessionId)
        }
    }

    // ─── Chat management ───────────────────────────────────────────────────────

    fun clearChatSelection() {
        chatSessionId++
        isLoading = false
        isErrorState = false
        activeChatId = null
        regularMessagesByChat.clear()
        multiAgentTraceMessagesByChat.clear()
        isBranchingEnabled = false
        selectedBranchNumber = null
        agentState = AgentState.Idle
        userRequestText = ""
        planText = ""
        executionText = ""
        branchNames.clear()
        updateInputText(TextFieldValue(""))
        planEditInput = TextFieldValue("")
        lastPlanEditText = ""
        executionSteps.clear()
        executionCurrentStepIndex = 0
        executionResults.clear()
        executionRecoveryInstruction = null
        validationFailure = null
        validationRecoveryBranchNumber = null
        validationRecoveryAttempt = 0
        validationPhase = 1
        isPaused = false
        planClarificationNeeded = null
        invariantViolations = emptyList()
        planInvariantCheckFailed = false
        planInvariantViolationByAi = false
        apiSelectorExpanded = false
        modelSelectorExpanded = false
    }

    fun openChat(chatId: Long) {
        chatSessionId++
        isLoading = false
        activeChatId = chatId
        branchesByChat.remove(chatId)
        regularMessagesByChat[chatId] = loadRegularMessagesForChat(chatId)
        multiAgentTraceMessagesByChat[chatId] = loadMultiAgentTraceMessagesForChat(chatId)
        branchNames.clear()

        val branches = loadBranchesForChat(chatId)
        if (branches.isNotEmpty()) {
            branchesByChat[chatId] = branches
            branchCounterByChat[chatId] = branches.maxOfOrNull { it.number } ?: 1
            branchVisibilityByChat[chatId] = true
            isBranchingEnabled = true
            branchNames[1] = "Основная"
            branchNames[2] = "Планирование"
            branchNames[3] = "Выполнение"
            branchNames[4] = "Проверка"

            val maxBranch = branches.maxOfOrNull { it.number } ?: 0
            when {
                maxBranch >= 4 -> {
                    agentState = AgentState.Done
                    selectedBranchNumber = 1
                    planText = branches.firstOrNull { it.number == 2 }
                        ?.messages?.lastOrNull { !it.isUser }?.text.orEmpty()
                    executionText = branches.firstOrNull { it.number == 3 }
                        ?.messages?.filter { !it.isUser }?.joinToString("\n\n") { it.text }.orEmpty()
                }
                maxBranch >= 3 -> {
                    agentState = AgentState.Execution
                    selectedBranchNumber = 3
                    planText = branches.firstOrNull { it.number == 2 }
                        ?.messages?.lastOrNull { !it.isUser }?.text.orEmpty()
                    executionText = branches.firstOrNull { it.number == 3 }
                        ?.messages?.filter { !it.isUser }?.joinToString("\n\n") { it.text }.orEmpty()
                }
                maxBranch >= 2 -> {
                    agentState = AgentState.Planning
                    selectedBranchNumber = 2
                    val planningMsgs = branches.firstOrNull { it.number == 2 }?.messages.orEmpty()
                    planText = planningMsgs
                        .lastOrNull { !it.isUser && it.paramsInfo != "stage=planning|clarification" }
                        ?.text.orEmpty()
                    val lastClarIdx = planningMsgs.indexOfLast {
                        !it.isUser && it.paramsInfo == "stage=planning|clarification"
                    }
                    planClarificationNeeded = if (lastClarIdx >= 0 && planningMsgs.drop(lastClarIdx + 1).none { it.isUser }) {
                        planningMsgs[lastClarIdx].text
                    } else null
                }
                else -> {
                    agentState = AgentState.Idle
                    selectedBranchNumber = 1
                }
            }
        } else {
            isBranchingEnabled = false
            selectedBranchNumber = null
            agentState = AgentState.Idle
            userRequestText = ""
            planText = ""
            executionText = ""
        }

        updateInputText(TextFieldValue(""))
        invariantViolations = emptyList()
        apiSelectorExpanded = false
        modelSelectorExpanded = false
        if (!isStateMachineEnabled) {
            agentState = AgentState.Idle
            isErrorState = false
            isPaused = false
            planClarificationNeeded = null
            validationFailure = null
            validationRecoveryBranchNumber = null
            validationRecoveryAttempt = 0
            planInvariantCheckFailed = false
            planInvariantViolationByAi = false
            selectedBranchNumber = null
            isBranchingEnabled = false
        }
        selectedProfileId?.let { loadLongTermMemoryForProfile(it) }
    }

    fun setStateMachineEnabled(enabled: Boolean) {
        if (isStateMachineEnabled == enabled || isLoading) return
        isStateMachineEnabled = enabled
        saveAppSettingBool("state_machine_enabled", enabled)

        if (!enabled) {
            chatSessionId++
            agentState = AgentState.Idle
            isErrorState = false
            isPaused = false
            planClarificationNeeded = null
            validationFailure = null
            validationRecoveryBranchNumber = null
            validationRecoveryAttempt = 0
            invariantViolations = emptyList()
            planInvariantCheckFailed = false
            planInvariantViolationByAi = false
            activeChatId?.let { chatId ->
                regularMessagesByChat[chatId] = loadRegularMessagesForChat(chatId)
                isBranchingEnabled = false
                selectedBranchNumber = null
            }
        } else {
            activeChatId?.let(::openChat)
        }
    }

    fun createNewChatAndOpen() {
        val title = "Чат ${chats.size + 1}"
        queries.insertChat(
            title = title,
            created_at = System.currentTimeMillis(),
            selected_profile_id = selectedProfileId
        )
        val updatedChats = loadChatsFromDb()
        chats.clear()
        chats += updatedChats
        val latestChat = updatedChats.lastOrNull() ?: return
        openChat(latestChat.id)
    }

    fun deleteChat(chatId: Long) {
        if (isLoading) return
        val wasActive = activeChatId == chatId
        queries.deleteMultiAgentToolCallsByChat(chat_id = chatId)
        queries.deleteMultiAgentEventsByChat(chat_id = chatId)
        queries.deleteMultiAgentRunsByChat(chat_id = chatId)
        queries.deleteBranchMessagesByChat(chat_id = chatId)
        queries.deleteMessagesByChat(chatId)
        queries.deleteChatById(chatId)
        branchesByChat.remove(chatId)
        regularMessagesByChat.remove(chatId)
        multiAgentTraceMessagesByChat.remove(chatId)
        branchVisibilityByChat.remove(chatId)
        branchCounterByChat.remove(chatId)
        websocketChatIdsByKey.entries.removeAll { it.value == chatId }
        val updatedChats = loadChatsFromDb()
        chats.clear()
        chats += updatedChats
        if (updatedChats.isEmpty()) { clearChatSelection(); return }
        if (wasActive || updatedChats.none { it.id == activeChatId }) {
            openChat(updatedChats.last().id)
        }
    }

    fun deleteAllChats() {
        if (isLoading) return
        queries.deleteAllMessages()
        queries.deleteAllBranchMessages()
        queries.deleteAllMultiAgentToolCalls()
        queries.deleteAllMultiAgentEvents()
        queries.deleteAllMultiAgentSteps()
        queries.deleteAllMultiAgentRuns()
        queries.deleteAllChats()
        branchesByChat.clear()
        regularMessagesByChat.clear()
        multiAgentTraceMessagesByChat.clear()
        branchVisibilityByChat.clear()
        branchCounterByChat.clear()
        websocketChatIdsByKey.clear()
        chats.clear()
        clearChatSelection()
    }

    // ─── Init ──────────────────────────────────────────────────────────────────

    LaunchedEffect(Unit) {
        ensureProfileAndLtm()
        isStateMachineEnabled = appSettingBool("state_machine_enabled", default = true)
        isMcpEnabled = appSettingBool("mcp_enabled", default = false)
        isInvariantsEnabled = appSettingBool("invariants_enabled", default = true)
        isRagEnabled = loadAiAgentMainRagEnabled()
        agentModeState = AgentModeState(
            isEnabled = appSettingBool("agent_mode_enabled", default = false),
            projectFolderPath = appSettingString("agent_project_folder", default = "")
        )
        isMultiAgentEnabled = appSettingBool("multi_agent_enabled", default = false)
        isMultiAgentTraceMode = appSettingBool("multi_agent_trace_mode", default = false)
        if (agentModeState.isEnabled && isStateMachineEnabled) {
            setStateMachineEnabled(false)
        }
        ensureDefaultMultiAgentSubagents()
        refreshMultiAgentSubagents()
        mcpServerOptions.forEach { server ->
            mcpServerEnabled[server.url] = appSettingBool(
                key = mcpServerSettingKey(server.url),
                default = false
            )
        }
        val storedChats = loadChatsFromDb()
        chats.clear()
        chats += storedChats
        if (chats.isEmpty()) createNewChatAndOpen() else openChat(chats.last().id)
        mcpServerOptions.forEach { server -> refreshMcpServerTools(server.url) }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) inputFocusRequester.requestFocus()
    }

    val displayBranchNumber = selectedBranchNumber
    val displayMessages: List<AiAgentMessage> =
        if (agentModeState.isEnabled && isMultiAgentEnabled && isMultiAgentTraceMode) {
            multiAgentTraceMessagesByChat[activeChatId] ?: emptyList()
        } else if (!isStateMachineEnabled) {
            regularMessagesByChat[activeChatId] ?: emptyList()
        } else if (isBranchingEnabled && displayBranchNumber != null) {
            branchesByChat[activeChatId]?.firstOrNull { it.number == displayBranchNumber }?.messages ?: emptyList()
        } else {
            emptyList()
        }

    val latestExecutionAssistantMessage = displayMessages.lastOrNull { message ->
        !message.isUser && message.paramsInfo.startsWith("stage=execution|step=")
    }
    val parsedExecutionJson = remember(latestExecutionAssistantMessage?.text) {
        latestExecutionAssistantMessage?.text?.let(::tryParseJsonExecution)
    }

    LaunchedEffect(displayMessages.size, isLoading) {
        if (displayMessages.isNotEmpty()) listState.animateScrollToItem(displayMessages.lastIndex)
    }

    val activeChatTitle = chats.firstOrNull { it.id == activeChatId }?.title.orEmpty()
    val activeBranchName = if (isStateMachineEnabled) selectedBranchNumber?.let { branchNames[it] }.orEmpty() else ""
    val titleSuffix = buildString {
        if (activeChatTitle.isNotBlank()) append(" | $activeChatTitle")
        if (activeBranchName.isNotBlank()) append(" | $activeBranchName")
    }

    // ─── UI ────────────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(onClick = { screensMenuExpanded = true }, enabled = !isLoading) {
                            Text("AiAgentMain$titleSuffix")
                        }
                        DropdownMenu(
                            expanded = screensMenuExpanded,
                            onDismissRequest = { screensMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiAgentRAG) "AiAgentRAG ✓" else "AiAgentRAG") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiAgentRAG) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.EmbedingGeneration) "EmbedingGeneration ✓" else "EmbedingGeneration") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.EmbedingGeneration) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiStateAgent) "AiStateAgent ✓" else "AiStateAgent") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiStateAgent) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiAgentMCP) "AiAgentMCP ✓" else "AiAgentMCP") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiAgentMCP) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiAgentMain) "AiAgentMain ✓" else "AiAgentMain") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiAgentMain) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiWeek3) "Ai неделя 3 ✓" else "Ai неделя 3") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiWeek3) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiAgent) "AiAgent ✓" else "AiAgent") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiAgent) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.App) "App ✓" else "App") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.App) }
                            )
                        }
                    }
                },
                actions = {
                    Text(
                        text = if (isStateMachineEnabled) agentState.name else "CHAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = AiAgentMainScreenTheme.topBarContent,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = ::createNewChatAndOpen, enabled = !isLoading) {
                        Text("Новый чат")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AiAgentMainScreenTheme.topBarContainer,
                    titleContentColor = AiAgentMainScreenTheme.topBarContent,
                    actionIconContentColor = AiAgentMainScreenTheme.topBarContent
                )
            )
        }
    ) { innerPadding ->
        Row(
            modifier = modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Chat list sidebar ──────────────────────────────────────────────
            Column(
                modifier = Modifier.width(200.dp).fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Чаты", style = MaterialTheme.typography.titleSmall)
                    Box(modifier = Modifier.weight(1f))
                    TextButton(onClick = ::deleteAllChats, enabled = chats.isNotEmpty() && !isLoading) {
                        Text("Удалить всё")
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Screen Features", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = agentModeState.isEnabled,
                            onCheckedChange = { checked ->
                                agentModeState = agentModeState.copy(isEnabled = checked)
                                saveAppSettingBool("agent_mode_enabled", checked)
                                if (!checked && isMultiAgentEnabled) {
                                    isMultiAgentEnabled = false
                                    saveAppSettingBool("multi_agent_enabled", false)
                                }
                                if (checked && isStateMachineEnabled) {
                                    setStateMachineEnabled(false)
                                }
                                isAgentCommandMenuExpanded = shouldShowAgentCommandMenu(
                                    text = inputText.text,
                                    isAgentModeEnabled = checked
                                )
                            },
                            enabled = !isLoading
                        )
                        Text("Агент", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isMultiAgentEnabled,
                            onCheckedChange = { checked ->
                                isMultiAgentEnabled = checked
                                saveAppSettingBool("multi_agent_enabled", checked)
                                if (checked && isStateMachineEnabled) {
                                    setStateMachineEnabled(false)
                                }
                            },
                            enabled = !isLoading && agentModeState.isEnabled
                        )
                        Text("Мультиагентность", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isMultiAgentTraceMode,
                            onCheckedChange = { checked ->
                                isMultiAgentTraceMode = checked
                                saveAppSettingBool("multi_agent_trace_mode", checked)
                            },
                            enabled = !isLoading && agentModeState.isEnabled && isMultiAgentEnabled
                        )
                        Text("Trace чат", style = MaterialTheme.typography.labelSmall)
                    }
                    if (agentModeState.isEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Checkbox(
                                checked = isMultiAgentSettingsVisible,
                                onCheckedChange = { isMultiAgentSettingsVisible = it },
                                enabled = !isLoading
                            )
                            Text("Панель мультиагентов", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isRagEnabled,
                            onCheckedChange = {
                                isRagEnabled = it
                                saveAiAgentMainRagEnabled(it)
                            },
                            enabled = !isLoading && !agentModeState.isEnabled
                        )
                        Text("RAG", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isStateMachineEnabled,
                            onCheckedChange = { setStateMachineEnabled(it) },
                            enabled = !isLoading && !agentModeState.isEnabled
                        )
                        Text("State machine", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isInvariantPanelVisible,
                            onCheckedChange = { isInvariantPanelVisible = it },
                            enabled = !isLoading && !agentModeState.isEnabled
                        )
                        Text("Invariants panel", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isMemoryPanelVisible,
                            onCheckedChange = { isMemoryPanelVisible = it },
                            enabled = !isLoading && !agentModeState.isEnabled
                        )
                        Text("Memory panel", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isMcpPanelVisible,
                            onCheckedChange = { isMcpPanelVisible = it },
                            enabled = !isLoading && !agentModeState.isEnabled
                        )
                        Text("MCP panel", style = MaterialTheme.typography.labelSmall)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chats, key = { it.id }) { chat ->
                        val isSelected = chat.id == activeChatId
                        val chatBranches = branchesByChat[chat.id]
                        val hasBranches = isStateMachineEnabled && !chatBranches.isNullOrEmpty()
                        val areBranchesVisible = branchVisibilityByChat[chat.id] ?: true

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            if (isSelected) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(chat.title, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { deleteChat(chat.id) }, enabled = !isLoading) {
                                        Text("X", color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f)
                                            .clickable(enabled = !isLoading) { openChat(chat.id) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(chat.title, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    TextButton(onClick = { deleteChat(chat.id) }, enabled = !isLoading) { Text("X") }
                                }
                            }

                            if (hasBranches) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                            RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                        )
                                        .clickable(enabled = !isLoading) {
                                            branchVisibilityByChat[chat.id] = !areBranchesVisible
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (areBranchesVisible) "▾" else "▸",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            if (hasBranches && areBranchesVisible) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    chatBranches.forEach { branch ->
                                        val isActiveBranch = chat.id == activeChatId && selectedBranchNumber == branch.number
                                        val branchContentColor = if (isActiveBranch)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .then(
                                                    if (isActiveBranch)
                                                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                                    else
                                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                                )
                                                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = branchNames[branch.number] ?: "Ветка ${branch.number}",
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            if (activeChatId != chat.id) openChat(chat.id)
                                                            selectedBranchNumber = branch.number
                                                        }
                                                        .padding(vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = branchContentColor
                                                )
                                                TextButton(
                                                    onClick = {
                                                        if (activeChatId != chat.id) openChat(chat.id)
                                                        restartBranch(chat.id, branch.number)
                                                    },
                                                    enabled = !isLoading && isStateMachineEnabled,
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("↺", style = MaterialTheme.typography.labelSmall, color = branchContentColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiAgentMainScreenTheme.divider))

            // ── Main content ───────────────────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // API / model selectors
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = apiSelectorExpanded,
                        onExpandedChange = { if (!isLoading) apiSelectorExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedApi.label,
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isLoading)
                                .fillMaxWidth().height(52.dp),
                            readOnly = true,
                            enabled = !isLoading,
                            placeholder = { Text("API", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(apiSelectorExpanded) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                        ExposedDropdownMenu(expanded = apiSelectorExpanded, onDismissRequest = { apiSelectorExpanded = false }) {
                            AiAgentApi.entries.forEach { api ->
                                DropdownMenuItem(
                                    text = { Text(api.label) },
                                    onClick = { selectedApi = api; modelInput = api.defaultModel; apiSelectorExpanded = false }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = modelSelectorExpanded,
                        onExpandedChange = { if (!isLoading) modelSelectorExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !isLoading)
                                .fillMaxWidth().height(52.dp),
                            readOnly = true,
                            enabled = !isLoading,
                            placeholder = { Text("model", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelSelectorExpanded) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                        ExposedDropdownMenu(expanded = modelSelectorExpanded, onDismissRequest = { modelSelectorExpanded = false }) {
                            selectedApi.supportedModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = { modelInput = model; modelSelectorExpanded = false }
                                )
                            }
                        }
                    }
                }

                if (agentModeState.isEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Режим Агент: выберите папку проекта (обязательно для /help)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        OutlinedTextField(
                            value = agentModeState.projectFolderPath,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            readOnly = true,
                            enabled = !isLoading,
                            placeholder = { Text("Папка проекта не выбрана") },
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    pickProjectDirectory(agentModeState.projectFolderPath)?.let { selectedPath ->
                                        agentModeState = agentModeState.copy(projectFolderPath = selectedPath)
                                        saveAppSettingString("agent_project_folder", selectedPath)
                                    }
                                },
                                enabled = !isLoading
                            ) {
                                Text("Выбрать папку")
                            }
                            if (agentModeState.projectFolderPath.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        agentModeState = agentModeState.copy(projectFolderPath = "")
                                        saveAppSettingString("agent_project_folder", "")
                                    },
                                    enabled = !isLoading
                                ) {
                                    Text("Очистить")
                                }
                            }
                        }
                    }
                }

                if (agentModeState.isEnabled && isMultiAgentSettingsVisible) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Настройки мультиагентности",
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (multiAgentSubagents.isEmpty()) {
                            Text(
                                text = "Субагенты не загружены",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else {
                            multiAgentSubagents.forEach { subagent ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Checkbox(
                                        checked = subagent.isEnabled,
                                        onCheckedChange = { checked ->
                                            updateMultiAgentSubagentEnabled(subagent.key, checked)
                                        },
                                        enabled = !isLoading
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${subagent.title} (${subagent.key})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            text = subagent.description,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Messages
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Долговременная память: ${longTermMemory.size} зап.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (longTermMemory.isNotEmpty()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    items(displayMessages) { message ->
                        AiAgentBubble(message = message)
                    }
                    if (isStateMachineEnabled && agentState == AgentState.Execution && parsedExecutionJson != null) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "JSON ответа ИИ (Execution)",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    SelectionContainer {
                                        JsonTreeView(parsedExecutionJson)
                                    }
                                }
                            }
                        }
                    }
                    if (isLoading && !isPaused) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }

                // Invariant violations banner
                if (invariantViolations.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                if (planInvariantViolationByAi && isLoading)
                                    "ИИ нарушил ограничения — автоматически исправляю..."
                                else if (planInvariantViolationByAi)
                                    "ИИ нарушил ограничения (исправление не удалось):"
                                else
                                    "Найдены нарушения ограничений (запрошено пользователем):",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                            invariantViolations.forEach { violation ->
                                Text(
                                    "• $violation",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (agentState != AgentState.Checking && !planInvariantViolationByAi) {
                                TextButton(onClick = {
                                    invariantViolations = emptyList()
                                    agentState = invariantViolationPreviousState
                                    selectedBranchNumber = when (invariantViolationPreviousState) {
                                        AgentState.Planning -> 2
                                        AgentState.Execution -> 3
                                        else -> selectedBranchNumber
                                    }
                                }) {
                                    Text(
                                        "Вернуться на предыдущий статус",
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                if (false && agentState == AgentState.Execution && parsedExecutionJson != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "JSON ответа ИИ (Execution)",
                                style = MaterialTheme.typography.labelMedium
                            )
                            SelectionContainer {
                                JsonTreeView(parsedExecutionJson)
                            }
                        }
                    }
                }

                if (agentState == AgentState.Checking && validationFailure != null) {
                    val failure = validationFailure!!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Найдена проблема при проверке результата",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "Проблема: ${failure.problem}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Проблемный шаг плана: ${failure.failedStepId}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Источник определения шага: ${failure.stepDetectionSource}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Решение: ${failure.proposedSolution}",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // State machine action area
                if (!isStateMachineEnabled) {
                    AiAgentMainCommandMenu(
                        expanded = isAgentCommandMenuExpanded,
                        suggestions = agentCommandSuggestions,
                        modifier = Modifier.fillMaxWidth(),
                        onDismiss = { isAgentCommandMenuExpanded = false },
                        onSelect = { suggestion ->
                            val value = "${suggestion.command} "
                            updateInputText(
                                TextFieldValue(
                                    text = value,
                                    selection = TextRange(value.length)
                                )
                            )
                            inputFocusRequester.requestFocus()
                            isAgentCommandMenuExpanded = false
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { updateInputText(it) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(inputFocusRequester)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Enter) {
                                        return@onPreviewKeyEvent false
                                    }
                                    if (keyEvent.isAltPressed || keyEvent.isShiftPressed || keyEvent.isCtrlPressed) {
                                        val start = inputText.selection.min
                                        val end = inputText.selection.max
                                        updateInputText(
                                            inputText.copy(
                                                text = inputText.text.replaceRange(start, end, "\n"),
                                                selection = TextRange(start + 1)
                                            )
                                        )
                                        return@onPreviewKeyEvent true
                                    }
                                    sendPrimaryInput()
                                    true
                                },
                            enabled = !isLoading,
                            label = { Text("Сообщение") },
                            maxLines = 4
                        )
                        Button(
                            onClick = ::sendPrimaryInput,
                            enabled = inputText.text.isNotBlank() && !isLoading
                        ) { Text("Отправить") }
                    }
                } else when {
                    (agentState == AgentState.Execution || agentState == AgentState.Checking) && isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                if (isPaused) "Пауза" else
                                    if (agentState == AgentState.Execution) "Выполнение шагов..."
                                    else "Валидация...",
                                modifier = Modifier.weight(1f),
                                color = if (isPaused) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Button(onClick = { isPaused = !isPaused }) {
                                Text(if (isPaused) "Продолжить" else "Пауза")
                            }
                        }
                    }
                    agentState == AgentState.Planning && !isLoading -> {
                        if (planClarificationNeeded != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { updateInputText(it) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(inputFocusRequester)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Enter) {
                                                return@onPreviewKeyEvent false
                                            }
                                            if (keyEvent.isAltPressed || keyEvent.isShiftPressed || keyEvent.isCtrlPressed) {
                                                val start = inputText.selection.min
                                                val end = inputText.selection.max
                                                updateInputText(
                                                    inputText.copy(
                                                        text = inputText.text.replaceRange(start, end, "\n"),
                                                        selection = TextRange(start + 1)
                                                    )
                                                )
                                                return@onPreviewKeyEvent true
                                            }
                                            sendClarificationResponse()
                                            true
                                        },
                                    label = { Text("Ответ на уточняющий вопрос") },
                                    maxLines = 4
                                )
                                Button(
                                    onClick = ::sendClarificationResponse,
                                    enabled = inputText.text.isNotBlank()
                                ) { Text("Отправить") }
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // поле редактирования плана
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = planEditInput,
                                        onValueChange = { planEditInput = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Enter) {
                                                    return@onPreviewKeyEvent false
                                                }
                                                if (keyEvent.isAltPressed || keyEvent.isShiftPressed || keyEvent.isCtrlPressed) {
                                                    val start = planEditInput.selection.min
                                                    val end = planEditInput.selection.max
                                                    planEditInput = planEditInput.copy(
                                                        text = planEditInput.text.replaceRange(start, end, "\n"),
                                                        selection = TextRange(start + 1)
                                                    )
                                                    return@onPreviewKeyEvent true
                                                }
                                                sendPlanEdit()
                                                true
                                            },
                                        label = { Text("Скорректировать план") },
                                        maxLines = 4
                                    )
                                    Button(
                                        onClick = ::sendPlanEdit,
                                        enabled = planEditInput.text.isNotBlank()
                                    ) { Text("Отправить") }
                                }
                                // строка подтверждения / ошибки
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when {
                                        isErrorState -> {
                                            Text(
                                                "Ошибка при планировании",
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Button(onClick = ::retryPlanning) { Text("Повторить") }
                                        }
                                        planInvariantCheckFailed -> {
                                            Text(
                                                "Вы запросили действие, нарушающее инварианты. Скорректируйте план:",
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        else -> {
                                            Text(
                                                "Устраивает план?",
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Button(onClick = ::onPlanApproved) { Text("Да") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    agentState == AgentState.Execution && !isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isErrorState) {
                                Text(
                                    "Ошибка при выполнении",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = ::retryExecution) { Text("Повторить") }
                            } else {
                                Box(modifier = Modifier.weight(1f))
                                Button(onClick = ::onExecutionDone) { Text("Дальше") }
                            }
                        }
                    }
                    agentState == AgentState.Checking && !isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isErrorState) {
                                Text(
                                    "Ошибка при проверке",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = ::retryChecking) { Text("Повторить") }
                            } else if (validationFailure != null) {
                                Text(
                                    "Проверка нашла проблему. Можно вернуться к шагу исправления.",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = ::applyValidationFix) { Text("Исправить") }
                            } else if (invariantViolations.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f))
                                Button(onClick = {
                                    invariantViolations = emptyList()
                                    agentState = AgentState.Planning
                                    selectedBranchNumber = 2
                                }) { Text("К планированию") }
                                Button(onClick = {
                                    invariantViolations = emptyList()
                                    agentState = AgentState.Execution
                                    selectedBranchNumber = 3
                                }) { Text("К выполнению") }
                            } else {
                                Box(modifier = Modifier.weight(1f))
                                Button(onClick = ::onCheckingDone) { Text("Дальше") }
                            }
                        }
                    }
                    agentState == AgentState.Done -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Агент завершил работу.", modifier = Modifier.weight(1f))
                            Button(onClick = ::createNewChatAndOpen, enabled = !isLoading) { Text("Новый чат") }
                        }
                    }
                    agentState == AgentState.Idle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { updateInputText(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(inputFocusRequester)
                                    .onPreviewKeyEvent { keyEvent ->
                                        if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Enter) {
                                            return@onPreviewKeyEvent false
                                        }
                                        if (keyEvent.isAltPressed || keyEvent.isShiftPressed || keyEvent.isCtrlPressed) {
                                            val start = inputText.selection.min
                                            val end = inputText.selection.max
                                            updateInputText(
                                                inputText.copy(
                                                    text = inputText.text.replaceRange(start, end, "\n"),
                                                    selection = TextRange(start + 1)
                                                )
                                            )
                                            return@onPreviewKeyEvent true
                                        }
                                        sendPrimaryInput()
                                        true
                                    },
                                enabled = !isLoading,
                                label = { Text("Запрос") },
                                maxLines = 4
                            )
                            Button(
                                onClick = {
                                    sendPrimaryInput()
                                },
                                enabled = inputText.text.isNotBlank() && !isLoading
                            ) { Text("Отправить") }
                        }
                    }
                }
            }

            // ── Invariants panel ───────────────────────────────────────────────
            if (isInvariantPanelVisible) {
                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiAgentMainScreenTheme.divider))

                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isInvariantPanelExpanded = !isInvariantPanelExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Инварианты", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (isInvariantPanelExpanded) "▾" else "▸", style = MaterialTheme.typography.labelSmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = isInvariantsEnabled,
                            onCheckedChange = { checked ->
                                isInvariantsEnabled = checked
                                saveAppSettingBool("invariants_enabled", checked)
                            }
                        )
                        Text("Включить", style = MaterialTheme.typography.labelSmall)
                    }

                    if (isInvariantPanelExpanded) {
                        invariants.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(entry.value, style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { invariantEditingId = entry.id; invariantKeyInput = entry.key; invariantValueInput = entry.value; isInvariantFormVisible = true },
                                    enabled = !isLoading
                                ) { Text("✎") }
                                TextButton(
                                    onClick = { scope.launch { deleteInvariantEntry(entry.id) } },
                                    enabled = !isLoading
                                ) { Text("X") }
                            }
                        }
                        if (invariants.isEmpty()) {
                            Text("Инвариантов нет", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { invariantEditingId = null; invariantKeyInput = ""; invariantValueInput = ""; isInvariantFormVisible = !isInvariantFormVisible },
                                enabled = !isLoading
                            ) { Text(if (isInvariantFormVisible && invariantEditingId == null) "Отмена" else "+ Добавить") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        queries.deleteAllInvariantEntries()
                                        invariants.clear()
                                    }
                                },
                                enabled = invariants.isNotEmpty() && !isLoading
                            ) { Text("Очистить") }
                        }

                        if (isInvariantFormVisible) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = invariantKeyInput,
                                    onValueChange = { invariantKeyInput = it },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    enabled = invariantEditingId == null && !isLoading,
                                    placeholder = { Text("Ключ", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.labelSmall
                                )
                                OutlinedTextField(
                                    value = invariantValueInput,
                                    onValueChange = { invariantValueInput = it },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    enabled = !isLoading,
                                    placeholder = { Text("Значение", style = MaterialTheme.typography.labelSmall) },
                                    textStyle = MaterialTheme.typography.labelSmall,
                                    maxLines = 3
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            if (invariantKeyInput.isNotBlank() && invariantValueInput.isNotBlank()) {
                                                scope.launch {
                                                    val editId = invariantEditingId
                                                    if (editId != null) updateInvariantEntry(editId, invariantValueInput)
                                                    else insertInvariantEntry(invariantKeyInput, invariantValueInput)
                                                    isInvariantFormVisible = false
                                                    invariantEditingId = null
                                                    invariantKeyInput = ""
                                                    invariantValueInput = ""
                                                }
                                            }
                                        },
                                        enabled = invariantKeyInput.isNotBlank() && invariantValueInput.isNotBlank() && !isLoading
                                    ) { Text(if (invariantEditingId != null) "Сохранить" else "Добавить") }
                                    TextButton(
                                        onClick = { isInvariantFormVisible = false; invariantEditingId = null; invariantKeyInput = ""; invariantValueInput = "" },
                                        enabled = !isLoading
                                    ) { Text("Отмена") }
                                }
                            }
                        }
                    }
                }
            }

            // ── Long-term memory panel ─────────────────────────────────────────
            if (isMemoryPanelVisible) {
                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiAgentMainScreenTheme.divider))

                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { isMemoryPanelExpanded = !isMemoryPanelExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Долговременная память", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (isMemoryPanelExpanded) "▾" else "▸", style = MaterialTheme.typography.labelSmall)
                    }

                    if (isMemoryPanelExpanded) {
                        longTermMemory.forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(entry.value, style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(
                                    onClick = { memoryEditingId = entry.id; memoryKeyInput = entry.key; memoryValueInput = entry.value; isMemoryFormVisible = true },
                                    enabled = !isLoading
                                ) { Text("✎") }
                                TextButton(
                                    onClick = { scope.launch { deleteLongTermEntry(entry.id) } },
                                    enabled = !isLoading
                                ) { Text("X") }
                            }
                        }
                        if (longTermMemory.isEmpty()) {
                            Text("Записей нет", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = { memoryEditingId = null; memoryKeyInput = ""; memoryValueInput = ""; isMemoryFormVisible = !isMemoryFormVisible },
                                enabled = !isLoading
                            ) { Text(if (isMemoryFormVisible && memoryEditingId == null) "Отмена" else "+ Добавить") }
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        val profileId = selectedProfileId
                                        if (profileId != null) queries.deleteAllMemoryEntriesByProfile(profile_id = profileId)
                                        longTermMemory.clear()
                                    }
                                },
                                enabled = longTermMemory.isNotEmpty() && !isLoading
                            ) { Text("Очистить") }
                        }

                        if (isMemoryFormVisible) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(
                                    value = memoryKeyInput,
                                    onValueChange = { memoryKeyInput = it },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    enabled = !isLoading,
                                    placeholder = { Text("Ключ", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.labelSmall
                                )
                                OutlinedTextField(
                                    value = memoryValueInput,
                                    onValueChange = { memoryValueInput = it },
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                    enabled = !isLoading,
                                    placeholder = { Text("Значение", style = MaterialTheme.typography.labelSmall) },
                                    textStyle = MaterialTheme.typography.labelSmall,
                                    maxLines = 3
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            if (memoryKeyInput.isNotBlank() && memoryValueInput.isNotBlank()) {
                                                scope.launch {
                                                    val editId = memoryEditingId
                                                    if (editId != null) updateLongTermEntry(editId, memoryValueInput)
                                                    else insertLongTermEntry(memoryKeyInput, memoryValueInput)
                                                    isMemoryFormVisible = false
                                                    memoryEditingId = null
                                                    memoryKeyInput = ""
                                                    memoryValueInput = ""
                                                }
                                            }
                                        },
                                        enabled = memoryKeyInput.isNotBlank() && memoryValueInput.isNotBlank() && !isLoading
                                    ) { Text(if (memoryEditingId != null) "Сохранить" else "Добавить") }
                                    TextButton(
                                        onClick = { isMemoryFormVisible = false; memoryEditingId = null; memoryKeyInput = ""; memoryValueInput = "" },
                                        enabled = !isLoading
                                    ) { Text("Отмена") }
                                }
                            }
                        }
                    }
                }
            }

            if (isMcpPanelVisible) {
                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiAgentMainScreenTheme.divider))

                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("MCP servers", style = MaterialTheme.typography.titleSmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Checkbox(
                            checked = isMcpEnabled,
                            onCheckedChange = { checked ->
                                isMcpEnabled = checked
                                saveAppSettingBool("mcp_enabled", checked)
                                if (checked) {
                                    scope.launch { refreshEnabledMcpServerTools() }
                                } else {
                                    updateMcpSummaryFast()
                                }
                            },
                            enabled = !isLoading
                        )
                        Text(
                            text = if (isMcpEnabled) "Enabled" else "Disabled",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    mcpServerOptions.forEach { server ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = mcpServerEnabled[server.url] == true,
                                onCheckedChange = { checked ->
                                    mcpServerEnabled[server.url] = checked
                                    saveAppSettingBool(
                                        key = mcpServerSettingKey(server.url),
                                        value = checked
                                    )
                                    if (isMcpEnabled && checked) {
                                        scope.launch { refreshMcpServerTools(server.url) }
                                    } else {
                                        updateMcpSummaryFast()
                                    }
                                },
                                enabled = !isLoading && isMcpEnabled
                            )
                            Text(
                                text = server.title,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                refreshEnabledMcpServerTools()
                            }
                        },
                        enabled = !isLoading && isMcpEnabled
                    ) {
                        Text("Обновить инструменты")
                    }

                    SelectionContainer {
                        Text(
                            text = mcpToolsSummary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

