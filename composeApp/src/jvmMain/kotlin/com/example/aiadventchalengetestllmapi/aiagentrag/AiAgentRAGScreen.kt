package com.example.aiadventchalengetestllmapi.aiagentrag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.example.aiadventchalengetestllmapi.network.LocalLlmApi
import com.example.aiadventchalengetestllmapi.network.OpenAiApi
import com.example.aiadventchalengetestllmapi.network.ProxyOpenAiApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.prefs.Preferences

private enum class RagApi(
    val label: String,
    val envVar: String,
    val defaultModel: String,
    val supportedModels: List<String>
) {
    DeepSeek("DeepSeek", "DEEPSEEK_API_KEY", "deepseek-chat", listOf("deepseek-chat", "deepseek-reasoner")),
    OpenAI("OpenAI", "OPENAI_API_KEY", "gpt-4o-mini", listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1", "o3-mini")),
    GigaChat("GigaChat", "GIGACHAT_ACCESS_TOKEN", "GigaChat-2", listOf("GigaChat-2", "GigaChat-2-Pro", "GigaChat-2-Max")),
    ProxyOpenAI(
        "ProxyAPI (OpenAI)",
        "PROXYAPI_API_KEY",
        "openai/gpt-4o-mini",
        listOf(
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
    ),
    LocalLlm(
        "Локальная LLM",
        "LOCAL_LLM_API_KEY",
        "llama3.1:8b",
        listOf("llama3.1:8b", "gemma2:2b", "qwen2.5:7b")
    )
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
    val sources: List<RetrievedChunk> = emptyList(),
    val retrievalInfo: String? = null
)

private data class RetrievalConfig(
    val api: RagApi,
    val model: String,
    val useRag: Boolean,
    val useFilter: Boolean,
    val useRewrite: Boolean,
    val topKBefore: Int,
    val topKAfter: Int,
    val threshold: Double
)

private data class RetrievalOutput(
    val requestMessages: List<DeepSeekMessage>,
    val selectedChunks: List<RetrievedChunk>,
    val retrievalInfo: String?
)

private data class AssistantResult(
    val answer: String,
    val paramsInfo: String,
    val sources: List<RetrievedChunk>,
    val retrievalInfo: String?
)

private data class RagTaskState(
    val goal: String = "",
    val clarifiedPoints: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val terms: List<String> = emptyList()
)

private const val PREF_NODE = "com.example.aiadventchalengetestllmapi.aiagentrag"
private const val USE_RAG_KEY = "use_rag_enabled"
private const val DEFAULT_TOP_K_BEFORE = 12
private const val DEFAULT_TOP_K_AFTER = 5
private const val DEFAULT_MIN_SCORE = 0.30
private const val RAG_HISTORY_LIMIT = 10
private const val TASK_STATE_ITEMS_LIMIT = 14
private val ragJson = Json { ignoreUnknownKeys = true }

private fun ragReadApiKey(envVar: String): String {
    if (envVar == "LOCAL_LLM_API_KEY") return "local-llm"
    val fromBuildSecrets = BuildSecrets.apiKeyFor(envVar).trim()
    if (fromBuildSecrets.isNotEmpty()) return fromBuildSecrets
    return System.getenv(envVar)?.trim().orEmpty()
}

private fun loadUseRagState(): Boolean = Preferences.userRoot().node(PREF_NODE).getBoolean(USE_RAG_KEY, true)
private fun saveUseRagState(enabled: Boolean) = Preferences.userRoot().node(PREF_NODE).putBoolean(USE_RAG_KEY, enabled)

private fun taskStateSettingKey(chatId: Long): String = "rag_task_state_chat_$chatId"

private fun normalizeTaskItem(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim().trim('\"', '\'', '.', ',', ';', ':')

private fun mergeDistinct(base: List<String>, additions: List<String>, limit: Int = TASK_STATE_ITEMS_LIMIT): List<String> {
    val result = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    (base + additions)
        .map(::normalizeTaskItem)
        .filter { it.length >= 3 }
        .forEach { item ->
            val key = item.lowercase()
            if (key !in seen) {
                seen += key
                result += item
            }
        }
    return result.take(limit)
}

private fun extractQuotedTerms(message: String): List<String> {
    val quoteRegex = Regex("[\"“”«»'`]{1}([^\"“”«»'`]{2,80})[\"“”«»'`]{1}")
    val quoted = quoteRegex.findAll(message).mapNotNull { it.groupValues.getOrNull(1) }.toList()
    val termMarkerRegex = Regex("(?i)\\bтермин(?:ы)?\\b\\s*[:\\-]?\\s*([^\\n.]{2,120})")
    val marked = termMarkerRegex.findAll(message).mapNotNull { it.groupValues.getOrNull(1) }.toList()
    val tokenized = message.split(Regex("[\\s,;()\\[\\]{}]+"))
        .filter { token -> token.length in 3..40 && (token.contains("_") || token.contains("-")) }
    return mergeDistinct(emptyList(), quoted + marked + tokenized, limit = TASK_STATE_ITEMS_LIMIT)
}

private fun extractConstraintCandidates(message: String): List<String> {
    val cues = listOf(
        "не ", "нельзя", "без ", "только", "обязательно", "огранич", "формат",
        "срок", "максимум", "минимум", "должен", "нужно", "не трогай", "не удаляй"
    )
    val sentences = message.split(Regex("[\\n.!?]+")).map(::normalizeTaskItem).filter { it.isNotBlank() }
    return sentences.filter { sentence ->
        val low = sentence.lowercase()
        cues.any { low.contains(it) }
    }.take(TASK_STATE_ITEMS_LIMIT)
}

private fun extractClarifiedPoints(message: String): List<String> {
    val parts = message.split(Regex("[\\n.!?]+"))
        .map(::normalizeTaskItem)
        .filter { it.length >= 8 }
    return parts.take(4)
}

private fun extractGoalCandidate(currentGoal: String, message: String): String {
    val explicitGoal = Regex("(?i)\\b(цель|задача|хочу|нужно|надо)\\b\\s*[:\\-]?\\s*(.+)")
        .find(message)
        ?.groupValues
        ?.getOrNull(2)
        ?.let(::normalizeTaskItem)
        ?.takeIf { it.length >= 8 }
    if (!explicitGoal.isNullOrBlank()) return explicitGoal.take(240)
    if (currentGoal.isNotBlank()) return currentGoal
    return message.split(Regex("[\\n.!?]+"))
        .map(::normalizeTaskItem)
        .firstOrNull { it.length >= 8 }
        ?.take(240)
        .orEmpty()
}

private fun updateTaskStateFromUserMessage(current: RagTaskState, userMessage: String): RagTaskState {
    val goal = extractGoalCandidate(current.goal, userMessage)
    val clarified = mergeDistinct(current.clarifiedPoints, extractClarifiedPoints(userMessage))
    val constraints = mergeDistinct(current.constraints, extractConstraintCandidates(userMessage))
    val terms = mergeDistinct(current.terms, extractQuotedTerms(userMessage))
    return RagTaskState(goal = goal, clarifiedPoints = clarified, constraints = constraints, terms = terms)
}

private fun taskStateToJson(state: RagTaskState): String =
    JsonObject(
        mapOf(
            "goal" to JsonPrimitive(state.goal),
            "clarified_points" to JsonArray(state.clarifiedPoints.map { JsonPrimitive(it) }),
            "constraints" to JsonArray(state.constraints.map { JsonPrimitive(it) }),
            "terms" to JsonArray(state.terms.map { JsonPrimitive(it) })
        )
    ).toString()

private fun jsonArrayStrings(obj: JsonObject, key: String): List<String> =
    (obj[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?.map(::normalizeTaskItem)
        ?.filter { it.isNotBlank() }
        .orEmpty()

private fun taskStateFromJson(raw: String): RagTaskState {
    val root = runCatching { ragJson.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return RagTaskState()
    val goal = (root["goal"] as? JsonPrimitive)?.contentOrNull?.let(::normalizeTaskItem).orEmpty()
    return RagTaskState(
        goal = goal,
        clarifiedPoints = jsonArrayStrings(root, "clarified_points"),
        constraints = jsonArrayStrings(root, "constraints"),
        terms = jsonArrayStrings(root, "terms")
    )
}

private fun buildDialogHistory(conversation: List<RagMessage>, limit: Int = RAG_HISTORY_LIMIT): String {
    if (conversation.isEmpty()) return "нет сообщений"
    return conversation.takeLast(limit).joinToString("\n") { message ->
        val role = if (message.isUser) "Пользователь" else "Ассистент"
        val text = message.text.replace(Regex("\\s+"), " ").trim().take(360)
        "$role: $text"
    }
}

private fun buildTaskStateBlock(taskState: RagTaskState): String = buildString {
    appendLine("goal: ${if (taskState.goal.isBlank()) "не зафиксирована" else taskState.goal}")
    appendLine("clarified_points:")
    if (taskState.clarifiedPoints.isEmpty()) appendLine("- нет")
    taskState.clarifiedPoints.forEach { appendLine("- $it") }
    appendLine("constraints:")
    if (taskState.constraints.isEmpty()) appendLine("- нет")
    taskState.constraints.forEach { appendLine("- $it") }
    appendLine("terms:")
    if (taskState.terms.isEmpty()) appendLine("- нет")
    taskState.terms.forEach { appendLine("- $it") }
}.trimEnd()

private fun sourceLabel(index: Int): String = "[S${index + 1}]"

private fun normalizeQuote(raw: String): String {
    val oneLine = raw.replace(Regex("\\s+"), " ").trim()
    return if (oneLine.length <= 220) oneLine else "${oneLine.take(217)}..."
}

private fun buildSourcesBlock(sources: List<RetrievedChunk>): String {
    if (sources.isEmpty()) return "- нет релевантных источников"
    return sources.mapIndexed { index, src ->
        "${sourceLabel(index)} ${src.title} | ${src.section} | ${src.source}"
    }.joinToString("\n")
}

private fun buildQuotesBlock(sources: List<RetrievedChunk>): String {
    if (sources.isEmpty()) return "- нет релевантных цитат"
    return sources.mapIndexed { index, src ->
        "- ${sourceLabel(index)} \"${normalizeQuote(src.chunkText)}\""
    }.joinToString("\n")
}

private fun ensureStrictAnswerFormat(rawAnswer: String, sources: List<RetrievedChunk>): String {
    val trimmed = rawAnswer.trim().ifBlank { "Недостаточно данных для ответа." }
    val answerPart = if (trimmed.startsWith("Ответ:")) trimmed else "Ответ: $trimmed"
    return buildString {
        append(answerPart)
        append("\n\nИсточники:\n")
        append(buildSourcesBlock(sources))
        append("\n\nЦитаты:\n")
        append(buildQuotesBlock(sources))
    }.trim()
}

private fun tokenizeForOverlapV2(text: String): Set<String> =
    text.lowercase()
        .split(Regex("[^\\p{L}\\p{N}_-]+"))
        .filter { it.length >= 4 }
        .toSet()

private fun extractUsedSourceIndicesV2(rawAnswer: String, sources: List<RetrievedChunk>): List<Int> {
    if (sources.isEmpty()) return emptyList()
    val explicitByTag = Regex("\\[S(\\d+)]")
        .findAll(rawAnswer)
        .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull()?.minus(1) }
        .filter { it in sources.indices }
        .distinct()
        .toList()
    if (explicitByTag.isNotEmpty()) return explicitByTag

    val answerOnly = rawAnswer
        .substringBefore("\nИсточники:", missingDelimiterValue = rawAnswer)
        .substringBefore("\nЦитаты:", missingDelimiterValue = rawAnswer)
    val answerTokens = tokenizeForOverlapV2(answerOnly)
    if (answerTokens.isEmpty()) return emptyList()

    return sources.mapIndexed { index, source ->
        val sourceTokens = tokenizeForOverlapV2("${source.title} ${source.section} ${source.chunkText}")
        index to answerTokens.intersect(sourceTokens).size
    }.filter { (_, overlap) ->
        overlap >= 2
    }.sortedByDescending { (_, overlap) ->
        overlap
    }.take(4).map { (index, _) ->
        index
    }
}

private fun buildSourcesWithInlineQuotesV2(sources: List<RetrievedChunk>, usedIndices: List<Int>): String {
    if (sources.isEmpty() || usedIndices.isEmpty()) return "- нет релевантных источников"
    return usedIndices.map { index ->
        val src = sources[index]
        "- ${sourceLabel(index)} ${src.title} | ${src.section} | ${src.source}\n  Цитата: \"${normalizeQuote(src.chunkText)}\""
    }.joinToString("\n")
}

private fun ensureStrictAnswerFormatV2(rawAnswer: String, sources: List<RetrievedChunk>): String {
    val trimmed = rawAnswer.trim().ifBlank { "Недостаточно данных для ответа." }
    val answerCore = trimmed
        .substringBefore("\nИсточники:", missingDelimiterValue = trimmed)
        .substringBefore("\nЦитаты:", missingDelimiterValue = trimmed)
        .trim()
    val answerPart = if (answerCore.startsWith("Ответ:")) answerCore else "Ответ: $answerCore"
    val usedIndices = extractUsedSourceIndicesV2(rawAnswer, sources)
    return buildString {
        append(answerPart)
        append("\n\nИсточники:\n")
        append(buildSourcesWithInlineQuotesV2(sources, usedIndices))
    }.trim()
}

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

private fun heuristicRewriteQuery(original: String): String {
    val normalized = original.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (normalized.isEmpty()) return original
    val keywords = normalized.split(" ")
        .filter { it.length >= 4 }
        .distinct()
        .take(16)
    return if (keywords.isEmpty()) original else keywords.joinToString(" ")
}

private fun readPositiveInt(raw: String, fallback: Int): Int = raw.toIntOrNull()?.coerceAtLeast(1) ?: fallback
private fun readThreshold(raw: String, fallback: Double): Double = raw.toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: fallback

private fun userParams(config: RetrievalConfig): String =
    "api=${config.api.label} | model=${config.model} | rag=${if (config.useRag) "on" else "off"} | filter=${if (config.useFilter) "on" else "off"} | rewrite=${if (config.useRewrite) "on" else "off"}"

private fun assistantParams(config: RetrievalConfig): String =
    "api=${config.api.label} | model=${config.model} | rag=${if (config.useRag) "on" else "off"} | filter=${if (config.useFilter) "on" else "off"} | rewrite=${if (config.useRewrite) "on" else "off"} | kBefore=${config.topKBefore} | kAfter=${config.topKAfter} | threshold=${"%.2f".format(config.threshold)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentRAGScreen(currentScreen: RootScreen, onSelectScreen: (RootScreen) -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1D4ED8),
            background = Color(0xFFF8FAFC),
            surface = Color.White
        )
    ) {
        val scope = rememberCoroutineScope()
        val deepSeekApi = remember { DeepSeekApi() }
        val openAiApi = remember { OpenAiApi() }
        val gigaChatApi = remember { GigaChatApi() }
        val proxyOpenAiApi = remember { ProxyOpenAiApi() }
        val localLlmApi = remember { LocalLlmApi() }
        val ragDb = remember { createAiAgentRagDatabase(AiAgentRagDatabaseDriverFactory()) }
        val ragQueries = remember(ragDb) { ragDb.chatHistoryQueries }
        val embDb = remember { createEmbedingGenerationDatabase(EmbedingGenerationDatabaseDriverFactory()) }
        val embQueries = remember(embDb) { embDb.embeddingChunksQueries }

        val chats = remember { mutableStateListOf<RagChatItem>() }
        val messages = remember { mutableStateListOf<RagMessage>() }
        val comparisonMessages = remember { mutableStateListOf<RagMessage>() }
        val listState = rememberLazyListState()
        val comparisonListState = rememberLazyListState()
        var activeChatId by remember { mutableStateOf<Long?>(null) }
        var inputText by remember { mutableStateOf("") }
        var selectedApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var comparisonApi by remember { mutableStateOf(RagApi.DeepSeek) }
        var apiMenuExpanded by remember { mutableStateOf(false) }
        var comparisonApiMenuExpanded by remember { mutableStateOf(false) }
        var localModel by remember { mutableStateOf(RagApi.LocalLlm.defaultModel) }
        var comparisonLocalModel by remember { mutableStateOf(RagApi.LocalLlm.defaultModel) }
        var localModelMenuExpanded by remember { mutableStateOf(false) }
        var comparisonLocalModelMenuExpanded by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var useRag by remember { mutableStateOf(loadUseRagState()) }
        var comparisonUseRag by remember { mutableStateOf(true) }
        var useFilter by remember { mutableStateOf(true) }
        var comparisonUseFilter by remember { mutableStateOf(false) }
        var useRewrite by remember { mutableStateOf(false) }
        var comparisonUseRewrite by remember { mutableStateOf(false) }
        var topKBeforeRaw by remember { mutableStateOf(DEFAULT_TOP_K_BEFORE.toString()) }
        var topKAfterRaw by remember { mutableStateOf(DEFAULT_TOP_K_AFTER.toString()) }
        var thresholdRaw by remember { mutableStateOf("%.2f".format(DEFAULT_MIN_SCORE)) }
        var comparisonTopKBeforeRaw by remember { mutableStateOf(DEFAULT_TOP_K_BEFORE.toString()) }
        var comparisonTopKAfterRaw by remember { mutableStateOf(DEFAULT_TOP_K_AFTER.toString()) }
        var comparisonThresholdRaw by remember { mutableStateOf("%.2f".format(DEFAULT_MIN_SCORE)) }
        var dualChatEnabled by remember { mutableStateOf(false) }
        var screensMenuExpanded by remember { mutableStateOf(false) }
        var nextMessageId by remember { mutableStateOf(1L) }
        var selectedSourcesMessageId by remember { mutableStateOf<Long?>(null) }
        var comparisonSelectedSourcesMessageId by remember { mutableStateOf<Long?>(null) }
        var taskState by remember { mutableStateOf(RagTaskState()) }

        fun loadTaskState(chatId: Long): RagTaskState {
            val raw = ragQueries.selectAppSettingByKey(setting_key = taskStateSettingKey(chatId))
                .executeAsOneOrNull()
                ?.setting_value
                .orEmpty()
            if (raw.isBlank()) return RagTaskState()
            return taskStateFromJson(raw)
        }

        fun saveTaskState(chatId: Long, value: RagTaskState) {
            ragQueries.upsertAppSetting(
                setting_key = taskStateSettingKey(chatId),
                setting_value = taskStateToJson(value)
            )
        }

        fun reloadChats() {
            chats.clear()
            chats.addAll(ragQueries.selectChats().executeAsList().map { RagChatItem(it.id, it.title) })
        }

        fun loadMessages(chatId: Long) {
            messages.clear()
            messages.addAll(
                ragQueries.selectMessagesByChat(chat_id = chatId).executeAsList().mapIndexed { i, row ->
                    RagMessage(
                        id = (i + 1).toLong(),
                        text = row.message,
                        isUser = row.role == "user",
                        paramsInfo = row.params_info
                    )
                }
            )
            comparisonMessages.clear()
            nextMessageId = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
            selectedSourcesMessageId = null
            comparisonSelectedSourcesMessageId = null
            taskState = loadTaskState(chatId)
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
            ragQueries.deleteAppSettingByKey(setting_key = taskStateSettingKey(chatId))
            ragQueries.deleteChatById(chatId)
            reloadChats()
            if (activeChatId == chatId) {
                if (chats.isEmpty()) {
                    activeChatId = null
                    messages.clear()
                    comparisonMessages.clear()
                    selectedSourcesMessageId = null
                    comparisonSelectedSourcesMessageId = null
                    taskState = RagTaskState()
                } else {
                    val fallbackChatId = chats.first().id
                    activeChatId = fallbackChatId
                    loadMessages(fallbackChatId)
                }
            }
        }

        fun deleteAllChats() {
            ragQueries.selectChats().executeAsList().forEach { chat ->
                ragQueries.deleteAppSettingByKey(setting_key = taskStateSettingKey(chat.id))
            }
            ragQueries.deleteAllBranchMessages()
            ragQueries.deleteAllMessages()
            ragQueries.deleteAllChats()
            chats.clear()
            messages.clear()
            comparisonMessages.clear()
            activeChatId = null
            selectedSourcesMessageId = null
            comparisonSelectedSourcesMessageId = null
            taskState = RagTaskState()
        }

        suspend fun buildRetrievalOutput(
            question: String,
            conversation: List<RagMessage>,
            taskState: RagTaskState,
            config: RetrievalConfig
        ): RetrievalOutput {
            if (!config.useRag) return RetrievalOutput(emptyList(), emptyList(), null)
            val retrievalQuestion = if (config.useRewrite) heuristicRewriteQuery(question) else question
            val queryEmb = EmbeddingGeneratorStub.createEmbedding(retrievalQuestion).getOrElse { throw it }
            val allRanked = embQueries.selectAll().executeAsList().mapNotNull { row ->
                val emb = parseEmbeddingVector(row.embedding_json)
                if (emb.size != queryEmb.size) return@mapNotNull null
                val score = cosineSimilarity(queryEmb, emb)
                if (!score.isFinite()) return@mapNotNull null
                RetrievedChunk(row.source, row.title, row.section, row.chunk_id, row.strategy, row.chunk_text, score)
            }.sortedByDescending { it.score }

            val topBefore = allRanked.take(config.topKBefore)
            val afterFilter = if (config.useFilter) topBefore.filter { it.score >= config.threshold } else topBefore
            val topAfter = afterFilter.take(config.topKAfter)
            val context = if (topAfter.isEmpty()) {
                "Контекст не найден после фильтрации."
            } else {
                topAfter.mapIndexed { index, chunk ->
                    "${sourceLabel(index)} title=${chunk.title}\n" +
                        "section=${chunk.section}\n" +
                        "path=${chunk.source}\n" +
                        "chunk=${chunk.chunkText}"
                }.joinToString("\n\n")
            }
            val retrievalInfo = buildString {
                append("rewrite=${if (config.useRewrite) "on" else "off"}")
                append(" | filter=${if (config.useFilter) "on" else "off"}")
                append(" | threshold=${"%.2f".format(config.threshold)}")
                append(" | candidates=${allRanked.size}")
                append(" | topK-before=${topBefore.size}")
                append(" | after-filter=${afterFilter.size}")
                append(" | topK-after=${topAfter.size}")
                if (taskState.goal.isNotBlank()) append(" | goal=${taskState.goal.take(80)}")
                append(" | constraints=${taskState.constraints.size}")
                append(" | terms=${taskState.terms.size}")
            }
            val ragPrompt = AiAgentRagPrompts.buildUserPrompt(
                question = question,
                retrievalQuery = retrievalQuestion,
                context = if (topAfter.isEmpty()) AiAgentRagPrompts.EMPTY_CONTEXT else context,
                dialogHistory = buildDialogHistory(conversation),
                taskState = buildTaskStateBlock(taskState)
            )

            return RetrievalOutput(
                requestMessages = listOf(
                    DeepSeekMessage("system", AiAgentRagPrompts.SYSTEM),
                    DeepSeekMessage("user", ragPrompt)
                ),
                selectedChunks = topAfter,
                retrievalInfo = retrievalInfo
            )
        }

        suspend fun requestAssistant(
            question: String,
            conversation: List<RagMessage>,
            taskState: RagTaskState,
            config: RetrievalConfig
        ): AssistantResult {
            return try {
                val apiKey = ragReadApiKey(config.api.envVar)
                if (apiKey.isBlank()) error("Missing API key: ${config.api.envVar}")

                val retrieval = buildRetrievalOutput(question, conversation, taskState, config)
                if (config.useRag && retrieval.selectedChunks.isEmpty()) {
                    return AssistantResult(
                        answer = ensureStrictAnswerFormatV2("Недостаточно данных в переданном контексте для точного ответа.", emptyList()),
                        paramsInfo = assistantParams(config),
                        sources = emptyList(),
                        retrievalInfo = retrieval.retrievalInfo
                    )
                }
                val requestMessages = if (config.useRag) {
                    retrieval.requestMessages
                } else {
                    conversation.map { msg -> DeepSeekMessage(if (msg.isUser) "user" else "assistant", msg.text) }
                }

                val request = DeepSeekChatRequest(config.model, requestMessages)
                val response = when (config.api) {
                    RagApi.DeepSeek -> deepSeekApi.createChatCompletion(apiKey, request)
                    RagApi.OpenAI -> openAiApi.createChatCompletion(apiKey, request)
                    RagApi.GigaChat -> gigaChatApi.createChatCompletion(apiKey, request)
                    RagApi.ProxyOpenAI -> proxyOpenAiApi.createChatCompletion(apiKey, request)
                    RagApi.LocalLlm -> localLlmApi.createChatCompletion(request)
                }
                val answer = ensureStrictAnswerFormatV2(
                    response.choices.firstOrNull()?.message?.content?.trim().orEmpty().ifEmpty { "Empty response" },
                    retrieval.selectedChunks
                )
                AssistantResult(answer, assistantParams(config), retrieval.selectedChunks, retrieval.retrievalInfo)
            } catch (e: Exception) {
                AssistantResult(
                    ensureStrictAnswerFormatV2("Request failed: ${e.message ?: "unknown error"}", emptyList()),
                    assistantParams(config),
                    emptyList(),
                    null
                )
            }
        }

        fun sendMessage() {
            val chatId = activeChatId ?: return
            val text = inputText.trim()
            if (text.isEmpty() || isLoading) return

            val primaryConfig = RetrievalConfig(
                api = selectedApi,
                model = if (selectedApi == RagApi.LocalLlm) localModel else selectedApi.defaultModel,
                useRag = useRag,
                useFilter = useFilter,
                useRewrite = useRewrite,
                topKBefore = readPositiveInt(topKBeforeRaw, DEFAULT_TOP_K_BEFORE),
                topKAfter = readPositiveInt(topKAfterRaw, DEFAULT_TOP_K_AFTER),
                threshold = readThreshold(thresholdRaw, DEFAULT_MIN_SCORE)
            )
            val compareConfig = RetrievalConfig(
                api = comparisonApi,
                model = if (comparisonApi == RagApi.LocalLlm) comparisonLocalModel else comparisonApi.defaultModel,
                useRag = comparisonUseRag,
                useFilter = comparisonUseFilter,
                useRewrite = comparisonUseRewrite,
                topKBefore = readPositiveInt(comparisonTopKBeforeRaw, DEFAULT_TOP_K_BEFORE),
                topKAfter = readPositiveInt(comparisonTopKAfterRaw, DEFAULT_TOP_K_AFTER),
                threshold = readThreshold(comparisonThresholdRaw, DEFAULT_MIN_SCORE)
            )
            taskState = updateTaskStateFromUserMessage(taskState, text)
            saveTaskState(chatId, taskState)

                ragQueries.insertMessage(
                    chatId,
                    primaryConfig.api.label,
                    primaryConfig.model,
                    "user",
                    text,
                    userParams(primaryConfig),
                System.currentTimeMillis()
            )
            messages += RagMessage(nextMessageId++, text, true, userParams(primaryConfig))
            if (dualChatEnabled) {
                comparisonMessages += RagMessage(nextMessageId++, text, true, userParams(compareConfig))
            }
            inputText = ""
            saveUseRagState(useRag)

            scope.launch {
                isLoading = true
                val taskStateSnapshot = taskState
                val primaryDeferred = async { requestAssistant(text, messages.toList(), taskStateSnapshot, primaryConfig) }
                val comparisonDeferred = if (dualChatEnabled) async {
                    requestAssistant(text, comparisonMessages.toList(), taskStateSnapshot, compareConfig)
                } else null

                val primaryResult = primaryDeferred.await()
                ragQueries.insertMessage(
                    chatId,
                    primaryConfig.api.label,
                    primaryConfig.model,
                    "assistant",
                    primaryResult.answer,
                    primaryResult.paramsInfo,
                    System.currentTimeMillis()
                )
                messages += RagMessage(
                    id = nextMessageId++,
                    text = primaryResult.answer,
                    isUser = false,
                    paramsInfo = primaryResult.paramsInfo,
                    sources = primaryResult.sources,
                    retrievalInfo = primaryResult.retrievalInfo
                )

                if (dualChatEnabled) {
                    val compareResult = comparisonDeferred?.await()
                    if (compareResult != null) {
                        comparisonMessages += RagMessage(
                            id = nextMessageId++,
                            text = compareResult.answer,
                            isUser = false,
                            paramsInfo = compareResult.paramsInfo,
                            sources = compareResult.sources,
                            retrievalInfo = compareResult.retrievalInfo
                        )
                    }
                }
                isLoading = false
            }
        }

        LaunchedEffect(Unit) {
            reloadChats()
            if (activeChatId == null && chats.isNotEmpty()) {
                activeChatId = chats.first().id
                loadMessages(chats.first().id)
            }
        }
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
        }
        LaunchedEffect(comparisonMessages.size) {
            if (comparisonMessages.isNotEmpty()) comparisonListState.animateScrollToItem(comparisonMessages.lastIndex)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("AiAgentRAG") },
                    actions = {
                        Box {
                            TextButton(onClick = { screensMenuExpanded = true }, enabled = !isLoading) { Text("Экраны") }
                            DropdownMenu(expanded = screensMenuExpanded, onDismissRequest = { screensMenuExpanded = false }) {
                                RootScreen.entries.forEach { screen ->
                                    DropdownMenuItem(
                                        text = { Text(if (currentScreen == screen) "${screen.name} *" else screen.name) },
                                        onClick = { screensMenuExpanded = false; onSelectScreen(screen) }
                                    )
                                }
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
            Row(Modifier.fillMaxSize().imePadding().padding(padding)) {
                Column(
                    Modifier.width(240.dp).fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { createChat(selectNew = true) }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("+ Новый чат") }
                    Button(onClick = ::deleteAllChats, modifier = Modifier.fillMaxWidth(), enabled = chats.isNotEmpty() && !isLoading) { Text("Удалить все чаты") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chats, key = { it.id }) { chat ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(
                                        if (chat.id == activeChatId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isLoading) {
                                        activeChatId = chat.id
                                        loadMessages(chat.id)
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(chat.title, modifier = Modifier.weight(1f))
                                TextButton(onClick = { deleteChat(chat.id) }, enabled = !isLoading) { Text("X") }
                            }
                        }
                    }
                }

                Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))

                Column(Modifier.weight(1f).fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Включить двойной чат для сравнения")
                        Switch(checked = dualChatEnabled, onCheckedChange = { dualChatEnabled = it }, enabled = !isLoading)
                    }

                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Настройки основного чата")
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box {
                                                TextButton(
                                                    onClick = { apiMenuExpanded = true },
                                                    enabled = !isLoading
                                                ) { Text("API: ${selectedApi.label}") }
                                                DropdownMenu(
                                                    expanded = apiMenuExpanded,
                                                    onDismissRequest = { apiMenuExpanded = false }
                                                ) {
                                                    RagApi.entries.forEach { api ->
                                                        DropdownMenuItem(
                                                            text = { Text(api.label) },
                                                            onClick = {
                                                                selectedApi = api
                                                                localModelMenuExpanded = false
                                                                apiMenuExpanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            if (selectedApi == RagApi.LocalLlm) {
                                                Box {
                                                    TextButton(
                                                        onClick = { localModelMenuExpanded = true },
                                                        enabled = !isLoading
                                                    ) { Text("Model: $localModel") }
                                                    DropdownMenu(
                                                        expanded = localModelMenuExpanded,
                                                        onDismissRequest = { localModelMenuExpanded = false }
                                                    ) {
                                                        RagApi.LocalLlm.supportedModels.forEach { model ->
                                                            DropdownMenuItem(
                                                                text = { Text(model) },
                                                                onClick = {
                                                                    localModel = model
                                                                    localModelMenuExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = topKBeforeRaw,
                                            onValueChange = { topKBeforeRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Top-K до фильтра") },
                                            maxLines = 1,
                                            enabled = !isLoading
                                        )
                                        OutlinedTextField(
                                            value = topKAfterRaw,
                                            onValueChange = { topKAfterRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Top-K после фильтра") },
                                            maxLines = 1,
                                            enabled = !isLoading
                                        )
                                        OutlinedTextField(
                                            value = thresholdRaw,
                                            onValueChange = {
                                                val normalized = it.replace(',', '.')
                                                thresholdRaw = normalized.filter { c -> c.isDigit() || c == '.' }.take(4)
                                            },
                                            modifier = Modifier.weight(1f),
                                            label = { Text("Порог similarity") },
                                            maxLines = 1,
                                            enabled = !isLoading
                                        )
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("RAG")
                                        Switch(checked = useRag, onCheckedChange = { useRag = it; saveUseRagState(it) }, enabled = !isLoading)
                                        Text("filter")
                                        Switch(checked = useFilter, onCheckedChange = { useFilter = it }, enabled = !isLoading)
                                        Text("rewrite")
                                        Switch(checked = useRewrite, onCheckedChange = { useRewrite = it }, enabled = !isLoading)
                                    }
                                }
                            }

                            LazyColumn(modifier = Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(messages, key = { it.id }) { message ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                        Box(
                                            Modifier.fillMaxWidth(0.9f)
                                                .background(if (message.isUser) Color(0xFF1D4ED8) else Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                SelectionContainer { Text(message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A)) }
                                                Text(
                                                    message.paramsInfo,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f)
                                                )
                                                if (!message.isUser && !message.retrievalInfo.isNullOrBlank()) {
                                                    Text(message.retrievalInfo, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                                }
                                                if (!message.isUser && message.sources.isNotEmpty()) {
                                                    val expanded = selectedSourcesMessageId == message.id
                                                    TextButton(onClick = { selectedSourcesMessageId = if (expanded) null else message.id }) {
                                                        Text(if (expanded) "Скрыть источники" else "Показать источники")
                                                    }
                                                    if (expanded) {
                                                        message.sources.forEach { src ->
                                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                                Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text(src.title, style = MaterialTheme.typography.labelMedium)
                                                                    Text("раздел=${src.section} | score=${"%.4f".format(src.score)}", style = MaterialTheme.typography.labelSmall)
                                                                    SelectionContainer { Text(src.chunkText, style = MaterialTheme.typography.bodySmall) }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (isLoading) item {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { CircularProgressIndicator() }
                                }
                            }
                        }

                        if (dualChatEnabled) {
                            Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFDBEAFE)))
                            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Настройки чата сравнения")
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box {
                                                    TextButton(
                                                        onClick = { comparisonApiMenuExpanded = true },
                                                        enabled = !isLoading
                                                    ) { Text("API: ${comparisonApi.label}") }
                                                    DropdownMenu(
                                                        expanded = comparisonApiMenuExpanded,
                                                        onDismissRequest = { comparisonApiMenuExpanded = false }
                                                    ) {
                                                        RagApi.entries.forEach { api ->
                                                            DropdownMenuItem(
                                                                text = { Text(api.label) },
                                                                onClick = {
                                                                    comparisonApi = api
                                                                    comparisonLocalModelMenuExpanded = false
                                                                    comparisonApiMenuExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                if (comparisonApi == RagApi.LocalLlm) {
                                                    Box {
                                                        TextButton(
                                                            onClick = { comparisonLocalModelMenuExpanded = true },
                                                            enabled = !isLoading
                                                        ) { Text("Model: $comparisonLocalModel") }
                                                        DropdownMenu(
                                                            expanded = comparisonLocalModelMenuExpanded,
                                                            onDismissRequest = { comparisonLocalModelMenuExpanded = false }
                                                        ) {
                                                            RagApi.LocalLlm.supportedModels.forEach { model ->
                                                                DropdownMenuItem(
                                                                    text = { Text(model) },
                                                                    onClick = {
                                                                        comparisonLocalModel = model
                                                                        comparisonLocalModelMenuExpanded = false
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedTextField(
                                                value = comparisonTopKBeforeRaw,
                                                onValueChange = { comparisonTopKBeforeRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Top-K до фильтра") },
                                                maxLines = 1,
                                                enabled = !isLoading
                                            )
                                            OutlinedTextField(
                                                value = comparisonTopKAfterRaw,
                                                onValueChange = { comparisonTopKAfterRaw = it.filter(Char::isDigit).ifBlank { "1" } },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Top-K после фильтра") },
                                                maxLines = 1,
                                                enabled = !isLoading
                                            )
                                            OutlinedTextField(
                                                value = comparisonThresholdRaw,
                                                onValueChange = {
                                                    val normalized = it.replace(',', '.')
                                                    comparisonThresholdRaw = normalized.filter { c -> c.isDigit() || c == '.' }.take(4)
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = { Text("Порог similarity") },
                                                maxLines = 1,
                                                enabled = !isLoading
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("RAG")
                                            Switch(checked = comparisonUseRag, onCheckedChange = { comparisonUseRag = it }, enabled = !isLoading)
                                            Text("filter")
                                            Switch(checked = comparisonUseFilter, onCheckedChange = { comparisonUseFilter = it }, enabled = !isLoading)
                                            Text("rewrite")
                                            Switch(checked = comparisonUseRewrite, onCheckedChange = { comparisonUseRewrite = it }, enabled = !isLoading)
                                            Spacer(Modifier.weight(1f))
                                            TextButton(
                                                onClick = {
                                                    comparisonUseFilter = false
                                                    comparisonUseRewrite = false
                                                },
                                                enabled = !isLoading
                                            ) { Text("Без filter/rewrite") }
                                        }
                                    }
                                }

                                LazyColumn(modifier = Modifier.weight(1f), state = comparisonListState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(comparisonMessages, key = { it.id }) { message ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End) {
                                            Box(
                                                Modifier.fillMaxWidth(0.9f)
                                                    .background(if (message.isUser) Color(0xFF0EA5E9) else Color(0xFFE5E7EB), RoundedCornerShape(16.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    SelectionContainer { Text(message.text, color = if (message.isUser) Color.White else Color(0xFF0F172A)) }
                                                    Text(
                                                        message.paramsInfo,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (message.isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.7f)
                                                    )
                                                    if (!message.isUser && !message.retrievalInfo.isNullOrBlank()) {
                                                        Text(message.retrievalInfo, style = MaterialTheme.typography.labelSmall, color = Color(0xFF334155))
                                                    }
                                                    if (!message.isUser && message.sources.isNotEmpty()) {
                                                        val expanded = comparisonSelectedSourcesMessageId == message.id
                                                        TextButton(onClick = { comparisonSelectedSourcesMessageId = if (expanded) null else message.id }) {
                                                            Text(if (expanded) "Скрыть источники" else "Показать источники")
                                                        }
                                                        if (expanded) {
                                                            message.sources.forEach { src ->
                                                                Card(modifier = Modifier.fillMaxWidth()) {
                                                                    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                        Text(src.title, style = MaterialTheme.typography.labelMedium)
                                                                        Text("раздел=${src.section} | score=${"%.4f".format(src.score)}", style = MaterialTheme.typography.labelSmall)
                                                                        SelectionContainer { Text(src.chunkText, style = MaterialTheme.typography.bodySmall) }
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
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        val withModifier = event.isCtrlPressed || event.isShiftPressed || event.isAltPressed
                                        if (withModifier) {
                                            inputText += "\n"
                                        } else if (inputText.isNotBlank() && !isLoading && activeChatId != null) {
                                            sendMessage()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                },
                            label = { Text(if (dualChatEnabled) "Сообщение для двух чатов" else "Сообщение") },
                            maxLines = 4,
                            enabled = !isLoading
                        )
                        Button(
                            onClick = ::sendMessage,
                            enabled = inputText.isNotBlank() && !isLoading && activeChatId != null
                        ) { Text(if (dualChatEnabled) "Отправить в оба" else "Отправить") }
                    }
                }
            }
        }
    }
}
