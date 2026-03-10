package com.example.aiadventchalengetestllmapi.aiagentmcp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.aiadventchalengetestllmapi.RootScreen
import com.example.aiadventchalengetestllmapi.mcp.McpToolInfo
import com.example.aiadventchalengetestllmapi.mcp.RemoteMcpService
import kotlinx.coroutines.launch

private data class McpServerOption(
    val title: String,
    val url: String
)

private val mcpServerOptions = listOf(
    McpServerOption(
        title = "Microsoft Learn MCP",
        url = "https://learn.microsoft.com/api/mcp"
    ),
    McpServerOption(
        title = "Local MCP (127.0.0.1)",
        url = "http://127.0.0.1:8080/mcp"
    )
)

internal object AiAgentMCPScreenTheme {
    val primary = Color(0xFF1F6F50)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFCFECDD)
    val onPrimaryContainer = Color(0xFF0B2E21)

    val secondary = Color(0xFF4B8E74)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFE3F4EA)
    val onSecondaryContainer = Color(0xFF123D2E)

    val background = Color(0xFFF4FBF6)
    val onBackground = Color(0xFF102019)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF14221B)
    val surfaceVariant = Color(0xFFE8F3EC)
    val onSurfaceVariant = Color(0xFF385246)
    val outline = Color(0xFF9AB8A9)

    val accent = Color(0xFF8FCB9B)
    val accentSoft = Color(0xFFDDF1E2)
    val success = Color(0xFF2A7A57)
    val errorSoft = Color(0xFFF5E5E7)
    val divider = Color(0xFFD4E6DA)

    fun colorScheme() = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = Color(0xFFB3261E),
        onError = Color(0xFFFFFFFF),
        errorContainer = errorSoft,
        onErrorContainer = Color(0xFF601410)
    )
}

@Composable
private fun SelectableText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    SelectionContainer {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentMCPScreen(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    MaterialTheme(colorScheme = AiAgentMCPScreenTheme.colorScheme()) {
        AiAgentMCPScreenContent(
            currentScreen = currentScreen,
            onSelectScreen = onSelectScreen
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiAgentMCPScreenContent(
    currentScreen: RootScreen,
    onSelectScreen: (RootScreen) -> Unit
) {
    val scope = rememberCoroutineScope()
    val remoteMcpService = remember { RemoteMcpService() }
    val tools = remember { mutableStateListOf<McpToolInfo>() }
    var screensMenuExpanded by remember { mutableStateOf(false) }
    var serverMenuExpanded by remember { mutableStateOf(false) }
    var selectedServer by remember { mutableStateOf(mcpServerOptions.first()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("Готово к загрузке инструментов ${selectedServer.title}.") }

    fun loadTools() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            errorMessage = null
            statusText = "Запрашиваю список инструментов у ${selectedServer.title}..."
            runCatching {
                remoteMcpService.listAvailableTools(selectedServer.url)
            }.onSuccess { loadedTools ->
                tools.clear()
                tools += loadedTools
                statusText = if (loadedTools.isEmpty()) {
                    "${selectedServer.title} ответил, но список инструментов пуст."
                } else {
                    "Получено инструментов: ${loadedTools.size}"
                }
            }.onFailure { error ->
                errorMessage = error.message ?: error::class.simpleName ?: "Неизвестная ошибка"
                statusText = "Не удалось получить инструменты."
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        TextButton(
                            onClick = { screensMenuExpanded = true },
                            enabled = !isLoading
                        ) {
                            SelectionContainer {
                                Text("Microsoft Learn MCP")
                            }
                        }
                        DropdownMenu(
                            expanded = screensMenuExpanded,
                            onDismissRequest = { screensMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    SelectableText(if (currentScreen == RootScreen.AiAgentMCP) "Microsoft Learn MCP ✓" else "Microsoft Learn MCP")
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.AiAgentMCP)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    SelectableText(if (currentScreen == RootScreen.AiStateAgent) "AiStateAgent ✓" else "AiStateAgent")
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.AiStateAgent)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    SelectableText(if (currentScreen == RootScreen.AiWeek3) "Ai неделя 3 ✓" else "Ai неделя 3")
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.AiWeek3)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    SelectableText(if (currentScreen == RootScreen.AiAgent) "AiAgent ✓" else "AiAgent")
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.AiAgent)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    SelectableText(if (currentScreen == RootScreen.App) "App ✓" else "App")
                                },
                                onClick = {
                                    screensMenuExpanded = false
                                    onSelectScreen(RootScreen.App)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AiAgentMCPScreenTheme.secondaryContainer,
                    titleContentColor = AiAgentMCPScreenTheme.onSecondaryContainer,
                    actionIconContentColor = AiAgentMCPScreenTheme.onSecondaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = AiAgentMCPScreenTheme.primaryContainer,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SelectableText(
                    text = "Новый экран для ${selectedServer.title}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                SelectableText(
                    text = "Экран подключается к выбранному remote endpoint и показывает доступные инструменты этого MCP-сервера.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(AiAgentMCPScreenTheme.accent, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        SelectableText(
                            text = "MCP over HTTP",
                            color = AiAgentMCPScreenTheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(AiAgentMCPScreenTheme.accentSoft, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        SelectableText(
                            text = "Endpoint: ${selectedServer.url}",
                            color = AiAgentMCPScreenTheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Box {
                    TextButton(
                        onClick = { serverMenuExpanded = true },
                        enabled = !isLoading
                    ) {
                        SelectableText(
                            text = "MCP server: ${selectedServer.title}",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    DropdownMenu(
                        expanded = serverMenuExpanded,
                        onDismissRequest = { serverMenuExpanded = false }
                    ) {
                        mcpServerOptions.forEach { server ->
                            DropdownMenuItem(
                                text = {
                                    SelectableText(
                                        if (selectedServer.url == server.url) "${server.title} [selected]" else server.title
                                    )
                                },
                                onClick = {
                                    selectedServer = server
                                    serverMenuExpanded = false
                                    tools.clear()
                                    errorMessage = null
                                    statusText = "Выбран сервер: ${server.title}"
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = ::loadTools,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        SelectionContainer {
                            Text("Получить инструменты: ${selectedServer.title}")
                        }
                    }
                }
                SelectableText(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectableText(
                        text = "Ошибка подключения",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    SelectableText(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, AiAgentMCPScreenTheme.divider, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SelectableText(
                    text = "Доступные инструменты",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (tools.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(AiAgentMCPScreenTheme.accentSoft, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        SelectableText(
                            text = "Нажмите кнопку выше, чтобы запросить `tools/list` у ${selectedServer.title}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tools, key = { it.name }) { tool ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AiAgentMCPScreenTheme.surfaceVariant, RoundedCornerShape(18.dp))
                                    .border(1.dp, AiAgentMCPScreenTheme.divider, RoundedCornerShape(18.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SelectableText(
                                    text = tool.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AiAgentMCPScreenTheme.success
                                )
                                SelectableText(
                                    text = if (tool.description.isBlank()) "Описание не передано сервером." else tool.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
