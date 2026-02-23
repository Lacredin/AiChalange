package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.launch
import java.util.Locale

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
    val paramsInfo: String
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(onOpenApp: () -> Unit) {
    var newChatTrigger by remember { mutableIntStateOf(0) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ai \u0410\u0433\u0435\u043D\u0442") },
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

    val messages = remember { mutableStateListOf<AiAgentMessage>() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var selectedApi by remember { mutableStateOf(AiAgentApi.DeepSeek) }
    var apiSelectorExpanded by remember { mutableStateOf(false) }
    var modelSelectorExpanded by remember { mutableStateOf(false) }
    var modelInput by remember { mutableStateOf(AiAgentApi.DeepSeek.defaultModel) }
    var chatSessionId by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    fun sendMessage() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() || isLoading) return

        val model = modelInput.trim().ifEmpty { selectedApi.defaultModel }
        val paramsInfo = "api=${selectedApi.label} | model=$model"

        messages += AiAgentMessage(
            text = trimmed,
            isUser = true,
            paramsInfo = "$paramsInfo | response_time=pending"
        )

        scope.launch {
            val requestSessionId = chatSessionId
            isLoading = true
            val startedAtNanos = System.nanoTime()
            val answer = try {
                val apiKey = aiAgentReadApiKey(selectedApi.envVar)
                if (apiKey.isBlank()) {
                    error("Missing API key in secrets.properties or env var: ${selectedApi.envVar}")
                }

                val request = DeepSeekChatRequest(
                    model = model,
                    messages = messages
                        .filter { it.isApiHistoryMessage() }
                        .map { it.toRequestMessage() }
                )

                val response = when (selectedApi) {
                    AiAgentApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
                    AiAgentApi.OpenAI -> openAiApi.createChatCompletion(apiKey = apiKey, request = request)
                    AiAgentApi.GigaChat -> gigaChatApi.createChatCompletion(accessToken = apiKey, request = request)
                    AiAgentApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey = apiKey, request = request)
                }

                response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
                    .ifEmpty { "Empty response from ${selectedApi.label}." }
            } catch (e: Exception) {
                "Request failed: ${e.message ?: "unknown error"}"
            }

            val responseTimeSec = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0
            if (requestSessionId != chatSessionId) {
                isLoading = false
                return@launch
            }
            messages += AiAgentMessage(
                text = answer,
                isUser = false,
                paramsInfo = "$paramsInfo | response_time=${responseTimeSec.aiAgentFormatSeconds()}"
            )
            isLoading = false
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(newChatTrigger) {
        if (newChatTrigger > 0) {
            chatSessionId++
            messages.clear()
            inputText = ""
            modelSelectorExpanded = false
            apiSelectorExpanded = false
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = apiSelectorExpanded,
            onExpandedChange = { expanded -> if (!isLoading) apiSelectorExpanded = expanded }
        ) {
            OutlinedTextField(
                value = selectedApi.label,
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = !isLoading
                    )
                    .fillMaxWidth(),
                readOnly = true,
                label = { Text("Current API") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = apiSelectorExpanded) },
                enabled = !isLoading
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
            onExpandedChange = { expanded -> if (!isLoading) modelSelectorExpanded = expanded }
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

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
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
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                label = { Text("Message") },
                maxLines = 4
            )
            Button(
                onClick = ::sendMessage,
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Text("Send")
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
                    text = message.paramsInfo,
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
