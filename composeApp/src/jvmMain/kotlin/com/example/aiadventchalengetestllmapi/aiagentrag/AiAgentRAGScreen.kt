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
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.launch

private enum class RagApi(val label: String, val envVar: String, val defaultModel: String) {
    DeepSeek("DeepSeek", "DEEPSEEK_API_KEY", "deepseek-chat"),
    OpenAI("OpenAI", "OPENAI_API_KEY", "gpt-4o-mini"),
    GigaChat("GigaChat", "GIGACHAT_ACCESS_TOKEN", "GigaChat-2"),
    ProxyOpenAI("ProxyAPI (OpenAI)", "PROXYAPI_API_KEY", "openai/gpt-4o-mini")
}

private data class RagChatItem(val id: Long, val title: String)
private data class RagMessage(val text: String, val isUser: Boolean, val paramsInfo: String)

private fun ragReadApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentRAGScreen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1D4ED8),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A),
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A)
        )
    ) {
        val scope = rememberCoroutineScope()
        val deepSeekApi = remember { DeepSeekApi() }
        val openAiApi = remember { OpenAiApi() }
        val gigaChatApi = remember { GigaChatApi() }
        val proxyOpenAiApi = remember { ProxyOpenAiApi() }
        val database = remember { createAiAgentRagDatabase(AiAgentRagDatabaseDriverFactory()) }
        val queries = remember(database) { database.chatHistoryQueries }

        val chats = remember { mutableStateListOf<RagChatItem>() }
        val messages = remember { mutableStateListOf<RagMessage>() }
        val listState = rememberLazyListState()

        var activeChatId by remember { mutableStateOf<Long?>(null) }
        var inputText by remember { mutableStateOf("") }
        var selectedApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var apiSelectorExpanded by remember { mutableStateOf(false) }
        var screensMenuExpanded by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }

        fun reloadChats() {
            chats.clear()
            chats.addAll(queries.selectChats().executeAsList().map { RagChatItem(it.id, it.title) })
        }

        fun loadMessages(chatId: Long) {
            messages.clear()
            messages.addAll(
                queries.selectMessagesByChat(chat_id = chatId).executeAsList().map {
                    RagMessage(
                        text = it.message,
                        isUser = it.role == "user",
                        paramsInfo = it.params_info
                    )
                }
            )
        }

        fun createChat(selectNew: Boolean) {
            val nextIndex = (chats.maxOfOrNull { it.id } ?: 0L) + 1L
            queries.insertChat(
                title = "Chat $nextIndex",
                created_at = System.currentTimeMillis(),
                selected_profile_id = null
            )
            val newId = queries.selectLastInsertedChatId().executeAsOne()
            reloadChats()
            if (selectNew) {
                activeChatId = newId
                messages.clear()
            }
        }

        fun deleteAllChats() {
            queries.deleteAllBranchMessages()
            queries.deleteAllMessages()
            queries.deleteAllChats()
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
            val paramsInfo = "api=${selectedApi.label} | model=$model"
            queries.insertMessage(
                chat_id = chatId,
                api = selectedApi.label,
                model = model,
                role = "user",
                message = text,
                params_info = paramsInfo,
                created_at = System.currentTimeMillis()
            )
            messages += RagMessage(text = text, isUser = true, paramsInfo = paramsInfo)
            inputText = ""

            scope.launch {
                isLoading = true
                val answer = try {
                    val apiKey = ragReadApiKey(selectedApi.envVar)
                    if (apiKey.isBlank()) error("Missing API key: ${selectedApi.envVar}")
                    val history = buildList {
                        queries.selectMessagesByChat(chat_id = chatId).executeAsList().forEach { dbMessage ->
                            add(DeepSeekMessage(role = dbMessage.role, content = dbMessage.message))
                        }
                    }
                    val request = DeepSeekChatRequest(model = model, messages = history)
                    val response = when (selectedApi) {
                        RagApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
                        RagApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
                        RagApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
                        RagApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
                    }
                    response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifEmpty { "Empty response" }
                } catch (e: Exception) {
                    "Request failed: ${e.message ?: "unknown error"}"
                }

                queries.insertMessage(
                    chat_id = chatId,
                    api = selectedApi.label,
                    model = model,
                    role = "assistant",
                    message = answer,
                    params_info = paramsInfo,
                    created_at = System.currentTimeMillis()
                )
                messages += RagMessage(text = answer, isUser = false, paramsInfo = paramsInfo)
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            reloadChats()
            if (chats.isEmpty()) createChat(selectNew = true)
            if (activeChatId == null && chats.isNotEmpty()) {
                activeChatId = chats.first().id
                loadMessages(chats.first().id)
            }
        }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }

        Scaffold(
            topBar = {
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFEFF6FF),
                        titleContentColor = Color(0xFF1E3A8A)
                    )
                )
            }
        ) { padding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.width(240.dp).fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { createChat(selectNew = true) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                        Text("+ New chat")
                    }
                    Button(
                        onClick = ::deleteAllChats,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = chats.isNotEmpty() && !isLoading
                    ) {
                        Text("Удалить все чаты")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (chat.id == activeChatId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isLoading) {
                                        activeChatId = chat.id
                                        loadMessages(chat.id)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(chat.title, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        queries.deleteChatById(chat.id)
                                        reloadChats()
                                        if (activeChatId == chat.id) {
                                            if (chats.isEmpty()) createChat(selectNew = true)
                                            else {
                                                activeChatId = chats.first().id
                                                loadMessages(chats.first().id)
                                            }
                                        }
                                    },
                                    enabled = !isLoading
                                ) { Text("X") }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))

                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        Button(
                            onClick = { apiSelectorExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading
                        ) {
                            Text("API: ${selectedApi.label}")
                        }
                        DropdownMenu(
                            expanded = apiSelectorExpanded,
                            onDismissRequest = { apiSelectorExpanded = false }
                        ) {
                            RagApi.entries.forEach { api ->
                                DropdownMenuItem(
                                    text = { Text(api.label) },
                                    onClick = {
                                        selectedApi = api
                                        apiSelectorExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(messages) { message ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .background(
                                            color = if (message.isUser) Color(0xFF1D4ED8) else Color(0xFFE2E8F0),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        SelectionContainer {
                                            Text(text = message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A))
                                        }
                                        Text(
                                            text = message.paramsInfo,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                        if (isLoading) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Message") },
                            maxLines = 4,
                            enabled = !isLoading
                        )
                        Button(onClick = ::sendMessage, enabled = inputText.isNotBlank() && !isLoading && activeChatId != null) {
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}
