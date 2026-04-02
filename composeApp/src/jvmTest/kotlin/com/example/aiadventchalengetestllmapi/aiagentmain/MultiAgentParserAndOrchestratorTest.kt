package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MultiAgentParserAndOrchestratorTest {
    @Test
    fun parseMcpSelection_handlesNullArrays() {
        val raw = """
            {
              "action": "NEED_CLARIFICATION",
              "reason": "not enough data",
              "mcp_call": {
                "toolName": "search_docs",
                "endpoint": "http://localhost",
                "arguments": null,
                "output_filter": "keep only important"
              },
              "clarification_questions": null,
              "impossible_reason": null
            }
        """.trimIndent()

        val parsed = MultiAgentParser.parseMcpSelection(raw)
        assertNotNull(parsed)
        assertEquals(MultiAgentMcpSelectionAction.NEED_CLARIFICATION, parsed.action)
        assertTrue(parsed.clarificationQuestions.isEmpty())
        assertEquals(null, parsed.arguments)
        assertEquals("keep only important", parsed.outputFilter)
    }

    @Test
    fun parseMcpSelection_handlesNullArguments() {
        val raw = """
            {
              "action": "MCP_CALL",
              "reason": "call tool",
              "mcp_call": {
                "toolName": "search_docs",
                "endpoint": "http://localhost",
                "arguments": null
              },
              "clarification_questions": [],
              "impossible_reason": null
            }
        """.trimIndent()

        val parsed = MultiAgentParser.parseMcpSelection(raw)
        assertNotNull(parsed)
        assertEquals(MultiAgentMcpSelectionAction.MCP_CALL, parsed.action)
        assertEquals("search_docs", parsed.toolName)
        assertEquals(null, parsed.arguments)
        assertEquals(null, parsed.outputFilter)
    }

    @Test
    fun execute_survivesSelectorException_andEmitsDiagnosticTrace() = runBlocking {
        val orchestrator = MultiAgentOrchestrator()
        val events = mutableListOf<MultiAgentEvent>()
        var jsonCallCount = 0

        val summary = orchestrator.execute(
            request = MultiAgentRequest(
                userRequest = "Найди данные через MCP",
                projectFolderPath = "C:/tmp/project",
                subagents = defaultMultiAgentSubagents().filter {
                    it.key in setOf("mcp_selector", "mcp_executor", "validator", "diagnostic")
                },
                mcpToolsCatalog = "server: local (http://localhost)\n  - tool: search_docs\n    input_schema: {}"
            ),
            callModel = { call ->
                val hasSystemSelector = call.messages.any { it.role == "system" && it.content.contains("MCP Selector") }
                val hasSystemDiagnostic = call.messages.any { it.role == "system" && it.content.contains("Diagnostic") }
                when {
                    hasSystemSelector -> error("selector failure")
                    hasSystemDiagnostic -> "diagnostic completed"
                    call.responseAsJson -> {
                        jsonCallCount++
                        if (jsonCallCount == 1) {
                            """
                            {
                              "action":"DELEGATE",
                              "reason":"need mcp",
                              "execution_plan":{
                                "plan_steps":[
                                  {"index":1,"title":"MCP step","assignee_key":"mcp_executor","task_input":"run tool"}
                                ],
                                "tool_plan":{
                                  "requires_tools":true,
                                  "tools":[
                                    {
                                      "tool_kind":"MCP_CALL",
                                      "tool_scope":"SINGLE_TARGET",
                                      "reason":"need external data",
                                      "params":{"toolName":"search_docs","arguments":{}},
                                      "step_index":1
                                    }
                                  ],
                                  "fallback_policy":"DEGRADE"
                                }
                              }
                            }
                            """.trimIndent()
                        } else {
                            """
                            {
                              "outcome":"COMPLETE",
                              "final_answer":"готово",
                              "rework_instruction":null,
                              "clarification_question":null,
                              "impossible_reason":null,
                              "tool_call_ids":[1],
                              "rag_evidence":[]
                            }
                            """.trimIndent()
                        }
                    }

                    else -> "ok"
                }
            },
            executeTool = { toolRequest ->
                ToolGatewayResult(
                    success = true,
                    toolKind = toolRequest.toolKind,
                    rawOutput = """{"ok":true}""",
                    normalizedOutput = """{"ok":true}""",
                    errorCode = "",
                    errorMessage = "",
                    latencyMs = 1,
                    metadataJson = """{"toolCallId":1,"preflight":${toolRequest.preflight}}"""
                )
            },
            onEvent = { event -> events += event },
            onPlanningReady = {},
            onStepReady = {}
        )

        assertEquals(MultiAgentRunStatus.DONE, summary.runStatus)
        assertTrue(events.any { it.channel == MultiAgentEventChannel.TRACE && it.actorType == "diagnostic" })
        assertTrue(events.none { it.channel == MultiAgentEventChannel.USER && it.actorType == "diagnostic" })
    }

    @Test
    fun implementer_receivesContextFromPreviousSteps() = runBlocking {
        val orchestrator = MultiAgentOrchestrator()
        var implementerPrompt = ""
        var jsonCallCount = 0
        var subagentCallCount = 0

        val summary = orchestrator.execute(
            request = MultiAgentRequest(
                userRequest = "Подготовь решение",
                projectFolderPath = "C:/tmp/project",
                subagents = defaultMultiAgentSubagents().filter {
                    it.key in setOf("researcher", "implementer", "validator", "diagnostic")
                }
            ),
            callModel = { call ->
                if (call.responseAsJson) {
                    jsonCallCount++
                    if (jsonCallCount == 1) {
                        """
                        {
                          "action":"DELEGATE",
                          "reason":"need two steps",
                          "execution_plan":{
                            "plan_steps":[
                              {"index":1,"title":"Research","assignee_key":"researcher","task_input":"find facts"},
                              {"index":2,"title":"Implement","assignee_key":"implementer","task_input":"build result"}
                            ],
                            "tool_plan":{
                              "requires_tools":false,
                              "tools":[],
                              "fallback_policy":"DEGRADE"
                            }
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "outcome":"COMPLETE",
                          "final_answer":"готово",
                          "rework_instruction":null,
                          "clarification_question":null,
                          "impossible_reason":null,
                          "tool_call_ids":[],
                          "rag_evidence":[]
                        }
                        """.trimIndent()
                    }
                } else {
                    subagentCallCount++
                    val system = call.messages.firstOrNull { it.role == "system" }?.content.orEmpty()
                    val user = call.messages.firstOrNull { it.role == "user" }?.content.orEmpty()
                    if (system.contains("Implementer")) {
                        implementerPrompt = user
                        "implementation ready"
                    } else {
                        "research findings KEY_FACT=42"
                    }
                }
            },
            executeTool = { toolRequest ->
                ToolGatewayResult(
                    success = true,
                    toolKind = toolRequest.toolKind,
                    rawOutput = "",
                    normalizedOutput = "",
                    errorCode = "",
                    errorMessage = "",
                    latencyMs = 1
                )
            },
            onEvent = {},
            onPlanningReady = {},
            onStepReady = {}
        )

        assertEquals(MultiAgentRunStatus.DONE, summary.runStatus)
        assertTrue(subagentCallCount >= 2)
        assertTrue(implementerPrompt.contains("Контекст предыдущих шагов"))
        assertTrue(implementerPrompt.contains("research findings KEY_FACT=42"))
    }

    @Test
    fun userRequestExtractor_buildsClarificationPairs() {
        val extracted = MultiAgentUserRequestExtractor.extract(
            conversation = listOf(
                MultiAgentConversationMessage(
                    role = MultiAgentConversationRole.USER,
                    text = "Нужно обновить файлы A, B и C"
                ),
                MultiAgentConversationMessage(
                    role = MultiAgentConversationRole.AGENT,
                    text = "Какой стиль форматирования использовать?"
                ),
                MultiAgentConversationMessage(
                    role = MultiAgentConversationRole.USER,
                    text = "Используй текущий стиль проекта, без рефакторинга"
                )
            ),
            fallbackObjective = "fallback"
        )

        assertEquals("Нужно обновить файлы A, B и C", extracted.objective)
        assertEquals(listOf("Нужно обновить файлы A, B и C"), extracted.taskMessages)
        assertEquals(listOf("Какой стиль форматирования использовать?"), extracted.agentQuestions)
        assertEquals(listOf("Используй текущий стиль проекта, без рефакторинга"), extracted.clarificationMessages)
        assertEquals(1, extracted.clarifications.size)
        assertEquals(
            "Какой стиль форматирования использовать?",
            extracted.clarifications.first().question
        )
    }

    @Test
    fun singleTargetTool_expandsPlanByTargets() = runBlocking {
        val orchestrator = MultiAgentOrchestrator()
        val planningDecisions = mutableListOf<MultiAgentPlanningDecision>()
        var jsonCallCount = 0

        val summary = orchestrator.execute(
            request = MultiAgentRequest(
                userRequest = "Обнови A B C",
                projectFolderPath = "C:/tmp/project",
                subagents = defaultMultiAgentSubagents().filter {
                    it.key in setOf("implementer", "validator", "diagnostic")
                }
            ),
            callModel = { call ->
                if (call.responseAsJson) {
                    jsonCallCount++
                    if (jsonCallCount == 1) {
                        """
                        {
                          "action":"DELEGATE",
                          "reason":"update files",
                          "execution_plan":{
                            "plan_steps":[
                              {"index":1,"title":"Edit files","assignee_key":"implementer","task_input":"apply updates"}
                            ],
                            "tool_plan":{
                              "requires_tools":true,
                              "tools":[
                                {
                                  "tool_kind":"MCP_CALL",
                                  "tool_scope":"SINGLE_TARGET",
                                  "reason":"edit each file",
                                  "params":{"files":["A.kt","B.kt","C.kt"]},
                                  "step_index":1
                                }
                              ],
                              "fallback_policy":"DEGRADE"
                            }
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "outcome":"COMPLETE",
                          "final_answer":"готово",
                          "tool_call_ids":[1,2,3],
                          "rag_evidence":[]
                        }
                        """.trimIndent()
                    }
                } else {
                    "ok"
                }
            },
            executeTool = { toolRequest ->
                ToolGatewayResult(
                    success = true,
                    toolKind = toolRequest.toolKind,
                    rawOutput = """{"ok":true}""",
                    normalizedOutput = """{"ok":true}""",
                    errorCode = "",
                    errorMessage = "",
                    latencyMs = 1,
                    metadataJson = """{"toolCallId":${toolRequest.stepIndex ?: 1}}"""
                )
            },
            onEvent = {},
            onPlanningReady = { planningDecisions += it },
            onStepReady = {}
        )

        assertEquals(MultiAgentRunStatus.DONE, summary.runStatus)
        val planning = planningDecisions.last()
        assertEquals(4, planning.planSteps.size)
        assertTrue(planning.planSteps[0].title.contains("A.kt"))
        assertTrue(planning.planSteps[1].title.contains("B.kt"))
        assertTrue(planning.planSteps[2].title.contains("C.kt"))
        assertTrue(planning.planSteps.last().title.contains("валидац", ignoreCase = true))
        assertEquals(3, planning.toolPlan?.tools?.count { it.toolKind == MultiAgentToolKind.MCP_CALL })
    }

    @Test
    fun multiTargetTool_keepsAggregatedStep() = runBlocking {
        val orchestrator = MultiAgentOrchestrator()
        val planningDecisions = mutableListOf<MultiAgentPlanningDecision>()
        var jsonCallCount = 0

        val summary = orchestrator.execute(
            request = MultiAgentRequest(
                userRequest = "Обнови A B C",
                projectFolderPath = "C:/tmp/project",
                subagents = defaultMultiAgentSubagents().filter {
                    it.key in setOf("implementer", "validator", "diagnostic")
                }
            ),
            callModel = { call ->
                if (call.responseAsJson) {
                    jsonCallCount++
                    if (jsonCallCount == 1) {
                        """
                        {
                          "action":"DELEGATE",
                          "reason":"batch update",
                          "execution_plan":{
                            "plan_steps":[
                              {"index":1,"title":"Batch edit","assignee_key":"implementer","task_input":"apply updates"}
                            ],
                            "tool_plan":{
                              "requires_tools":true,
                              "tools":[
                                {
                                  "tool_kind":"MCP_CALL",
                                  "tool_scope":"MULTI_TARGET",
                                  "reason":"bulk editor",
                                  "params":{"files":["A.kt","B.kt","C.kt"]},
                                  "step_index":1
                                }
                              ],
                              "fallback_policy":"DEGRADE"
                            }
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "outcome":"COMPLETE",
                          "final_answer":"готово",
                          "tool_call_ids":[1],
                          "rag_evidence":[]
                        }
                        """.trimIndent()
                    }
                } else {
                    "ok"
                }
            },
            executeTool = { toolRequest ->
                ToolGatewayResult(
                    success = true,
                    toolKind = toolRequest.toolKind,
                    rawOutput = """{"ok":true}""",
                    normalizedOutput = """{"ok":true}""",
                    errorCode = "",
                    errorMessage = "",
                    latencyMs = 1,
                    metadataJson = """{"toolCallId":1}"""
                )
            },
            onEvent = {},
            onPlanningReady = { planningDecisions += it },
            onStepReady = {}
        )

        assertEquals(MultiAgentRunStatus.DONE, summary.runStatus)
        val planning = planningDecisions.last()
        assertEquals(2, planning.planSteps.size)
        assertTrue(planning.planSteps.first().title.contains("Batch edit"))
        assertTrue(planning.planSteps.last().title.contains("валидац", ignoreCase = true))
        assertEquals(1, planning.toolPlan?.tools?.count { it.toolKind == MultiAgentToolKind.MCP_CALL })
    }

    @Test
    fun selectorNeedClarification_usesAutonomousFallbackTool() = runBlocking {
        val orchestrator = MultiAgentOrchestrator()
        var jsonCallCount = 0
        val events = mutableListOf<MultiAgentEvent>()
        var usedAutonomousGenericTool = false

        val summary = orchestrator.execute(
            request = MultiAgentRequest(
                userRequest = "Выбери файлы сам и дай короткую сводку",
                projectFolderPath = "C:/tmp/project",
                subagents = defaultMultiAgentSubagents().filter {
                    it.key in setOf("mcp_selector", "mcp_executor", "validator", "diagnostic")
                },
                mcpToolsCatalog = """
                    server: Local (http://localhost)
                      - tool: explore_directory
                      - tool: project_read_file
                      - tool: git_list_files
                """.trimIndent()
            ),
            callModel = { call ->
                val hasSystemSelector = call.messages.any { it.role == "system" && it.content.contains("MCP Selector") }
                if (hasSystemSelector) {
                    """
                    {
                      "action":"NEED_CLARIFICATION",
                      "reason":"no exact list_files tool",
                      "clarification_questions":["уточните пути"],
                      "impossible_reason":null,
                      "mcp_call":null
                    }
                    """.trimIndent()
                } else if (call.responseAsJson) {
                    jsonCallCount++
                    if (jsonCallCount == 1) {
                        """
                        {
                          "action":"DELEGATE",
                          "reason":"need files",
                          "execution_plan":{
                            "plan_steps":[
                              {"index":1,"title":"Find files","assignee_key":"mcp_executor","task_input":"find AiAgentRag files"}
                            ],
                            "tool_plan":{
                              "requires_tools":true,
                              "tools":[
                                {
                                  "tool_kind":"MCP_CALL",
                                  "tool_scope":"SINGLE_TARGET",
                                  "reason":"need list_files",
                                  "params":{"tool_name":"list_files","directory_path":"C:/tmp/project","filter_pattern":"*AiAgentRag*"},
                                  "step_index":1
                                }
                              ],
                              "fallback_policy":"DEGRADE"
                            }
                          }
                        }
                        """.trimIndent()
                    } else {
                        """
                        {
                          "outcome":"COMPLETE",
                          "final_answer":"готово",
                          "tool_call_ids":[1],
                          "rag_evidence":[]
                        }
                        """.trimIndent()
                    }
                } else {
                    "ok"
                }
            },
            executeTool = { toolRequest ->
                if (!toolRequest.preflight && toolRequest.toolKind == MultiAgentToolKind.MCP_CALL) {
                    usedAutonomousGenericTool =
                        toolRequest.paramsJson.contains("\"toolName\":\"explore_directory\"") ||
                            toolRequest.paramsJson.contains("\"toolName\":\"git_list_files\"") ||
                            toolRequest.paramsJson.contains("\"toolName\":\"project_read_file\"")
                }
                ToolGatewayResult(
                    success = true,
                    toolKind = toolRequest.toolKind,
                    rawOutput = """{"ok":true}""",
                    normalizedOutput = """{"ok":true}""",
                    errorCode = "",
                    errorMessage = "",
                    latencyMs = 1,
                    metadataJson = """{"toolCallId":1}"""
                )
            },
            onEvent = { events += it },
            onPlanningReady = {},
            onStepReady = {}
        )

        assertEquals(MultiAgentRunStatus.DONE, summary.runStatus)
        assertTrue(usedAutonomousGenericTool)
        assertTrue(
            events.any {
                it.channel == MultiAgentEventChannel.TRACE &&
                    it.actorKey == "mcp_selector_autonomy"
            }
        )
    }
}
