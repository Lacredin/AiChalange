package com.example.aiadventchalengetestllmapi.aistateagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal const val EXECUTION_STEP_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий конкретный шаг общего плана.
Контекст задачи:
{task_context}

Текущий шаг (step_id: {step_id}):
{step_description}
Результаты предыдущих шагов:
{previous_results_formatted}
Инструкция:
1. Проанализируй результаты предыдущих шагов и контекст задачи.
2. Если для выполнения шага нужны данные, которых нет в предыдущих результатах, запроси их в поле "missing_data".
Ответ должен быть строго в формате JSON:
{
  "tool_call": {
    "tool": "имя_инструмента",
    "params": {
      "param1": "значение1",
      "param2": "значение2"
    }
  },
  "reasoning": "Краткое объяснение, почему выбраны такие параметры",
  "missing_data": null
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
