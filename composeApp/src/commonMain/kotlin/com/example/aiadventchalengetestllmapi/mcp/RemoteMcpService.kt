package com.example.aiadventchalengetestllmapi.mcp

import com.example.aiadventchalengetestllmapi.network.NetworkClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.WebSocketClientTransport
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val MICROSOFT_LEARN_MCP_URL = "https://learn.microsoft.com/api/mcp"

data class McpToolInfo(
    val name: String,
    val description: String,
    val supportsWebSocket: Boolean,
    val inputSchema: String
)

class RemoteMcpService {
    private suspend fun createConnectedClient(serverUrl: String): Client {
        val client = Client(
            clientInfo = Implementation(
                name = "microsoft-learn-mcp-screen",
                version = "1.0.0"
            )
        )
        val transport = StreamableHttpClientTransport(
            client = NetworkClient.httpClient,
            url = serverUrl
        )
        client.connect(transport)
        return client
    }

    private suspend fun createConnectedWebSocketClient(webSocketUrl: String): Client {
        val client = Client(
            clientInfo = Implementation(
                name = "microsoft-learn-mcp-screen",
                version = "1.0.0"
            )
        )
        val transport = WebSocketClientTransport(
            client = NetworkClient.httpClient,
            urlString = webSocketUrl
        )
        client.connect(transport)
        return client
    }

    private fun looksLikeWebSocketTool(tool: Tool): Boolean {
        val flattened = buildString {
            append(tool.name)
            append(' ')
            append(tool.title.orEmpty())
            append(' ')
            append(tool.description.orEmpty())
            append(' ')
            append(tool.annotations?.title.orEmpty())
            append(' ')
            append(tool.inputSchema.properties?.toString().orEmpty())
            append(' ')
            append(tool.meta?.toString().orEmpty())
        }.lowercase()

        return flattened.contains("websocket") ||
            flattened.contains("web socket") ||
            flattened.contains("web-socket") ||
            flattened.contains("web_socket") ||
            flattened.contains("ws://") ||
            flattened.contains("wss://")
    }

    private fun toWebSocketUrl(serverUrl: String): String {
        val normalized = serverUrl.trim()
        return when {
            normalized.startsWith("https://", ignoreCase = true) ->
                "wss://${normalized.removePrefix("https://")}"
            normalized.startsWith("http://", ignoreCase = true) ->
                "ws://${normalized.removePrefix("http://")}"
            normalized.startsWith("wss://", ignoreCase = true) ||
                normalized.startsWith("ws://", ignoreCase = true) -> normalized
            else -> error("Unsupported URL for WebSocket transport: $serverUrl")
        }
    }

    suspend fun listAvailableTools(serverUrl: String = MICROSOFT_LEARN_MCP_URL): List<McpToolInfo> {
        val client = createConnectedClient(serverUrl)

        return client.listTools().tools
            .map { tool ->
                McpToolInfo(
                    name = tool.name,
                    description = tool.description?.trim().orEmpty(),
                    supportsWebSocket = looksLikeWebSocketTool(tool),
                    inputSchema = tool.inputSchema.toString()
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun callTool(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any?> = emptyMap()
    ): String {
        val client = createConnectedClient(serverUrl)
        val result = client.callTool(name = toolName, arguments = arguments)
        val contentText = result.content.joinToString("\n") { block ->
            when (block) {
                is TextContent -> block.text
                else -> block.toString()
            }
        }.trim()
        if (contentText.isNotBlank()) return contentText
        val structured = result.structuredContent?.toString().orEmpty()
        return if (structured.isNotBlank()) structured else "Пустой ответ инструмента."
    }

    suspend fun callToolViaWebSocket(
        serverUrl: String,
        webSocketEndpoint: String? = null,
        toolName: String,
        arguments: Map<String, Any?> = emptyMap(),
        onWebSocketLog: suspend (direction: String, payload: String) -> Unit
    ): String {
        val wsEndpoint = resolveWebSocketEndpoint(serverUrl, webSocketEndpoint)
        onWebSocketLog("CONNECT", "Opening WebSocket to $wsEndpoint")
        val client = createConnectedWebSocketClient(wsEndpoint)
        return try {
            val argsJson = buildJsonObject {
                arguments.forEach { (key, value) ->
                    put(key, value?.toString() ?: "null")
                }
            }
            onWebSocketLog(
                "SEND",
                buildJsonObject {
                    put("method", "tools/call")
                    putJsonObject("params") {
                        put("name", toolName)
                        put("arguments", argsJson.toString())
                    }
                }.toString()
            )

            val result = client.callTool(name = toolName, arguments = arguments)
            val contentText = result.content.joinToString("\n") { block ->
                when (block) {
                    is TextContent -> block.text
                    else -> block.toString()
                }
            }.trim()
            val structured = result.structuredContent?.toString().orEmpty()
            val output = if (contentText.isNotBlank()) contentText
            else if (structured.isNotBlank()) structured
            else "Пустой ответ инструмента."

            onWebSocketLog("RECV", output)
            output
        } catch (error: Throwable) {
            onWebSocketLog("ERROR", error.message ?: error::class.simpleName ?: "unknown")
            throw error
        } finally {
            runCatching {
                client.close()
                onWebSocketLog("CLOSE", "WebSocket connection closed")
            }.onFailure { closeError ->
                onWebSocketLog("ERROR", "Close failed: ${closeError.message ?: closeError::class.simpleName ?: "unknown"}")
            }
        }
    }

    suspend fun callToolWithAuxWebSocket(
        serverUrl: String,
        webSocketEndpoint: String,
        toolName: String,
        arguments: Map<String, Any?> = emptyMap(),
        onWebSocketLog: suspend (direction: String, payload: String) -> Unit
    ): String {
        val wsEndpoint = resolveWebSocketEndpoint(serverUrl, webSocketEndpoint)
        onWebSocketLog("CONNECT", "Opening auxiliary WebSocket to $wsEndpoint")
        val session = NetworkClient.httpClient.webSocketSession(wsEndpoint)
        try {
            onWebSocketLog("OPEN", "Auxiliary WebSocket connected")
            val toolOutput = callTool(
                serverUrl = serverUrl,
                toolName = toolName,
                arguments = arguments
            )
            onWebSocketLog("RECV_TOOL", toolOutput)

            var wsEvent: String? = null
            withTimeoutOrNull(300_000L) {
                while (wsEvent == null) {
                    val frame = session.incoming.receive()
                    if (frame is Frame.Text) {
                        wsEvent = frame.readText()
                    }
                }
            }
            if (wsEvent != null) {
                onWebSocketLog("RECV", wsEvent)
                return "$toolOutput\n\nWebSocket event:\n$wsEvent"
            }
            onWebSocketLog("TIMEOUT", "No WebSocket event received within timeout")
            return toolOutput
        } finally {
            runCatching {
                session.close()
                onWebSocketLog("CLOSE", "Auxiliary WebSocket closed")
            }.onFailure { closeError ->
                onWebSocketLog("ERROR", "Aux close failed: ${closeError.message ?: closeError::class.simpleName ?: "unknown"}")
            }
        }
    }

    private fun resolveWebSocketEndpoint(serverUrl: String, webSocketEndpoint: String?): String {
        val endpoint = webSocketEndpoint?.trim().orEmpty()
        if (endpoint.isEmpty()) return toWebSocketUrl(serverUrl)
        if (endpoint.startsWith("ws://", ignoreCase = true) || endpoint.startsWith("wss://", ignoreCase = true)) {
            return endpoint
        }
        if (endpoint.startsWith("http://", ignoreCase = true) || endpoint.startsWith("https://", ignoreCase = true)) {
            return toWebSocketUrl(endpoint)
        }
        if (endpoint.startsWith("/")) {
            val base = toWebSocketUrl(serverUrl)
            val scheme = base.substringBefore("://")
            val authority = base.substringAfter("://").substringBefore("/")
            return "$scheme://$authority$endpoint"
        }
        return toWebSocketUrl(serverUrl)
    }
}
