package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AiAgentBubble(message: AiAgentMessage) {
    val userBubbleColor = AiAgentMainScreenTheme.userBubble
    val userTextColor = AiAgentMainScreenTheme.onUserBubble
    val assistantBubbleColor = AiAgentMainScreenTheme.assistantBubble
    val assistantTextColor = AiAgentMainScreenTheme.onAssistantBubble

    val parsedElement = remember(message.text) {
        if (message.isUser) null else tryParseJson(message.text)
    }
    var showParsed by remember { mutableStateOf(true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(
                    color = if (message.isUser) userBubbleColor else assistantBubbleColor,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.isUser && parsedElement != null && showParsed) {
                    SelectionContainer {
                        JsonTreeView(parsedElement)
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = if (message.isUser) userTextColor else assistantTextColor
                        )
                    }
                }
                Text(
                    text = message.displayParamsInfo(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isUser) userTextColor.copy(alpha = 0.7f)
                    else assistantTextColor.copy(alpha = 0.7f)
                )
                if (!message.isUser && parsedElement != null) {
                    TextButton(
                        onClick = { showParsed = !showParsed },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (showParsed) "Source" else "Tree",
                            style = MaterialTheme.typography.labelSmall,
                            color = assistantTextColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
