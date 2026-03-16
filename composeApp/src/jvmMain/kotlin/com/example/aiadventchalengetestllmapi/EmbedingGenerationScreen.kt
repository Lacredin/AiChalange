package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.embedinggenerationdb.EmbedingGenerationDatabaseDriverFactory
import com.example.aiadventchalengetestllmapi.embedinggenerationdb.createEmbedingGenerationDatabase
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private data class EmbeddingApiModel(
    val apiLabel: String,
    val modelLabel: String
)

private enum class ChunkStrategy(val dbValue: String, val label: String) {
    Fixed500("fixed_2000", "Фиксированная (2000)"),
    Structured("structured", "По структуре текста"),
    Semantic("semantic", "Семантическая")
}

private enum class FileProcessingState(val label: String) {
    Added("Добавлен"),
    Queued("В очереди"),
    Processing("Идёт обработка"),
    Completed("Обработка завершена")
}

private const val EMBEDDING_PREFS_NODE = "com.example.aiadventchalengetestllmapi.embedinggeneration"
private const val SELECTED_STRATEGIES_KEY = "selected_chunk_strategies"

private data class ProcessingFileItem(
    val id: Long,
    val source: String,
    val fileName: String,
    val state: FileProcessingState = FileProcessingState.Added,
    val progress: Float = 0f
)

private data class TextSection(
    val title: String,
    val text: String
)

private fun readApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

private fun loadSelectedStrategies(): Set<ChunkStrategy> {
    val prefs = Preferences.userRoot().node(EMBEDDING_PREFS_NODE)
    val raw = prefs.get(SELECTED_STRATEGIES_KEY, "").trim()
    if (raw.isEmpty()) return emptySet()
    val names = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    return ChunkStrategy.entries.filterTo(mutableSetOf()) { it.name in names }
}

private fun saveSelectedStrategies(strategies: Set<ChunkStrategy>) {
    val prefs = Preferences.userRoot().node(EMBEDDING_PREFS_NODE)
    val value = strategies.joinToString(",") { it.name }
    prefs.put(SELECTED_STRATEGIES_KEY, value)
}

private fun chunkFixed(text: String, size: Int = 2000): List<String> {
    if (text.isBlank()) return emptyList()
    return text.trim().chunked(size)
}

private data class StructuredBlock(
    val type: String,
    val text: String
)

private fun parseStructuredBlocks(text: String): List<StructuredBlock> {
    val lines = text.lines()
    if (lines.isEmpty()) return emptyList()

    val blocks = mutableListOf<StructuredBlock>()
    val paragraph = StringBuilder()
    var inCodeFence = false
    val codeFence = StringBuilder()

    fun flushParagraph() {
        val content = paragraph.toString().trim()
        if (content.isNotEmpty()) {
            blocks += StructuredBlock(type = "paragraph", text = content)
        }
        paragraph.clear()
    }

    fun flushCodeFence() {
        val content = codeFence.toString().trim()
        if (content.isNotEmpty()) {
            blocks += StructuredBlock(type = "code", text = content)
        }
        codeFence.clear()
    }

    lines.forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        val isHeading = isHeadingLine(trimmed)
        val isList = trimmed.startsWith("- ") ||
            trimmed.startsWith("* ") ||
            trimmed.matches(Regex("^\\d+[.)]\\s+.+$"))
        val isFence = trimmed.startsWith("```")

        if (isFence) {
            flushParagraph()
            inCodeFence = !inCodeFence
            codeFence.appendLine(line)
            if (!inCodeFence) {
                flushCodeFence()
            }
            return@forEach
        }

        if (inCodeFence) {
            codeFence.appendLine(line)
            return@forEach
        }

        if (trimmed.isEmpty()) {
            flushParagraph()
            return@forEach
        }

        if (isHeading) {
            flushParagraph()
            blocks += StructuredBlock(type = "heading", text = trimmed)
            return@forEach
        }

        if (isList) {
            flushParagraph()
            blocks += StructuredBlock(type = "list", text = trimmed)
            return@forEach
        }

        if (paragraph.isNotEmpty()) paragraph.appendLine()
        paragraph.append(trimmed)
    }

    flushParagraph()
    flushCodeFence()
    return blocks
}

private fun splitLongTextBySentences(text: String, hardLimit: Int): List<String> {
    if (text.length <= hardLimit) return listOf(text)
    val sentences = text
        .split(Regex("(?<=[.!?])\\s+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return text.chunked(hardLimit)

    val parts = mutableListOf<String>()
    val current = StringBuilder()
    fun flush() {
        val value = current.toString().trim()
        if (value.isNotEmpty()) parts += value
        current.clear()
    }
    sentences.forEach { sentence ->
        if (sentence.length > hardLimit) {
            flush()
            parts += sentence.chunked(hardLimit)
            return@forEach
        }
        if (current.isEmpty()) {
            current.append(sentence)
        } else if (current.length + 1 + sentence.length <= hardLimit) {
            current.append(" ").append(sentence)
        } else {
            flush()
            current.append(sentence)
        }
    }
    flush()
    return parts
}

private fun chunkStructured(
    text: String,
    targetSize: Int = 2000,
    softLimit: Int = 1600,
    overlapChars: Int = 220
): List<String> {
    if (text.isBlank()) return emptyList()
    val hardLimit = targetSize
    val blocks = parseStructuredBlocks(text).flatMap { block ->
        splitLongTextBySentences(block.text, hardLimit).map { part ->
            StructuredBlock(type = block.type, text = part)
        }
    }
    if (blocks.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    var current = StringBuilder()
    var currentKeywords = emptySet<String>()

    fun chunkTailForOverlap(value: String): String {
        val paragraphs = value
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val tail = paragraphs.lastOrNull().orEmpty()
        if (tail.length <= overlapChars) return tail
        return tail.takeLast(overlapChars)
    }

    fun flush() {
        val content = current.toString().trim()
        if (content.isNotEmpty()) {
            chunks += content
        }
        current.clear()
        currentKeywords = emptySet()
    }

    blocks.forEach { block ->
        val blockText = block.text.trim()
        if (blockText.isEmpty()) return@forEach

        if (current.isEmpty()) {
            current.append(blockText)
            currentKeywords = semanticKeywords(current.toString())
            return@forEach
        }

        val separator = if (block.type == "heading") "\n\n" else "\n"
        val candidateSize = current.length + separator.length + blockText.length
        val blockKeywords = semanticKeywords(blockText)
        val closeByMeaning = jaccard(currentKeywords, blockKeywords) >= 0.12

        val shouldAttach = when {
            candidateSize > hardLimit -> false
            current.length < softLimit -> true
            else -> closeByMeaning
        }

        if (shouldAttach) {
            current.append(separator).append(blockText)
            currentKeywords = semanticKeywords(current.toString())
        } else {
            val previousChunk = current.toString()
            flush()
            val overlap = chunkTailForOverlap(previousChunk)
            if (overlap.isNotBlank()) {
                current.append(overlap).append("\n\n")
            }
            current.append(blockText)
            currentKeywords = semanticKeywords(current.toString())
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

private fun chunkSemantic(text: String, targetSize: Int = 2000, similarityThreshold: Double = 0.2): List<String> {
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
            chunks += SemanticChunk(text = textValue, keywords = semanticKeywords(textValue))
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
                chunks += SemanticChunk(text = part, keywords = semanticKeywords(part))
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
        chunks += SemanticChunk(text = textValue, keywords = semanticKeywords(textValue))
    }

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
                    current = SemanticChunk(combinedText, semanticKeywords(combinedText))
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
    ChunkStrategy.Fixed500 -> chunkFixed(text, size = 2000)
    ChunkStrategy.Structured -> chunkStructured(text, targetSize = 2000)
    ChunkStrategy.Semantic -> chunkSemantic(text, targetSize = 2000, similarityThreshold = 0.2)
}

private fun isHeadingLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    if (trimmed.startsWith("#")) return true
    if (trimmed.matches(Regex("^\\d+(\\.\\d+)*[.)]?\\s+.+$"))) return true
    return false
}

private fun extractSections(text: String): List<TextSection> {
    if (text.isBlank()) return emptyList()

    val lines = text.lines()
    val sections = mutableListOf<TextSection>()
    var currentTitle = "Без секции"
    val currentBody = StringBuilder()

    fun flush() {
        val body = currentBody.toString().trim()
        if (body.isNotEmpty()) {
            sections += TextSection(title = currentTitle, text = body)
        }
        currentBody.clear()
    }

    lines.forEach { line ->
        if (isHeadingLine(line)) {
            flush()
            currentTitle = line.trim().removePrefix("#").trim().ifEmpty { "Без секции" }
        } else {
            currentBody.appendLine(line)
        }
    }
    flush()

    if (sections.isEmpty()) {
        return listOf(TextSection(title = "Без секции", text = text.trim()))
    }
    return sections
}

private suspend fun readTextFromFile(path: String): String = withContext(Dispatchers.IO) {
    val file = File(path)
    when (file.extension.lowercase()) {
        "txt", "md", "csv", "json", "xml", "log", "kt", "java" -> file.readText()
        "pdf" -> {
            Loader.loadPDF(file).use { doc ->
                PDFTextStripper().getText(doc)
            }
        }
        else -> file.readText()
    }
}

private fun openFilesDialog(): List<File> {
    val chooser = JFileChooser().apply {
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter(
            "Text and PDF files",
            "txt", "md", "csv", "json", "xml", "log", "pdf"
        )
    }
    val result = chooser.showOpenDialog(null)
    if (result != JFileChooser.APPROVE_OPTION) return emptyList()
    return chooser.selectedFiles.toList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmbedingGenerationScreen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deepSeekApi = remember { DeepSeekApi() }
    val database = remember { createEmbedingGenerationDatabase(EmbedingGenerationDatabaseDriverFactory()) }
    val queries = remember(database) { database.embeddingChunksQueries }
    val json = remember { Json { prettyPrint = true; encodeDefaults = true } }

    val supportedModels = remember {
        listOf(
            EmbeddingApiModel(
                apiLabel = "DeepSeek API",
                modelLabel = "deepseek-chat"
            )
        )
    }

    val files = remember { mutableStateListOf<ProcessingFileItem>() }
    val selectedFileIds = remember { mutableStateMapOf<Long, Boolean>() }
    val selectedStrategies = remember {
        val restored = loadSelectedStrategies()
        mutableStateMapOf<ChunkStrategy, Boolean>().apply {
            ChunkStrategy.entries.forEach { put(it, it in restored) }
        }
    }

    var selectedModel by remember { mutableStateOf(supportedModels.first()) }
    var screensMenuExpanded by remember { mutableStateOf(false) }
    var openedFileSource by remember { mutableStateOf<String?>(null) }
    var openedFileStrategyFilter by remember { mutableStateOf("all") }
    var openedFileStrategyMenuExpanded by remember { mutableStateOf(false) }
    var dbReloadCounter by remember { mutableIntStateOf(0) }
    var isGlobalProcessing by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("Логи обработки появятся здесь.") }
    val hasActiveStrategies = selectedStrategies.values.any { it }

    fun updateFile(id: Long, transform: (ProcessingFileItem) -> ProcessingFileItem) {
        val index = files.indexOfFirst { it.id == id }
        if (index >= 0) {
            files[index] = transform(files[index])
        }
    }

    val openedFileRecords = remember(openedFileSource, dbReloadCounter) {
        openedFileSource?.let { source ->
            queries.selectBySource(source = source).executeAsList()
        }.orEmpty()
    }
    val openedFileStrategies = remember(openedFileRecords) {
        openedFileRecords.map { it.strategy }.distinct()
    }
    val filteredOpenedFileRecords = remember(openedFileRecords, openedFileStrategyFilter) {
        if (openedFileStrategyFilter == "all") openedFileRecords
        else openedFileRecords.filter { it.strategy == openedFileStrategyFilter }
    }

    suspend fun processSingleFile(fileItem: ProcessingFileItem, strategies: List<ChunkStrategy>, apiKey: String) {
        updateFile(fileItem.id) { it.copy(state = FileProcessingState.Processing, progress = 0f) }
        val sourcePath = fileItem.source
        val fileName = fileItem.fileName

        val text = runCatching { readTextFromFile(sourcePath) }.getOrElse { error ->
            updateFile(fileItem.id) { it.copy(state = FileProcessingState.Added, progress = 0f) }
            logText += "\n[${fileName}] Ошибка чтения: ${error.message}"
            return
        }.trim()

        if (text.isBlank()) {
            updateFile(fileItem.id) { it.copy(state = FileProcessingState.Added, progress = 0f) }
            logText += "\n[${fileName}] Пустой текст после чтения."
            return
        }

        val sections = extractSections(text)
        val chunksByStrategyAndSection = strategies.associateWith { strategy ->
            sections.map { section ->
                section to splitByStrategy(section.text, strategy)
            }
        }
        val totalChunks = chunksByStrategyAndSection.values
            .sumOf { sectionPairs -> sectionPairs.sumOf { it.second.size } }
            .coerceAtLeast(1)
        var processedChunks = 0

        queries.deleteBySource(source = sourcePath)

        chunksByStrategyAndSection.forEach { (strategy, sectionPairs) ->
            var strategyChunkId = 1L
            sectionPairs.forEach { (section, chunks) ->
                chunks.forEach { chunkText ->
                val request = DeepSeekChatRequest(
                    model = selectedModel.modelLabel,
                    temperature = 0.0,
                    messages = listOf(
                        DeepSeekMessage(
                            role = "system",
                            content = "Return valid JSON only: {\"embedding\":[float,...],\"dimensions\":64}. Build semantic vector for user text. Exactly 64 float values."
                        ),
                        DeepSeekMessage(role = "user", content = chunkText)
                    )
                )
                val requestJson = json.encodeToString(request)
                try {
                    val response = deepSeekApi.createChatCompletion(apiKey = apiKey, request = request)
                    val embeddingJson = response.choices.firstOrNull()?.message?.content.orEmpty()
                    queries.insertChunk(
                        source = sourcePath,
                        title = fileName,
                        section = section.title,
                        chunk_id = strategyChunkId,
                        strategy = strategy.dbValue,
                        chunk_text = chunkText,
                        embedding_json = embeddingJson,
                        created_at = System.currentTimeMillis()
                    )
                    strategyChunkId += 1L
                    val responseJson = json.encodeToString(response)
                    println("[${fileName}] ${strategy.dbValue} request: $requestJson")
                    println("[${fileName}] ${strategy.dbValue} response: $responseJson")
                } catch (e: Exception) {
                    val errorText = "[${fileName}] ${strategy.dbValue} section '${section.title}': ${e.message ?: "unknown error"}"
                    logText += "\n$errorText"
                    println(errorText)
                }

                processedChunks += 1
                val progress = (processedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
                updateFile(fileItem.id) { it.copy(progress = progress) }
            }
            }
        }

        updateFile(fileItem.id) { it.copy(state = FileProcessingState.Completed, progress = 1f) }
        dbReloadCounter += 1
    }

    if (openedFileSource != null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Данные по файлу") },
                    navigationIcon = {
                        TextButton(onClick = { openedFileSource = null }) {
                            Text("Назад")
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text("Source: $openedFileSource")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Записей: ${filteredOpenedFileRecords.size}")
                        Box {
                            TextButton(onClick = { openedFileStrategyMenuExpanded = true }) {
                                Text(
                                    if (openedFileStrategyFilter == "all") {
                                        "Стратегия: Все"
                                    } else {
                                        "Стратегия: $openedFileStrategyFilter"
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = openedFileStrategyMenuExpanded,
                                onDismissRequest = { openedFileStrategyMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Все") },
                                    onClick = {
                                        openedFileStrategyFilter = "all"
                                        openedFileStrategyMenuExpanded = false
                                    }
                                )
                                openedFileStrategies.forEach { strategy ->
                                    DropdownMenuItem(
                                        text = { Text(strategy) },
                                        onClick = {
                                            openedFileStrategyFilter = strategy
                                            openedFileStrategyMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                items(filteredOpenedFileRecords) { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Стратегия: ${row.strategy}")
                            Text("Chunk ID: ${row.chunk_id}")
                            Text("Section: ${row.section}")
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Чанк")
                                SelectionContainer {
                                    Text(row.chunk_text)
                                }
                            }
                            Text("Embedding: ${row.embedding_json.take(300)}")
                        }
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EmbedingGeneration") },
                actions = {
                    TextButton(onClick = { screensMenuExpanded = true }, enabled = !isGlobalProcessing) {
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val selected = openFilesDialog()
                        if (selected.isEmpty()) return@Button
                        val existing = files.map { it.source }.toSet()
                        selected
                            .filter { it.exists() && it.path !in existing }
                            .forEach { file ->
                                val id = System.nanoTime() + files.size
                                files += ProcessingFileItem(
                                    id = id,
                                    source = file.absolutePath,
                                    fileName = file.name
                                )
                                selectedFileIds[id] = true
                            }
                    },
                    enabled = !isGlobalProcessing
                ) {
                    Text("Выбрать файлы")
                }
                Text("Выбрано: ${files.size}")
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChunkStrategy.entries.forEach { strategy ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedStrategies[strategy] == true,
                            onCheckedChange = { checked ->
                                selectedStrategies[strategy] = checked
                                saveSelectedStrategies(
                                    selectedStrategies.filterValues { it }.keys
                                )
                            },
                            enabled = !isGlobalProcessing
                        )
                        Text(strategy.label)
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val activeStrategies = selectedStrategies.filterValues { it }.keys.toList()
                        if (activeStrategies.isEmpty()) {
                            logText += "\nВыберите хотя бы одну стратегию."
                            return@Button
                        }
                        if (files.isEmpty()) {
                            logText += "\nДобавьте файлы перед обработкой."
                            return@Button
                        }
                        val apiKey = readApiKey("DEEPSEEK_API_KEY")
                        if (apiKey.isBlank()) {
                            logText += "\nОтсутствует DEEPSEEK_API_KEY."
                            return@Button
                        }

                        isGlobalProcessing = true
                        files.forEach { file ->
                            updateFile(file.id) { it.copy(state = FileProcessingState.Queued, progress = 0f) }
                        }

                        scope.launch {
                            supervisorScope {
                                files.map { file ->
                                    async {
                                        processSingleFile(file, activeStrategies, apiKey)
                                    }
                                }.awaitAll()
                            }
                            isGlobalProcessing = false
                        }
                    },
                    enabled = files.isNotEmpty() && hasActiveStrategies && !isGlobalProcessing
                ) {
                    Text("Обработать")
                }

                Button(
                    onClick = {
                        val selectedIds = selectedFileIds.filterValues { it }.keys
                        files.removeAll { it.id in selectedIds }
                        selectedIds.forEach { selectedFileIds.remove(it) }
                    },
                    enabled = files.isNotEmpty() && !isGlobalProcessing
                ) {
                    Text("Удалить")
                }

                Button(
                    onClick = {
                        queries.deleteAll()
                        dbReloadCounter += 1
                        logText += "\nТаблица чанков очищена."
                    },
                    enabled = !isGlobalProcessing
                ) {
                    Text("Очистить БД")
                }

                Button(
                    onClick = {
                        files.clear()
                        selectedFileIds.clear()
                        queries.deleteAll()
                        dbReloadCounter += 1
                        logText += "\nФайлы и таблица чанков очищены."
                    },
                    enabled = !isGlobalProcessing
                ) {
                    Text("Очистить всё")
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 170.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (file.state == FileProcessingState.Completed) {
                                    openedFileSource = file.source
                                } else {
                                    selectedFileIds[file.id] = !(selectedFileIds[file.id] ?: false)
                                }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selectedFileIds[file.id] == true,
                                    onCheckedChange = { checked -> selectedFileIds[file.id] = checked },
                                    enabled = !isGlobalProcessing
                                )
                                Text(file.state.label)
                            }

                            when (file.state) {
                                FileProcessingState.Added -> Text("•")
                                FileProcessingState.Queued -> CircularProgressIndicator(modifier = Modifier.width(20.dp))
                                FileProcessingState.Processing -> LinearProgressIndicator(progress = { file.progress })
                                FileProcessingState.Completed -> Text("✓")
                            }

                            Text(file.fileName)
                        }
                    }
                }
            }

            Text("Лог:")
            Text(logText, style = MaterialTheme.typography.bodySmall)
        }
    }

    LaunchedEffect(Unit) {
        val processedBySource = queries.selectAll()
            .executeAsList()
            .groupBy { it.source }
        val existingSources = files.map { it.source }.toSet()
        processedBySource.forEach { (source, rows) ->
            if (source !in existingSources) {
                files += ProcessingFileItem(
                    id = System.nanoTime() + files.size,
                    source = source,
                    fileName = rows.firstOrNull()?.title ?: File(source).name,
                    state = FileProcessingState.Completed,
                    progress = 1f
                )
            }
        }
        dbReloadCounter += 1
    }
}
