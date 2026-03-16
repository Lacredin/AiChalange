package com.example.aiadventchalengetestllmapi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.aiadventchalengetestllmapi.aiagentrag.AiAgentRAGScreen
import com.example.aiadventchalengetestllmapi.aiagentmain.AiAgentMainScreen
import com.example.aiadventchalengetestllmapi.aiweek3.AiWeek3Screen
import com.example.aiadventchalengetestllmapi.aistateagent.AiStateAgentScreen
import com.example.aiadventchalengetestllmapi.aiagentmcp.AiAgentMCPScreen

enum class RootScreen {
    AiAgentRAG,
    AiAgentMain,
    AiAgentMCP,
    AiStateAgent,
    AiWeek3,
    AiAgent,
    App
}

@Composable
fun RootApp() {
    var currentScreen by remember { mutableStateOf(RootScreen.AiAgentRAG) }

    when (currentScreen) {
        RootScreen.AiAgentRAG -> AiAgentRAGScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.AiAgentMain -> AiAgentMainScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.AiAgentMCP -> AiAgentMCPScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.AiStateAgent -> AiStateAgentScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.AiWeek3 -> AiWeek3Screen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.AiAgent -> AiAgentScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.App -> App(
            onBackClick = { currentScreen = RootScreen.AiAgentRAG }
        )
    }
}
