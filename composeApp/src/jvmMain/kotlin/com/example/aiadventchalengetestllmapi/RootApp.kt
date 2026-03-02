package com.example.aiadventchalengetestllmapi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.aiadventchalengetestllmapi.aiweek3.AiWeek3Screen

enum class RootScreen {
    AiWeek3,
    AiAgent,
    App
}

@Composable
fun RootApp() {
    var currentScreen by remember { mutableStateOf(RootScreen.AiWeek3) }

    when (currentScreen) {
        RootScreen.AiWeek3 -> AiWeek3Screen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.AiAgent -> AiAgentScreen(
            currentScreen = currentScreen,
            onSelectScreen = { selectedScreen -> currentScreen = selectedScreen }
        )

        RootScreen.App -> App(
            onBackClick = { currentScreen = RootScreen.AiWeek3 }
        )
    }
}
