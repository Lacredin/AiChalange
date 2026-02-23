package com.example.aiadventchalengetestllmapi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private enum class RootScreen {
    AiAgent,
    App
}

@Composable
fun RootApp() {
    var currentScreen by remember { mutableStateOf(RootScreen.AiAgent) }

    when (currentScreen) {
        RootScreen.AiAgent -> AiAgentScreen(
            onOpenApp = { currentScreen = RootScreen.App }
        )

        RootScreen.App -> App()
    }
}
