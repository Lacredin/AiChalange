Ты работаешь в проекте:
C:\AndroidProjects\Example\AiChalange\AiAdventChalengeTestLlmApi

Задача:
Реализовать следующую итерацию мультиагентного режима в `AiAgentMain` с заделом на будущее.
Фокус: инструментальная оркестрация через единый gateway, preflight, журнал tool-вызовов, MCP-субагент, валидация с доказательствами.

Обязательные правила проекта:
- Соблюдай AGENTS.md в корне и в проекте.
- Не писать классические миграции БД.
- Для `AiAgentMain` сохраняй модульность: `AiAgentMainScreen.kt` только UI/оркестрация, логику выносить в профильные файлы.
- Коммиты и commit message на русском.
- Не ломай существующий функционал `/help`, `/review-pr`, `/auth-token`, RAG/MCP, текущий мультиагентный режим.

## Цель итерации (MVP)
1) Добавить единый `ToolGateway` для RAG/MCP/ProjectFS.
2) Добавить в оркестратор этапы `tool_plan` и `preflight`.
3) Добавить БД-журнал инструментальных вызовов `multi_agent_tool_calls`.
4) Добавить tool-aware субагент `mcp_executor`.
5) Усилить валидацию: итог должен ссылаться на evidence (tool call или RAG-контекст).

---

## 1. Архитектурные изменения

### 1.1 ToolGateway
Добавь новый слой, например:
- `AiAgentMainToolGateway.kt`
- `AiAgentMainToolGatewayModels.kt`

Сделай единый интерфейс:
- `suspend fun execute(request: ToolGatewayRequest): ToolGatewayResult`

Поддерживаемые типы:
- `RAG_QUERY`
- `MCP_CALL`
- `PROJECT_FS_SUMMARY` (чтение структуры/сниппетов выбранной папки проекта)

`ToolGatewayResult` должен содержать:
- `success`
- `toolKind`
- `normalizedOutput`
- `rawOutput`
- `errorCode`
- `errorMessage`
- `latencyMs`
- `metadataJson`

Внутри gateway переиспользуй текущие механизмы:
- RAG (`buildRagPayloadForPrompt` / существующие retrieval helpers),
- MCP (`RemoteMcpService`),
- project folder context (существующий provider из agent mode).

Добавь timeout/retry минимум для MCP-вызовов.

### 1.2 Оркестратор: tool_plan + preflight
Расширь мультиагентный оркестратор:
- Перед исполнением шагов он строит `tool_plan` (JSON от LLM).
- Затем выполняет `preflight`:
  - доступность MCP (хотя бы один server/tool из плана),
  - доступность RAG-данных (если нужны),
  - наличие project folder (если нужен FS инструмент).
- Если preflight не проходит:
  - либо деградация (перепланирование без инструмента),
  - либо `NEED_CLARIFICATION`,
  - либо `IMPOSSIBLE`.
- Логи preflight обязаны идти в trace.

Контракт `tool_plan` (пример):
```json
{
  "requires_tools": true,
  "tools": [
    {"tool_kind":"MCP_CALL","reason":"...","params":{"toolName":"...","arguments":{}}},
    {"tool_kind":"RAG_QUERY","reason":"...","params":{"query":"..."}}
  ],
  "fallback_policy":"DEGRADE|FAIL"
}
```

### 1.3 MCP-субагент
Добавь отдельного субагента `mcp_executor`:
- Получает от оркестратора задачу вызова MCP.
- Использует `ToolGateway` вместо прямого `RemoteMcpService`.
- Возвращает структурированный результат:
  - `summary`
  - `tool_call_refs` (список id вызовов)
  - `diagnostics`

### 1.4 Валидация с evidence
Расширь валидационный контракт:
- Финальный ответ должен содержать evidence references:
  - `tool_call_ids`
  - и/или `rag_evidence` (краткие ссылки на retrieval chunks).
- Если evidence недостаточен -> `REWORK`.
- Это должно работать минимум для ветки `DELEGATE`.

---

## 2. Изменения в БД (без миграций)

Расширь SQLDelight схему `aiagentmaindb`:
- Добавь таблицу `multi_agent_tool_calls`:
  - `id` PK
  - `run_id` FK -> `multi_agent_runs`
  - `step_id` FK -> `multi_agent_steps` (nullable, если preflight/global)
  - `tool_kind` TEXT
  - `request_payload` TEXT
  - `response_payload` TEXT
  - `status` TEXT (`success|error|timeout|skipped`)
  - `error_code` TEXT
  - `error_message` TEXT
  - `latency_ms` INTEGER
  - `created_at` INTEGER
- Добавь select/insert/update запросы.
- Добавь cleanup-запросы при удалении чатов/ранов.

Обнови `DatabaseFactory.jvm.kt`:
- учти новую таблицу и колонки в `hasCompatibleSchema`.
- сохрани текущую стратегию пересоздания БД при несовместимости.

---

## 3. Интеграция в существующий код

### 3.1 AiAgentMainScreen
- Не переноси бизнес-логику в Screen.
- В screen:
  - инициализация gateway/orchestrator use-cases,
  - передача контекста (выбранный проект, MCP-сервера, RAG enable),
  - отображение статусов.

### 3.2 Trace/логирование
- Все tool-вызовы и preflight события логируй в:
  - `multi_agent_events` (читаемый trace),
  - `multi_agent_tool_calls` (технический журнал).
- В обычный чат только короткие статусы:
  - "Проверяю доступность инструментов..."
  - "Выполняю шаг 2/4 через mcp_executor..."
  - "Готово / Нужна доработка / Нужны уточнения"

### 3.3 Совместимость
- Если `multi_agent_enabled = false`, поведение без изменений.
- Если `multi_agent_enabled = true`, но tools не нужны, режим должен работать как раньше (DIRECT/DELEGATE без tool_plan).
- `/help`, `/review-pr`, `/auth-token` не ломать.

---

## 4. Файлы (ориентир)

Обновить:
- `composeApp/src/commonMain/sqldelight_aiagentmain/.../ChatHistory.sq`
- `composeApp/src/jvmMain/kotlin/.../aiagentmaindb/DatabaseFactory.jvm.kt`
- `composeApp/src/jvmMain/kotlin/.../aiagentmain/AiAgentMainScreen.kt`
- `composeApp/src/jvmMain/kotlin/.../aiagentmain/AiAgentMainMultiAgentOrchestrator.kt`
- `composeApp/src/jvmMain/kotlin/.../aiagentmain/AiAgentMainMultiAgentModels.kt`
- `composeApp/src/jvmMain/kotlin/.../aiagentmain/AiAgentMainMultiAgentPrompts.kt`
- `composeApp/src/jvmMain/kotlin/.../aiagentmain/AiAgentMainMultiAgentParser.kt`
- `composeApp/src/jvmMain/kotlin/.../aiagentmain/AiAgentMainMultiAgentSubAgents.kt`

Добавить:
- `AiAgentMainToolGateway.kt`
- `AiAgentMainToolGatewayModels.kt`
- при необходимости `AiAgentMainMultiAgentTooling.kt` / `AiAgentMainMultiAgentValidation.kt`

---

## 5. Критерии приемки

1. Компиляция проходит:
- `.\gradlew.bat :composeApp:compileKotlinJvm`

2. В мультиагентном run видно:
- сгенерированный `tool_plan`,
- результат `preflight`,
- tool-вызовы в `multi_agent_tool_calls`,
- trace-сообщения в `multi_agent_events`.

3. Для задач с делегированием:
- оркестратор может вызвать `mcp_executor`,
- валидатор требует evidence,
- при отсутствии evidence возвращается `REWORK` и выполняется доработка.

4. При удалении чата/всех чатов:
- связанные записи run/step/event/tool_calls корректно очищаются.
- 

5. Регрессий в старом режиме нет:
- обычный чат работает,
- agent команды работают,
- текущие toggles и панели не ломаются.

---

## 6. Формат ответа после выполнения

В финальном ответе покажи:
1) Кратко: что реализовано архитектурно.
2) Список измененных файлов.
3) Как реализованы `tool_plan`, `preflight`, `ToolGateway`, `mcp_executor`, evidence validation.
4) Результат проверки сборки.
5) Ограничения текущей итерации и что делать в следующей.
