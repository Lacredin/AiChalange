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
                              "direct_answer":null,
                              "clarification_question":null,
                              "impossible_reason":null,
                              "plan_steps":[
                                {"index":1,"title":"MCP step","assignee_key":"mcp_executor","task_input":"run tool"}
                              ],
                              "tool_plan":{
                                "requires_tools":true,
                                "tools":[
                                  {
                                    "tool_kind":"MCP_CALL",
                                    "reason":"need external data",
                                    "params":{"toolName":"search_docs","arguments":{}},
                                    "step_index":1
                                  }
                                ],
                                "fallback_policy":"DEGRADE"
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
                          "direct_answer":null,
                          "clarification_question":null,
                          "impossible_reason":null,
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
}
