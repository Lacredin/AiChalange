package com.example.aiadventchalengetestllmapi.aiagentmain

internal data class ToolGatewayRequest(
    val toolKind: MultiAgentToolKind,
    val paramsJson: String,
    val reason: String = "",
    val preflight: Boolean = false,
    val stepIndex: Int? = null
)

internal data class ToolGatewayResult(
    val success: Boolean,
    val toolKind: MultiAgentToolKind,
    val normalizedOutput: String,
    val rawOutput: String,
    val errorCode: String,
    val errorMessage: String,
    val latencyMs: Long,
    val metadataJson: String = "{}"
)

internal data class ToolGatewayMcpRequest(
    val toolName: String,
    val endpoint: String?,
    val arguments: Map<String, Any?>
)

internal data class ToolGatewayAvailability(
    val available: Boolean,
    val source: String,
    val reason: String = "",
    val details: String = ""
)
