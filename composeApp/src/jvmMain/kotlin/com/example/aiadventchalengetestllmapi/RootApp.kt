package com.example.aiadventchalengetestllmapi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class RootScreen {
    AiAgent,
    App
}

@Composable
fun RootApp() {
    var currentScreen by remember { mutableStateOf(RootScreen.AiAgent) }

    when (currentScreen) {
        RootScreen.AiAgent -> AiAgentScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.App -> App(
            onBackClick = { currentScreen = RootScreen.AiAgent }
        )
    }
}
