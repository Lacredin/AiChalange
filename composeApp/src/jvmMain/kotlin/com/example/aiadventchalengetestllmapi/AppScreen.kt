package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        ChatContent(
            modifier = Modifier.fillMaxSize(),
            allowApiKeyInput = true,
            showSystemPromptField = true,
            showAdvancedParams = true
        )
    }
}
