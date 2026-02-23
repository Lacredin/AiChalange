package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAgentScreen(onOpenApp: () -> Unit) {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Ai Агент") },
                    actions = {
                        IconButton(onClick = onOpenApp) {
                            Text(text = "⚙", fontSize = 18.sp)
                        }
                    }
                )
            }
        ) { innerPadding ->
            ChatContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                allowApiKeyInput = false,
                showSystemPromptField = false,
                showAdvancedParams = false
            )
        }
    }
}
