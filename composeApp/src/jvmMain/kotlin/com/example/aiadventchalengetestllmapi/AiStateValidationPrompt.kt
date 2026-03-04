package com.example.aiadventchalengetestllmapi.aistateagent

internal const val VALIDATION_FORMAL_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий формальную проверку результатов выполнения задачи.

Исходная цель задачи:
{original_goal}

Критерии формальной проверки:
{validation_criteria}

Результаты выполнения (данные для проверки):
{execution_results}
Инструкция:
1. Проверь результаты на соответствие каждому формальному критерию.
2. Формальные критерии включают: тип данных, структуру, формат, наличие обязательных полей, диапазоны значений.
3. Для каждого критерия укажи, пройден он или нет.
4. Если какой-то критерий не пройден, объясни причину.
Ответ должен быть строго в формате JSON:
{
  "validation_type": "formal",
  "criteria_results": [
    {
      "criterion": "описание критерия",
      "passed": true,
      "expected": "что ожидалось",
      "actual": "что получено",
      "error_details": null
    }
  ],
  "overall_passed": true,
  "can_auto_fix": false,
  "recommendation": "перейти в DONE"
}"""

internal const val VALIDATION_SEMANTIC_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий смысловую проверку результатов выполнения задачи.

Исходная цель задачи (что требовалось получить):
{original_goal}

Контекст задачи (дополнительные требования):
{task_context}

Критерии семантической проверки:
{validation_criteria}

Результаты выполнения:
{execution_results}

Предыдущие шаги и промежуточные результаты:
{previous_steps_results}
Инструкция:
1. Оцени, насколько результат соответствует СМЫСЛУ и ЦЕЛИ задачи, а не только формальным критериям.
2. Проверь на логические ошибки, противоречия, галлюцинации.
3. Оцени полноту ответа (не упущено ли что-то важное).
4. Проверь, решает ли результат исходную проблему пользователя.
5. Если результат неудовлетворительный, определи причину: неверные данные, неполнота, нерелевантность.

Ответ должен быть в формате JSON:
{
  "validation_type": "semantic",
  "analysis": {
    "goal_alignment": 90,
    "completeness": 85,
    "accuracy": 95,
    "coherence": 90
  },
  "issues": [],
  "strengths": ["список сильных сторон результата"],
  "overall_assessment": "good",
  "needs_replanning": false,
  "feedback_for_planning": null,
  "recommendation": "перейти в DONE"
}"""
