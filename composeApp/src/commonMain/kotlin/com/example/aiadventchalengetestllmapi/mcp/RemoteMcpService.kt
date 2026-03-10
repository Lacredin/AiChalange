package com.example.aiadventchalengetestllmapi.mcp

import com.example.aiadventchalengetestllmapi.network.NetworkClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

private const val MICROSOFT_LEARN_MCP_URL = "https://learn.microsoft.com/api/mcp"

data class McpToolInfo(
    val name: String,
    val description: String
)

class RemoteMcpService {
    suspend fun listAvailableTools(serverUrl: String = MICROSOFT_LEARN_MCP_URL): List<McpToolInfo> {
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

        return client.listTools().tools
            .map { tool ->
                McpToolInfo(
                    name = tool.name,
                    description = tool.description?.trim().orEmpty()
                )
            }
            .sortedBy { it.name.lowercase() }
    }
}
