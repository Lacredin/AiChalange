package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private data class EmbeddingApiModel(
    val apiLabel: String,
    val modelLabel: String
)

private fun readApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedingGenerationScreen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val supportedModels = remember {
        listOf(
            EmbeddingApiModel(
                apiLabel = "DeepSeek API",
                modelLabel = "deepseek-chat"
            )
        )
    }
    val json = remember { Json { prettyPrint = true; encodeDefaults = true } }

    var selectedModel by remember { mutableStateOf(supportedModels.first()) }
    var modelsMenuExpanded by remember { mutableStateOf(false) }
    var screensMenuExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("Result will appear after pressing Send.") }
    var logText by remember { mutableStateOf("Logs will appear after request.") }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EmbedingGeneration") },
                actions = {
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
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEFF6FF),
                    titleContentColor = Color(0xFF1E3A8A)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Embedding API model selection")
            TextButton(onClick = { modelsMenuExpanded = true }, enabled = !isLoading) {
                Text("API/Model: ${selectedModel.apiLabel} / ${selectedModel.modelLabel}")
            }
            DropdownMenu(
                expanded = modelsMenuExpanded,
                onDismissRequest = { modelsMenuExpanded = false }
            ) {
                supportedModels.forEach { model ->
                    DropdownMenuItem(
                        text = { Text("${model.apiLabel} / ${model.modelLabel}") },
                        onClick = {
                            selectedModel = model
                            modelsMenuExpanded = false
                        }
                    )
                }
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Text for embedding") },
                minLines = 4,
                enabled = !isLoading
            )

            Button(
                onClick = {
                    val trimmed = inputText.trim()
                    if (trimmed.isEmpty() || isLoading) return@Button

                    val request = DeepSeekChatRequest(
                        model = selectedModel.modelLabel,
                        temperature = 0.0,
                        messages = listOf(
                            DeepSeekMessage(
                                role = "system",
                                content = "Return valid JSON only: {\"embedding\":[float,...],\"dimensions\":64}. Build semantic vector for user text. Exactly 64 float values."
                            ),
                            DeepSeekMessage(
                                role = "user",
                                content = trimmed
                            )
                        )
                    )
                    val requestJson = json.encodeToString(request)
                    val apiKey = readApiKey("DEEPSEEK_API_KEY")
                    if (apiKey.isBlank()) {
                        resultText = "Error: missing DEEPSEEK_API_KEY."
                        logText = "REQUEST:\n$requestJson\n\nRESPONSE:\nMissing DEEPSEEK_API_KEY"
                        return@Button
                    }

                    scope.launch {
                        isLoading = true
                        logText = "REQUEST:\n$requestJson\n\nRESPONSE:\nPending..."
                        try {
                            val response = deepSeekApi.createChatCompletion(
                                apiKey = apiKey,
                                request = request
                            )
                            val responseJson = json.encodeToString(response)
                            val answerText = response.choices.firstOrNull()?.message?.content.orEmpty()
                            resultText = buildString {
                                appendLine("Model: ${response.model}")
                                appendLine("Prompt tokens: ${response.usage?.promptTokens ?: "n/a"}")
                                appendLine("Completion tokens: ${response.usage?.completionTokens ?: "n/a"}")
                                appendLine("Total tokens: ${response.usage?.totalTokens ?: "n/a"}")
                                appendLine()
                                append(answerText)
                            }
                            logText = "REQUEST:\n$requestJson\n\nRESPONSE:\n$responseJson"
                            println("Embedding request: $requestJson")
                            println("Embedding response: $responseJson")
                        } catch (e: Exception) {
                            val errorText = "Request failed: ${e.message ?: "unknown error"}"
                            resultText = errorText
                            logText = "REQUEST:\n$requestJson\n\nRESPONSE:\n$errorText"
                            println("Embedding error: $errorText")
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Text(if (isLoading) "Отправка..." else "Отправить")
            }

            OutlinedTextField(
                value = resultText,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Result") },
                readOnly = true,
                minLines = 6
            )

            OutlinedTextField(
                value = logText,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Request/Response log") },
                readOnly = true,
                minLines = 8
            )
        }
    }
}
