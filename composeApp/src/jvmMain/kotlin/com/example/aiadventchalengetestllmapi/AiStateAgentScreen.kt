package com.example.aiadventchalengetestllmapi.aistateagent

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
import com.example.aiadventchalengetestllmapi.aistateagentdb.AiStateAgentDatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.aistateagentdb.createAiStateAgentDatabase
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.DeepSeekResponseFormat
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val streamStripRegex = Regex("""\s*\|\s*stream=(real|raw)""")
private val epochStripRegex = Regex("""\s*\|\s*epoch=\d+""")
private val lenientJson = Json { ignoreUnknownKeys = true }

private fun tryParseJson(text: String): JsonElement? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
    return try {
        lenientJson.parseToJsonElement(trimmed)
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun JsonTreeView(element: JsonElement, indent: Int = 0) {
    val pad = (indent * 16).dp
    when (element) {
        is JsonObject -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                element.entries.forEach { (key, value) ->
                    when (value) {
                        is JsonObject, is JsonArray -> {
                            Column(
                                modifier = Modifier.padding(start = pad),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "$key:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                JsonTreeView(value, indent + 1)
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.padding(start = pad),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$key:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                JsonPrimitiveText(value)
                            }
                        }
                    }
                }
            }
        }
        is JsonArray -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                element.forEachIndexed { index, value ->
                    when (value) {
                        is JsonObject, is JsonArray -> {
                            Column(
                                modifier = Modifier.padding(start = pad),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "[$index]",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                JsonTreeView(value, indent + 1)
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.padding(start = pad),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                JsonPrimitiveText(value)
                            }
                        }
                    }
                }
            }
        }
        is JsonPrimitive -> JsonPrimitiveText(element)
        is JsonNull -> Text(
            text = "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun JsonPrimitiveText(element: JsonElement) {
    val primitive = element as? JsonPrimitive ?: return
    val boolVal = primitive.booleanOrNull
    val text: String
    val color: Color
    when {
        primitive is JsonNull -> {
            text = "—"
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        }
        boolVal != null -> {
            text = if (boolVal) "✓" else "✗"
            color = if (boolVal) Color(0xFF2E7D32) else Color(0xFFC62828)
        }
        else -> {
            text = primitive.contentOrNull ?: "null"
            color = MaterialTheme.colorScheme.onSurface
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}

private enum class AiAgentApi(
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
    )
}

private enum class AgentState { Idle, Planning, Execution, Checking, Done }

private data class AiAgentMessage(
    val text: String,
    val isUser: Boolean,
    val paramsInfo: String,
    val stream: AiAgentStream,
    val epoch: Int,
    val createdAt: Long
)

private data class LongTermMemoryEntry(
    val id: Long,
    val key: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long
)

private data class InvariantEntry(
    val id: Long,
    val key: String,
    val value: String,
    val createdAt: Long,
    val updatedAt: Long
)

private enum class AiAgentStream { Real, Raw }

private data class AiAgentChatItem(val id: Long, val title: String)

private data class AiAgentBranchItem(
    val number: Int,
    val messages: SnapshotStateList<AiAgentMessage>
)

private fun AiAgentMessage.displayParamsInfo(): String =
    paramsInfo
        .replace(streamStripRegex, "")
        .replace(epochStripRegex, "")

private fun aiAgentReadApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

internal object AiStateAgentScreenTheme {
    val primary = Color(0xFFE65100)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFFFE0B2)
    val onPrimaryContainer = Color(0xFF4A1800)
    val secondary = Color(0xFFBF360C)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFFF3E0)
    val onSecondaryContainer = Color(0xFF3E1000)
    val background = Color(0xFFFFFBF5)
    val onBackground = Color(0xFF1A0800)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF1A0800)
    val surfaceVariant = Color(0xFFFFF3E0)
    val onSurfaceVariant = Color(0xFF4A1800)
    val outline = Color(0xFFFFCC80)
    val userBubble = Color(0xFFC47A52)
    val onUserBubble = Color(0xFFFFFFFF)
    val assistantBubble = Color(0xFFE2C9A8)
    val onAssistantBubble = Color(0xFF2D0F00)
    val divider = Color(0xFFFFE0B2)
    val topBarContainer = Color(0xFFFFF3E0)
    val topBarContent = Color(0xFF6D2B00)

    fun colorScheme() = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStateAgentScreen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    MaterialTheme(colorScheme = AiStateAgentScreenTheme.colorScheme()) {
        AiStateAgentChat(
            modifier = Modifier.fillMaxSize(),
            currentScreen = currentScreen,
            onSelectScreen = onSelectScreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiStateAgentChat(
    modifier: Modifier = Modifier,
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val openAiApi = remember { OpenAiApi() }
    val gigaChatApi = remember { GigaChatApi() }
    val proxyOpenAiApi = remember { ProxyOpenAiApi() }
    val database = remember { createAiStateAgentDatabase(AiStateAgentDatabaseDriverFactory()) }
    val queries = remember(database) { database.chatHistoryQueries }

    val chats = remember { mutableStateListOf<AiAgentChatItem>() }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var screensMenuExpanded by remember { mutableStateOf(false) }
    var selectedApi by remember { mutableStateOf(AiAgentApi.DeepSeek) }
    var apiSelectorExpanded by remember { mutableStateOf(false) }
    var modelSelectorExpanded by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(AiAgentApi.DeepSeek.defaultModel) }
    var activeChatId by remember { mutableStateOf<Long?>(null) }
    var chatSessionId by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isMemoryPanelVisible by remember { mutableStateOf(false) }

    // Branching
    val branchesByChat = remember { mutableStateMapOf<Long, SnapshotStateList<AiAgentBranchItem>>() }
    val branchCounterByChat = remember { mutableStateMapOf<Long, Int>() }
    val branchVisibilityByChat = remember { mutableStateMapOf<Long, Boolean>() }
    var selectedBranchNumber by remember { mutableStateOf<Int?>(null) }
    var isBranchingEnabled by remember { mutableStateOf(false) }
    val branchNames = remember { mutableStateMapOf<Int, String>() }

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
    var invariantViolations by remember { mutableStateOf<List<String>>(emptyList()) }
    var invariantViolationPreviousState by remember { mutableStateOf(AgentState.Idle) }
    var planInvariantCheckFailed by remember { mutableStateOf(false) }
    var planInvariantViolationByAi by remember { mutableStateOf(false) }

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
    var validationPhase by remember { mutableStateOf(1) }
    var isPaused by remember { mutableStateOf(false) }
    var planClarificationNeeded by remember { mutableStateOf<String?>(null) }

    // ─── DB helpers ────────────────────────────────────────────────────────────

    fun loadChatsFromDb(): List<AiAgentChatItem> =
        queries.selectChats().executeAsList().map { AiAgentChatItem(id = it.id, title = it.title) }

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

    suspend fun callPlanningApi(userQuery: String): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val promptText = PLANNING_PROMPT_TEMPLATE.replace("{user_query}", userQuery)
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
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
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
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
        val promptText = CLARIFICATION_FOLLOW_UP_PROMPT_TEMPLATE
            .replace("{original_user_query}", originalUserQuery)
            .replace("{clarification_question}", clarificationQuestion)
            .replace("{user_clarification_response}", userClarificationResponse)
            .replace("{available_tools_formatted}", "нет доступных инструментов")
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
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
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
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
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
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
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
        }
        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            .ifEmpty { "Пустой ответ от ${requestApi.label}." }
    }

    suspend fun callApi(userMessageText: String): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            add(DeepSeekMessage(role = "user", content = userMessageText))
        }
        val request = DeepSeekChatRequest(model = model, messages = messages)
        val response = when (requestApi) {
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
        }
        return response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            .ifEmpty { "Пустой ответ от ${requestApi.label}." }
    }

    suspend fun callExecutionStepApi(
        taskContext: String,
        stepId: Int,
        stepDescription: String,
        previousResultsFormatted: String
    ): String {
        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val apiKey = aiAgentReadApiKey(requestApi.envVar)
        if (apiKey.isBlank()) error("Нет API ключа. Проверьте ${requestApi.envVar}")
        val promptText = EXECUTION_STEP_PROMPT_TEMPLATE
            .replace("{task_context}", taskContext)
            .replace("{step_id}", stepId.toString())
            .replace("{step_description}", stepDescription)
            .replace("{previous_results_formatted}", previousResultsFormatted)
        val messages = buildList {
            buildLtmSystemMessage()?.let { add(it) }
            buildInvariantsSystemMessage()?.let { add(it) }
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
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
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
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
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
            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
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

            val result = try {
                val r = callExecutionStepApi(
                    taskContext = taskContext,
                    stepId = step.stepId,
                    stepDescription = step.description,
                    previousResultsFormatted = previousResultsFormatted
                )
                isErrorState = false
                r
            } catch (e: Exception) {
                isErrorState = true
                "Request failed: ${e.message ?: "unknown error"}"
            }

            if (sessionId != chatSessionId) { isLoading = false; return }

            val stepRespMsg = AiAgentMessage(
                text = result, isUser = false,
                paramsInfo = "stage=execution|step=${step.stepId}",
                stream = AiAgentStream.Raw, epoch = 0,
                createdAt = System.currentTimeMillis()
            )
            execMessages += stepRespMsg
            appendMessageToBranch(chatId, 3, stepRespMsg)

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
        inputText = TextFieldValue("")
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

        inputText = TextFieldValue("")

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
        val execMessages = branchesByChat[chatId]?.firstOrNull { it.number == 3 }?.messages ?: return
        scope.launch {
            val sessionId = chatSessionId
            isLoading = true
            executeStepsLoop(chatId, execMessages, sessionId)
        }
    }

    fun retryChecking() {
        val chatId = activeChatId ?: return
        if (isLoading) return
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
        isBranchingEnabled = false
        selectedBranchNumber = null
        agentState = AgentState.Idle
        userRequestText = ""
        planText = ""
        executionText = ""
        branchNames.clear()
        inputText = TextFieldValue("")
        planEditInput = TextFieldValue("")
        lastPlanEditText = ""
        executionSteps.clear()
        executionCurrentStepIndex = 0
        executionResults.clear()
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

        inputText = TextFieldValue("")
        invariantViolations = emptyList()
        apiSelectorExpanded = false
        modelSelectorExpanded = false
        selectedProfileId?.let { loadLongTermMemoryForProfile(it) }
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
        queries.deleteBranchMessagesByChat(chat_id = chatId)
        queries.deleteMessagesByChat(chatId)
        queries.deleteChatById(chatId)
        branchesByChat.remove(chatId)
        branchVisibilityByChat.remove(chatId)
        branchCounterByChat.remove(chatId)
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
        queries.deleteAllChats()
        branchesByChat.clear()
        branchVisibilityByChat.clear()
        branchCounterByChat.clear()
        chats.clear()
        clearChatSelection()
    }

    // ─── Init ──────────────────────────────────────────────────────────────────

    LaunchedEffect(Unit) {
        ensureProfileAndLtm()
        val storedChats = loadChatsFromDb()
        chats.clear()
        chats += storedChats
        if (chats.isEmpty()) createNewChatAndOpen() else openChat(chats.last().id)
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) inputFocusRequester.requestFocus()
    }

    val displayMessages: List<AiAgentMessage> =
        if (isBranchingEnabled && selectedBranchNumber != null)
            branchesByChat[activeChatId]?.firstOrNull { it.number == selectedBranchNumber }?.messages
                ?: emptyList()
        else emptyList()

    LaunchedEffect(displayMessages.size, isLoading) {
        if (displayMessages.isNotEmpty()) listState.animateScrollToItem(displayMessages.lastIndex)
    }

    val activeChatTitle = chats.firstOrNull { it.id == activeChatId }?.title.orEmpty()
    val activeBranchName = selectedBranchNumber?.let { branchNames[it] }.orEmpty()
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
                            Text("AiStateAgent$titleSuffix")
                        }
                        DropdownMenu(
                            expanded = screensMenuExpanded,
                            onDismissRequest = { screensMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiStateAgent) "AiStateAgent ✓" else "AiStateAgent") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiStateAgent) }
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
                        text = agentState.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = AiStateAgentScreenTheme.topBarContent,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    TextButton(onClick = { isInvariantPanelVisible = !isInvariantPanelVisible }) {
                        Text(if (isInvariantPanelVisible) "Инварианты ▾" else "Инварианты ▸")
                    }
                    TextButton(onClick = { isMemoryPanelVisible = !isMemoryPanelVisible }) {
                        Text(if (isMemoryPanelVisible) "Память ▾" else "Память ▸")
                    }
                    TextButton(onClick = ::createNewChatAndOpen, enabled = !isLoading) {
                        Text("Новый чат")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AiStateAgentScreenTheme.topBarContainer,
                    titleContentColor = AiStateAgentScreenTheme.topBarContent,
                    actionIconContentColor = AiStateAgentScreenTheme.topBarContent
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chats, key = { it.id }) { chat ->
                        val isSelected = chat.id == activeChatId
                        val chatBranches = branchesByChat[chat.id]
                        val hasBranches = !chatBranches.isNullOrEmpty()
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
                                    chatBranches!!.forEach { branch ->
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

            Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiStateAgentScreenTheme.divider))

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

                // State machine action area
                when {
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
                                    onValueChange = { inputText = it },
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
                                                inputText = inputText.copy(
                                                    text = inputText.text.replaceRange(start, end, "\n"),
                                                    selection = TextRange(start + 1)
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
                                onValueChange = { inputText = it },
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
                                            inputText = inputText.copy(
                                                text = inputText.text.replaceRange(start, end, "\n"),
                                                selection = TextRange(start + 1)
                                            )
                                            return@onPreviewKeyEvent true
                                        }
                                        startPlanningPhase()
                                        true
                                    },
                                enabled = !isLoading,
                                label = { Text("Запрос") },
                                maxLines = 4
                            )
                            Button(
                                onClick = ::startPlanningPhase,
                                enabled = inputText.text.isNotBlank() && !isLoading
                            ) { Text("Отправить") }
                        }
                    }
                }
            }

            // ── Invariants panel ───────────────────────────────────────────────
            if (isInvariantPanelVisible) {
                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiStateAgentScreenTheme.divider))

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
                        Checkbox(checked = isInvariantsEnabled, onCheckedChange = { isInvariantsEnabled = it })
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
                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(AiStateAgentScreenTheme.divider))

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
        }
    }
}

@Composable
private fun AiAgentBubble(
    message: AiAgentMessage
) {
    val userBubbleColor = AiStateAgentScreenTheme.userBubble
    val userTextColor = AiStateAgentScreenTheme.onUserBubble
    val assistantBubbleColor = AiStateAgentScreenTheme.assistantBubble
    val assistantTextColor = AiStateAgentScreenTheme.onAssistantBubble

    val parsedElement = remember(message.text) {
        if (message.isUser) null else tryParseJson(message.text)
    }
    var showParsed by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(
                    color = if (message.isUser) userBubbleColor else assistantBubbleColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.isUser && parsedElement != null && showParsed) {
                    SelectionContainer {
                        JsonTreeView(parsedElement)
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = if (message.isUser) userTextColor else assistantTextColor
                        )
                    }
                }
                Text(
                    text = message.displayParamsInfo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) userTextColor.copy(alpha = 0.7f)
                            else assistantTextColor.copy(alpha = 0.7f)
                )
                if (!message.isUser && parsedElement != null) {
                    TextButton(
                        onClick = { showParsed = !showParsed },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (showParsed) "Исходный" else "Дерево",
                            style = MaterialTheme.typography.labelSmall,
                            color = assistantTextColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
