package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.mcp.RemoteMcpService
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class AgentPrReviewRequest(
    val projectFolderPath: String,
    val commandArguments: String
)

internal data class AgentPrReviewTarget(
    val repoUrl: String,
    val prNumber: Int?,
    val publishResult: Boolean
)

internal data class AgentMcpDiagnostic(
    val code: String,
    val message: String,
    val hint: String,
    val requiredTokenType: String? = null,
    val scope: String? = null
)

internal data class AgentGitChangedFile(
    val path: String,
    val status: String
)

internal data class AgentGitFileContent(
    val path: String,
    val sha: String?,
    val content: String
)

internal data class AgentGitPrContext(
    val repoUrl: String,
    val prNumber: Int?,
    val title: String,
    val body: String,
    val baseSha: String,
    val headSha: String,
    val changedFiles: List<AgentGitChangedFile>,
    val diff: String,
    val files: List<AgentGitFileContent>,
    val truncatedDiff: Boolean,
    val truncatedFiles: List<String>
)

internal data class AgentGitPrContextRequest(
    val repoUrl: String,
    val prNumber: Int?,
    val projectFolderPath: String,
    val baseSha: String? = null,
    val headSha: String? = null,
    val includeFileContents: Boolean = true,
    val maxFiles: Int = 200,
    val maxDiffBytes: Int = 1_500_000,
    val maxFileBytes: Int = 200_000
)

internal data class AgentSubmitPrReviewRequest(
    val repoUrl: String,
    val prNumber: Int,
    val reviewBody: String,
    val mode: String = "comment"
)

internal sealed interface AgentGitPrContextResult {
    data class Success(val context: AgentGitPrContext) : AgentGitPrContextResult
    data class MissingToken(val requiredTokenType: String, val scope: String?, val hint: String) : AgentGitPrContextResult
    data class AccessDenied(val code: String, val details: String, val hint: String) : AgentGitPrContextResult
    data class NotConfigured(val details: String) : AgentGitPrContextResult
    data class Failure(val details: String) : AgentGitPrContextResult
}

internal sealed interface AgentSubmitPrReviewResult {
    data class Success(val url: String?) : AgentSubmitPrReviewResult
    data class MissingToken(val requiredTokenType: String, val scope: String?, val hint: String) : AgentSubmitPrReviewResult
    data class AccessDenied(val code: String, val details: String, val hint: String) : AgentSubmitPrReviewResult
    data class Failure(val details: String) : AgentSubmitPrReviewResult
}

internal interface AgentPrReviewGitGateway {
    suspend fun loadPrContext(request: AgentGitPrContextRequest): AgentGitPrContextResult
    suspend fun submitReview(request: AgentSubmitPrReviewRequest): AgentSubmitPrReviewResult
}

internal class AgentNotConfiguredPrReviewGitGateway : AgentPrReviewGitGateway {
    override suspend fun loadPrContext(request: AgentGitPrContextRequest): AgentGitPrContextResult =
        AgentGitPrContextResult.NotConfigured(
            details = "MCP Git workflow не подключен: нет реализации получения diff и измененных файлов."
        )

    override suspend fun submitReview(request: AgentSubmitPrReviewRequest): AgentSubmitPrReviewResult =
        AgentSubmitPrReviewResult.Failure(
            details = "MCP Git workflow не подключен: нет реализации scm.submit_pr_review."
        )
}

internal class AgentMcpPrReviewGitGateway(
    private val remoteMcpService: RemoteMcpService,
    private val servers: List<AgentMcpServerSnapshot>,
    private val githubTokenProvider: () -> String
) : AgentPrReviewGitGateway {
    override suspend fun loadPrContext(request: AgentGitPrContextRequest): AgentGitPrContextResult {
        val toolResponse = callToolWithAuthRecovery(
            toolName = "git.pr_review_context",
            repoUrl = request.repoUrl,
            arguments = buildMap {
                put("repoUrl", request.repoUrl)
                request.prNumber?.let { put("prNumber", it) }
                request.baseSha?.takeIf { it.isNotBlank() }?.let { put("baseSha", it) }
                request.headSha?.takeIf { it.isNotBlank() }?.let { put("headSha", it) }
                put("includeFileContents", request.includeFileContents)
                put("maxFiles", request.maxFiles)
                put("maxDiffBytes", request.maxDiffBytes)
                put("maxFileBytes", request.maxFileBytes)
            }
        )

        if (toolResponse == null) {
            return AgentGitPrContextResult.NotConfigured("MCP tool git.pr_review_context недоступен на активных серверах.")
        }

        val authIssue = findAuthDiagnostic(toolResponse.diagnostics)
        if (authIssue != null) {
            return when (authIssue.code) {
                "AUTH_TOKEN_MISSING" -> AgentGitPrContextResult.MissingToken(
                    requiredTokenType = authIssue.requiredTokenType ?: "github_personal_access_token",
                    scope = authIssue.scope,
                    hint = authIssue.hint.ifBlank { "Вызовите auth.upsert_token и передайте токен." }
                )

                "AUTH_INVALID",
                "AUTH_FORBIDDEN",
                "AUTH_SCOPE_INSUFFICIENT",
                "AUTH_SSO_REQUIRED",
                "REPO_NOT_FOUND_OR_NO_ACCESS",
                "RATE_LIMITED" -> AgentGitPrContextResult.AccessDenied(
                    code = authIssue.code,
                    details = authIssue.message,
                    hint = authIssue.hint.ifBlank { "Проверьте токен и доступ к репозиторию." }
                )

                else -> AgentGitPrContextResult.Failure(authIssue.message.ifBlank { "Неизвестная ошибка доступа." })
            }
        }

        if (!toolResponse.success) {
            val first = toolResponse.diagnostics.firstOrNull()
            return AgentGitPrContextResult.Failure(
                first?.message ?: "git.pr_review_context вернул success=false без diagnostics."
            )
        }

        val root = toolResponse.root
        val prObj = root["pr"]?.jsonObject
            ?: return AgentGitPrContextResult.Failure("Ответ git.pr_review_context не содержит поле pr.")

        val changedFiles = root["changedFiles"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (path.isEmpty()) return@mapNotNull null
            AgentGitChangedFile(
                path = path,
                status = obj["status"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "modified" }
            )
        }

        val files = root["files"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (path.isBlank()) return@mapNotNull null
            AgentGitFileContent(
                path = path,
                sha = obj["sha"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null },
                content = content
            )
        }

        val truncatedObj = root["truncated"]?.jsonObject
        val context = AgentGitPrContext(
            repoUrl = prObj["repoUrl"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { request.repoUrl },
            prNumber = prObj["number"]?.jsonPrimitive?.intOrNull ?: request.prNumber,
            title = prObj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            body = prObj["body"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            baseSha = prObj["baseSha"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            headSha = prObj["headSha"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            changedFiles = changedFiles,
            diff = root["diff"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            files = files,
            truncatedDiff = truncatedObj?.get("diff")?.jsonPrimitive?.booleanOrNull ?: false,
            truncatedFiles = truncatedObj
                ?.get("files")
                ?.let { it as? JsonArray }
                .orEmpty()
                .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.ifBlank { null } }
        )

        return AgentGitPrContextResult.Success(context)
    }

    override suspend fun submitReview(request: AgentSubmitPrReviewRequest): AgentSubmitPrReviewResult {
        val toolResponse = callToolWithAuthRecovery(
            toolName = "scm.submit_pr_review",
            repoUrl = request.repoUrl,
            arguments = mapOf(
                "repoUrl" to request.repoUrl,
                "prNumber" to request.prNumber,
                "reviewBody" to request.reviewBody,
                "mode" to request.mode
            )
        ) ?: return AgentSubmitPrReviewResult.Failure("MCP tool scm.submit_pr_review недоступен на активных серверах.")

        val authIssue = findAuthDiagnostic(toolResponse.diagnostics)
        if (authIssue != null) {
            return when (authIssue.code) {
                "AUTH_TOKEN_MISSING" -> AgentSubmitPrReviewResult.MissingToken(
                    requiredTokenType = authIssue.requiredTokenType ?: "github_personal_access_token",
                    scope = authIssue.scope,
                    hint = authIssue.hint.ifBlank { "Вызовите auth.upsert_token и передайте токен." }
                )

                "AUTH_INVALID",
                "AUTH_FORBIDDEN",
                "AUTH_SCOPE_INSUFFICIENT",
                "AUTH_SSO_REQUIRED",
                "REPO_NOT_FOUND_OR_NO_ACCESS",
                "RATE_LIMITED" -> AgentSubmitPrReviewResult.AccessDenied(
                    code = authIssue.code,
                    details = authIssue.message,
                    hint = authIssue.hint.ifBlank { "Проверьте токен и доступ к репозиторию." }
                )

                else -> AgentSubmitPrReviewResult.Failure(authIssue.message.ifBlank { "Неизвестная ошибка доступа." })
            }
        }

        if (!toolResponse.success) {
            val first = toolResponse.diagnostics.firstOrNull()
            return AgentSubmitPrReviewResult.Failure(
                first?.message ?: "scm.submit_pr_review вернул success=false без diagnostics."
            )
        }

        val url = toolResponse.root["url"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
        return AgentSubmitPrReviewResult.Success(url = url)
    }

    private suspend fun callToolWithAuthRecovery(
        toolName: String,
        repoUrl: String,
        arguments: Map<String, Any?>
    ): ToolCallPayload? {
        val first = callTool(toolName, arguments) ?: return null
        val authIssue = findAuthDiagnostic(first.diagnostics) ?: return first

        val token = githubTokenProvider().trim()
        if (token.isBlank()) return first

        val upsertResult = upsertGithubToken(repoUrl = repoUrl, token = token, authIssue = authIssue)
        if (!upsertResult) return first

        return callTool(toolName, arguments) ?: first
    }

    private suspend fun upsertGithubToken(
        repoUrl: String,
        token: String,
        authIssue: AgentMcpDiagnostic
    ): Boolean {
        val bindingAttempts = buildTokenBindingAttempts(repoUrl)
        val tokenType = toServerTokenType(authIssue.requiredTokenType)

        for (binding in bindingAttempts) {
            val args = mutableMapOf<String, Any?>(
                "provider" to "github",
                "tokenType" to tokenType,
                "token" to token,
                "bindingType" to binding.bindingType,
                "overwrite" to true
            )
            binding.repoUrl?.let { args["repoUrl"] = it }
            binding.owner?.let { args["owner"] = it }
            authIssue.scope?.takeIf { it.isNotBlank() }?.let { args["permissionsHint"] = listOf(it) }

            val response = callTool("auth.upsert_token", args) ?: continue
            if (response.success) return true
        }
        return false
    }

    private fun buildTokenBindingAttempts(repoUrl: String): List<TokenBindingAttempt> {
        val normalized = normalizeGithubRepoUrl(repoUrl)
        val owner = normalized
            ?.removePrefix("https://github.com/")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }

        return buildList {
            normalized?.let { add(TokenBindingAttempt(bindingType = "repo", repoUrl = it, owner = null)) }
            owner?.let { add(TokenBindingAttempt(bindingType = "owner", repoUrl = null, owner = it)) }
            add(TokenBindingAttempt(bindingType = "global", repoUrl = null, owner = null))
        }
    }

    private fun toServerTokenType(requiredTokenType: String?): String {
        val normalized = requiredTokenType?.lowercase().orEmpty()
        return when {
            normalized.contains("app") -> "github_app_token"
            normalized.contains("classic") -> "classic_pat"
            else -> "fine_grained_pat"
        }
    }

    private suspend fun callTool(toolName: String, arguments: Map<String, Any?>): ToolCallPayload? {
        val server = resolveServerForTool(toolName) ?: return null
        val raw = runCatching {
            remoteMcpService.callTool(
                serverUrl = server.url,
                toolName = toolName,
                arguments = arguments
            )
        }.getOrElse { error ->
            return ToolCallPayload(
                success = false,
                diagnostics = listOf(
                    AgentMcpDiagnostic(
                        code = "INTERNAL_ERROR",
                        message = error.message ?: error::class.simpleName ?: "unknown",
                        hint = "Проверьте доступность MCP сервера и инструмента $toolName."
                    )
                ),
                root = JsonObject(emptyMap())
            )
        }

        val root = tryParseJsonExecution(raw)?.jsonObject
            ?: return ToolCallPayload(
                success = false,
                diagnostics = listOf(
                    AgentMcpDiagnostic(
                        code = "INTERNAL_ERROR",
                        message = "Инструмент $toolName вернул не-JSON ответ.",
                        hint = "Проверьте формат result.content[0].text на MCP сервере."
                    )
                ),
                root = JsonObject(emptyMap())
            )

        val diagnostics = root["diagnostics"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            AgentMcpDiagnostic(
                code = obj["code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                hint = obj["hint"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                requiredTokenType = obj["requiredTokenType"]?.jsonPrimitive?.contentOrNull,
                scope = obj["scope"]?.jsonPrimitive?.contentOrNull
            )
        }

        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: true
        return ToolCallPayload(
            success = success,
            diagnostics = diagnostics,
            root = root
        )
    }

    private fun resolveServerForTool(toolName: String): AgentMcpServerSnapshot? {
        val normalized = toolName.lowercase()
        return servers.firstOrNull { snapshot ->
            snapshot.error == null && snapshot.tools.any { it.name.equals(normalized, ignoreCase = true) }
        } ?: servers.firstOrNull { snapshot ->
            snapshot.error == null && snapshot.tools.any { it.name.equals(toolName, ignoreCase = true) }
        }
    }

    private fun findAuthDiagnostic(diagnostics: List<AgentMcpDiagnostic>): AgentMcpDiagnostic? {
        return diagnostics.firstOrNull { it.code in AUTH_DIAGNOSTIC_CODES }
    }

    private data class ToolCallPayload(
        val success: Boolean,
        val diagnostics: List<AgentMcpDiagnostic>,
        val root: JsonObject
    )

    private data class TokenBindingAttempt(
        val bindingType: String,
        val repoUrl: String?,
        val owner: String?
    )
}

internal object AgentPrReviewCommandParser {
    fun parse(arguments: String): AgentPrReviewTarget? {
        val trimmed = arguments.trim()
        if (trimmed.isBlank()) return null

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val publish = tokens.any { it.equals("--publish", ignoreCase = true) }
        val filtered = tokens.filterNot { it.equals("--publish", ignoreCase = true) }
        if (filtered.isEmpty()) return null

        val repoUrl = filtered.first()
        val normalizedRepo = normalizeGithubRepoUrl(repoUrl) ?: return null
        if (filtered.size == 1) {
            return AgentPrReviewTarget(repoUrl = normalizedRepo, prNumber = null, publishResult = publish)
        }

        val prToken = filtered[1].removePrefix("#")
        val prNumber = prToken.toIntOrNull() ?: return null
        return AgentPrReviewTarget(repoUrl = normalizedRepo, prNumber = prNumber, publishResult = publish)
    }
}

internal class AgentPrReviewUseCase {
    suspend fun execute(
        request: AgentPrReviewRequest,
        gitGateway: AgentPrReviewGitGateway,
        runRagQuery: suspend (query: String) -> AgentRagExecutionResult,
        callReviewModel: suspend (messages: List<DeepSeekMessage>) -> String
    ): String {
        val target = AgentPrReviewCommandParser.parse(request.commandArguments)
            ?: return "Формат команды: /review-pr <repo_url> [pr_number] [--publish]"

        val gitContextResult = gitGateway.loadPrContext(
            AgentGitPrContextRequest(
                repoUrl = target.repoUrl,
                prNumber = target.prNumber,
                projectFolderPath = request.projectFolderPath
            )
        )

        val gitContext = when (gitContextResult) {
            is AgentGitPrContextResult.Success -> gitContextResult.context
            is AgentGitPrContextResult.MissingToken ->
                return "Нет токена доступа (${gitContextResult.requiredTokenType}). ${gitContextResult.hint}"
            is AgentGitPrContextResult.AccessDenied ->
                return "Ошибка доступа [${gitContextResult.code}]: ${gitContextResult.details}. ${gitContextResult.hint}"
            is AgentGitPrContextResult.NotConfigured ->
                return "Сценарий /review-pr подготовлен, но Git-контекст пока не подключен. ${gitContextResult.details}"
            is AgentGitPrContextResult.Failure ->
                return "Не удалось получить контекст PR: ${gitContextResult.details}"
        }

        val ragQueries = buildList {
            add("Архитектура и правила проекта ${request.projectFolderPath}")
            if (gitContext.title.isNotBlank()) add(gitContext.title)
            if (gitContext.body.isNotBlank()) add(gitContext.body.take(500))
            if (gitContext.diff.isNotBlank()) add(gitContext.diff.take(3000))
            if (gitContext.changedFiles.isNotEmpty()) {
                add("Измененные файлы PR: " + gitContext.changedFiles.take(80).joinToString(", ") { it.path })
            }
        }.distinct()

        val ragResults = ragQueries.map { runRagQuery(it) }
        val ragContext = ragResults
            .mapNotNull { it.payload?.promptContext?.takeIf(String::isNotBlank) }
            .joinToString("\n\n")
            .ifBlank { "RAG context is empty." }

        val prompt = buildReviewPrompt(gitContext, ragContext)
        val reviewBody = callReviewModel(listOf(DeepSeekMessage(role = "user", content = prompt))).trim()
            .ifBlank { "Пустой ответ модели при ревью PR." }

        if (!target.publishResult || gitContext.prNumber == null) {
            return reviewBody
        }

        val submitResult = gitGateway.submitReview(
            AgentSubmitPrReviewRequest(
                repoUrl = gitContext.repoUrl,
                prNumber = gitContext.prNumber,
                reviewBody = reviewBody,
                mode = "comment"
            )
        )

        return when (submitResult) {
            is AgentSubmitPrReviewResult.Success -> {
                val published = submitResult.url?.let { "\n\nОпубликовано в PR: $it" }.orEmpty()
                reviewBody + published
            }

            is AgentSubmitPrReviewResult.MissingToken ->
                reviewBody + "\n\nПубликация не выполнена: нет токена (${submitResult.requiredTokenType}). ${submitResult.hint}"

            is AgentSubmitPrReviewResult.AccessDenied ->
                reviewBody + "\n\nПубликация не выполнена [${submitResult.code}]: ${submitResult.details}. ${submitResult.hint}"

            is AgentSubmitPrReviewResult.Failure ->
                reviewBody + "\n\nПубликация не выполнена: ${submitResult.details}"
        }
    }

    private fun buildReviewPrompt(
        context: AgentGitPrContext,
        ragContext: String
    ): String = buildString {
        appendLine("Ты выполняешь AI-ревью pull request.")
        appendLine("Верни ответ на русском языке в markdown.")
        appendLine("Структура ответа строго:")
        appendLine("1) Потенциальные баги")
        appendLine("2) Архитектурные проблемы")
        appendLine("3) Рекомендации")
        appendLine()
        appendLine("PR:")
        appendLine("- repo: ${context.repoUrl}")
        appendLine("- number: ${context.prNumber ?: "unknown"}")
        appendLine("- title: ${context.title}")
        appendLine("- base: ${context.baseSha}")
        appendLine("- head: ${context.headSha}")
        appendLine()
        appendLine("Changed files:")
        context.changedFiles.take(120).forEach { file ->
            appendLine("- [${file.status}] ${file.path}")
        }
        appendLine()
        appendLine("Diff:")
        appendLine(context.diff.take(120_000))
        appendLine()
        appendLine("RAG:")
        appendLine(ragContext.take(80_000))
    }.trim()
}

private val AUTH_DIAGNOSTIC_CODES = setOf(
    "AUTH_TOKEN_MISSING",
    "AUTH_INVALID",
    "AUTH_FORBIDDEN",
    "AUTH_SCOPE_INSUFFICIENT",
    "AUTH_SSO_REQUIRED",
    "REPO_NOT_FOUND_OR_NO_ACCESS",
    "RATE_LIMITED"
)

internal fun normalizeGithubRepoUrl(raw: String): String? {
    val trimmed = raw.trim().removeSuffix("/")
    return when {
        trimmed.startsWith("https://github.com/") -> trimmed.removeSuffix(".git")
        trimmed.startsWith("git@github.com:") -> {
            val repo = trimmed.removePrefix("git@github.com:").removeSuffix(".git")
            "https://github.com/$repo"
        }

        else -> null
    }
}
