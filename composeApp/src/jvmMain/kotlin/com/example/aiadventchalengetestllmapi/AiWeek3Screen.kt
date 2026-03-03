package com.example.aiadventchalengetestllmapi.aiweek3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.BuildSecrets
import com.example.aiadventchalengetestllmapi.RootScreen
import com.example.aiadventchalengetestllmapi.aiweek3db.AiWeek3DatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.aiweek3db.createAiWeek3Database
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.launch
import java.util.Locale

private val tokensInParamsRegex = Regex("""(?:^|\s|\|)tokens=(\d+)(?:\s|$)""")
private val streamInParamsRegex = Regex("""(?:^|\s|\|)stream=(real|raw)(?:\s|$)""")
private val epochInParamsRegex = Regex("""(?:^|\s|\|)epoch=(\d+)(?:\s|$)""")
private val streamStripRegex = Regex("""\s*\|\s*stream=(real|raw)""")
private val epochStripRegex = Regex("""\s*\|\s*epoch=\d+""")

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

private enum class AiAgentStream { Real, Raw }

private data class AiAgentChatItem(
    val id: Long,
    val title: String,
    val selectedProfileId: Long?
)

private data class AiAgentProfileItem(
    val id: Long,
    val name: String,
    val featureState: AiAgentFeatureState
)

private data class AiAgentBranchItem(
    val number: Int,
    val messages: SnapshotStateList<AiAgentMessage>
)

private data class AiAgentCompletionResult(
    val answer: String,
    val requestTokens: Int?,
    val responseTokens: Int?,
    val totalTokens: Int?
)

private enum class AiAgentContextStrategy {
    Summarization,
    SlidingWindow,
    StickyFacts,
    Branching
}

private data class AiAgentFeatureState(
    val isLongTermMemoryEnabled: Boolean = true,
    val isSystemPromptEnabled: Boolean = false,
    val systemPromptText: String = "",
    val isSummarizationEnabled: Boolean = false,
    val summarizeAfterTokensInput: String = "10000",
    val isSlidingWindowEnabled: Boolean = false,
    val slidingWindowSizeInput: String = "12",
    val isStickyFactsEnabled: Boolean = false,
    val stickyFactsWindowSizeInput: String = "12",
    val stickyFactsSystemMessage: String = "",
    val isBranchingEnabled: Boolean = false,
    val showRawHistory: Boolean = false
)

private fun aiAgentTakeLastMessages(messages: List<AiAgentMessage>, lastN: Int): List<AiAgentMessage> {
    if (lastN <= 0) return emptyList()
    if (messages.size <= lastN) return messages
    return messages.takeLast(lastN)
}

private const val aiAgentStickyFactsExtractorPrompt = """
Ты выделяешь только устойчивые важные факты из беседы.
Верни ответ строго в формате "ключ: значение", по одной паре на строку, без markdown и пояснений.
Включай только подтвержденные факты из контекста.
Приоритет ключей: цель, ограничения, предпочтения, решения, договоренности.
Если данных нет, верни пустую строку.
"""

private fun aiAgentNormalizeFactsText(text: String): String =
    text.lineSequence()
        .map { it.trim().trimStart('-', '*', '•') }
        .filter { it.isNotEmpty() && it.contains(":") }
        .map { line ->
            val key = line.substringBefore(":").trim().lowercase(Locale.getDefault())
            val value = line.substringAfter(":").trim()
            if (key.isNotEmpty() && value.isNotEmpty()) "$key: $value" else ""
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

private fun aiAgentFactsMapFromText(text: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    aiAgentNormalizeFactsText(text)
        .lineSequence()
        .filter { it.contains(":") }
        .forEach { line ->
            val key = line.substringBefore(":").trim()
            val value = line.substringAfter(":").trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }
    return result
}

private fun aiAgentFactsSystemMessage(factsText: String): DeepSeekMessage? {
    val normalizedFacts = aiAgentNormalizeFactsText(factsText)
    if (normalizedFacts.isBlank()) return null
    val systemText = buildString {
        append("Важные факты диалога (ключ-значение):\n")
        append(normalizedFacts)
        append("\nСледуй этим фактам при ответе, если они относятся к запросу.")
    }
    return DeepSeekMessage(role = "system", content = systemText)
}

private fun aiAgentSystemPromptMessage(systemPromptText: String): DeepSeekMessage? {
    val text = systemPromptText.trim()
    if (text.isEmpty()) return null
    return DeepSeekMessage(role = "system", content = text)
}

private fun aiAgentLongTermMemoryMessage(entries: List<LongTermMemoryEntry>): DeepSeekMessage? {
    if (entries.isEmpty()) return null
    val content = buildString {
        entries.forEach { e -> appendLine("${e.key}: ${e.value}") }
        append("\nУчитывай эту информацию во всех ответах.")
    }
    return DeepSeekMessage(role = "system", content = content.trim())
}

private fun aiAgentMergeSystemPrompts(
    baseSystemPrompt: String,
    stickyFactsSystemMessage: String
): DeepSeekMessage? {
    val base = baseSystemPrompt.trim()
    val sticky = aiAgentFactsSystemMessage(stickyFactsSystemMessage)?.content.orEmpty().trim()
    val content = buildString {
        if (base.isNotEmpty()) append(base)
        if (base.isNotEmpty() && sticky.isNotEmpty()) append("\n\n")
        if (sticky.isNotEmpty()) append(sticky)
    }
    if (content.isBlank()) return null
    return DeepSeekMessage(role = "system", content = content)
}

private fun AiAgentMessage.toRequestMessage(): DeepSeekMessage =
    DeepSeekMessage(
        role = if (isUser) "user" else "assistant",
        content = text
    )

private fun AiAgentMessage.isApiHistoryMessage(): Boolean =
    isUser || !text.startsWith("Request failed:", ignoreCase = true)

private fun aiAgentReadApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

private fun Double.aiAgentFormatSeconds(): String = String.format(Locale.US, "%.2f", this)

private fun AiAgentMessage.tokensSpent(): Int =
    tokensInParamsRegex.find(paramsInfo)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

private fun aiAgentStreamFromParams(paramsInfo: String): AiAgentStream? =
    when (streamInParamsRegex.find(paramsInfo)?.groupValues?.getOrNull(1)) {
        "real" -> AiAgentStream.Real
        "raw" -> AiAgentStream.Raw
        else -> null
    }

private fun aiAgentEpochFromParams(paramsInfo: String): Int? =
    epochInParamsRegex.find(paramsInfo)?.groupValues?.getOrNull(1)?.toIntOrNull()

private fun aiAgentStreamSuffix(stream: AiAgentStream, epoch: Int?): String =
    when (stream) {
        AiAgentStream.Raw -> " | stream=raw"
        AiAgentStream.Real -> " | stream=real | epoch=${epoch ?: 0}"
    }

private fun aiAgentApplyStream(paramsInfo: String, stream: AiAgentStream, epoch: Int?): String =
    paramsInfo + aiAgentStreamSuffix(stream, epoch)

private fun AiAgentMessage.displayParamsInfo(): String =
    paramsInfo
        .replace(streamStripRegex, "")
        .replace(epochStripRegex, "")

/**
 * Тема экрана AiWeek3. Меняйте цвета здесь, чтобы изменить оформление всего экрана.
 */
internal object AiWeek3ScreenTheme {
    // Основная палитра красных оттенков
    val primary = Color(0xFFB71C1C)            // Red 900 — основной акцент
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFFFCDD2)   // Red 100 — фон пузырьков пользователя
    val onPrimaryContainer = Color(0xFF5C0000) // Тёмно-красный — текст пользователя

    val secondary = Color(0xFFD32F2F)          // Red 700
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFFEBEE) // Red 50
    val onSecondaryContainer = Color(0xFF7F0000)

    val background = Color(0xFFFFF8F8)         // Тёплый белый с красным оттенком
    val onBackground = Color(0xFF1A0000)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF1A0000)
    val surfaceVariant = Color(0xFFFFEAEA)
    val onSurfaceVariant = Color(0xFF5C0000)
    val outline = Color(0xFFEF9A9A)            // Red 200

    // Пузырьки ответов ассистента
    val assistantBubble = Color(0xFFFFF0F0)    // Очень светлый розово-красный
    val onAssistantBubble = Color(0xFF3E0000)  // Тёмно-бордовый

    // Баннер Sticky Facts
    val stickyFactsBg = Color(0xFFFFEBEE)      // Red 50
    val stickyFactsBorder = Color(0xFFEF9A9A)  // Red 200
    val stickyFactsTitle = Color(0xFF7F0000)   // Тёмно-красный заголовок
    val stickyFactsText = Color(0xFF1A0000)    // Почти чёрный с красным оттенком

    // Разделительные линии между панелями
    val divider = Color(0xFFFFCDD2)            // Red 100

    // TopAppBar
    val topBarContainer = Color(0xFFFFEBEE)    // Red 50 — светлая красная шапка
    val topBarContent = Color(0xFF7F0000)      // Тёмно-красный текст/иконки

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
fun AiWeek3Screen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    MaterialTheme(colorScheme = AiWeek3ScreenTheme.colorScheme()) {
        AiWeek3Chat(
            modifier = Modifier.fillMaxSize(),
            currentScreen = currentScreen,
            onSelectScreen = onSelectScreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiWeek3Chat(
    modifier: Modifier = Modifier,
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val openAiApi = remember { OpenAiApi() }
    val gigaChatApi = remember { GigaChatApi() }
    val proxyOpenAiApi = remember { ProxyOpenAiApi() }
    val database = remember { createAiWeek3Database(AiWeek3DatabaseDriverFactory()) }
    val queries = remember(database) { database.chatHistoryQueries }

    val chats = remember { mutableStateListOf<AiAgentChatItem>() }
    val rawMessages = remember { mutableStateListOf<AiAgentMessage>() }
    val realMessages = remember { mutableStateListOf<AiAgentMessage>() }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var screensMenuExpanded by remember { mutableStateOf(false) }
    var selectedApi by remember { mutableStateOf(AiAgentApi.DeepSeek) }
    var apiSelectorExpanded by remember { mutableStateOf(false) }
    var modelSelectorExpanded by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(AiAgentApi.DeepSeek.defaultModel) }
    var activeChatId by remember { mutableStateOf<Long?>(null) }
    val profiles = remember { mutableStateListOf<AiAgentProfileItem>() }
    var selectedProfileId by remember { mutableStateOf<Long?>(null) }
    var selectedProfileNameInput by remember { mutableStateOf("") }
    var newProfileNameInput by remember { mutableStateOf("") }
    var profileSelectorExpanded by remember { mutableStateOf(false) }
    var isProfileRenameMode by remember { mutableStateOf(false) }
    var isProfileCreateMode by remember { mutableStateOf(false) }
    var isApplyingProfileState by remember { mutableStateOf(false) }
    var chatSessionId by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isFeaturesPanelVisible by remember { mutableStateOf(false) }
    var isSystemPromptEnabled by remember { mutableStateOf(false) }
    var systemPromptText by remember { mutableStateOf("") }
    var isSummarizationEnabled by remember { mutableStateOf(false) }
    var summarizeAfterTokensInput by remember { mutableStateOf("10000") }
    var isSlidingWindowEnabled by remember { mutableStateOf(false) }
    var slidingWindowSizeInput by remember { mutableStateOf("12") }
    var isStickyFactsEnabled by remember { mutableStateOf(false) }
    var stickyFactsWindowSizeInput by remember { mutableStateOf("12") }
    var stickyFactsSystemMessage by remember { mutableStateOf("") }
    val stickyFacts = remember { mutableStateMapOf<String, String>() }
    var isBranchingEnabled by remember { mutableStateOf(false) }
    val branchesByChat = remember { mutableStateMapOf<Long, SnapshotStateList<AiAgentBranchItem>>() }
    val branchVisibilityByChat = remember { mutableStateMapOf<Long, Boolean>() }
    val branchCounterByChat = remember { mutableStateMapOf<Long, Int>() }
    val branchingEnabledByChat = remember { mutableStateMapOf<Long, Boolean>() }
    var selectedBranchNumber by remember { mutableStateOf<Int?>(null) }
    var showRawHistory by remember { mutableStateOf(false) }
    var realEpoch by remember { mutableIntStateOf(0) }

    // Tier 3: Долговременная память
    val longTermMemory = remember { mutableStateListOf<LongTermMemoryEntry>() }
    var isLongTermMemoryEnabled by remember { mutableStateOf(true) }
    var isMemoryPanelExpanded by remember { mutableStateOf(true) }
    var memoryEditingId by remember { mutableStateOf<Long?>(null) }
    var memoryKeyInput by remember { mutableStateOf("") }
    var memoryValueInput by remember { mutableStateOf("") }
    var isMemoryFormVisible by remember { mutableStateOf(false) }

    fun loadChatsFromDb(): List<AiAgentChatItem> {
        val result = queries.selectChats().executeAsList().map {
            AiAgentChatItem(
                id = it.id,
                title = it.title,
                selectedProfileId = it.selected_profile_id
            )
        }
        val existingIds = result.map { it.id }.toSet()
        branchingEnabledByChat.keys.toList()
            .filterNot { it in existingIds }
            .forEach { branchingEnabledByChat.remove(it) }
        result.forEach { chat ->
            val isEnabled = profiles.firstOrNull { it.id == chat.selectedProfileId }
                ?.featureState
                ?.isBranchingEnabled
                ?: false
            branchingEnabledByChat[chat.id] = isEnabled
        }
        return result
    }

    fun loadProfilesFromDb(): List<AiAgentProfileItem> {
        return queries.selectProfiles().executeAsList().map { row ->
            AiAgentProfileItem(
                id = row.id,
                name = row.name,
                featureState = AiAgentFeatureState(
                    isLongTermMemoryEnabled = row.is_long_term_memory_enabled != 0L,
                    isSystemPromptEnabled = row.is_system_prompt_enabled != 0L,
                    systemPromptText = row.system_prompt_text,
                    isSummarizationEnabled = row.is_summarization_enabled != 0L,
                    summarizeAfterTokensInput = row.summarize_after_tokens,
                    isSlidingWindowEnabled = row.is_sliding_window_enabled != 0L,
                    slidingWindowSizeInput = row.sliding_window_size,
                    isStickyFactsEnabled = row.is_sticky_facts_enabled != 0L,
                    stickyFactsWindowSizeInput = row.sticky_facts_window_size,
                    stickyFactsSystemMessage = row.sticky_facts_system_message,
                    isBranchingEnabled = row.is_branching_enabled != 0L,
                    showRawHistory = row.show_raw_history != 0L
                )
            )
        }
    }

    fun refreshProfiles() {
        profiles.clear()
        profiles += loadProfilesFromDb()
    }

    fun applyFeatureState(state: AiAgentFeatureState) {
        isApplyingProfileState = true
        isLongTermMemoryEnabled = state.isLongTermMemoryEnabled
        isSystemPromptEnabled = state.isSystemPromptEnabled
        systemPromptText = state.systemPromptText
        isSummarizationEnabled = state.isSummarizationEnabled
        summarizeAfterTokensInput = state.summarizeAfterTokensInput
        isSlidingWindowEnabled = state.isSlidingWindowEnabled
        slidingWindowSizeInput = state.slidingWindowSizeInput
        isStickyFactsEnabled = state.isStickyFactsEnabled
        stickyFactsWindowSizeInput = state.stickyFactsWindowSizeInput
        stickyFactsSystemMessage = aiAgentNormalizeFactsText(state.stickyFactsSystemMessage)
        stickyFacts.clear()
        stickyFacts.putAll(aiAgentFactsMapFromText(stickyFactsSystemMessage))
        isBranchingEnabled = state.isBranchingEnabled
        showRawHistory = state.showRawHistory
        isApplyingProfileState = false
    }

    fun refreshBranchingForProfile(profileId: Long) {
        val enabled = profiles.firstOrNull { it.id == profileId }?.featureState?.isBranchingEnabled ?: false
        chats.filter { it.selectedProfileId == profileId }.forEach { chat ->
            branchingEnabledByChat[chat.id] = enabled
        }
    }

    fun ensureDefaultProfileExists() {
        if (profiles.isNotEmpty()) return
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
        refreshProfiles()
    }

    fun nextAutoProfileName(): String {
        var number = 1
        while (true) {
            val candidate = "Профиль $number"
            if (profiles.none { it.name.equals(candidate, ignoreCase = true) }) {
                return candidate
            }
            number++
        }
    }

    fun loadLongTermMemoryForProfile(profileId: Long) {
        val entries = queries.selectMemoryEntriesByProfile(profile_id = profileId).executeAsList().map { row ->
            LongTermMemoryEntry(
                id = row.id,
                key = row.entry_key, value = row.entry_value,
                createdAt = row.created_at, updatedAt = row.updated_at
            )
        }
        longTermMemory.clear()
        longTermMemory += entries
    }

    fun loadMessagesForChat(chatId: Long) {
        rawMessages.clear()
        realMessages.clear()
        realEpoch = 0
        queries.selectMessagesByChat(chatId).executeAsList().forEach { row ->
            val stream = aiAgentStreamFromParams(row.params_info)
            val epoch = aiAgentEpochFromParams(row.params_info) ?: 0
            val baseMessage = AiAgentMessage(
                text = row.message,
                isUser = row.role == "user",
                paramsInfo = row.params_info,
                stream = stream ?: AiAgentStream.Raw,
                epoch = epoch,
                createdAt = row.created_at
            )

            when (stream) {
                AiAgentStream.Raw -> rawMessages += baseMessage
                AiAgentStream.Real -> {
                    realMessages += baseMessage
                    if (epoch > realEpoch) {
                        realEpoch = epoch
                    }
                }
                null -> {
                    rawMessages += baseMessage.copy(stream = AiAgentStream.Raw, epoch = 0)
                    realMessages += baseMessage.copy(stream = AiAgentStream.Real, epoch = 0)
                }
            }
        }

        if (realEpoch > 0) {
            val latestReal = realMessages.filter { it.epoch == realEpoch }
            realMessages.clear()
            realMessages += latestReal
        } else {
            realMessages.clear()
        }
    }

    fun loadFeatureStateForChat(chatId: Long) {
        val chatRow = queries.selectChatById(chatId).executeAsOneOrNull()
        var profileId = chatRow?.selected_profile_id
        if (profileId == null) {
            val fallbackId = profiles.firstOrNull()?.id ?: return
            queries.updateChatSelectedProfile(selected_profile_id = fallbackId, id = chatId)
            profileId = fallbackId
        }
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        selectedProfileId = profile.id
        selectedProfileNameInput = profile.name
        applyFeatureState(profile.featureState)
        loadLongTermMemoryForProfile(profile.id)
        branchingEnabledByChat[chatId] = profile.featureState.isBranchingEnabled
    }

    fun selectProfileForActiveChat(profileId: Long) {
        val chatId = activeChatId ?: return
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        queries.updateChatSelectedProfile(selected_profile_id = profile.id, id = chatId)
        selectedProfileId = profile.id
        selectedProfileNameInput = profile.name
        applyFeatureState(profile.featureState)
        loadLongTermMemoryForProfile(profile.id)
        branchingEnabledByChat[chatId] = profile.featureState.isBranchingEnabled
        val updatedChats = loadChatsFromDb()
        chats.clear()
        chats += updatedChats
    }

    fun createProfile() {
        val requestedName = newProfileNameInput.trim()
        val profileName = if (requestedName.isEmpty()) nextAutoProfileName() else requestedName
        if (profiles.any { it.name.equals(profileName, ignoreCase = true) }) return

        queries.insertProfile(
            name = profileName,
            is_long_term_memory_enabled = if (isLongTermMemoryEnabled) 1 else 0,
            is_system_prompt_enabled = if (isSystemPromptEnabled) 1 else 0,
            system_prompt_text = systemPromptText,
            is_summarization_enabled = if (isSummarizationEnabled) 1 else 0,
            summarize_after_tokens = summarizeAfterTokensInput,
            is_sliding_window_enabled = if (isSlidingWindowEnabled) 1 else 0,
            sliding_window_size = slidingWindowSizeInput,
            is_sticky_facts_enabled = if (isStickyFactsEnabled) 1 else 0,
            sticky_facts_window_size = stickyFactsWindowSizeInput,
            sticky_facts_system_message = if (isStickyFactsEnabled) stickyFactsSystemMessage else "",
            is_branching_enabled = if (isBranchingEnabled) 1 else 0,
            show_raw_history = if (showRawHistory) 1 else 0
        )
        refreshProfiles()
        newProfileNameInput = ""
        val created = profiles.lastOrNull() ?: return
        selectProfileForActiveChat(created.id)
        isProfileCreateMode = false
    }

    fun renameSelectedProfile() {
        val profileId = selectedProfileId ?: return
        val newName = selectedProfileNameInput.trim()
        if (newName.isEmpty()) return
        if (profiles.any { it.id != profileId && it.name.equals(newName, ignoreCase = true) }) return
        val profile = profiles.firstOrNull { it.id == profileId } ?: return
        val state = profile.featureState
        queries.updateProfile(
            name = newName,
            is_long_term_memory_enabled = if (state.isLongTermMemoryEnabled) 1 else 0,
            is_system_prompt_enabled = if (state.isSystemPromptEnabled) 1 else 0,
            system_prompt_text = state.systemPromptText,
            is_summarization_enabled = if (state.isSummarizationEnabled) 1 else 0,
            summarize_after_tokens = state.summarizeAfterTokensInput,
            is_sliding_window_enabled = if (state.isSlidingWindowEnabled) 1 else 0,
            sliding_window_size = state.slidingWindowSizeInput,
            is_sticky_facts_enabled = if (state.isStickyFactsEnabled) 1 else 0,
            sticky_facts_window_size = state.stickyFactsWindowSizeInput,
            sticky_facts_system_message = state.stickyFactsSystemMessage,
            is_branching_enabled = if (state.isBranchingEnabled) 1 else 0,
            show_raw_history = if (state.showRawHistory) 1 else 0,
            id = profileId
        )
        refreshProfiles()
        selectedProfileNameInput = newName
        val updatedChats = loadChatsFromDb()
        chats.clear()
        chats += updatedChats
        isProfileRenameMode = false
    }

    fun insertLongTermEntry(key: String, value: String) {
        val profileId = selectedProfileId ?: return
        val now = System.currentTimeMillis()
        queries.insertMemoryEntry(profile_id = profileId, entry_key = key.trim(), entry_value = value.trim(), created_at = now, updated_at = now)
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

    fun saveAssistantMessageToLongTermMemory(text: String) {
        memoryEditingId = null
        memoryKeyInput = ""
        memoryValueInput = text.take(500)
        isMemoryFormVisible = true
        isFeaturesPanelVisible = true
        isMemoryPanelExpanded = true
    }

    fun saveActiveChatFeatureState() {
        val profileId = selectedProfileId ?: return
        val profileName = profiles.firstOrNull { it.id == profileId }?.name ?: return
        queries.upsertFeatureState(
            id = profileId,
            name = profileName,
            is_long_term_memory_enabled = if (isLongTermMemoryEnabled) 1 else 0,
            is_system_prompt_enabled = if (isSystemPromptEnabled) 1 else 0,
            system_prompt_text = systemPromptText,
            is_summarization_enabled = if (isSummarizationEnabled) 1 else 0,
            summarize_after_tokens = summarizeAfterTokensInput,
            is_sliding_window_enabled = if (isSlidingWindowEnabled) 1 else 0,
            sliding_window_size = slidingWindowSizeInput,
            is_sticky_facts_enabled = if (isStickyFactsEnabled) 1 else 0,
            sticky_facts_window_size = stickyFactsWindowSizeInput,
            sticky_facts_system_message = if (isStickyFactsEnabled) stickyFactsSystemMessage else "",
            is_branching_enabled = if (isBranchingEnabled) 1 else 0,
            show_raw_history = if (showRawHistory) 1 else 0
        )
        refreshProfiles()
        refreshBranchingForProfile(profileId)
    }

    fun loadBranchesForChat(chatId: Long): SnapshotStateList<AiAgentBranchItem> {
        val branchMap = linkedMapOf<Int, SnapshotStateList<AiAgentMessage>>()
        queries.selectBranchMessagesByChat(chatId).executeAsList().forEach { row ->
            val stream = when (row.stream) {
                "real" -> AiAgentStream.Real
                else -> AiAgentStream.Raw
            }
            val message = AiAgentMessage(
                text = row.message,
                isUser = row.role == "user",
                paramsInfo = row.params_info,
                stream = stream,
                epoch = row.epoch.toInt(),
                createdAt = row.created_at
            )
            val list = branchMap.getOrPut(row.branch_number.toInt()) { mutableStateListOf() }
            list += message
        }

        return branchMap.entries
            .sortedBy { it.key }
            .map { (number, messages) -> AiAgentBranchItem(number = number, messages = messages) }
            .toMutableStateList()
    }

    fun saveBranchSnapshot(chatId: Long, branchNumber: Int, messages: List<AiAgentMessage>) {
        queries.deleteBranchMessagesByChatAndBranch(chat_id = chatId, branch_number = branchNumber.toLong())
        messages.forEach { message ->
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

    fun snapshotCurrentHistory(): SnapshotStateList<AiAgentMessage> {
        val source = if (realEpoch > 0) realMessages else rawMessages
        return source.map { it.copy() }.toMutableStateList()
    }

    fun ensureBaseBranchForChat(chatId: Long, resetFromCurrentHistory: Boolean = false): SnapshotStateList<AiAgentBranchItem> {
        val existing = branchesByChat[chatId] ?: run {
            val loaded = loadBranchesForChat(chatId)
            if (loaded.isNotEmpty()) {
                branchesByChat[chatId] = loaded
                branchCounterByChat[chatId] = loaded.maxOfOrNull { it.number } ?: 1
                branchVisibilityByChat.putIfAbsent(chatId, true)
                return loaded
            }
            val baseBranch = AiAgentBranchItem(
                number = 1,
                messages = snapshotCurrentHistory()
            )
            val created = mutableStateListOf(baseBranch)
            branchesByChat[chatId] = created
            branchCounterByChat[chatId] = 1
            branchVisibilityByChat.putIfAbsent(chatId, true)
            saveBranchSnapshot(chatId = chatId, branchNumber = 1, messages = created.first().messages)
            return created
        }

        if (existing.isEmpty()) {
            existing += AiAgentBranchItem(
                number = 1,
                messages = snapshotCurrentHistory()
            )
            branchCounterByChat[chatId] = 1
            saveBranchSnapshot(chatId = chatId, branchNumber = 1, messages = existing.first().messages)
        }

        if (resetFromCurrentHistory) {
            val base = existing.firstOrNull() ?: return existing
            base.messages.clear()
            base.messages += snapshotCurrentHistory()
            while (existing.size > 1) {
                existing.removeLast()
            }
            branchCounterByChat[chatId] = 1
            queries.deleteBranchMessagesByChatExceptFirst(chat_id = chatId)
            saveBranchSnapshot(chatId = chatId, branchNumber = 1, messages = base.messages)
        }

        branchVisibilityByChat.putIfAbsent(chatId, true)
        return existing
    }

    fun createBranchForActiveChat() {
        val chatId = activeChatId ?: return
        val branches = ensureBaseBranchForChat(chatId)
        val nextNumber = (branchCounterByChat[chatId] ?: branches.maxOfOrNull { it.number } ?: 1) + 1
        val activeBranchMessages = if (isBranchingEnabled) {
            branches.firstOrNull { it.number == selectedBranchNumber }?.messages
        } else {
            null
        }
        val snapshot = (activeBranchMessages ?: (if (realEpoch > 0) realMessages else rawMessages))
            .map { it.copy() }
            .toMutableStateList()
        branches += AiAgentBranchItem(number = nextNumber, messages = snapshot)
        branchCounterByChat[chatId] = nextNumber
        branchVisibilityByChat[chatId] = true
        selectedBranchNumber = nextNumber
        saveBranchSnapshot(chatId = chatId, branchNumber = nextNumber, messages = snapshot)
    }

    fun deleteBranch(chatId: Long, branchNumber: Int) {
        val branches = branchesByChat[chatId] ?: return
        if (branches.size <= 1) return
        val firstBranchNumber = branches.firstOrNull()?.number
        if (branchNumber == firstBranchNumber) return
        val removed = branches.removeAll { it.number == branchNumber }
        if (!removed) return
        queries.deleteBranchMessagesByChatAndBranch(chat_id = chatId, branch_number = branchNumber.toLong())
        if (activeChatId == chatId && selectedBranchNumber == branchNumber) {
            selectedBranchNumber = branches.firstOrNull()?.number
        }
    }

    fun keepOnlyFirstBranchForActiveChat() {
        val chatId = activeChatId ?: return
        val branches = ensureBaseBranchForChat(chatId, resetFromCurrentHistory = true)
        selectedBranchNumber = branches.firstOrNull()?.number
    }

    fun openChat(chatId: Long) {
        chatSessionId++
        isLoading = false
        activeChatId = chatId
        loadMessagesForChat(chatId)
        loadFeatureStateForChat(chatId)
        branchesByChat.remove(chatId)
        val branches = ensureBaseBranchForChat(chatId)
        selectedBranchNumber = branches.firstOrNull()?.number
        inputText = TextFieldValue("")
        apiSelectorExpanded = false
        modelSelectorExpanded = false
    }

    fun createNewChatAndOpen() {
        val title = "Чат ${chats.size + 1}"
        val profileIdForChat = selectedProfileId ?: profiles.firstOrNull()?.id
        queries.insertChat(
            title = title,
            created_at = System.currentTimeMillis(),
            selected_profile_id = profileIdForChat
        )
        val updatedChats = loadChatsFromDb()
        chats.clear()
        chats += updatedChats
        val latestChat = updatedChats.lastOrNull() ?: return
        openChat(latestChat.id)
    }

    fun clearChatSelection() {
        chatSessionId++
        isLoading = false
        activeChatId = null
        rawMessages.clear()
        realMessages.clear()
        realEpoch = 0
        isSystemPromptEnabled = false
        systemPromptText = ""
        isSummarizationEnabled = false
        summarizeAfterTokensInput = "10000"
        isSlidingWindowEnabled = false
        slidingWindowSizeInput = "12"
        isStickyFactsEnabled = false
        stickyFactsWindowSizeInput = "12"
        stickyFactsSystemMessage = ""
        isBranchingEnabled = false
        selectedBranchNumber = null
        selectedProfileId = null
        selectedProfileNameInput = ""
        newProfileNameInput = ""
        isLongTermMemoryEnabled = true
        longTermMemory.clear()
        profileSelectorExpanded = false
        isProfileRenameMode = false
        isProfileCreateMode = false
        inputText = TextFieldValue("")
        apiSelectorExpanded = false
        modelSelectorExpanded = false
        showRawHistory = false
        stickyFacts.clear()
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
        branchingEnabledByChat.remove(chatId)

        val updatedChats = loadChatsFromDb()
        chats.clear()
        chats += updatedChats

        if (updatedChats.isEmpty()) {
            clearChatSelection()
            return
        }

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
        branchingEnabledByChat.clear()
        chats.clear()
        clearChatSelection()
    }

    fun currentHistoryMessages(): List<AiAgentMessage> =
        if (realEpoch > 0) realMessages else rawMessages

    fun activeBranchMessages(): SnapshotStateList<AiAgentMessage>? {
        if (!isBranchingEnabled) return null
        val chatId = activeChatId ?: return null
        val branchNumber = selectedBranchNumber ?: return null
        return branchesByChat[chatId]?.firstOrNull { it.number == branchNumber }?.messages
    }

    fun historyForContext(): List<AiAgentMessage> = activeBranchMessages() ?: currentHistoryMessages()

    fun contextStrategy(): AiAgentContextStrategy = when {
        isBranchingEnabled -> AiAgentContextStrategy.Branching
        isStickyFactsEnabled -> AiAgentContextStrategy.StickyFacts
        isSlidingWindowEnabled -> AiAgentContextStrategy.SlidingWindow
        isSummarizationEnabled -> AiAgentContextStrategy.Summarization
        else -> AiAgentContextStrategy.Summarization
    }

    fun isCompressedViewAvailable(): Boolean =
        !isBranchingEnabled && (isSummarizationEnabled || isSlidingWindowEnabled || isStickyFactsEnabled)

    fun compressedMessagesForDisplay(): List<AiAgentMessage> {
        val history = currentHistoryMessages()
        return when (contextStrategy()) {
            AiAgentContextStrategy.StickyFacts -> {
                val lastN = stickyFactsWindowSizeInput.toIntOrNull() ?: 12
                aiAgentTakeLastMessages(history, lastN)
            }
            AiAgentContextStrategy.SlidingWindow -> {
                val lastN = slidingWindowSizeInput.toIntOrNull() ?: 12
                aiAgentTakeLastMessages(history, lastN)
            }
            else -> history
        }
    }

    fun stickyFactsContextMessages(): List<AiAgentMessage> {
        val lastN = stickyFactsWindowSizeInput.toIntOrNull() ?: 12
        val history = historyForContext().filter { it.isApiHistoryMessage() }
        return aiAgentTakeLastMessages(history, lastN)
    }

    fun buildRequestMessages(): List<DeepSeekMessage> {
        val history = historyForContext().filter { it.isApiHistoryMessage() }
        val baseSystemPrompt = if (isSystemPromptEnabled) systemPromptText else ""

        // Объединяет системный промт + sticky facts (рабочая память) + опциональный доп. контент
        fun buildSystemMessage(extraContent: String = ""): DeepSeekMessage? {
            val longTermMemory =  if (isLongTermMemoryEnabled) {
                aiAgentLongTermMemoryMessage(longTermMemory)?.content.orEmpty()
            } else ""
            val sticky = if (isStickyFactsEnabled) {
                aiAgentFactsSystemMessage(stickyFactsSystemMessage)?.content.orEmpty()
            } else ""
            val parts = listOfNotNull(
                longTermMemory.trim().takeIf { it.isNotEmpty() },
                baseSystemPrompt.trim().takeIf { it.isNotEmpty() },
                sticky.trim().takeIf { it.isNotEmpty() },
                extraContent.trim().takeIf { it.isNotEmpty() }
            )
            if (parts.isEmpty()) return null
            return DeepSeekMessage(role = "system", content = parts.joinToString("\n\n"))
        }

        return when (contextStrategy()) {
            AiAgentContextStrategy.StickyFacts -> {
                buildList {
                    if (isLongTermMemoryEnabled) {
                        aiAgentLongTermMemoryMessage(longTermMemory)?.let { add(it) }
                    }
                    buildSystemMessage()?.let { add(it) }
                    addAll(stickyFactsContextMessages().map { it.toRequestMessage() })
                }
            }
            AiAgentContextStrategy.SlidingWindow -> {
                val lastN = slidingWindowSizeInput.toIntOrNull() ?: 12
                buildList {
                    if (isLongTermMemoryEnabled) {
                        aiAgentLongTermMemoryMessage(longTermMemory)?.let { add(it) }
                    }
                    buildSystemMessage()?.let { add(it) }
                    addAll(aiAgentTakeLastMessages(history, lastN).map { it.toRequestMessage() })
                }
            }
            else -> {
                // Суммаризация — рабочая память: результат идёт в системный промт
                val summaryText = if (realEpoch > 0 && !isBranchingEnabled) {
                    history.firstOrNull { !it.isUser }?.text.orEmpty()
                } else ""
                val summaryExtra = if (summaryText.isNotEmpty()) {
                    "Краткое содержание предыдущего диалога:\n$summaryText"
                } else ""
                buildList {
//                    if (isLongTermMemoryEnabled) {
//                        aiAgentLongTermMemoryMessage(longTermMemory)?.let { add(it) }
//                    }
                    buildSystemMessage(summaryExtra)?.let { add(it) }
                    val historyToSend = if (summaryText.isNotEmpty()) {
                        history.dropWhile { !it.isUser }
                    } else history
                    addAll(historyToSend.map { it.toRequestMessage() })
                }
            }
        }
    }

    fun displayedMessages(): List<AiAgentMessage> {
        val branchMessages = activeBranchMessages()
        if (branchMessages != null) return branchMessages
        if (showRawHistory && isCompressedViewAvailable()) return rawMessages
        return if (isCompressedViewAvailable()) compressedMessagesForDisplay() else currentHistoryMessages()
    }

    fun appendMessageToStream(
        chatId: Long,
        stream: AiAgentStream,
        epoch: Int,
        text: String,
        isUser: Boolean,
        paramsInfoBase: String,
        apiLabel: String,
        model: String,
        createdAt: Long
    ) {
        val paramsInfo = aiAgentApplyStream(paramsInfoBase, stream, epoch)
        val message = AiAgentMessage(
            text = text,
            isUser = isUser,
            paramsInfo = paramsInfo,
            stream = stream,
            epoch = epoch,
            createdAt = createdAt
        )

        when (stream) {
            AiAgentStream.Raw -> rawMessages += message
            AiAgentStream.Real -> realMessages += message
        }

        queries.insertMessage(
            chat_id = chatId,
            api = apiLabel,
            model = model,
            role = if (isUser) "user" else "assistant",
            message = text,
            params_info = paramsInfo,
            created_at = createdAt
        )
    }

    fun sendMessage() {
        val currentChatId = activeChatId ?: return
        val trimmed = inputText.text.trim()
        if (trimmed.isEmpty() || isLoading) return

        val requestApi = selectedApi
        val model = modelInput.trim().ifEmpty { requestApi.defaultModel }
        val paramsInfoPrefix = "api=${requestApi.label} | model=$model"
        val userParamsInfo = "$paramsInfoPrefix | response_time=pending"
        val userCreatedAt = System.currentTimeMillis()

        appendMessageToStream(
            chatId = currentChatId,
            stream = AiAgentStream.Raw,
            epoch = 0,
            text = trimmed,
            isUser = true,
            paramsInfoBase = userParamsInfo,
            apiLabel = requestApi.label,
            model = model,
            createdAt = userCreatedAt
        )
        if (realEpoch > 0) {
            appendMessageToStream(
                chatId = currentChatId,
                stream = AiAgentStream.Real,
                epoch = realEpoch,
                text = trimmed,
                isUser = true,
                paramsInfoBase = userParamsInfo,
                apiLabel = requestApi.label,
                model = model,
                createdAt = userCreatedAt
            )
        }
        val activeBranch = activeBranchMessages()
        val activeBranchNumber = if (isBranchingEnabled) selectedBranchNumber else null
        if (activeBranch != null) {
            val branchMessage = AiAgentMessage(
                text = trimmed,
                isUser = true,
                paramsInfo = aiAgentApplyStream(userParamsInfo, AiAgentStream.Real, realEpoch),
                stream = AiAgentStream.Real,
                epoch = realEpoch,
                createdAt = userCreatedAt
            )
            activeBranch += branchMessage
            if (activeBranchNumber != null) {
                appendMessageToBranch(
                    chatId = currentChatId,
                    branchNumber = activeBranchNumber,
                    message = branchMessage
                )
            }
        }
        inputText = TextFieldValue("")

        scope.launch {
            val requestSessionId = chatSessionId
            val requestChatId = currentChatId
            isLoading = true
            val startedAtNanos = System.nanoTime()
            val completionResult = try {
                val apiKey = aiAgentReadApiKey(requestApi.envVar)
                if (apiKey.isBlank()) {
                    error("Missing API key in secrets.properties or env var: ${requestApi.envVar}")
                }

                val request = DeepSeekChatRequest(
                    model = model,
                    messages = buildRequestMessages()
                )

                val response = when (requestApi) {
                    AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
                    AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
                    AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
                    AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
                }

                val answerText = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    .ifEmpty { "Empty response from ${requestApi.label}." }
                AiAgentCompletionResult(
                    answer = answerText,
                    requestTokens = response.usage?.promptTokens,
                    responseTokens = response.usage?.completionTokens,
                    totalTokens = response.usage?.totalTokens
                )
            } catch (e: Exception) {
                AiAgentCompletionResult(
                    answer = "Request failed: ${e.message ?: "unknown error"}",
                    requestTokens = null,
                    responseTokens = null,
                    totalTokens = null
                )
            }

            val responseTimeSec = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0
            if (requestSessionId != chatSessionId || requestChatId != activeChatId) {
                isLoading = false
                return@launch
            }

            val totalTokens = completionResult.totalTokens
                ?: if (completionResult.requestTokens != null && completionResult.responseTokens != null) {
                    completionResult.requestTokens + completionResult.responseTokens
                } else {
                    null
                }
            val tokenInfoSuffix = buildString {
                completionResult.requestTokens?.let { append(" | request_tokens=$it") }
                completionResult.responseTokens?.let { append(" | response_tokens=$it") }
                totalTokens?.let { append(" | tokens=$it") }
            }
            val assistantParamsInfo = "$paramsInfoPrefix | response_time=${responseTimeSec.aiAgentFormatSeconds()}$tokenInfoSuffix"
            val assistantCreatedAt = System.currentTimeMillis()
            appendMessageToStream(
                chatId = requestChatId,
                stream = AiAgentStream.Raw,
                epoch = 0,
                text = completionResult.answer,
                isUser = false,
                paramsInfoBase = assistantParamsInfo,
                apiLabel = requestApi.label,
                model = model,
                createdAt = assistantCreatedAt
            )
            if (realEpoch > 0) {
                appendMessageToStream(
                    chatId = requestChatId,
                    stream = AiAgentStream.Real,
                    epoch = realEpoch,
                    text = completionResult.answer,
                    isUser = false,
                    paramsInfoBase = assistantParamsInfo,
                    apiLabel = requestApi.label,
                    model = model,
                    createdAt = assistantCreatedAt
                )
            }
            if (activeBranch != null) {
                val branchMessage = AiAgentMessage(
                    text = completionResult.answer,
                    isUser = false,
                    paramsInfo = aiAgentApplyStream(assistantParamsInfo, AiAgentStream.Real, realEpoch),
                    stream = AiAgentStream.Real,
                    epoch = realEpoch,
                    createdAt = assistantCreatedAt
                )
                activeBranch += branchMessage
                if (activeBranchNumber != null) {
                    appendMessageToBranch(
                        chatId = requestChatId,
                        branchNumber = activeBranchNumber,
                        message = branchMessage
                    )
                }
            }

            if (isStickyFactsEnabled) {
                val stickyContextMessages = stickyFactsContextMessages()
                if (stickyContextMessages.isNotEmpty()) {
                    val stickyTranscript = stickyContextMessages.joinToString("\n") { message ->
                        val prefix = if (message.isUser) "Пользователь" else "Ассистент"
                        "$prefix: ${message.text}"
                    }

                    val stickyRequest = DeepSeekChatRequest(
                        model = model,
                        messages = buildList {
                            aiAgentFactsSystemMessage(stickyFactsSystemMessage)?.let { add(it) }
                            add(
                                DeepSeekMessage(
                                    role = "user",
                                    content = buildString {
                                        appendLine(aiAgentStickyFactsExtractorPrompt.trimIndent())
                                        append("Контекст последних сообщений:\n")
                                        append(stickyTranscript)
                                    }
                                )
                            )
                        },
                        temperature = 0.1
                    )

                    val stickyApiKey = aiAgentReadApiKey(requestApi.envVar)
                    if (stickyApiKey.isNotBlank()) {
                        val stickyFactsText = runCatching {
                            val stickyResponse = when (requestApi) {
                                AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = stickyApiKey, request = stickyRequest)
                                AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = stickyApiKey, request = stickyRequest)
                                AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = stickyApiKey, request = stickyRequest)
                                AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = stickyApiKey, request = stickyRequest)
                            }
                            stickyResponse.choices.firstOrNull()?.message?.content.orEmpty()
                        }.getOrNull()

                        if (requestSessionId == chatSessionId && requestChatId == activeChatId) {
                            stickyFactsSystemMessage = aiAgentNormalizeFactsText(stickyFactsText.orEmpty())
                            stickyFacts.clear()
                            stickyFacts.putAll(aiAgentFactsMapFromText(stickyFactsSystemMessage))
                        }
                    }
                }
            }

            if (isSummarizationEnabled) {
                val tokenThreshold = summarizeAfterTokensInput.toIntOrNull() ?: 10000
                val tokenTotal = historyForContext()
                    .asReversed()
                    .firstOrNull { it.tokensSpent() > 0 }
                    ?.tokensSpent()
                    ?: 0
                if (tokenThreshold > 0 && tokenTotal >= tokenThreshold) {
                    val summarySessionId = chatSessionId
                    val summaryChatId = activeChatId
                    val summaryStartedAtNanos = System.nanoTime()
                    val summaryResult = try {
                        val apiKey = aiAgentReadApiKey(requestApi.envVar)
                        if (apiKey.isBlank()) {
                            error("Missing API key in secrets.properties or env var: ${requestApi.envVar}")
                        }

                        val transcript = historyForContext()
                            .filter { it.isApiHistoryMessage() }
                            .joinToString("\n") { message ->
                                val prefix = if (message.isUser) "Пользователь" else "Ассистент"
                                "$prefix: ${message.text}"
                            }

                        val summaryRequest = DeepSeekChatRequest(
                            model = model,
                            messages = buildList {
                                if (isSystemPromptEnabled) {
                                    aiAgentSystemPromptMessage(systemPromptText)?.let { add(it) }
                                }
                                add(
                                    DeepSeekMessage(
                                        role = "system",
                                        content = "Суммируй контекст беседы кратко. Сохрани факты, договоренности и важные детали."
                                    )
                                )
                                add(
                                    DeepSeekMessage(
                                        role = "user",
                                        content = "Контекст:\n$transcript"
                                    )
                                )
                            }
                        )

                        val summaryResponse = when (requestApi) {
                            AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = summaryRequest)
                            AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = summaryRequest)
                            AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = summaryRequest)
                            AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = summaryRequest)
                        }

                        val summaryText = summaryResponse.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                            .ifEmpty { "Empty response from ${requestApi.label}." }
                        AiAgentCompletionResult(
                            answer = summaryText,
                            requestTokens = summaryResponse.usage?.promptTokens,
                            responseTokens = summaryResponse.usage?.completionTokens,
                            totalTokens = summaryResponse.usage?.totalTokens
                        )
                    } catch (e: Exception) {
                        null
                    }

                    if (summaryResult != null && summarySessionId == chatSessionId && summaryChatId == activeChatId) {
                        realEpoch += 1
                        realMessages.clear()

                        val summaryTokens = summaryResult.totalTokens
                            ?: if (summaryResult.requestTokens != null && summaryResult.responseTokens != null) {
                                summaryResult.requestTokens + summaryResult.responseTokens
                            } else {
                                null
                            }
                        val summaryTokenSuffix = buildString {
                            summaryResult.requestTokens?.let { append(" | request_tokens=$it") }
                            summaryResult.responseTokens?.let { append(" | response_tokens=$it") }
                            summaryTokens?.let { append(" | tokens=$it") }
                        }
                        val summaryTimeSec = (System.nanoTime() - summaryStartedAtNanos) / 1_000_000_000.0
                        val summaryParamsInfo = "$paramsInfoPrefix | response_time=${summaryTimeSec.aiAgentFormatSeconds()}$summaryTokenSuffix"
                        val summaryCreatedAt = System.currentTimeMillis()
                        appendMessageToStream(
                            chatId = requestChatId,
                            stream = AiAgentStream.Real,
                            epoch = realEpoch,
                            text = summaryResult.answer,
                            isUser = false,
                            paramsInfoBase = summaryParamsInfo,
                            apiLabel = requestApi.label,
                            model = model,
                            createdAt = summaryCreatedAt
                        )
                    }
                }
            }

            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshProfiles()
        ensureDefaultProfileExists()
        val storedChats = loadChatsFromDb()
        chats.clear()
        chats += storedChats
        if (chats.isEmpty()) {
            createNewChatAndOpen()
        } else {
            openChat(chats.last().id)
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            inputFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isSummarizationEnabled, isSlidingWindowEnabled, isStickyFactsEnabled, isBranchingEnabled) {
        if (!isCompressedViewAvailable()) {
            showRawHistory = false
        }
    }

    LaunchedEffect(isStickyFactsEnabled) {
        if (!isStickyFactsEnabled) {
            stickyFactsSystemMessage = ""
            stickyFacts.clear()
        }
    }

    LaunchedEffect(isBranchingEnabled, activeChatId) {
        val chatId = activeChatId ?: return@LaunchedEffect
        branchingEnabledByChat[chatId] = isBranchingEnabled
        if (isBranchingEnabled) {
            val branches = ensureBaseBranchForChat(chatId)
            if (selectedBranchNumber == null || branches.none { it.number == selectedBranchNumber }) {
                selectedBranchNumber = branches.firstOrNull()?.number
            }
        } else {
            keepOnlyFirstBranchForActiveChat()
        }
    }

    LaunchedEffect(
        activeChatId,
        selectedProfileId,
        isLongTermMemoryEnabled,
        isSystemPromptEnabled,
        systemPromptText,
        isSummarizationEnabled,
        summarizeAfterTokensInput,
        isSlidingWindowEnabled,
        slidingWindowSizeInput,
        isStickyFactsEnabled,
        stickyFactsWindowSizeInput,
        stickyFactsSystemMessage,
        isBranchingEnabled,
        showRawHistory
    ) {
        if (isApplyingProfileState) return@LaunchedEffect
        saveActiveChatFeatureState()
    }

    val displayMessages = displayedMessages()

    LaunchedEffect(displayMessages.size, isLoading) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.lastIndex)
        }
    }

    val activeChatTitle = chats.firstOrNull { it.id == activeChatId }?.title.orEmpty()
    val activeProfile = profiles.firstOrNull { it.id == selectedProfileId }
    val profileNameExists = profiles.any {
        it.id != selectedProfileId && it.name.equals(selectedProfileNameInput.trim(), ignoreCase = true)
    }
    val newProfileNameExists = profiles.any {
        it.name.equals(newProfileNameInput.trim(), ignoreCase = true)
    }
    val activeChatTotalTokens = displayMessages
        .asReversed()
        .firstOrNull { it.tokensSpent() > 0 }
        ?.tokensSpent()
        ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(
                            onClick = { screensMenuExpanded = true },
                            enabled = !isLoading
                        ) {
                            val titleSuffix = if (activeChatTitle.isNotBlank()) {
                                " | $activeChatTitle ($activeChatTotalTokens токенов)"
                            } else {
                                ""
                            }
                            Text("Ai неделя 3$titleSuffix")
                        }
                        DropdownMenu(
                            expanded = screensMenuExpanded,
                            onDismissRequest = { screensMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (currentScreen == RootScreen.AiWeek3) "Ai неделя 3 ?" else "Ai неделя 3"
                                    )
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.AiWeek3)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (currentScreen == RootScreen.AiAgent) "AiAgent ?" else "AiAgent"
                                    )
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.AiAgent)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (currentScreen == RootScreen.App) "App ?" else "App"
                                    )
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.App)
                                }
                            )
                        }
                    }
                },
                actions = {
                    if (isCompressedViewAvailable()) {
                        TextButton(
                            onClick = { showRawHistory = !showRawHistory },
                            enabled = !isLoading
                        ) {
                            Text(if (showRawHistory) "Показать сжатый" else "Показать полный")
                        }
                    }
                    IconButton(
                        onClick = { isFeaturesPanelVisible = !isFeaturesPanelVisible },
                        enabled = !isLoading
                    ) {
                        Text(text = if (isFeaturesPanelVisible) "?" else "\u2610")
                    }
                    TextButton(onClick = ::createNewChatAndOpen, enabled = !isLoading) {
                        Text("Новый чат")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AiWeek3ScreenTheme.topBarContainer,
                    titleContentColor = AiWeek3ScreenTheme.topBarContent,
                    actionIconContentColor = AiWeek3ScreenTheme.topBarContent
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
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Чаты",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Box(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = ::deleteAllChats,
                        enabled = chats.isNotEmpty() && !isLoading
                    ) {
                        Text("Удалить всё")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(chats, key = { it.id }) { chat ->
                        val chatBranches = branchesByChat[chat.id]
                        val hasBranches = (branchingEnabledByChat[chat.id] == true) && !chatBranches.isNullOrEmpty()
                        val areBranchesVisible = branchVisibilityByChat[chat.id] ?: true
                        val isSelected = chat.id == activeChatId
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            if (isSelected) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = chat.title,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { deleteChat(chat.id) },
                                        enabled = !isLoading
                                    ) {
                                        Text(
                                            text = "X",
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable(enabled = !isLoading) { openChat(chat.id) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = chat.title,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    TextButton(
                                        onClick = { deleteChat(chat.id) },
                                        enabled = !isLoading
                                    ) {
                                        Text("X")
                                    }
                                }
                            }

                            if (hasBranches) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .background(
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    Color.Transparent
                                                },
                                                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.outline
                                                },
                                                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                            )
                                            .clickable(enabled = !isLoading) {
                                                branchVisibilityByChat[chat.id] = !areBranchesVisible
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (areBranchesVisible) "?" else "?",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }

                            if (hasBranches && areBranchesVisible) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    chatBranches.forEach { branch ->
                                        val isActiveBranch = isBranchingEnabled &&
                                            chat.id == activeChatId &&
                                            selectedBranchNumber == branch.number
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .then(
                                                    if (isActiveBranch) {
                                                        Modifier.background(
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                    } else {
                                                        Modifier.border(
                                                            width = 1.dp,
                                                            color = MaterialTheme.colorScheme.outline,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                    }
                                                )
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    if (activeChatId != chat.id) {
                                                        openChat(chat.id)
                                                    }
                                                    selectedBranchNumber = branch.number
                                                },
                                                enabled = !isLoading,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "ветка (${branch.number})",
                                                    color = if (isActiveBranch) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                            TextButton(
                                                onClick = { deleteBranch(chat.id, branch.number) },
                                                enabled = !isLoading && branch.number != 1
                                            ) {
                                                Text(
                                                    text = "X",
                                                    color = if (isActiveBranch) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(AiWeek3ScreenTheme.divider)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = profileSelectorExpanded,
                    onExpandedChange = { expanded -> if (!isLoading) profileSelectorExpanded = expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = activeProfile?.name.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = !isLoading && activeChatId != null && profiles.isNotEmpty()
                            )
                            .fillMaxWidth()
                            .height(52.dp),
                        readOnly = true,
                        enabled = !isLoading && activeChatId != null && profiles.isNotEmpty(),
                        placeholder = { Text("Профиль", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileSelectorExpanded) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    ExposedDropdownMenu(
                        expanded = profileSelectorExpanded,
                        onDismissRequest = { profileSelectorExpanded = false }
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = { Text(profile.name) },
                                onClick = {
                                    selectProfileForActiveChat(profile.id)
                                    profileSelectorExpanded = false
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        isProfileRenameMode = true
                        isProfileCreateMode = false
                    },
                    enabled = !isLoading && selectedProfileId != null
                ) {
                    Text("Редактировать")
                }
                Button(
                    onClick = {
                        isProfileCreateMode = true
                        isProfileRenameMode = false
                    },
                    enabled = !isLoading && activeChatId != null
                ) {
                    Text("Создать")
                }
            }

            if (isProfileRenameMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedProfileNameInput,
                        onValueChange = { selectedProfileNameInput = it },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = !isLoading && selectedProfileId != null,
                        singleLine = true,
                        label = { Text("Имя профиля") },
                        isError = selectedProfileNameInput.trim().isNotEmpty() && profileNameExists,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        onClick = ::renameSelectedProfile,
                        enabled = !isLoading &&
                            selectedProfileId != null &&
                            selectedProfileNameInput.trim().isNotEmpty() &&
                            !profileNameExists
                    ) {
                        Text("Сохранить")
                    }
                    TextButton(
                        onClick = { isProfileRenameMode = false },
                        enabled = !isLoading
                    ) {
                        Text("Отмена")
                    }
                }
            }

            if (isProfileCreateMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newProfileNameInput,
                        onValueChange = { newProfileNameInput = it },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled = !isLoading && activeChatId != null,
                        singleLine = true,
                        label = { Text("Новый профиль") },
                        placeholder = { Text("Пусто = автоимя", style = MaterialTheme.typography.labelSmall) },
                        isError = newProfileNameInput.trim().isNotEmpty() && newProfileNameExists,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        onClick = ::createProfile,
                        enabled = !isLoading && activeChatId != null && !newProfileNameExists
                    ) {
                        Text("Создать профиль")
                    }
                    TextButton(
                        onClick = {
                            isProfileCreateMode = false
                            newProfileNameInput = ""
                        },
                        enabled = !isLoading
                    ) {
                        Text("Отмена")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = apiSelectorExpanded,
                    onExpandedChange = { expanded -> if (!isLoading) apiSelectorExpanded = expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = selectedApi.label,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = !isLoading
                            )
                            .fillMaxWidth()
                            .height(52.dp),
                        readOnly = true,
                        placeholder = { Text("Current API", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiSelectorExpanded) },
                        enabled = !isLoading,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    ExposedDropdownMenu(
                        expanded = apiSelectorExpanded,
                        onDismissRequest = { apiSelectorExpanded = false }
                    ) {
                        AiAgentApi.entries.forEach { api ->
                            DropdownMenuItem(
                                text = { Text(api.label) },
                                onClick = {
                                    selectedApi = api
                                    modelInput = api.defaultModel
                                    apiSelectorExpanded = false
                                    modelSelectorExpanded = false
                                }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = modelSelectorExpanded,
                    onExpandedChange = { expanded -> if (!isLoading) modelSelectorExpanded = expanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = !isLoading
                            )
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoading,
                        readOnly = true,
                        placeholder = { Text("model", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelSelectorExpanded) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                    ExposedDropdownMenu(
                        expanded = modelSelectorExpanded,
                        onDismissRequest = { modelSelectorExpanded = false }
                    ) {
                        selectedApi.supportedModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    modelInput = model
                                    modelSelectorExpanded = false
                                }
                            )
                        }
                    }
                }
                Button(
                    onClick = ::createBranchForActiveChat,
                    enabled = !isLoading && activeChatId != null && isBranchingEnabled
                ) {
                    Text("+ ветка")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Краткосрочная: ${currentHistoryMessages().size} сообщ.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text("|", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        val workingMemoryLabel = when {
                            isStickyFactsEnabled && stickyFacts.isNotEmpty() -> "${stickyFacts.size} фактов"
                            isSummarizationEnabled && realEpoch > 0 -> "сводка"
                            else -> "—"
                        }
                        val isWorkingMemoryActive = (isStickyFactsEnabled && stickyFacts.isNotEmpty()) ||
                            (isSummarizationEnabled && realEpoch > 0)
                        Text(
                            text = "Рабочая: $workingMemoryLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWorkingMemoryActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text("|", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        Text(
                            text = "Долговременная: ${longTermMemory.size} зап.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (longTermMemory.isNotEmpty() && isLongTermMemoryEnabled)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
                val stickyFactsBannerText = aiAgentMergeSystemPrompts(
                    baseSystemPrompt = if (isSystemPromptEnabled) systemPromptText else "",
                    stickyFactsSystemMessage = stickyFactsSystemMessage
                )?.content.orEmpty()
                if (isStickyFactsEnabled && stickyFactsBannerText.isNotBlank()) {
                    item {
                        AiAgentStickyFactsBanner(stickyFactsBannerText)
                    }
                }
                items(displayMessages) { message ->
                    AiAgentBubble(
                        message = message,
                        onSaveToMemory = if (!message.isUser) { { text -> saveAssistantMessageToLongTermMemory(text) } } else null
                    )
                }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }

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

                            if (keyEvent.isAltPressed) {
                                val start = inputText.selection.min
                                val end = inputText.selection.max
                                inputText = inputText.copy(
                                    text = inputText.text.replaceRange(start, end, "\n"),
                                    selection = TextRange(start + 1)
                                )
                                return@onPreviewKeyEvent true
                            }

                            sendMessage()
                            true
                        },
                    enabled = !isLoading,
                    label = { Text("Message") },
                    maxLines = 4
                )
                Button(
                    onClick = ::sendMessage,
                    enabled = inputText.text.isNotBlank() && !isLoading
                ) {
                    Text("Отправить")
                }
            }
        }

        if (isFeaturesPanelVisible) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
                    .background(AiWeek3ScreenTheme.divider)
            )

            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Доп. функции",
                    style = MaterialTheme.typography.titleSmall
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading) {
                            isMemoryPanelExpanded = !isMemoryPanelExpanded
                        },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Долговременная память",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(if (isMemoryPanelExpanded) "▾" else "▸", style = MaterialTheme.typography.labelSmall)
                    }
                    if (isMemoryPanelExpanded) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = isLongTermMemoryEnabled, onCheckedChange = { isLongTermMemoryEnabled = it }, enabled = !isLoading)
                            Text(if (isLongTermMemoryEnabled) "Вкл (инжект в запрос)" else "Выкл", style = MaterialTheme.typography.labelSmall)
                        }
                        longTermMemory.forEach { entry ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.key, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(entry.value, style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = { memoryEditingId = entry.id; memoryKeyInput = entry.key; memoryValueInput = entry.value; isMemoryFormVisible = true }, enabled = !isLoading) { Text("✎") }
                                TextButton(onClick = { scope.launch { deleteLongTermEntry(entry.id) } }, enabled = !isLoading) { Text("X") }
                            }
                        }
                        if (longTermMemory.isEmpty()) {
                            Text("Записей нет", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { memoryEditingId = null; memoryKeyInput = ""; memoryValueInput = ""; isMemoryFormVisible = !isMemoryFormVisible }, enabled = !isLoading) {
                                Text(if (isMemoryFormVisible && memoryEditingId == null) "Отмена" else "+ Добавить")
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    val profileId = selectedProfileId
                                    if (profileId != null) {
                                        queries.deleteAllMemoryEntriesByProfile(profile_id = profileId)
                                    }
                                    longTermMemory.clear()
                                }
                            }, enabled = longTermMemory.isNotEmpty() && !isLoading) {
                                Text("Очистить")
                            }
                        }
                        if (isMemoryFormVisible) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedTextField(value = memoryKeyInput, onValueChange = { memoryKeyInput = it },
                                    modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !isLoading,
                                    placeholder = { Text("Ключ (имя, стек, цель...)", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true, textStyle = MaterialTheme.typography.labelSmall)
                                OutlinedTextField(value = memoryValueInput, onValueChange = { memoryValueInput = it },
                                    modifier = Modifier.fillMaxWidth().height(80.dp), enabled = !isLoading,
                                    placeholder = { Text("Значение", style = MaterialTheme.typography.labelSmall) },
                                    textStyle = MaterialTheme.typography.labelSmall, maxLines = 3)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            if (memoryKeyInput.isNotBlank() && memoryValueInput.isNotBlank()) {
                                                scope.launch {
                                                    val editId = memoryEditingId
                                                    if (editId != null) updateLongTermEntry(editId, memoryValueInput)
                                                    else insertLongTermEntry(memoryKeyInput, memoryValueInput)
                                                    isMemoryFormVisible = false; memoryEditingId = null
                                                    memoryKeyInput = ""; memoryValueInput = ""
                                                }
                                            }
                                        },
                                        enabled = memoryKeyInput.isNotBlank() && memoryValueInput.isNotBlank() && !isLoading
                                    ) { Text(if (memoryEditingId != null) "Сохранить" else "Добавить") }
                                    TextButton(onClick = { isMemoryFormVisible = false; memoryEditingId = null; memoryKeyInput = ""; memoryValueInput = "" }, enabled = !isLoading) { Text("Отмена") }
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Системный промт (краткосрочная)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isSystemPromptEnabled,
                            onCheckedChange = { checked -> isSystemPromptEnabled = checked },
                            enabled = !isLoading
                        )
                        Text(
                            text = if (isSystemPromptEnabled) "Вкл" else "Выкл",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    OutlinedTextField(
                        value = systemPromptText,
                        onValueChange = { value -> systemPromptText = value },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        enabled = !isLoading,
                        placeholder = { Text("Введите системный промт", style = MaterialTheme.typography.labelSmall) },
                        textStyle = MaterialTheme.typography.labelSmall
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Рабочая память — Суммаризация",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isSummarizationEnabled,
                            onCheckedChange = { checked -> isSummarizationEnabled = checked },
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = summarizeAfterTokensInput,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    summarizeAfterTokensInput = value
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !isLoading,
                            placeholder = { Text("tokens", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Sliding Window (последние N)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isSlidingWindowEnabled,
                            onCheckedChange = { checked -> isSlidingWindowEnabled = checked },
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = slidingWindowSizeInput,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    slidingWindowSizeInput = value
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !isLoading,
                            placeholder = { Text("N", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Рабочая память — Sticky Facts",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isStickyFactsEnabled,
                            onCheckedChange = { checked -> isStickyFactsEnabled = checked },
                            enabled = !isLoading
                        )
                        OutlinedTextField(
                            value = stickyFactsWindowSizeInput,
                            onValueChange = { value ->
                                if (value.isEmpty() || value.all { it.isDigit() }) {
                                    stickyFactsWindowSizeInput = value
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !isLoading,
                            placeholder = { Text("N", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.labelSmall
                        )
                    }
                    TextButton(
                        onClick = {
                            stickyFactsSystemMessage = ""
                            stickyFacts.clear()
                        },
                        enabled = stickyFactsSystemMessage.isNotBlank() && !isLoading
                    ) {
                        Text("Очистить facts")
                    }
                    if (stickyFacts.isNotEmpty()) {
                        Text(
                            text = stickyFacts.entries.joinToString("\n") { (k, v) -> "$k: $v" },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Branching (ветки диалога)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isBranchingEnabled,
                            onCheckedChange = { checked -> isBranchingEnabled = checked },
                            enabled = !isLoading
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun AiAgentStickyFactsBanner(systemFacts: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = AiWeek3ScreenTheme.stickyFactsBg,
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = AiWeek3ScreenTheme.stickyFactsBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Рабочая память • Sticky Facts",
                style = MaterialTheme.typography.labelSmall,
                color = AiWeek3ScreenTheme.stickyFactsTitle
            )
            SelectionContainer {
                Text(
                    text = systemFacts,
                    color = AiWeek3ScreenTheme.stickyFactsText
                )
            }
        }
    }
}

@Composable
private fun AiAgentBubble(
    message: AiAgentMessage,
    onSaveToMemory: ((String) -> Unit)? = null
) {
    val assistantBubbleColor = AiWeek3ScreenTheme.assistantBubble
    val assistantTextColor = AiWeek3ScreenTheme.onAssistantBubble

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        assistantBubbleColor
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        color = if (message.isUser) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            assistantTextColor
                        }
                    )
                }
                Text(
                    text = message.displayParamsInfo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        assistantTextColor.copy(alpha = 0.7f)
                    }
                )
                if (!message.isUser && onSaveToMemory != null) {
                    TextButton(
                        onClick = { onSaveToMemory(message.text) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Сохранить в память", style = MaterialTheme.typography.labelSmall,
                            color = assistantTextColor.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}


