package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private enum class ChunkStrategy(val label: String) {
    Fixed500("Фиксированная (500)"),
    Structured("По структуре текста"),
    Semantic("Семантическая")
}

private fun readApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

private fun chunkFixed(text: String, size: Int = 500): List<String> {
    if (text.isBlank()) return emptyList()
    return text.trim().chunked(size)
}

private fun chunkStructured(text: String, targetSize: Int = 500): List<String> {
    if (text.isBlank()) return emptyList()
    val paragraphs = text
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val chunks = mutableListOf<String>()
    val current = StringBuilder()

    fun flush() {
        if (current.isNotBlank()) {
            chunks += current.toString().trim()
            current.clear()
        }
    }

    paragraphs.forEach { paragraph ->
        if (paragraph.length >= targetSize) {
            flush()
            chunks += paragraph.chunked(targetSize)
            return@forEach
        }

        if (current.isEmpty()) {
            current.append(paragraph)
        } else if (current.length + 2 + paragraph.length <= targetSize) {
            current.append("\n\n").append(paragraph)
        } else {
            flush()
            current.append(paragraph)
        }
    }
    flush()
    return chunks
}

private fun semanticKeywords(text: String): Set<String> =
    text.lowercase()
        .split(Regex("[^a-zа-я0-9]+"))
        .filter { it.length > 2 }
        .toSet()

private fun jaccard(a: Set<String>, b: Set<String>): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    val intersection = a.intersect(b).size.toDouble()
    val union = a.union(b).size.toDouble()
    if (union == 0.0) return 0.0
    return intersection / union
}

private data class SemanticChunk(
    val text: String,
    val keywords: Set<String>
)

private fun chunkSemantic(text: String, targetSize: Int = 500, similarityThreshold: Double = 0.2): List<String> {
    if (text.isBlank()) return emptyList()
    val sentences = text
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (sentences.isEmpty()) return emptyList()

    val chunks = mutableListOf<SemanticChunk>()
    var currentChunk = sentences.first()
    var currentKeywords = semanticKeywords(currentChunk)

    fun flush() {
        if (currentChunk.isNotBlank()) {
            val textValue = currentChunk.trim()
            chunks += SemanticChunk(
                text = textValue,
                keywords = semanticKeywords(textValue)
            )
        }
    }

    sentences.drop(1).forEach { sentence ->
        val sentenceKeywords = semanticKeywords(sentence)
        val closeByMeaning = jaccard(currentKeywords, sentenceKeywords) >= similarityThreshold
        val fitsBySize = currentChunk.length + 1 + sentence.length <= targetSize

        if (closeByMeaning && fitsBySize) {
            currentChunk += " $sentence"
            currentKeywords = semanticKeywords(currentChunk)
        } else if (!fitsBySize && sentence.length > targetSize) {
            flush()
            sentence.chunked(targetSize).forEach { part ->
                chunks += SemanticChunk(
                    text = part,
                    keywords = semanticKeywords(part)
                )
            }
            currentChunk = ""
            currentKeywords = emptySet()
        } else {
            flush()
            currentChunk = sentence
            currentKeywords = sentenceKeywords
        }
    }

    if (currentChunk.isNotBlank()) {
        val textValue = currentChunk.trim()
        chunks += SemanticChunk(
            text = textValue,
            keywords = semanticKeywords(textValue)
        )
    }

    // Maximize chunk size without breaking semantic rule or size limit.
    // Merge adjacent semantic chunks while they remain close in meaning.
    var optimized = chunks.toList()
    var changed = true
    while (changed) {
        changed = false
        val merged = mutableListOf<SemanticChunk>()
        var index = 0

        while (index < optimized.size) {
            var current = optimized[index]
            while (index + 1 < optimized.size) {
                val next = optimized[index + 1]
                val combinedSize = current.text.length + 1 + next.text.length
                val closeByMeaning = jaccard(current.keywords, next.keywords) >= similarityThreshold
                if (combinedSize <= targetSize && closeByMeaning) {
                    val combinedText = "${current.text} ${next.text}".trim()
                    current = SemanticChunk(
                        text = combinedText,
                        keywords = semanticKeywords(combinedText)
                    )
                    index += 1
                    changed = true
                } else {
                    break
                }
            }
            merged += current
            index += 1
        }

        optimized = merged
    }

    return optimized.map { it.text }
}

private fun splitByStrategy(text: String, strategy: ChunkStrategy): List<String> = when (strategy) {
    ChunkStrategy.Fixed500 -> chunkFixed(text, size = 500)
    ChunkStrategy.Structured -> chunkStructured(text, targetSize = 500)
    ChunkStrategy.Semantic -> chunkSemantic(text, targetSize = 500, similarityThreshold = 0.2)
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
    var selectedChunkStrategy by remember { mutableStateOf(ChunkStrategy.Fixed500) }
    var modelsMenuExpanded by remember { mutableStateOf(false) }
    var strategyMenuExpanded by remember { mutableStateOf(false) }
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
                .verticalScroll(rememberScrollState())
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

            TextButton(onClick = { strategyMenuExpanded = true }, enabled = !isLoading) {
                Text("Chunk strategy: ${selectedChunkStrategy.label}")
            }
            DropdownMenu(
                expanded = strategyMenuExpanded,
                onDismissRequest = { strategyMenuExpanded = false }
            ) {
                ChunkStrategy.entries.forEach { strategy ->
                    DropdownMenuItem(
                        text = { Text(strategy.label) },
                        onClick = {
                            selectedChunkStrategy = strategy
                            strategyMenuExpanded = false
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

                    val chunks = splitByStrategy(trimmed, selectedChunkStrategy)
                    if (chunks.isEmpty()) {
                        resultText = "No chunks generated."
                        logText = "No chunks generated for selected strategy."
                        return@Button
                    }

                    val apiKey = readApiKey("DEEPSEEK_API_KEY")
                    if (apiKey.isBlank()) {
                        resultText = "Error: missing DEEPSEEK_API_KEY."
                        logText = "Missing DEEPSEEK_API_KEY"
                        return@Button
                    }

                    scope.launch {
                        isLoading = true
                        val allLogs = StringBuilder()
                        val allResults = StringBuilder()
                        allResults.appendLine("Strategy: ${selectedChunkStrategy.label}")
                        allResults.appendLine("Chunks: ${chunks.size}")
                        allResults.appendLine()

                        chunks.forEachIndexed { index, chunk ->
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
                                        content = chunk
                                    )
                                )
                            )
                            val requestJson = json.encodeToString(request)
                            allLogs.appendLine("CHUNK #${index + 1} REQUEST:")
                            allLogs.appendLine(requestJson)
                            allLogs.appendLine()

                            try {
                                val response = deepSeekApi.createChatCompletion(
                                    apiKey = apiKey,
                                    request = request
                                )
                                val responseJson = json.encodeToString(response)
                                val answerText = response.choices.firstOrNull()?.message?.content.orEmpty()

                                allResults.appendLine("Chunk #${index + 1} (${chunk.length} chars)")
                                allResults.appendLine(answerText)
                                allResults.appendLine()

                                allLogs.appendLine("CHUNK #${index + 1} RESPONSE:")
                                allLogs.appendLine(responseJson)
                                allLogs.appendLine()

                                println("Embedding chunk #${index + 1} request: $requestJson")
                                println("Embedding chunk #${index + 1} response: $responseJson")
                            } catch (e: Exception) {
                                val errorText = "Request failed for chunk #${index + 1}: ${e.message ?: "unknown error"}"
                                allResults.appendLine(errorText)
                                allResults.appendLine()
                                allLogs.appendLine("CHUNK #${index + 1} ERROR:")
                                allLogs.appendLine(errorText)
                                allLogs.appendLine()
                                println("Embedding chunk #${index + 1} error: $errorText")
                            }
                        }

                        resultText = allResults.toString().trim()
                        logText = allLogs.toString().trim()
                        isLoading = false
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
