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

private data class ProcessingFileItem(
    val id: Long,
    val source: String,
    val fileName: String,
    val state: FileProcessingState = FileProcessingState.Added,
    val progress: Float = 0f
)

private fun readApiKey(envVar: String): String {
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

private fun chunkFixed(text: String, size: Int = 2000): List<String> {
    if (text.isBlank()) return emptyList()
    return text.trim().chunked(size)
}

private fun chunkStructured(text: String, targetSize: Int = 2000): List<String> {
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
        mutableStateMapOf<ChunkStrategy, Boolean>().apply {
            ChunkStrategy.entries.forEach { put(it, true) }
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

        val chunksByStrategy = strategies.associateWith { splitByStrategy(text, it) }
        val totalChunks = chunksByStrategy.values.sumOf { it.size }.coerceAtLeast(1)
        var processedChunks = 0

        queries.deleteBySource(source = sourcePath)

        chunksByStrategy.forEach { (strategy, chunks) ->
            chunks.forEachIndexed { chunkIndex, chunkText ->
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
                        section = "full_text",
                        chunk_id = (chunkIndex + 1).toLong(),
                        strategy = strategy.dbValue,
                        chunk_text = chunkText,
                        embedding_json = embeddingJson,
                        created_at = System.currentTimeMillis()
                    )
                    val responseJson = json.encodeToString(response)
                    println("[${fileName}] ${strategy.dbValue} request: $requestJson")
                    println("[${fileName}] ${strategy.dbValue} response: $responseJson")
                } catch (e: Exception) {
                    val errorText = "[${fileName}] ${strategy.dbValue} chunk ${chunkIndex + 1}: ${e.message ?: "unknown error"}"
                    logText += "\n$errorText"
                    println(errorText)
                }

                processedChunks += 1
                val progress = (processedChunks.toFloat() / totalChunks.toFloat()).coerceIn(0f, 1f)
                updateFile(fileItem.id) { it.copy(progress = progress) }
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
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Стратегия: ${row.strategy}")
                            Text("Chunk ID: ${row.chunk_id}")
                            Text("Section: ${row.section}")
                            Text("Chunk: ${row.chunk_text.take(300)}")
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
                            onCheckedChange = { checked -> selectedStrategies[strategy] = checked },
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
                    enabled = files.isNotEmpty() && !isGlobalProcessing
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
