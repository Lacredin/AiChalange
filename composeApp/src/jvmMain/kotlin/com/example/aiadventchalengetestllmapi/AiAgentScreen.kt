package com.example.aiadventchalengetestllmapi

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import com.example.aiadventchalengetestllmapi.db.DatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.db.createAppDatabase
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

private enum class AiAgentStream { Real, Raw }

private data class AiAgentChatItem(
    val id: Long,
    val title: String
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

private fun aiAgentTakeLastMessages(messages: List<AiAgentMessage>, lastN: Int): List<AiAgentMessage> {
    if (lastN <= 0) return emptyList()
    if (messages.size <= lastN) return messages
    return messages.takeLast(lastN)
}

private fun aiAgentUpdateFacts(facts: MutableMap<String, String>, userMessage: String) {
    val text = userMessage.trim()
    if (text.isEmpty()) return

    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains(":") }
        .forEach { line ->
            val key = line.substringBefore(":").trim().lowercase(Locale.getDefault())
            val value = line.substringAfter(":").trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                facts[key] = value
            }
        }

    fun maybeSet(key: String, markers: List<String>) {
        if (markers.any { text.contains(it, ignoreCase = true) }) {
            facts[key] = text
        }
    }

    maybeSet("цель", listOf("цель", "goal"))
    maybeSet("ограничения", listOf("огранич", "нельзя", "limit"))
    maybeSet("предпочтения", listOf("предпочита", "prefer"))
    maybeSet("договоренности", listOf("договор", "решили", "agree"))
}

private fun aiAgentFactsSystemMessage(facts: Map<String, String>): DeepSeekMessage? {
    if (facts.isEmpty()) return null
    val factsText = buildString {
        append("Важные факты диалога (ключ-значение):\n")
        facts.forEach { (key, value) -> append("- $key: $value\n") }
        append("Следуй этим фактам при ответе, если они относятся к запросу.")
    }
    return DeepSeekMessage(role = "system", content = factsText)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(onOpenApp: () -> Unit) {
    var newChatTrigger by remember { mutableIntStateOf(0) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ai Агент") },
                    actions = {
                        TextButton(onClick = { newChatTrigger++ }) {
                            Text("Новый чат")
                        }
                        IconButton(onClick = onOpenApp) {
                            Text(text = "\u2699")
                        }
                    }
                )
            }
        ) { innerPadding ->
            AiAgentChat(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                newChatTrigger = newChatTrigger
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAgentChat(
    modifier: Modifier = Modifier,
    newChatTrigger: Int
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val openAiApi = remember { OpenAiApi() }
    val gigaChatApi = remember { GigaChatApi() }
    val proxyOpenAiApi = remember { ProxyOpenAiApi() }
    val database = remember { createAppDatabase(DatabaseDriverFactory()) }
    val queries = remember(database) { database.chatHistoryQueries }

    val chats = remember { mutableStateListOf<AiAgentChatItem>() }
    val rawMessages = remember { mutableStateListOf<AiAgentMessage>() }
    val realMessages = remember { mutableStateListOf<AiAgentMessage>() }
    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedApi by remember { mutableStateOf(AiAgentApi.DeepSeek) }
    var apiSelectorExpanded by remember { mutableStateOf(false) }
    var modelSelectorExpanded by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(AiAgentApi.DeepSeek.defaultModel) }
    var activeChatId by remember { mutableStateOf<Long?>(null) }
    var chatSessionId by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var isFeaturesPanelVisible by remember { mutableStateOf(false) }
    var isSummarizationEnabled by remember { mutableStateOf(false) }
    var summarizeAfterTokensInput by remember { mutableStateOf("10000") }
    var isSlidingWindowEnabled by remember { mutableStateOf(false) }
    var slidingWindowSizeInput by remember { mutableStateOf("12") }
    var isStickyFactsEnabled by remember { mutableStateOf(false) }
    var stickyFactsWindowSizeInput by remember { mutableStateOf("12") }
    val stickyFacts = remember { mutableStateMapOf<String, String>() }
    var isBranchingEnabled by remember { mutableStateOf(false) }
    val branches = remember { mutableStateMapOf<String, SnapshotStateList<AiAgentMessage>>() }
    var selectedBranchName by remember { mutableStateOf<String?>(null) }
    var checkpointSnapshot by remember { mutableStateOf<List<AiAgentMessage>>(emptyList()) }
    var checkpointIndex by remember { mutableIntStateOf(0) }
    var showRawHistory by remember { mutableStateOf(false) }
    var realEpoch by remember { mutableIntStateOf(0) }

    fun loadChatsFromDb(): List<AiAgentChatItem> {
        return queries.selectChats().executeAsList().map {
            AiAgentChatItem(id = it.id, title = it.title)
        }
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

    fun openChat(chatId: Long) {
        chatSessionId++
        isLoading = false
        activeChatId = chatId
        loadMessagesForChat(chatId)
        inputText = TextFieldValue("")
        apiSelectorExpanded = false
        modelSelectorExpanded = false
        showRawHistory = false
        stickyFacts.clear()
        branches.clear()
        selectedBranchName = null
        checkpointSnapshot = emptyList()
        checkpointIndex = 0
    }

    fun createNewChatAndOpen() {
        val title = "Чат ${chats.size + 1}"
        queries.insertChat(
            title = title,
            created_at = System.currentTimeMillis()
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
        inputText = TextFieldValue("")
        apiSelectorExpanded = false
        modelSelectorExpanded = false
        showRawHistory = false
        stickyFacts.clear()
        branches.clear()
        selectedBranchName = null
        checkpointSnapshot = emptyList()
        checkpointIndex = 0
    }

    fun deleteChat(chatId: Long) {
        if (isLoading) return

        val wasActive = activeChatId == chatId
        queries.deleteMessagesByChat(chatId)
        queries.deleteChatById(chatId)

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
        queries.deleteAllChats()
        chats.clear()
        clearChatSelection()
    }

    fun currentHistoryMessages(): List<AiAgentMessage> =
        if (realEpoch > 0) realMessages else rawMessages

    fun activeBranchMessages(): SnapshotStateList<AiAgentMessage>? {
        if (!isBranchingEnabled) return null
        val branchName = selectedBranchName ?: return null
        return branches[branchName]
    }

    fun historyForContext(): List<AiAgentMessage> = activeBranchMessages() ?: currentHistoryMessages()

    fun contextStrategy(): AiAgentContextStrategy = when {
        isBranchingEnabled -> AiAgentContextStrategy.Branching
        isStickyFactsEnabled -> AiAgentContextStrategy.StickyFacts
        isSlidingWindowEnabled -> AiAgentContextStrategy.SlidingWindow
        isSummarizationEnabled -> AiAgentContextStrategy.Summarization
        else -> AiAgentContextStrategy.Summarization
    }

    fun buildRequestMessages(): List<DeepSeekMessage> {
        val history = historyForContext().filter { it.isApiHistoryMessage() }
        return when (contextStrategy()) {
            AiAgentContextStrategy.StickyFacts -> {
                val lastN = stickyFactsWindowSizeInput.toIntOrNull() ?: 12
                val selectedHistory = aiAgentTakeLastMessages(history, lastN)
                buildList {
                    aiAgentFactsSystemMessage(stickyFacts)?.let { add(it) }
                    addAll(selectedHistory.map { it.toRequestMessage() })
                }
            }
            AiAgentContextStrategy.SlidingWindow -> {
                val lastN = slidingWindowSizeInput.toIntOrNull() ?: 12
                aiAgentTakeLastMessages(history, lastN).map { it.toRequestMessage() }
            }
            else -> history.map { it.toRequestMessage() }
        }
    }

    fun displayedMessages(): List<AiAgentMessage> =
        activeBranchMessages() ?: if (isSummarizationEnabled && showRawHistory) rawMessages else currentHistoryMessages()

    fun saveCheckpoint() {
        checkpointSnapshot = historyForContext().map { it.copy() }
    }

    fun createTwoBranchesFromCheckpoint() {
        if (checkpointSnapshot.isEmpty()) return
        checkpointIndex += 1
        val branchAName = "Ветка A$checkpointIndex"
        val branchBName = "Ветка B$checkpointIndex"
        branches[branchAName] = checkpointSnapshot.map { it.copy() }.toMutableStateList()
        branches[branchBName] = checkpointSnapshot.map { it.copy() }.toMutableStateList()
        selectedBranchName = branchAName
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

        if (isStickyFactsEnabled) {
            aiAgentUpdateFacts(stickyFacts, trimmed)
        }

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
        if (activeBranch != null) {
            activeBranch += AiAgentMessage(
                text = trimmed,
                isUser = true,
                paramsInfo = aiAgentApplyStream(userParamsInfo, AiAgentStream.Real, realEpoch),
                stream = AiAgentStream.Real,
                epoch = realEpoch,
                createdAt = userCreatedAt
            )
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
                activeBranch += AiAgentMessage(
                    text = completionResult.answer,
                    isUser = false,
                    paramsInfo = aiAgentApplyStream(assistantParamsInfo, AiAgentStream.Real, realEpoch),
                    stream = AiAgentStream.Real,
                    epoch = realEpoch,
                    createdAt = assistantCreatedAt
                )
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
                            messages = listOf(
                                DeepSeekMessage(
                                    role = "system",
                                    content = "Суммируй контекст беседы кратко. Сохрани факты, договоренности и важные детали."
                                ),
                                DeepSeekMessage(
                                    role = "user",
                                    content = "Контекст:\n$transcript"
                                )
                            )
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
        val storedChats = loadChatsFromDb()
        chats.clear()
        chats += storedChats
        if (chats.isEmpty()) {
            createNewChatAndOpen()
        } else {
            openChat(chats.last().id)
        }
    }

    LaunchedEffect(newChatTrigger) {
        if (newChatTrigger > 0) {
            createNewChatAndOpen()
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            inputFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(isSummarizationEnabled) {
        if (!isSummarizationEnabled) {
            showRawHistory = false
        }
    }

    val displayMessages = displayedMessages()

    LaunchedEffect(displayMessages.size, isLoading) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.lastIndex)
        }
    }

    val activeChatTitle = chats.firstOrNull { it.id == activeChatId }?.title.orEmpty()
    val activeChatTotalTokens = displayMessages
        .asReversed()
        .firstOrNull { it.tokensSpent() > 0 }
        ?.tokensSpent()
        ?: 0

    Row(
        modifier = modifier
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
                    val isSelected = chat.id == activeChatId
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
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxSize()
                .background(Color(0xFFD2D6DC))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeChatTitle.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$activeChatTitle ($activeChatTotalTokens токенов)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSummarizationEnabled) {
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
                        Text(text = if (isFeaturesPanelVisible) "✓" else "\u2610")
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
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayMessages) { message ->
                    AiAgentBubble(message = message)
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
                    .background(Color(0xFFD2D6DC))
            )

            Column(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Доп. функции",
                    style = MaterialTheme.typography.titleSmall
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Суммаризация контекста",
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
                        text = "Sticky Facts (facts + последние N)",
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
                        onClick = { stickyFacts.clear() },
                        enabled = stickyFacts.isNotEmpty() && !isLoading
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
                            onCheckedChange = { checked ->
                                isBranchingEnabled = checked
                                if (!checked) {
                                    selectedBranchName = null
                                }
                            },
                            enabled = !isLoading
                        )
                        TextButton(
                            onClick = ::saveCheckpoint,
                            enabled = !isLoading
                        ) {
                            Text("Checkpoint")
                        }
                        TextButton(
                            onClick = ::createTwoBranchesFromCheckpoint,
                            enabled = checkpointSnapshot.isNotEmpty() && !isLoading
                        ) {
                            Text("2 ветки")
                        }
                    }
                    branches.keys.sorted().forEach { branchName ->
                        TextButton(
                            onClick = { selectedBranchName = branchName },
                            enabled = !isLoading
                        ) {
                            val suffix = if (selectedBranchName == branchName) " (активна)" else ""
                            Text(branchName + suffix)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAgentBubble(message: AiAgentMessage) {
    val assistantBubbleColor = Color(0xFFE3F0FF)
    val assistantTextColor = Color(0xFF123A6B)

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
            }
        }
    }
}
