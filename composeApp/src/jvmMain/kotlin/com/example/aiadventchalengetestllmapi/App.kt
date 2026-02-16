package com.example.aiadventchalengetestllmapi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.aiadventchalengetestllmapi.network.DeepSeekApi
import com.example.aiadventchalengetestllmapi.network.DeepSeekChatRequest
import com.example.aiadventchalengetestllmapi.network.DeepSeekMessage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

import aiadventchalengetestllmapi.composeapp.generated.resources.Res
import aiadventchalengetestllmapi.composeapp.generated.resources.compose_multiplatform

private const val DEEPSEEK_API_KEY = "REDACTED_DEEPSEEK_KEY"
private const val DEEPSEEK_PROMPT = "Проверка работы API, всё хорошо?"

@Composable
@Preview
fun App() {
    MaterialTheme {
        val scope = rememberCoroutineScope()
        val deepSeekApi = remember { DeepSeekApi() }
        val greeting = remember { Greeting().greet() }
        var isLoading by remember { mutableStateOf(false) }
        var resultText by remember { mutableStateOf("Нажмите кнопку для проверки DeepSeek API") }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        resultText = "Отправка запроса..."
                        resultText = try {
                            val response = deepSeekApi.createChatCompletion(
                                apiKey = DEEPSEEK_API_KEY,
                                request = DeepSeekChatRequest(
                                    model = "deepseek-chat",
                                    messages = listOf(DeepSeekMessage(role = "user", content = DEEPSEEK_PROMPT))
                                )
                            )
                            response.choices.firstOrNull()?.message?.content
                                ?: "Ответ получен, но choices пустой."
                        } catch (e: Exception) {
                            "Ошибка запроса: ${e.message ?: "неизвестная ошибка"}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Загрузка..." else "Проверить DeepSeek API")
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(painterResource(Res.drawable.compose_multiplatform), null)
                Text("Compose: $greeting")
                Text(resultText)
            }
        }
    }
}
