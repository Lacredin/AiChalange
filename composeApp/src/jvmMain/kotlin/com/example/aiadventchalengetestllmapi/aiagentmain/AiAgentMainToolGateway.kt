package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal class AiAgentMainToolGateway(
    private val ragExecutor: suspend (query: String) -> String,
    private val mcpExecutor: suspend (request: ToolGatewayMcpRequest) -> String,
    private val projectFsSummaryExecutor: suspend (projectFolderPath: String) -> String,
    private val isRagAvailable: suspend () -> Boolean,
    private val isMcpToolAvailable: suspend (toolName: String) -> Boolean,
    private val isProjectFsAvailable: suspend (projectFolderPath: String) -> Boolean,
    private val mcpTimeoutMs: Long = 25_000L,
    private val mcpRetryCount: Int = 1
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun execute(request: ToolGatewayRequest): ToolGatewayResult {
        val start = System.currentTimeMillis()
        return runCatching {
            when (request.toolKind) {
                MultiAgentToolKind.RAG_QUERY -> runRag(request, start)
                MultiAgentToolKind.MCP_CALL -> runMcp(request, start)
                MultiAgentToolKind.PROJECT_FS_SUMMARY -> runProjectFs(request, start)
            }
        }.getOrElse { error ->
            ToolGatewayResult(
                success = false,
                toolKind = request.toolKind,
                normalizedOutput = "",
                rawOutput = "",
                errorCode = "INTERNAL_ERROR",
                errorMessage = error.message ?: error::class.simpleName ?: "unknown",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    private suspend fun runRag(request: ToolGatewayRequest, start: Long): ToolGatewayResult {
        val params = parseParams(request.paramsJson)
        val query = params["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (query.isBlank()) {
            return ToolGatewayResult(
                success = false,
                toolKind = MultiAgentToolKind.RAG_QUERY,
                normalizedOutput = "",
                rawOutput = "",
                errorCode = "INVALID_ARGUMENT",
                errorMessage = "RAG query is blank",
                latencyMs = System.currentTimeMillis() - start
            )
        }
        if (request.preflight) {
            val available = isRagAvailable()
            return ToolGatewayResult(
                success = available,
                toolKind = MultiAgentToolKind.RAG_QUERY,
                normalizedOutput = if (available) "RAG available" else "",
                rawOutput = "",
                errorCode = if (available) "" else "RAG_UNAVAILABLE",
                errorMessage = if (available) "" else "RAG index is unavailable or empty",
                latencyMs = System.currentTimeMillis() - start,
                metadataJson = """{"preflight":true}"""
            )
        }
        val raw = ragExecutor(query)
        return ToolGatewayResult(
            success = raw.isNotBlank(),
            toolKind = MultiAgentToolKind.RAG_QUERY,
            normalizedOutput = raw,
            rawOutput = raw,
            errorCode = if (raw.isBlank()) "EMPTY_RESULT" else "",
            errorMessage = if (raw.isBlank()) "RAG returned empty result" else "",
            latencyMs = System.currentTimeMillis() - start
        )
    }

    private suspend fun runMcp(request: ToolGatewayRequest, start: Long): ToolGatewayResult {
        val params = parseParams(request.paramsJson)
        val toolName = params["toolName"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val endpoint = params["endpoint"]?.jsonPrimitive?.contentOrNull?.trim()?.ifBlank { null }
        val arguments = params["arguments"]?.jsonObject?.mapValues { (_, value) -> jsonElementToAny(value) } ?: emptyMap()

        if (toolName.isBlank()) {
            return ToolGatewayResult(
                success = false,
                toolKind = MultiAgentToolKind.MCP_CALL,
                normalizedOutput = "",
                rawOutput = "",
                errorCode = "INVALID_ARGUMENT",
                errorMessage = "toolName is required for MCP_CALL",
                latencyMs = System.currentTimeMillis() - start
            )
        }
        if (request.preflight) {
            val available = isMcpToolAvailable(toolName)
            return ToolGatewayResult(
                success = available,
                toolKind = MultiAgentToolKind.MCP_CALL,
                normalizedOutput = if (available) "MCP tool '$toolName' available" else "",
                rawOutput = "",
                errorCode = if (available) "" else "MCP_TOOL_UNAVAILABLE",
                errorMessage = if (available) "" else "MCP tool '$toolName' unavailable",
                latencyMs = System.currentTimeMillis() - start,
                metadataJson = """{"preflight":true,"toolName":"$toolName"}"""
            )
        }

        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= mcpRetryCount) {
            attempt++
            val response = runCatching {
                withTimeout(mcpTimeoutMs) {
                    mcpExecutor(
                        ToolGatewayMcpRequest(
                            toolName = toolName,
                            endpoint = endpoint,
                            arguments = arguments
                        )
                    )
                }
            }
            if (response.isSuccess) {
                val raw = response.getOrNull().orEmpty()
                return ToolGatewayResult(
                    success = raw.isNotBlank(),
                    toolKind = MultiAgentToolKind.MCP_CALL,
                    normalizedOutput = raw,
                    rawOutput = raw,
                    errorCode = if (raw.isBlank()) "EMPTY_RESULT" else "",
                    errorMessage = if (raw.isBlank()) "MCP returned empty result" else "",
                    latencyMs = System.currentTimeMillis() - start,
                    metadataJson = """{"attempt":$attempt,"toolName":"$toolName"}"""
                )
            }
            lastError = response.exceptionOrNull()
        }

        return ToolGatewayResult(
            success = false,
            toolKind = MultiAgentToolKind.MCP_CALL,
            normalizedOutput = "",
            rawOutput = "",
            errorCode = "MCP_CALL_FAILED",
            errorMessage = lastError?.message ?: "unknown",
            latencyMs = System.currentTimeMillis() - start,
            metadataJson = """{"attempt":${mcpRetryCount + 1},"toolName":"$toolName"}"""
        )
    }

    private suspend fun runProjectFs(request: ToolGatewayRequest, start: Long): ToolGatewayResult {
        val params = parseParams(request.paramsJson)
        val projectFolderPath = params["projectFolderPath"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (projectFolderPath.isBlank()) {
            return ToolGatewayResult(
                success = false,
                toolKind = MultiAgentToolKind.PROJECT_FS_SUMMARY,
                normalizedOutput = "",
                rawOutput = "",
                errorCode = "INVALID_ARGUMENT",
                errorMessage = "projectFolderPath is blank",
                latencyMs = System.currentTimeMillis() - start
            )
        }
        if (request.preflight) {
            val available = isProjectFsAvailable(projectFolderPath)
            return ToolGatewayResult(
                success = available,
                toolKind = MultiAgentToolKind.PROJECT_FS_SUMMARY,
                normalizedOutput = if (available) "PROJECT_FS available" else "",
                rawOutput = "",
                errorCode = if (available) "" else "PROJECT_FS_UNAVAILABLE",
                errorMessage = if (available) "" else "Project folder unavailable",
                latencyMs = System.currentTimeMillis() - start,
                metadataJson = """{"preflight":true}"""
            )
        }
        val raw = projectFsSummaryExecutor(projectFolderPath)
        return ToolGatewayResult(
            success = raw.isNotBlank(),
            toolKind = MultiAgentToolKind.PROJECT_FS_SUMMARY,
            normalizedOutput = raw,
            rawOutput = raw,
            errorCode = if (raw.isBlank()) "EMPTY_RESULT" else "",
            errorMessage = if (raw.isBlank()) "Project FS summary is empty" else "",
            latencyMs = System.currentTimeMillis() - start
        )
    }

    private fun parseParams(raw: String): JsonObject {
        val text = raw.trim()
        if (text.isBlank()) return JsonObject(emptyMap())
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrDefault(JsonObject(emptyMap()))
    }

    private fun jsonElementToAny(value: JsonElement): Any? {
        val primitive = value.jsonPrimitive
        primitive.booleanOrNull?.let { return it }
        primitive.intOrNull?.let { return it }
        primitive.longOrNull?.let { return it }
        primitive.doubleOrNull?.let { return it }
        primitive.contentOrNull?.let { return it }
        return value.toString()
    }
}
