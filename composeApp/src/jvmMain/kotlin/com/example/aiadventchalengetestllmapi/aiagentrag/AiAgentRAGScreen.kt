package com.example.aiadventchalengetestllmapi.aiagentrag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    val sources: List<RetrievedChunk> = emptyList()
)

private const val PREF_NODE = "com.example.aiadventchalengetestllmapi.aiagentrag"
private const val USE_RAG_KEY = "use_rag_enabled"
private const val RAG_TOP_K = 5
private const val MIN_SCORE = 0.05
private const val RAG_SYSTEM = "Use only provided context. If insufficient, say so."
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentRAGScreen(currentScreen: RootScreen, onSelectScreen: (RootScreen) -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1D4ED8), background = Color(0xFFF8FAFC), surface = Color.White)) {
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
        val listState = rememberLazyListState()
        var activeChatId by remember { mutableStateOf<Long?>(null) }
        var inputText by remember { mutableStateOf("") }
        var selectedApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var isLoading by remember { mutableStateOf(false) }
        var useRag by remember { mutableStateOf(loadUseRagState()) }
        var screensMenuExpanded by remember { mutableStateOf(false) }
        var nextMessageId by remember { mutableStateOf(1L) }
        var selectedSourcesMessageId by remember { mutableStateOf<Long?>(null) }

        fun reloadChats() {
            chats.clear()
            chats.addAll(ragQueries.selectChats().executeAsList().map { RagChatItem(it.id, it.title) })
        }

        fun loadMessages(chatId: Long) {
            messages.clear()
            messages.addAll(ragQueries.selectMessagesByChat(chat_id = chatId).executeAsList().mapIndexed { i, it ->
                RagMessage(id = (i + 1).toLong(), text = it.message, isUser = it.role == "user", paramsInfo = it.params_info)
            })
            nextMessageId = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
            selectedSourcesMessageId = null
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
                    createChat(selectNew = true)
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
            activeChatId = null
            createChat(selectNew = true)
        }

        fun sendMessage() {
            val chatId = activeChatId ?: return
            val text = inputText.trim()
            if (text.isEmpty() || isLoading) return
            val model = selectedApi.defaultModel
            val paramsInfo = "api=${selectedApi.label} | model=$model | rag=${if (useRag) "on" else "off"}"
            ragQueries.insertMessage(chatId, selectedApi.label, model, "user", text, paramsInfo, System.currentTimeMillis())
            messages += RagMessage(nextMessageId++, text, true, paramsInfo)
            inputText = ""

            scope.launch {
                isLoading = true
                val (answer, sources) = try {
                    val apiKey = ragReadApiKey(selectedApi.envVar)
                    if (apiKey.isBlank()) error("Missing API key: ${selectedApi.envVar}")

                    val (requestMessages, topChunks) = if (useRag) {
                        val queryEmb = EmbeddingGeneratorStub.createEmbedding(text).getOrElse { throw it }
                        val chunks = embQueries.selectAll().executeAsList().mapNotNull { row ->
                            val emb = parseEmbeddingVector(row.embedding_json)
                            if (emb.size != queryEmb.size) return@mapNotNull null
                            val score = cosineSimilarity(queryEmb, emb)
                            if (score < MIN_SCORE || !score.isFinite()) return@mapNotNull null
                            RetrievedChunk(row.source, row.title, row.section, row.chunk_id, row.strategy, row.chunk_text, score)
                        }.sortedByDescending { it.score }.take(RAG_TOP_K)
                        val context = if (chunks.isEmpty()) "Context not found." else chunks.joinToString("\n\n") {
                            "Document: ${it.title}\nSection: ${it.section}\nPath: ${it.source}\nChunk: ${it.chunkText}"
                        }
                        listOf(
                            DeepSeekMessage("system", RAG_SYSTEM),
                            DeepSeekMessage("user", "Question:\n$text\n\nContext:\n$context\n\nAnswer only from context.")
                        ) to chunks
                    } else {
                        buildList {
                            ragQueries.selectMessagesByChat(chat_id = chatId).executeAsList().forEach {
                                add(DeepSeekMessage(it.role, it.message))
                            }
                        } to emptyList()
                    }

                    val response = when (selectedApi) {
                        RagApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey, DeepSeekChatRequest(model, requestMessages))
                        RagApi.OpenAI -> openAiApi.createChatCompletion(apiKey, DeepSeekChatRequest(model, requestMessages))
                        RagApi.GigaChat -> gigaChatApi.createChatCompletion(apiKey, DeepSeekChatRequest(model, requestMessages))
                        RagApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey, DeepSeekChatRequest(model, requestMessages))
                    }
                    response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifEmpty { "Empty response" } to topChunks
                } catch (e: Exception) {
                    "Request failed: ${e.message ?: "unknown error"}" to emptyList()
                }

                ragQueries.insertMessage(chatId, selectedApi.label, model, "assistant", answer, paramsInfo, System.currentTimeMillis())
                val answerId = nextMessageId++
                messages += RagMessage(id = answerId, text = answer, isUser = false, paramsInfo = paramsInfo, sources = sources)
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            reloadChats()
            if (chats.isEmpty()) {
                createChat(selectNew = true)
            }
            if (activeChatId == null && chats.isNotEmpty()) {
                activeChatId = chats.first().id
                loadMessages(chats.first().id)
            }
        }
        LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

        Scaffold(topBar = {
            TopAppBar(
                title = { Text("AiAgentRAG") },
                actions = {
                    Box {
                        TextButton(onClick = { screensMenuExpanded = true }, enabled = !isLoading) { Text("Screens") }
                        DropdownMenu(expanded = screensMenuExpanded, onDismissRequest = { screensMenuExpanded = false }) {
                            RootScreen.entries.forEach { screen ->
                                DropdownMenuItem(
                                    text = { Text(if (currentScreen == screen) "${screen.name} ✓" else screen.name) },
                                    onClick = { screensMenuExpanded = false; onSelectScreen(screen) }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFEFF6FF), titleContentColor = Color(0xFF1E3A8A))
            )
        }) { padding ->
            Row(Modifier.fillMaxSize().imePadding().padding(padding)) {
                Column(Modifier.width(240.dp).fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { createChat(selectNew = true) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("+ New chat") }
                    Button(onClick = ::deleteAllChats, modifier = Modifier.fillMaxWidth(), enabled = chats.isNotEmpty() && !isLoading) { Text("Delete all chats") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(Modifier.fillMaxWidth().background(if (chat.id == activeChatId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).clickable(enabled = !isLoading) { activeChatId = chat.id; loadMessages(chat.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.title, modifier = Modifier.weight(1f))
                                TextButton(onClick = { deleteChat(chat.id) }, enabled = !isLoading) { Text("X") }
                            }
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))
                Column(Modifier.weight(1f).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("API: ${selectedApi.label}")
                        TextButton(onClick = { selectedApi = RagApi.entries[(RagApi.entries.indexOf(selectedApi) + 1) % RagApi.entries.size] }, enabled = !isLoading) { Text("Сменить API") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Использовать RAG")
                        Switch(checked = useRag, onCheckedChange = { useRag = it; saveUseRagState(it) }, enabled = !isLoading)
                    }
                    LazyColumn(modifier = Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(messages, key = { it.id }) { message ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                Box(Modifier.fillMaxWidth(0.8f).background(if (message.isUser) Color(0xFF1D4ED8) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).padding(12.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SelectionContainer { Text(message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A)) }
                                        Text(message.paramsInfo, style = MaterialTheme.typography.labelSmall, color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f))
                                        if (!message.isUser && message.sources.isNotEmpty()) {
                                            val isSelected = selectedSourcesMessageId == message.id
                                            TextButton(onClick = { selectedSourcesMessageId = if (isSelected) null else message.id }) {
                                                Text(if (isSelected) "Скрыть источники" else "Посмотреть источники")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (isLoading) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CircularProgressIndicator() } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f), label = { Text("Message") }, maxLines = 4, enabled = !isLoading)
                        Button(onClick = ::sendMessage, enabled = inputText.isNotBlank() && !isLoading && activeChatId != null) { Text("Send") }
                    }
                }

                val selectedSources = messages.firstOrNull { it.id == selectedSourcesMessageId }?.sources.orEmpty()
                if (selectedSourcesMessageId != null) {
                    Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))
                    Column(
                        modifier = Modifier.width(420.dp).fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Источники ответа")
                            TextButton(onClick = { selectedSourcesMessageId = null }) { Text("Закрыть") }
                        }
                        Text("Использовано источников: ${selectedSources.size}", style = MaterialTheme.typography.labelSmall)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(selectedSources) { src ->
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(src.title, style = MaterialTheme.typography.titleSmall, color = Color(0xFF0F172A))
                                        Text(
                                            "Раздел: ${src.section}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                        Text(
                                            "Путь: ${src.source}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                        Text(
                                            "Стратегия: ${src.strategy} | Чанк: ${src.chunkId} | score=${"%.4f".format(src.score)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF334155)
                                        )
                                        Text("Чанк", style = MaterialTheme.typography.labelMedium, color = Color(0xFF0F172A))
                                        SelectionContainer {
                                            Text(
                                                text = src.chunkText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF0F172A)
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
    }
}
