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
private data class RagMessage(val text: String, val isUser: Boolean, val paramsInfo: String)
private data class RetrievedChunk(
    val source: String,
    val title: String,
    val section: String,
    val chunkId: Long,
    val strategy: String,
    val chunkText: String,
    val score: Double
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
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parsed = runCatching { ragJson.parseToJsonElement(trimmed) }.getOrNull()
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
        val l = left[i]
        val r = right[i]
        dot += l * r
        l2 += l * l
        r2 += r * r
    }
    if (l2 == 0.0 || r2 == 0.0) return 0.0
    return dot / (kotlin.math.sqrt(l2) * kotlin.math.sqrt(r2))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentRAGScreen(currentScreen: RootScreen, onSelectScreen: (RootScreen) -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1D4ED8), onPrimary = Color.White, primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A), background = Color(0xFFF8FAFC), onBackground = Color(0xFF0F172A),
            surface = Color.White, onSurface = Color(0xFF0F172A)
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
        val retrieved = remember { mutableStateListOf<RetrievedChunk>() }
        val sourceRows = remember { mutableStateListOf<String>() }
        val listState = rememberLazyListState()
        var activeChatId by remember { mutableStateOf<Long?>(null) }
        var inputText by remember { mutableStateOf("") }
        var selectedApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var isLoading by remember { mutableStateOf(false) }
        var useRag by remember { mutableStateOf(loadUseRagState()) }
        var showSources by remember { mutableStateOf(false) }
        var screensMenuExpanded by remember { mutableStateOf(false) }

        fun refreshSourceRows() {
            val scoreMap = retrieved.associateBy({ "${it.source}|${it.strategy}|${it.chunkId}" }, { it.score })
            sourceRows.clear()
            sourceRows.addAll(embQueries.selectAll().executeAsList().map { row ->
                val key = "${row.source}|${row.strategy}|${row.chunk_id}"
                val score = scoreMap[key]?.let { " score=${"%.4f".format(it)}" }.orEmpty()
                "[${row.title}] ${row.section} | ${row.strategy}#${row.chunk_id}$score\n${row.chunk_text}"
            })
        }

        fun reloadChats() {
            chats.clear()
            chats.addAll(ragQueries.selectChats().executeAsList().map { RagChatItem(it.id, it.title) })
        }

        fun loadMessages(chatId: Long) {
            messages.clear()
            messages.addAll(ragQueries.selectMessagesByChat(chat_id = chatId).executeAsList().map {
                RagMessage(it.message, it.role == "user", it.params_info)
            })
        }

        fun sendMessage() {
            val chatId = activeChatId ?: return
            val text = inputText.trim()
            if (text.isEmpty() || isLoading) return
            val model = selectedApi.defaultModel
            val paramsInfo = "api=${selectedApi.label} | model=$model | rag=${if (useRag) "on" else "off"}"
            ragQueries.insertMessage(chatId, selectedApi.label, model, "user", text, paramsInfo, System.currentTimeMillis())
            messages += RagMessage(text, true, paramsInfo)
            inputText = ""
            scope.launch {
                isLoading = true
                val answer = try {
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
                    retrieved.clear(); retrieved.addAll(topChunks)
                    if (showSources) refreshSourceRows()
                    response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifEmpty { "Empty response" }
                } catch (e: Exception) {
                    "Request failed: ${e.message ?: "unknown error"}"
                }
                ragQueries.insertMessage(chatId, selectedApi.label, model, "assistant", answer, paramsInfo, System.currentTimeMillis())
                messages += RagMessage(answer, false, paramsInfo)
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            reloadChats()
            if (chats.isEmpty()) {
                ragQueries.insertChat("Chat 1", System.currentTimeMillis(), null)
                reloadChats()
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
                        TextButton(onClick = { screensMenuExpanded = true }, enabled = !isLoading) {
                            Text("Screens")
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
                                text = { Text(if (currentScreen == RootScreen.AiAgentMain) "AiAgentMain ✓" else "AiAgentMain") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiAgentMain) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiAgentMCP) "AiAgentMCP ✓" else "AiAgentMCP") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiAgentMCP) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiStateAgent) "AiStateAgent ✓" else "AiStateAgent") },
                                onClick = { screensMenuExpanded = false; onSelectScreen(RootScreen.AiStateAgent) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (currentScreen == RootScreen.AiWeek3) "AiWeek3 ✓" else "AiWeek3") },
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFEFF6FF), titleContentColor = Color(0xFF1E3A8A))
            )
        }) { padding ->
            Row(Modifier.fillMaxSize().imePadding().padding(padding)) {
                Column(Modifier.width(240.dp).fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { ragQueries.insertChat("Chat ${System.currentTimeMillis()}", System.currentTimeMillis(), null); reloadChats() }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("+ New chat") }
                    Button(onClick = { ragQueries.deleteAllBranchMessages(); ragQueries.deleteAllMessages(); ragQueries.deleteAllChats(); chats.clear(); messages.clear() }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("Delete all chats") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(Modifier.fillMaxWidth().background(if (chat.id == activeChatId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)).clickable(enabled = !isLoading) { activeChatId = chat.id; loadMessages(chat.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(chat.title, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))
                Column(Modifier.weight(1f).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("API: ${selectedApi.label}", modifier = Modifier.weight(1f))
                        TextButton(onClick = { selectedApi = RagApi.entries[(RagApi.entries.indexOf(selectedApi) + 1) % RagApi.entries.size] }, enabled = !isLoading) { Text("Сменить API") }
                        Button(onClick = { showSources = !showSources; if (showSources) refreshSourceRows() }, enabled = !isLoading) { Text(if (showSources) "Скрыть источники" else "Посмотреть источники") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Использовать RAG")
                        Switch(checked = useRag, onCheckedChange = { useRag = it; saveUseRagState(it) }, enabled = !isLoading)
                    }
                    LazyColumn(modifier = Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(messages) { message ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                Box(Modifier.fillMaxWidth(0.8f).background(if (message.isUser) Color(0xFF1D4ED8) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp)).padding(12.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SelectionContainer { Text(message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A)) }
                                        Text(message.paramsInfo, style = MaterialTheme.typography.labelSmall, color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f))
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
                if (showSources) {
                    Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))
                    Column(Modifier.width(420.dp).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Источники (записи БД)")
                        Text("Всего: ${sourceRows.size} | В ответе: ${retrieved.size}", style = MaterialTheme.typography.labelSmall)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sourceRows) { row -> SelectionContainer { Text(row) } }
                        }
                    }
                }
            }
        }
    }
}
