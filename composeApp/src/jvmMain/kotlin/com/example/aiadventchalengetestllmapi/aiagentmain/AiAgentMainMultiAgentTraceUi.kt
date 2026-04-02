package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun MultiAgentTraceSubagentGroupBlock(
    group: MultiAgentTraceSubagentGroup,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Субагент: ${group.title}",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = if (expanded) "Свернуть" else "Развернуть",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.events.forEach { event ->
                    MultiAgentTraceEventBubble(event = event)
                }
            }
        }
    }
}

@Composable
internal fun MultiAgentTraceEventBubble(event: MultiAgentTraceEventRecord) {
    val metadata = parseMultiAgentTraceMetadata(event.metadataJson)
    val phase = metadata.tracePhase?.wireValue ?: metadata.tracePhaseRaw ?: "trace"
    val fullMessage = event.message.trimEnd()
    val isLongMessage = fullMessage.length > 1200 || fullMessage.count { it == '\n' } > 24
    var expanded by remember(event.id, event.createdAt, event.actorKey) { mutableStateOf(false) }
    val displayMessage = if (isLongMessage && !expanded) {
        compactTracePreview(fullMessage, maxLines = 18)
    } else {
        fullMessage
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.82f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text("phase: $phase", style = MaterialTheme.typography.labelSmall) },
                enabled = false,
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
            AssistChip(
                onClick = {},
                label = { Text("${event.actorType}:${event.actorKey}", style = MaterialTheme.typography.labelSmall) },
                enabled = false,
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    disabledLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
        SelectionContainer {
            Text(
                text = displayMessage,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp
                )
            )
        }
        if (isLongMessage) {
            Text(
                text = if (expanded) "Свернуть" else "Показать полностью",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}

private fun compactTracePreview(message: String, maxLines: Int): String {
    val lines = message.lines()
    if (lines.size <= maxLines) return message
    val head = lines.take(maxLines).joinToString("\n")
    val hidden = lines.size - maxLines
    return "$head\n... [+${hidden} lines hidden]"
}
