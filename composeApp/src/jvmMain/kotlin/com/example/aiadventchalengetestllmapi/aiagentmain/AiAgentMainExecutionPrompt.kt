package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val EXECUTION_STEP_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий аналитический шаг плана.
Контекст задачи:
{task_context}

Контекст исправления (если есть):
{recovery_context}

Текущий шаг (step_id: {step_id}):
{step_description}

Результаты предыдущих шагов:
{previous_results_formatted}

Инструкция:
1. Выполни шаг самостоятельно, используя свои знания и результаты предыдущих шагов.
2. Если шаг подразумевает генерацию текста, кода или анализа — создай это.
3. Если шаг подразумевает преобразование данных — выполни преобразование.
4. Если передан контекст исправления, обязательно учти его при повторном выполнении.
5. Верни результат в структурированном виде.

Ответ должен быть в формате JSON:
{
  "result": {
    "type": "text" | "code" | "data" | "error",
    "content": "содержимое результата",
    "metadata": {
      "format": "markdown" | "json" | "python" | "plain",
      "confidence": 0.0-1.0
    }
  },
  "reasoning": "объяснение хода мыслей",
  "next_step_ready": true
}"""

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
