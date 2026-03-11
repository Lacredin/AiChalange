package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val EXECUTION_STEP_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий один шаг плана.

Контекст задачи:
{task_context}

Контекст исправления (если есть):
{recovery_context}

Текущий шаг (step_id: {step_id}):
{step_description}

Инструмент, указанный в плане для этого шага:
{step_tool_name}

Доступные MCP-инструменты:
{available_tools_formatted}

Результаты предыдущих шагов:
{previous_results_formatted}

Критично:
- Если шаг явно требует инструмент (например, step.tool не null или описание шага указывает на конкретный tool),
  то при отсутствии внешних блокеров верни tool_request.should_call=true.
- Предварительное открытие WebSocket НЕ является блокером: платформа сама поднимет соединение по endpoint из tool_request.
  Не отвечай should_call=false только из-за того, что "соединение пока не открыто".
- Если в инструкции инструмента требуется clientId для WS и для arguments, ты обязан сгенерировать его сам
  (например: "agent-step-{step_id}") и использовать один и тот же clientId в endpoint и arguments.

Правила выполнения:
1. Не вызывай инструмент автоматически только потому, что он указан в плане.
2. Сначала проанализируй описание шага и инструкцию инструмента.
3. Если нужен MCP-инструмент, верни tool_request:
   - tool_name: точное имя инструмента;
   - endpoint: endpoint для подключения (если в инструкции есть отдельный WS endpoint, укажи его; не подставляй /mcp по умолчанию);
   - arguments: строго по input_schema, с корректными именами полей и типами;
   - reason: почему вызов нужен на этом шаге.
4. should_call=false допускается только если инструмент реально не нужен для этого шага
   или не хватает обязательных данных, которые невозможно вывести из контекста.
5. Возвращай строго JSON.

Формат ответа:
{
  "result": {
    "type": "text" | "code" | "data" | "error",
    "content": "содержимое результата или промежуточный вывод",
    "metadata": {
      "format": "markdown" | "json" | "python" | "plain",
      "confidence": 0.0
    }
  },
  "reasoning": "краткое объяснение хода решения",
  "tool_request": {
    "should_call": false,
    "tool_name": null,
    "endpoint": null,
    "reason": null,
    "arguments": {}
  },
  "next_step_ready": true
}

Пример для timer.start (если нужно поставить таймер на 10 секунд):
"tool_request": {
  "should_call": true,
  "tool_name": "timer.start",
  "endpoint": "ws://127.0.0.1:8080/ws?clientId=agent-step-{step_id}",
  "reason": "Запуск таймера — цель текущего шага",
  "arguments": {
    "clientId": "agent-step-{step_id}",
    "delaySeconds": 10,
    "message": "Таймер сработал"
  }
}

После фактического вызова инструмента этот же шаг будет запущен повторно с его результатом в контексте."""

@Serializable
internal data class PlanStepJson(
    @SerialName("step_id") val stepId: Int,
    val description: String,
    val tool: String? = null,
    val dependencies: List<Int> = emptyList()
)

@Serializable
internal data class PlanJson(
    val goal: String = "",
    val steps: List<PlanStepJson> = emptyList(),
    @SerialName("validation_criteria") val validationCriteria: List<String> = emptyList(),
    @SerialName("clarification_needed") val clarificationNeeded: String? = null
)
