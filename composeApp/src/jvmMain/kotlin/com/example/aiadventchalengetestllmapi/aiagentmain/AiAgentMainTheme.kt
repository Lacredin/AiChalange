package com.example.aiadventchalengetestllmapi.aiagentmain

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal object AiAgentMainScreenTheme {
    val primary = Color(0xFFE65100)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFFFE0B2)
    val onPrimaryContainer = Color(0xFF4A1800)
    val secondary = Color(0xFFBF360C)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFFF3E0)
    val onSecondaryContainer = Color(0xFF3E1000)
    val background = Color(0xFFFFFBF5)
    val onBackground = Color(0xFF1A0800)
    val surface = Color(0xFFFFFFFF)
    val onSurface = Color(0xFF1A0800)
    val surfaceVariant = Color(0xFFFFF3E0)
    val onSurfaceVariant = Color(0xFF4A1800)
    val outline = Color(0xFFFFCC80)
    val userBubble = Color(0xFFC47A52)
    val onUserBubble = Color(0xFFFFFFFF)
    val assistantBubble = Color(0xFFE2C9A8)
    val onAssistantBubble = Color(0xFF2D0F00)
    val divider = Color(0xFFFFE0B2)
    val topBarContainer = Color(0xFFFFF3E0)
    val topBarContent = Color(0xFF6D2B00)

    fun colorScheme() = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF)
    )
}
