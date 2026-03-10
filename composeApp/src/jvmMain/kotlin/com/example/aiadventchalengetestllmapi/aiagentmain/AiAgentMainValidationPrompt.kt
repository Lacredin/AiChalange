package com.example.aiadventchalengetestllmapi.aiagentmain

internal const val VALIDATION_FORMAL_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий формальную проверку результатов выполнения задачи.

Исходная цель задачи:
{original_goal}

Критерии формальной проверки:
{validation_criteria}

Результаты выполнения:
{execution_results}

Инструкция:
1. Проверь результаты на соответствие каждому формальному критерию.
2. Формальные критерии включают тип данных, структуру, формат, обязательные поля и диапазоны значений.
3. Для каждого критерия укажи, пройден он или нет.
4. Если критерий не пройден, объясни причину.
5. Если обнаружена проблема, определи конкретный шаг плана, на котором она возникла.
6. Предложи конкретное исправление и укажи, с какого шага нужно перезапустить выполнение.

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
  "detected_problem": null,
  "failed_step_id": null,
  "retry_from_step_id": null,
  "proposed_solution": null,
  "can_auto_fix": false,
  "recommendation": "перейти в DONE"
}"""

internal const val INVARIANT_PLAN_CHECK_PROMPT_TEMPLATE = """Ты — ИИ-агент, проверяющий соответствие плана заданным ограничениям.

План задачи (JSON):
{plan_json}

Инварианты — обязательные ограничения, которые план должен соблюдать:
{invariants_list}

Последнее изменение от пользователя:
{user_edit_request}

Инструкция:
1. Для каждого инварианта определи, нарушает ли его план.
2. Если инвариант не применим напрямую, считай его выполненным.
3. overall_passed = true только если пройдены все инварианты.
4. Для каждого нарушения определи источник: "ai" или "user".
5. ai_violated = true, если хотя бы одно нарушение возникло по инициативе ИИ.

Ответ строго в формате JSON:
{
  "invariant_checks": [
    {
      "invariant_key": "ключ инварианта",
      "invariant_value": "значение инварианта",
      "passed": true,
      "violation_details": null,
      "violation_source": "ai"
    }
  ],
  "overall_passed": true,
  "ai_violated": false,
  "summary": "краткое объяснение"
}"""

internal const val VALIDATION_SEMANTIC_PROMPT_TEMPLATE = """Ты — ИИ-агент, выполняющий смысловую проверку результатов выполнения задачи.

Исходная цель задачи:
{original_goal}

Контекст задачи:
{task_context}

Критерии смысловой проверки:
{validation_criteria}

Результаты выполнения:
{execution_results}

Предыдущие шаги и промежуточные результаты:
{previous_steps_results}

Инструкция:
1. Оцени, насколько результат соответствует цели задачи.
2. Проверь логические ошибки, противоречия и галлюцинации.
3. Оцени полноту ответа и релевантность результату пользователя.
4. Если результат неудовлетворительный, определи конкретную проблему.
5. Обязательно определи шаг плана, при выполнении которого возникла проблема.
6. Предложи конкретное решение и шаг, с которого нужно повторить выполнение.

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
  "overall_passed": true,
  "overall_assessment": "good",
  "needs_replanning": false,
  "detected_problem": null,
  "failed_step_id": null,
  "retry_from_step_id": null,
  "proposed_solution": null,
  "feedback_for_planning": null,
  "recommendation": "перейти в DONE"
}"""
