# AGENTS.md

## Project Rules
- Пиши сообщения коммитов и их описания на русском языке.
- Используй русский язык для заголовка и тела commit message.
- Не писать миграции базы данных.
- Для экрана `AiAgentMain` поддерживай модульную структуру: `AiAgentMainScreen.kt` только orchestration/UI-композиция экрана, модели и enum — в `AiAgentMainModels.kt`, JSON-парсинг/представление — в `AiAgentMainJson.kt`, тема — в `AiAgentMainTheme.kt`, пузырь сообщения — в `AiAgentMainBubble.kt`. Новую логику добавлять в профильные файлы, а не в один монолитный экран.
