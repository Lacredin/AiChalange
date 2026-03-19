package com.example.aiadventchalengetestllmapi.aiagentrag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.BuildSecrets
import com.example.aiadventchalengetestllmapi.RootScreen
import com.example.aiadventchalengetestllmapi.aiagentragdb.AiAgentRagDatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.aiagentragdb.createAiAgentRagDatabase
import com.example.aiadventchalengetestllmapi.embedding.EmbeddingGeneratorStub
import com.example.aiadventchalengetestllmapi.embedinggenerationdb.EmbedingGenerationDatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.embedinggenerationdb.createEmbedingGenerationDatabase
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.prefs.Preferences

private enum class RagApi(val label: String, val envVar: String, val defaultModel: String) {
    DeepSeek("DeepSeek", "DEEPSEEK_API_KEY", "deepseek-chat"),
    OpenAI("OpenAI", "OPENAI_API_KEY", "gpt-4o-mini"),
    GigaChat("GigaChat", "GIGACHAT_ACCESS_TOKEN", "GigaChat-2"),
    ProxyOpenAI("ProxyAPI (OpenAI)", "PROXYAPI_API_KEY", "openai/gpt-4o-mini")
}

private data class RagChatItem(val id: Long, val title: String)

private data class RetrievedChunk(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: Long,
    val strategy: String,
    val chunkText: String,
    val score: Double
)

private data class RagMessage(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val paramsInfo: String,
    val sources: List<RetrievedChunk> = emptyList(),
    val retrievalInfo: String? = null
)

private data class RetrievalConfig(
    val api: RagApi,
    val useRag: Boolean,
    val useFilter: Boolean,
    val useRewrite: Boolean,
    val topKBefore: Int,
    val topKAfter: Int,
    val threshold: Double
)

private data class RetrievalOutput(
    val requestMessages: List<DeepSeekMessage>,
    val selectedChunks: List<RetrievedChunk>,
    val retrievalInfo: String?
)

private data class AssistantResult(
    val answer: String,
    val paramsInfo: String,
    val sources: List<RetrievedChunk>,
    val retrievalInfo: String?
)

private const val PREF_NODE = "com.example.aiadventchalengetestllmapi.aiagentrag"
private const val USE_RAG_KEY = "use_rag_enabled"
private const val DEFAULT_TOP_K_BEFORE = 12
private const val DEFAULT_TOP_K_AFTER = 5
private const val DEFAULT_MIN_SCORE = 0.30
private val ragJson = Json { ignoreUnknownKeys = true }

private fun ragReadApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

private fun loadUseRagState(): Boolean = Preferences.userRoot().node(PREF_NODE).getBoolean(USE_RAG_KEY, true)
private fun saveUseRagState(enabled: Boolean) = Preferences.userRoot().node(PREF_NODE).putBoolean(USE_RAG_KEY, enabled)

private fun parseEmbeddingVector(raw: String): List<Double> {
    val parsed = runCatching { ragJson.parseToJsonElement(raw.trim()) }.getOrNull()
    val array = when (parsed) {
        is JsonObject -> parsed["embedding"] as? JsonArray
        is JsonArray -> parsed
        else -> null
    } ?: return emptyList()
    return array.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
}

private fun cosineSimilarity(left: List<Double>, right: List<Double>): Double {
    if (left.isEmpty() || right.isEmpty() || left.size != right.size) return 0.0
    var dot = 0.0
    var l2 = 0.0
    var r2 = 0.0
    for (i in left.indices) {
        dot += left[i] * right[i]
        l2 += left[i] * left[i]
        r2 += right[i] * right[i]
    }
    if (l2 == 0.0 || r2 == 0.0) return 0.0
    return dot / (kotlin.math.sqrt(l2) * kotlin.math.sqrt(r2))
}

private fun heuristicRewriteQuery(original: String): String {
    val normalized = original.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isEmpty()) return original
    val keywords = normalized.split(" ")
        .filter { it.length >= 4 }
        .distinct()
        .take(16)
    return if (keywords.isEmpty()) original else keywords.joinToString(" ")
}

private fun readPositiveInt(raw: String, fallback: Int): Int = raw.toIntOrNull()?.coerceAtLeast(1) ?: fallback
private fun readThreshold(raw: String, fallback: Double): Double = raw.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: fallback

private fun userParams(config: RetrievalConfig): String =
    "api=${config.api.label} | model=${config.api.defaultModel} | rag=${if (config.useRag) "on" else "off"} | filter=${if (config.useFilter) "on" else "off"} | rewrite=${if (config.useRewrite) "on" else "off"}"

private fun assistantParams(config: RetrievalConfig): String =
    "api=${config.api.label} | model=${config.api.defaultModel} | rag=${if (config.useRag) "on" else "off"} | filter=${if (config.useFilter) "on" else "off"} | rewrite=${if (config.useRewrite) "on" else "off"} | kBefore=${config.topKBefore} | kAfter=${config.topKAfter} | threshold=${"%.2f".format(config.threshold)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentRAGScreen(currentScreen: RootScreen, onSelectScreen: (RootScreen) -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1D4ED8),
            background = Color(0xFFF8FAFC),
            surface = Color.White
        )
    ) {
        val scope = rememberCoroutineScope()
        val deepSeekApi = remember { DeepSeekApi() }
        val openAiApi = remember { OpenAiApi() }
        val gigaChatApi = remember { GigaChatApi() }
        val proxyOpenAiApi = remember { ProxyOpenAiApi() }
        val ragDb = remember { createAiAgentRagDatabase(AiAgentRagDatabaseDriverFactory()) }
        val ragQueries = remember(ragDb) { ragDb.chatHistoryQueries }
        val embDb = remember { createEmbedingGenerationDatabase(EmbedingGenerationDatabaseDriverFactory()) }
        val embQueries = remember(embDb) { embDb.embeddingChunksQueries }

        val chats = remember { mutableStateListOf<RagChatItem>() }
        val messages = remember { mutableStateListOf<RagMessage>() }
        val comparisonMessages = remember { mutableStateListOf<RagMessage>() }
        val listState = rememberLazyListState()
        val comparisonListState = rememberLazyListState()
        var activeChatId by remember { mutableStateOf<Long?>(null) }
        var inputText by remember { mutableStateOf("") }
        var selectedApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var comparisonApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var isLoading by remember { mutableStateOf(false) }
        var useRag by remember { mutableStateOf(loadUseRagState()) }
        var comparisonUseRag by remember { mutableStateOf(true) }
        var useFilter by remember { mutableStateOf(true) }
        var comparisonUseFilter by remember { mutableStateOf(false) }
        var useRewrite by remember { mutableStateOf(false) }
        var comparisonUseRewrite by remember { mutableStateOf(false) }
        var topKBeforeRaw by remember { mutableStateOf(DEFAULT_TOP_K_BEFORE.toString()) }
        var topKAfterRaw by remember { mutableStateOf(DEFAULT_TOP_K_AFTER.toString()) }
        var thresholdRaw by remember { mutableStateOf("%.2f".format(DEFAULT_MIN_SCORE)) }
        var comparisonTopKBeforeRaw by remember { mutableStateOf(DEFAULT_TOP_K_BEFORE.toString()) }
        var comparisonTopKAfterRaw by remember { mutableStateOf(DEFAULT_TOP_K_AFTER.toString()) }
        var comparisonThresholdRaw by remember { mutableStateOf("%.2f".format(DEFAULT_MIN_SCORE)) }
        var dualChatEnabled by remember { mutableStateOf(false) }
        var screensMenuExpanded by remember { mutableStateOf(false) }
        var nextMessageId by remember { mutableStateOf(1L) }
        var selectedSourcesMessageId by remember { mutableStateOf<Long?>(null) }
        var comparisonSelectedSourcesMessageId by remember { mutableStateOf<Long?>(null) }

        fun reloadChats() {
            chats.clear()
            chats.addAll(ragQueries.selectChats().executeAsList().map { RagChatItem(it.id, it.title) })
        }

        fun loadMessages(chatId: Long) {
            messages.clear()
            messages.addAll(
                ragQueries.selectMessagesByChat(chat_id = chatId).executeAsList().mapIndexed { i, row ->
                    RagMessage(
                        id = (i + 1).toLong(),
                        text = row.message,
                        isUser = row.role == "user",
                        paramsInfo = row.params_info
                    )
                }
            )
            comparisonMessages.clear()
            nextMessageId = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
            selectedSourcesMessageId = null
            comparisonSelectedSourcesMessageId = null
        }

        fun createChat(selectNew: Boolean) {
            val nextIndex = (chats.maxOfOrNull { it.id } ?: 0L) + 1L
            ragQueries.insertChat("Chat $nextIndex", System.currentTimeMillis(), null)
            val newId = ragQueries.selectLastInsertedChatId().executeAsOne()
            reloadChats()
            if (selectNew) {
                activeChatId = newId
                loadMessages(newId)
            }
        }

        fun deleteChat(chatId: Long) {
            ragQueries.deleteChatById(chatId)
            reloadChats()
            if (activeChatId == chatId) {
                if (chats.isEmpty()) {
                    activeChatId = null
                    messages.clear()
                    comparisonMessages.clear()
                    selectedSourcesMessageId = null
                    comparisonSelectedSourcesMessageId = null
                } else {
                    val fallbackChatId = chats.first().id
                    activeChatId = fallbackChatId
                    loadMessages(fallbackChatId)
                }
            }
        }

        fun deleteAllChats() {
            ragQueries.deleteAllBranchMessages()
            ragQueries.deleteAllMessages()
            ragQueries.deleteAllChats()
            chats.clear()
            messages.clear()
            comparisonMessages.clear()
            activeChatId = null
            selectedSourcesMessageId = null
            comparisonSelectedSourcesMessageId = null
        }

        suspend fun buildRetrievalOutput(question: String, config: RetrievalConfig): RetrievalOutput {
            if (!config.useRag) return RetrievalOutput(emptyList(), emptyList(), null)
            val retrievalQuestion = if (config.useRewrite) heuristicRewriteQuery(question) else question
            val queryEmb = EmbeddingGeneratorStub.createEmbedding(retrievalQuestion).getOrElse { throw it }
            val allRanked = embQueries.selectAll().executeAsList().mapNotNull { row ->
                val emb = parseEmbeddingVector(row.embedding_json)
                if (emb.size != queryEmb.size) return@mapNotNull null
                val score = cosineSimilarity(queryEmb, emb)
                if (!score.isFinite()) return@mapNotNull null
                RetrievedChunk(row.source, row.title, row.section, row.chunk_id, row.strategy, row.chunk_text, score)
            }.sortedByDescending { it.score }

            val topBefore = allRanked.take(config.topKBefore)
            val afterFilter = if (config.useFilter) topBefore.filter { it.score >= config.threshold } else topBefore
            val topAfter = afterFilter.take(config.topKAfter)
            val context = if (topAfter.isEmpty()) {
                "Контекст не найден после фильтрации."
            } else {
                topAfter.joinToString("\n\n") {
                    "Document: ${it.title}\nSection: ${it.section}\nPath: ${it.source}\nChunk: ${it.chunkText}"
                }
            }
            val retrievalInfo =
                "rewrite=${if (config.useRewrite) "on" else "off"} | filter=${if (config.useFilter) "on" else "off"} | порог=${"%.2f".format(config.threshold)} | кандидатов=${allRanked.size} | topK-до=${topBefore.size} | после-filter=${afterFilter.size} | topK-после=${topAfter.size}"
            val ragPrompt = AiAgentRagPrompts.buildUserPrompt(
                question = question,
                retrievalQuery = retrievalQuestion,
                context = if (topAfter.isEmpty()) AiAgentRagPrompts.EMPTY_CONTEXT else context
            )

            return RetrievalOutput(
                requestMessages = listOf(
                    DeepSeekMessage("system", AiAgentRagPrompts.SYSTEM),
                    DeepSeekMessage("user", ragPrompt)
                ),
                selectedChunks = topAfter,
                retrievalInfo = retrievalInfo
            )
        }

        suspend fun requestAssistant(question: String, conversation: List<RagMessage>, config: RetrievalConfig): AssistantResult {
            return try {
                val apiKey = ragReadApiKey(config.api.envVar)
                if (apiKey.isBlank()) error("Missing API key: ${config.api.envVar}")

                val retrieval = buildRetrievalOutput(question, config)
                if (config.useRag && retrieval.selectedChunks.isEmpty()) {
                    return AssistantResult(
                        answer = AiAgentRagPrompts.NO_ANSWER,
                        paramsInfo = assistantParams(config),
                        sources = emptyList(),
                        retrievalInfo = retrieval.retrievalInfo
                    )
                }
                val requestMessages = if (config.useRag) {
                    retrieval.requestMessages
                } else {
                    conversation.map { msg -> DeepSeekMessage(if (msg.isUser) "user" else "assistant", msg.text) }
                }

                val request = DeepSeekChatRequest(config.api.defaultModel, requestMessages)
                val response = when (config.api) {
                    RagApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey, request)
                    RagApi.OpenAI -> openAiApi.createChatCompletion(apiKey, request)
                    RagApi.GigaChat -> gigaChatApi.createChatCompletion(apiKey, request)
                    RagApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey, request)
                }
                val answer = response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifEmpty { "Empty response" }
                AssistantResult(answer, assistantParams(config), retrieval.selectedChunks, retrieval.retrievalInfo)
            } catch (e: Exception) {
                AssistantResult("Request failed: ${e.message ?: "unknown error"}", assistantParams(config), emptyList(), null)
            }
        }

        fun sendMessage() {
            val chatId = activeChatId ?: return
            val text = inputText.trim()
            if (text.isEmpty() || isLoading) return

            val primaryConfig = RetrievalConfig(
                api = selectedApi,
                useRag = useRag,
                useFilter = useFilter,
                useRewrite = useRewrite,
                topKBefore = readPositiveInt(topKBeforeRaw, DEFAULT_TOP_K_BEFORE),
                topKAfter = readPositiveInt(topKAfterRaw, DEFAULT_TOP_K_AFTER),
                threshold = readThreshold(thresholdRaw, DEFAULT_MIN_SCORE)
            )
            val compareConfig = RetrievalConfig(
                api = comparisonApi,
                useRag = comparisonUseRag,
                useFilter = comparisonUseFilter,
                useRewrite = comparisonUseRewrite,
                topKBefore = readPositiveInt(comparisonTopKBeforeRaw, DEFAULT_TOP_K_BEFORE),
                topKAfter = readPositiveInt(comparisonTopKAfterRaw, DEFAULT_TOP_K_AFTER),
                threshold = readThreshold(comparisonThresholdRaw, DEFAULT_MIN_SCORE)
            )

            ragQueries.insertMessage(
                chatId,
                primaryConfig.api.label,
                primaryConfig.api.defaultModel,
                "user",
                text,
                userParams(primaryConfig),
                System.currentTimeMillis()
            )
            messages += RagMessage(nextMessageId++, text, true, userParams(primaryConfig))
            if (dualChatEnabled) {
                comparisonMessages += RagMessage(nextMessageId++, text, true, userParams(compareConfig))
            }
            inputText = ""
            saveUseRagState(useRag)

            scope.launch {
                isLoading = true
                val primaryDeferred = async { requestAssistant(text, messages.toList(), primaryConfig) }
                val comparisonDeferred = if (dualChatEnabled) async {
                    requestAssistant(text, comparisonMessages.toList(), compareConfig)
                } else null

                val primaryResult = primaryDeferred.await()
                ragQueries.insertMessage(
                    chatId,
                    primaryConfig.api.label,
                    primaryConfig.api.defaultModel,
                    "assistant",
                    primaryResult.answer,
                    primaryResult.paramsInfo,
                    System.currentTimeMillis()
                )
                messages += RagMessage(
                    id = nextMessageId++,
                    text = primaryResult.answer,
                    isUser = false,
                    paramsInfo = primaryResult.paramsInfo,
                    sources = primaryResult.sources,
                    retrievalInfo = primaryResult.retrievalInfo
                )

                if (dualChatEnabled) {
                    val compareResult = comparisonDeferred?.await()
                    if (compareResult != null) {
                        comparisonMessages += RagMessage(
                            id = nextMessageId++,
                            text = compareResult.answer,
                            isUser = false,
                            paramsInfo = compareResult.paramsInfo,
                            sources = compareResult.sources,
                            retrievalInfo = compareResult.retrievalInfo
                        )
                    }
                }
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            reloadChats()
            if (activeChatId == null && chats.isNotEmpty()) {
                activeChatId = chats.first().id
                loadMessages(chats.first().id)
            }
        }
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }
        LaunchedEffect(comparisonMessages.size) {
            if (comparisonMessages.isNotEmpty()) comparisonListState.animateScrollToItem(comparisonMessages.lastIndex)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AiAgentRAG") },
                    actions = {
                        Box {
                            TextButton(onClick = { screensMenuExpanded = true }, enabled = !isLoading) { Text("Экраны") }
                            DropdownMenu(expanded = screensMenuExpanded, onDismissRequest = { screensMenuExpanded = false }) {
                                RootScreen.entries.forEach { screen ->
                                    DropdownMenuItem(
                                        text = { Text(if (currentScreen == screen) "${screen.name} *" else screen.name) },
                                        onClick = { screensMenuExpanded = false; onSelectScreen(screen) }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFEFF6FF),
                        titleContentColor = Color(0xFF1E3A8A)
                    )
                )
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().imePadding().padding(padding)) {
                Column(
                    Modifier.width(240.dp).fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { createChat(selectNew = true) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("+ Новый чат") }
                    Button(onClick = ::deleteAllChats, modifier = Modifier.fillMaxWidth(), enabled = chats.isNotEmpty() && !isLoading) { Text("Удалить все чаты") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (chat.id == activeChatId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isLoading) {
                                        activeChatId = chat.id
                                        loadMessages(chat.id)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(chat.title, modifier = Modifier.weight(1f))
                                TextButton(onClick = { deleteChat(chat.id) }, enabled = !isLoading) { Text("X") }
                            }
                        }
                    }
                }

                Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))

                Column(Modifier.weight(1f).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Включить двойной чат для сравнения")
                        Switch(checked = dualChatEnabled, onCheckedChange = { dualChatEnabled = it }, enabled = !isLoading)
                    }

                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Настройки основного чата")
                                        TextButton(
                                            onClick = { selectedApi = RagApi.entries[(RagApi.entries.indexOf(selectedApi) + 1) % RagApi.entries.size] },
                                            enabled = !isLoading
                                        ) { Text("API: ${selectedApi.label}") }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = topKBeforeRaw,
                                            onValueChange = { topKBeforeRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Top-K до фильтра") },
                                            maxLines = 1,
                                            enabled = !isLoading
                                        )
                                        OutlinedTextField(
                                            value = topKAfterRaw,
                                            onValueChange = { topKAfterRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Top-K после фильтра") },
                                            maxLines = 1,
                                            enabled = !isLoading
                                        )
                                        OutlinedTextField(
                                            value = thresholdRaw,
                                            onValueChange = {
                                                val normalized = it.replace(',', '.')
                                                thresholdRaw = normalized.filter { c -> c.isDigit() || c == '.' }.take(4)
                                            },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Порог similarity") },
                                            maxLines = 1,
                                            enabled = !isLoading
                                        )
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("RAG")
                                        Switch(checked = useRag, onCheckedChange = { useRag = it; saveUseRagState(it) }, enabled = !isLoading)
                                        Text("filter")
                                        Switch(checked = useFilter, onCheckedChange = { useFilter = it }, enabled = !isLoading)
                                        Text("rewrite")
                                        Switch(checked = useRewrite, onCheckedChange = { useRewrite = it }, enabled = !isLoading)
                                    }
                                }
                            }

                            LazyColumn(modifier = Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(messages, key = { it.id }) { message ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                        Box(
                                            Modifier.fillMaxWidth(0.9f)
                                                .background(if (message.isUser) Color(0xFF1D4ED8) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                SelectionContainer { Text(message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A)) }
                                                Text(
                                                    message.paramsInfo,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f)
                                                )
                                                if (!message.isUser && !message.retrievalInfo.isNullOrBlank()) {
                                                    Text(message.retrievalInfo, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                                }
                                                if (!message.isUser && message.sources.isNotEmpty()) {
                                                    val expanded = selectedSourcesMessageId == message.id
                                                    TextButton(onClick = { selectedSourcesMessageId = if (expanded) null else message.id }) {
                                                        Text(if (expanded) "Скрыть источники" else "Показать источники")
                                                    }
                                                    if (expanded) {
                                                        message.sources.forEach { src ->
                                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text(src.title, style = MaterialTheme.typography.labelMedium)
                                                                    Text("раздел=${src.section} | score=${"%.4f".format(src.score)}", style = MaterialTheme.typography.labelSmall)
                                                                    SelectionContainer { Text(src.chunkText, style = MaterialTheme.typography.bodySmall) }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (isLoading) item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CircularProgressIndicator() }
                                }
                            }
                        }

                        if (dualChatEnabled) {
                            Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))
                            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Настройки чата сравнения")
                                            TextButton(
                                                onClick = { comparisonApi = RagApi.entries[(RagApi.entries.indexOf(comparisonApi) + 1) % RagApi.entries.size] },
                                                enabled = !isLoading
                                            ) { Text("API: ${comparisonApi.label}") }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = comparisonTopKBeforeRaw,
                                                onValueChange = { comparisonTopKBeforeRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Top-K до фильтра") },
                                                maxLines = 1,
                                                enabled = !isLoading
                                            )
                                            OutlinedTextField(
                                                value = comparisonTopKAfterRaw,
                                                onValueChange = { comparisonTopKAfterRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Top-K после фильтра") },
                                                maxLines = 1,
                                                enabled = !isLoading
                                            )
                                            OutlinedTextField(
                                                value = comparisonThresholdRaw,
                                                onValueChange = {
                                                    val normalized = it.replace(',', '.')
                                                    comparisonThresholdRaw = normalized.filter { c -> c.isDigit() || c == '.' }.take(4)
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Порог similarity") },
                                                maxLines = 1,
                                                enabled = !isLoading
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("RAG")
                                            Switch(checked = comparisonUseRag, onCheckedChange = { comparisonUseRag = it }, enabled = !isLoading)
                                            Text("filter")
                                            Switch(checked = comparisonUseFilter, onCheckedChange = { comparisonUseFilter = it }, enabled = !isLoading)
                                            Text("rewrite")
                                            Switch(checked = comparisonUseRewrite, onCheckedChange = { comparisonUseRewrite = it }, enabled = !isLoading)
                                            Spacer(Modifier.weight(1f))
                                            TextButton(
                                                onClick = {
                                                    comparisonUseFilter = false
                                                    comparisonUseRewrite = false
                                                },
                                                enabled = !isLoading
                                            ) { Text("Без filter/rewrite") }
                                        }
                                    }
                                }

                                LazyColumn(modifier = Modifier.weight(1f), state = comparisonListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(comparisonMessages, key = { it.id }) { message ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                            Box(
                                                Modifier.fillMaxWidth(0.9f)
                                                    .background(if (message.isUser) Color(0xFF0EA5E9) else Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    SelectionContainer { Text(message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A)) }
                                                    Text(
                                                        message.paramsInfo,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f)
                                                    )
                                                    if (!message.isUser && !message.retrievalInfo.isNullOrBlank()) {
                                                        Text(message.retrievalInfo, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                                    }
                                                    if (!message.isUser && message.sources.isNotEmpty()) {
                                                        val expanded = comparisonSelectedSourcesMessageId == message.id
                                                        TextButton(onClick = { comparisonSelectedSourcesMessageId = if (expanded) null else message.id }) {
                                                            Text(if (expanded) "Скрыть источники" else "Показать источники")
                                                        }
                                                        if (expanded) {
                                                            message.sources.forEach { src ->
                                                                Card(modifier = Modifier.fillMaxWidth()) {
                                                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        Text(src.title, style = MaterialTheme.typography.labelMedium)
                                                                        Text("раздел=${src.section} | score=${"%.4f".format(src.score)}", style = MaterialTheme.typography.labelSmall)
                                                                        SelectionContainer { Text(src.chunkText, style = MaterialTheme.typography.bodySmall) }
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
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        val withModifier = event.isCtrlPressed || event.isShiftPressed || event.isAltPressed
                                        if (withModifier) {
                                            inputText += "\n"
                                        } else if (inputText.isNotBlank() && !isLoading && activeChatId != null) {
                                            sendMessage()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            label = { Text(if (dualChatEnabled) "Сообщение для двух чатов" else "Сообщение") },
                            maxLines = 4,
                            enabled = !isLoading
                        )
                        Button(
                            onClick = ::sendMessage,
                            enabled = inputText.isNotBlank() && !isLoading && activeChatId != null
                        ) { Text(if (dualChatEnabled) "Отправить в оба" else "Отправить") }
                    }
                }
            }
        }
    }
}
