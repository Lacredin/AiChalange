package com.example.aiadventchalengetestllmapi.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class GigaChatApi(
    private val httpClient: HttpClient = NetworkClient.httpClient,
    private val baseUrl: String = "https://gigachat.devices.sberbank.ru/api/v1"
) {
    suspend fun createChatCompletion(
        accessToken: String,
        request: DeepSeekChatRequest
    ): DeepSeekChatResponse {
        return httpClient.post("$baseUrl/chat/completions") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }
}
