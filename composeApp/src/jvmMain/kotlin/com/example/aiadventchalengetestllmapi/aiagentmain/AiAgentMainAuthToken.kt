package com.example.aiadventchalengetestllmapi.aiagentmain

import com.example.aiadventchalengetestllmapi.mcp.RemoteMcpService
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class AgentAuthTokenInput(
    val bindingType: String,
    val repoUrl: String?,
    val owner: String?,
    val token: String,
    val tokenType: String
)

internal object AgentAuthTokenCommandParser {
    private val allowedTokenTypes = setOf("classic_pat", "fine_grained_pat", "github_app_token")

    fun parse(arguments: String): AgentAuthTokenInput? {
        val tokens = arguments.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val bindingRaw = tokens[0]
        val token = tokens[1]
        if (token.isBlank()) return null

        val tokenType = tokens.getOrNull(2)?.lowercase()?.trim().orEmpty()
            .ifBlank { "fine_grained_pat" }
        if (tokenType !in allowedTokenTypes) return null

        return when {
            bindingRaw.equals("global", ignoreCase = true) -> AgentAuthTokenInput(
                bindingType = "global",
                repoUrl = null,
                owner = null,
                token = token,
                tokenType = tokenType
            )

            bindingRaw.startsWith("owner:", ignoreCase = true) -> {
                val owner = bindingRaw.substringAfter(':').trim()
                if (owner.isBlank()) return null
                AgentAuthTokenInput(
                    bindingType = "owner",
                    repoUrl = null,
                    owner = owner,
                    token = token,
                    tokenType = tokenType
                )
            }

            else -> {
                val repoUrl = normalizeGithubRepoUrl(bindingRaw) ?: return null
                AgentAuthTokenInput(
                    bindingType = "repo",
                    repoUrl = repoUrl,
                    owner = null,
                    token = token,
                    tokenType = tokenType
                )
            }
        }
    }
}

internal class AgentAuthTokenUseCase(
    private val remoteMcpService: RemoteMcpService
) {
    suspend fun execute(arguments: String, servers: List<AgentMcpServerSnapshot>): String {
        val input = AgentAuthTokenCommandParser.parse(arguments)
            ?: return "Формат: /auth-token <repo_url|owner:ORG|global> <token> [token_type]"

        val server = servers.firstOrNull { snapshot ->
            snapshot.error == null && snapshot.tools.any { it.name.equals("auth.upsert_token", ignoreCase = true) }
        } ?: return "MCP tool auth.upsert_token недоступен на активных серверах."

        val raw = runCatching {
            remoteMcpService.callTool(
                serverUrl = server.url,
                toolName = "auth.upsert_token",
                arguments = buildMap {
                    put("provider", "github")
                    put("tokenType", input.tokenType)
                    put("token", input.token)
                    put("bindingType", input.bindingType)
                    input.repoUrl?.let { put("repoUrl", it) }
                    input.owner?.let { put("owner", it) }
                    put("overwrite", true)
                }
            )
        }.getOrElse { error ->
            return "Не удалось вызвать auth.upsert_token: ${error.message ?: error::class.simpleName ?: "unknown"}"
        }

        val root = tryParseJsonExecution(raw)?.jsonObject
            ?: return "auth.upsert_token вернул не-JSON ответ."
        val success = root["success"]?.jsonPrimitive?.booleanOrNull ?: false

        if (success) {
            val tokenRef = root["tokenRef"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val binding = root["binding"]?.jsonObject
            val bindingType = binding?.get("bindingType")?.jsonPrimitive?.contentOrNull.orEmpty()
            val bindingValue = binding?.get("repoUrl")?.jsonPrimitive?.contentOrNull
                ?: binding?.get("owner")?.jsonPrimitive?.contentOrNull
                ?: "global"
            return "Токен сохранен: tokenRef=$tokenRef, binding=$bindingType:$bindingValue"
        }

        val diagnostics = parseDiagnostics(root)
        if (diagnostics.isEmpty()) return "auth.upsert_token завершился с ошибкой без diagnostics."
        return diagnostics.joinToString("\n") { line ->
            val scopeSuffix = line.scope?.let { ", scope=$it" }.orEmpty()
            val typeSuffix = line.requiredTokenType?.let { ", requiredTokenType=$it" }.orEmpty()
            "[${line.code}] ${line.message}$scopeSuffix$typeSuffix. ${line.hint}"
        }
    }

    private data class ParsedDiagnostic(
        val code: String,
        val message: String,
        val hint: String,
        val requiredTokenType: String?,
        val scope: String?
    )

    private fun parseDiagnostics(root: JsonObject): List<ParsedDiagnostic> {
        return root["diagnostics"]?.jsonArray.orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            ParsedDiagnostic(
                code = obj["code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "UNKNOWN" },
                message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                hint = obj["hint"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                requiredTokenType = obj["requiredTokenType"]?.jsonPrimitive?.contentOrNull,
                scope = obj["scope"]?.jsonPrimitive?.contentOrNull
            )
        }
    }
}
