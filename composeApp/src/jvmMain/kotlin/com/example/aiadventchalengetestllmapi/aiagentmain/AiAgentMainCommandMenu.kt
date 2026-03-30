package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
internal fun AiAgentMainCommandMenu(
    expanded: Boolean,
    suggestions: List<AgentCommandSuggestion>,
    onDismiss: () -> Unit,
    onSelect: (AgentCommandSuggestion) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        suggestions.forEach { item ->
            DropdownMenuItem(
                text = {
                    androidx.compose.foundation.layout.Column {
                        Text(
                            text = item.hint,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                onClick = { onSelect(item) }
            )
        }
    }
}
