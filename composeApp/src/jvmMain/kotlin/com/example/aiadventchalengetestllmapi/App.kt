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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.coroutines.launch

private const val DEEPSEEK_API_KEY_ENV = "DEEPSEEK_API_KEY"

private data class UiChatMessage(
    val text: String,
    val isUser: Boolean,
    val paramsInfo: String
)

private fun readDeepSeekApiKeyFromEnv(): String? =
    System.getenv(DEEPSEEK_API_KEY_ENV)?.trim()?.takeIf { it.isNotEmpty() }

@Composable
@Preview
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val deepSeekApi = remember { DeepSeekApi() }
        val messages = remember { mutableStateListOf<UiChatMessage>() }
        val listState = rememberLazyListState()
        var inputText by remember { mutableStateOf("") }
        var apiKeyInput by remember { mutableStateOf(readDeepSeekApiKeyFromEnv().orEmpty()) }
        var systemPromptInput by remember { mutableStateOf("") }
        var modelInput by remember { mutableStateOf("deepseek-chat") }
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

            val model = modelInput.trim().ifEmpty { "deepseek-chat" }
            val temperature = temperatureInput.trim().toDoubleOrNull()
            val topP = topPInput.trim().toDoubleOrNull()
            val maxTokens = maxTokensInput.trim().toIntOrNull()
            val presencePenalty = presencePenaltyInput.trim().toDoubleOrNull()
            val frequencyPenalty = frequencyPenaltyInput.trim().toDoubleOrNull()
            val stop = stopInput
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
            val paramsInfo = buildString {
                append("model=")
                append(model)
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

            messages += UiChatMessage(text = trimmed, isUser = true, paramsInfo = paramsInfo)

            scope.launch {
                isLoading = true
                val answer = try {
                    val apiKey = apiKeyInput.trim().ifEmpty { readDeepSeekApiKeyFromEnv().orEmpty() }
                    if (apiKey.isBlank()) {
                        error("Missing API key in top field or env var: $DEEPSEEK_API_KEY_ENV")
                    }
                    val history = buildList {
                        val systemPrompt = systemPromptInput.trim()
                        if (systemPrompt.isNotEmpty()) {
                            add(DeepSeekMessage(role = "system", content = systemPrompt))
                        }
                        add(DeepSeekMessage(role = "user", content = trimmed))
                    }
                    val response = deepSeekApi.createChatCompletion(
                        apiKey = apiKey,
                        request = DeepSeekChatRequest(
                            model = model,
                            messages = history,
                            temperature = temperature,
                            maxTokens = maxTokens,
                            topP = topP,
                            presencePenalty = presencePenalty,
                            frequencyPenalty = frequencyPenalty,
                            stop = stop
                        )
                    )
                    response.choices.firstOrNull()?.message?.content?.trim()
                        .orEmpty()
                        .ifEmpty { "Пустой ответ от DeepSeek." }
                } catch (e: Exception) {
                    "Ошибка запроса: ${e.message ?: "неизвестная ошибка"}"
                }
                messages += UiChatMessage(text = answer, isUser = false, paramsInfo = paramsInfo)
                isLoading = false
            }
        }

        LaunchedEffect(messages.size, isLoading) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(DEEPSEEK_API_KEY_ENV) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading
            )
            OutlinedTextField(
                value = systemPromptInput,
                onValueChange = { systemPromptInput = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                label = { Text("System prompt") },
                maxLines = 2
            )

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

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = { modelInput = it },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp),
                        enabled = !isLoading,
                        placeholder = { Text("model", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.labelSmall
                    )
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
                    label = { Text("Сообщение") },
                    maxLines = 4
                )
                Button(
                    onClick = ::sendMessage,
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Text("Отправить")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: UiChatMessage) {
    val deepSeekBubbleColor = Color(0xFFE3F0FF)
    val deepSeekTextColor = Color(0xFF123A6B)

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
                        deepSeekBubbleColor
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
                            deepSeekTextColor
                        }
                    )
                }
                Text(
                    text = message.paramsInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        deepSeekTextColor.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}
