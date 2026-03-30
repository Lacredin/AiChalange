package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

internal val lenientJson = Json { ignoreUnknownKeys = true }

internal fun tryParseJson(text: String): JsonElement? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null
    return try {
        lenientJson.parseToJsonElement(trimmed)
    } catch (_: Exception) {
        null
    }
}

internal fun tryParseJsonExecution(text: String): JsonElement? {
    tryParseJson(text)?.let { return it }

    val codeBlock = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!codeBlock.isNullOrBlank()) {
        tryParseJson(codeBlock)?.let { return it }
    }

    val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
    val end = text.lastIndexOf('}').takeIf { it > start } ?: return null
    return tryParseJson(text.substring(start, end + 1))
}

internal fun jsonElementToAny(value: JsonElement): Any? = when (value) {
    JsonNull -> null
    is JsonPrimitive -> when {
        value.booleanOrNull != null -> value.boolean
        value.intOrNull != null -> value.int
        value.longOrNull != null -> value.long
        value.doubleOrNull != null -> value.double
        else -> value.contentOrNull
    }
    is JsonObject -> value.mapValues { (_, v) -> jsonElementToAny(v) }
    is JsonArray -> value.map { jsonElementToAny(it) }
}

@Composable
internal fun JsonTreeView(element: JsonElement, indent: Int = 0) {
    val pad = (indent * 16).dp
    when (element) {
        is JsonObject -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                element.entries.forEach { (key, value) ->
                    when (value) {
                        is JsonObject, is JsonArray -> {
                            Column(
                                modifier = Modifier.padding(start = pad),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "$key:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                JsonTreeView(value, indent + 1)
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.padding(start = pad),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "$key:",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                JsonPrimitiveText(value)
                            }
                        }
                    }
                }
            }
        }
        is JsonArray -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                element.forEachIndexed { index, value ->
                    when (value) {
                        is JsonObject, is JsonArray -> {
                            Column(
                                modifier = Modifier.padding(start = pad),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "[$index]",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                JsonTreeView(value, indent + 1)
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier.padding(start = pad),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "*",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                JsonPrimitiveText(value)
                            }
                        }
                    }
                }
            }
        }
        is JsonPrimitive -> JsonPrimitiveText(element)
        is JsonNull -> Text(
            text = "-",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun JsonPrimitiveText(element: JsonElement) {
    val primitive = element as? JsonPrimitive ?: return
    val boolVal = primitive.booleanOrNull
    val text: String
    val color: Color
    when {
        primitive is JsonNull -> {
            text = "-"
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        }
        boolVal != null -> {
            text = if (boolVal) "true" else "false"
            color = if (boolVal) Color(0xFF2E7D32) else Color(0xFFC62828)
        }
        else -> {
            text = primitive.contentOrNull ?: "null"
            color = MaterialTheme.colorScheme.onSurface
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color
    )
}
