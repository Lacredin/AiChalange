package com.example.aiadventchalengetestllmapi.aiagentmain

import java.util.prefs.Preferences

// Copy of persistence mechanism from AiAgentRAGScreen.
// Kept separate so AiAgentMain can evolve without edits in AiAgentRAG.
private const val RAG_PREF_NODE = "com.example.aiadventchalengetestllmapi.aiagentrag"
private const val RAG_USE_KEY = "use_rag_enabled"

internal fun loadAiAgentMainRagEnabled(): Boolean =
    Preferences.userRoot().node(RAG_PREF_NODE).getBoolean(RAG_USE_KEY, true)

internal fun saveAiAgentMainRagEnabled(enabled: Boolean) {
    Preferences.userRoot().node(RAG_PREF_NODE).putBoolean(RAG_USE_KEY, enabled)
}
