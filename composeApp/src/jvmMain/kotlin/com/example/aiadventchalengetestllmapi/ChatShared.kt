package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import com.example.aiadventchalengetestllmapi.network.GigaChatApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.launch
import java.util.Locale

private enum class ChatApi(
    val label: String,
    val envVar: String,
    val defaultModel: String,
    val supportedModels: List<String>
) {
    DeepSeek(
        label = "DeepSeek",
        envVar = "DEEPSEEK_API_KEY",
        defaultModel = "deepseek-chat",
        supportedModels = listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    ),
    OpenAI(
        label = "OpenAI",
        envVar = "OPENAI_API_KEY",
        defaultModel = "gpt-4o-mini",
        supportedModels = listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini",
            "gpt-4.1",
            "o3-mini"
        )
    ),
    GigaChat(
        label = "GigaChat",
        envVar = "GIGACHAT_ACCESS_TOKEN",
        defaultModel = "GigaChat-2",
        supportedModels = listOf(
            "GigaChat-2",
            "GigaChat-2-Pro",
            "GigaChat-2-Max"
        )
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
            "anthropic/claude-3-7-sonnet-20250219",
        )
    )
}

private data class UiChatMessage(
    val text: String,
    val isUser: Boolean,
    val paramsInfo: String
)

private fun readApiKeyFromEnv(envVar: String): String? =
    System.getenv(envVar)?.trim()?.takeIf { it.isNotEmpty() }

private fun Double.formatSeconds(): String = String.format(Locale.US, "%.2f", this)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatContent(
    modifier: Modifier = Modifier,
    allowApiKeyInput: Boolean,
    showSystemPromptField: Boolean,
    showAdvancedParams: Boolean
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val openAiApi = remember { OpenAiApi() }
    val gigaChatApi = remember { GigaChatApi() }
    val proxyOpenAiApi = remember { ProxyOpenAiApi() }
    val messages = remember { mutableStateListOf<UiChatMessage>() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val apiKeysByApi = remember {
        mutableStateMapOf<ChatApi, String>().apply {
            ChatApi.entries.forEach { api ->
                this[api] = readApiKeyFromEnv(api.envVar).orEmpty()
            }
        }
    }
    var selectedApi by remember { mutableStateOf(ChatApi.DeepSeek) }
    var apiSelectorExpanded by remember { mutableStateOf(false) }
    var modelSelectorExpanded by remember { mutableStateOf(false) }
    var systemPromptInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(ChatApi.DeepSeek.defaultModel) }
    var temperatureInput by remember { mutableStateOf("") }
    var topPInput by remember { mutableStateOf("") }
    var maxTokensInput by remember { mutableStateOf("") }
    var presencePenaltyInput by remember { mutableStateOf("") }
    var frequencyPenaltyInput by remember { mutableStateOf("") }
    var stopInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun sendMessage() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() || isLoading) return

        val model = modelInput.trim().ifEmpty { selectedApi.defaultModel }
        val temperature = if (showAdvancedParams) temperatureInput.trim().toDoubleOrNull() else null
        val topP = if (showAdvancedParams) topPInput.trim().toDoubleOrNull() else null
        val maxTokens = if (showAdvancedParams) maxTokensInput.trim().toIntOrNull() else null
        val presencePenalty = if (showAdvancedParams) presencePenaltyInput.trim().toDoubleOrNull() else null
        val frequencyPenalty = if (showAdvancedParams) frequencyPenaltyInput.trim().toDoubleOrNull() else null
        val stop = if (showAdvancedParams) {
            stopInput
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val paramsInfo = buildString {
            append("api=")
            append(selectedApi.label)
            append(" | model=")
            append(model)
            if (showAdvancedParams) {
                append(" | temperature=")
                append(temperature)
                append(" | top_p=")
                append(topP)
                append(" | max_tokens=")
                append(maxTokens)
                append(" | presence_penalty=")
                append(presencePenalty)
                append(" | frequency_penalty=")
                append(frequencyPenalty)
                append(" | stop=")
                append(stop)
            }
        }

        messages += UiChatMessage(
            text = trimmed,
            isUser = true,
            paramsInfo = "$paramsInfo | response_time=pending"
        )

        scope.launch {
            isLoading = true
            val startedAtNanos = System.nanoTime()
            val answer = try {
                val apiKey = if (allowApiKeyInput) {
                    apiKeysByApi[selectedApi].orEmpty().trim()
                        .ifEmpty { readApiKeyFromEnv(selectedApi.envVar).orEmpty() }
                } else {
                    readApiKeyFromEnv(selectedApi.envVar).orEmpty()
                }
                if (apiKey.isBlank()) {
                    error("Missing API key in env var: ${selectedApi.envVar}")
                }

                val history = buildList {
                    if (showSystemPromptField) {
                        val systemPrompt = systemPromptInput.trim()
                        if (systemPrompt.isNotEmpty()) {
                            add(DeepSeekMessage(role = "system", content = systemPrompt))
                        }
                    }
                    add(DeepSeekMessage(role = "user", content = trimmed))
                }
                val request = DeepSeekChatRequest(
                    model = model,
                    messages = history,
                    temperature = temperature,
                    maxTokens = maxTokens,
                    topP = topP,
                    presencePenalty = presencePenalty,
                    frequencyPenalty = frequencyPenalty,
                    stop = stop
                )
                val response = when (selectedApi) {
                    ChatApi.DeepSeek -> deepSeekApi.createChatCompletion(
                        apiKey = apiKey,
                        request = request
                    )

                    ChatApi.OpenAI -> openAiApi.createChatCompletion(
                        apiKey = apiKey,
                        request = request
                    )

                    ChatApi.GigaChat -> gigaChatApi.createChatCompletion(
                        accessToken = apiKey,
                        request = request
                    )

                    ChatApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(
                        apiKey = apiKey,
                        request = request
                    )
                }
                response.choices.firstOrNull()?.message?.content?.trim()
                    .orEmpty()
                    .ifEmpty { "Empty response from ${selectedApi.label}." }
            } catch (e: Exception) {
                "Request failed: ${e.message ?: "unknown error"}"
            }
            val responseTimeSec = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0
            val paramsInfoWithTiming =
                "$paramsInfo | response_time=${responseTimeSec.formatSeconds()}"
            messages += UiChatMessage(
                text = answer,
                isUser = false,
                paramsInfo = paramsInfoWithTiming
            )
            isLoading = false
        }
    }

    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
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
            onExpandedChange = { expanded ->
                if (!isLoading) {
                    apiSelectorExpanded = expanded
                }
            }
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
                ChatApi.entries.forEach { api ->
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

        if (allowApiKeyInput) {
            OutlinedTextField(
                value = apiKeysByApi[selectedApi].orEmpty(),
                onValueChange = { apiKeysByApi[selectedApi] = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(selectedApi.envVar) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading
            )
        }

        if (showSystemPromptField) {
            OutlinedTextField(
                value = systemPromptInput,
                onValueChange = { systemPromptInput = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                label = { Text("System prompt") },
                maxLines = 2
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
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
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = modelSelectorExpanded,
                onExpandedChange = { expanded ->
                    if (!isLoading) {
                        modelSelectorExpanded = expanded
                    }
                },
                modifier = Modifier.weight(if (showAdvancedParams) 1.2f else 1f)
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
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelSelectorExpanded)
                    },
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

            if (showAdvancedParams) {
                OutlinedTextField(
                    value = maxTokensInput,
                    onValueChange = { maxTokensInput = it },
                    modifier = Modifier
                        .weight(0.8f)
                        .height(52.dp),
                    enabled = !isLoading,
                    placeholder = { Text("max_tokens", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (showAdvancedParams) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = temperatureInput,
                    onValueChange = { temperatureInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isLoading,
                    placeholder = { Text("temperature", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = topPInput,
                    onValueChange = { topPInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isLoading,
                    placeholder = { Text("top_p", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = presencePenaltyInput,
                    onValueChange = { presencePenaltyInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isLoading,
                    placeholder = { Text("presence_penalty", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall
                )
                OutlinedTextField(
                    value = frequencyPenaltyInput,
                    onValueChange = { frequencyPenaltyInput = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    enabled = !isLoading,
                    placeholder = { Text("frequency_penalty", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelSmall
                )
            }
            OutlinedTextField(
                value = stopInput,
                onValueChange = { stopInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                placeholder = { Text("stop (comma-separated)", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelSmall
            )
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
private fun ChatBubble(message: UiChatMessage) {
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
