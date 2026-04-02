package com.example.aiadventchalengetestllmapi.aiagentmain

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpExecutorFilteringTest {
    @Test
    fun mcpExecutor_withoutFilter_keepsLegacyOutputFlow() = runBlocking {
        var nonJsonCalls = 0
        val scenario = runMcpScenario(
            toolParamsJson = """{"toolName":"search_docs","arguments":{}}""",
            toolOutput = "raw data 1",
            nonJsonResponder = {
                nonJsonCalls++
                "unexpected"
            }
        )

        assertEquals(MultiAgentRunStatus.DONE, scenario.summary.runStatus)
        assertEquals(0, nonJsonCalls)
        val stepOutput = scenario.summary.steps.first().output
        assertTrue(stepOutput.contains("final_result_candidates:"))
    }

    @Test
    fun mcpExecutor_withFilter_returnsFilteredData() = runBlocking {
        val scenario = runMcpScenario(
            toolParamsJson = """{"toolName":"search_docs","arguments":{},"output_filter":"оставь только KEEP"}""",
            toolOutput = "KEEP:one\nDROP:two\nKEEP:three",
            nonJsonResponder = { prompt ->
                when {
                    prompt.contains("NO_MATCH") -> prompt.lines().filter { it.contains("KEEP:") }.joinToString("\n")
                    prompt.contains("FILTERED_PART_") -> "KEEP:one\nKEEP:three"
                    else -> "NO_MATCH"
                }
            }
        )

        assertEquals(MultiAgentRunStatus.DONE, scenario.summary.runStatus)
        val stepOutput = scenario.summary.steps.first().output
        assertTrue(stepOutput.contains("filtered_result:"))
        assertTrue(stepOutput.contains("KEEP:one"))
        assertTrue(stepOutput.contains("mcp_filter_ok"))
    }

    @Test
    fun mcpExecutor_withLargePayload_splitsIntoChunks() = runBlocking {
        var chunkCalls = 0
        val large = buildString {
            repeat(25_000) { append(if (it % 2 == 0) 'A' else 'B') }
        }
        val scenario = runMcpScenario(
            toolParamsJson = """{"toolName":"search_docs","arguments":{},"output_filter":"оставь только A"}""",
            toolOutput = large,
            nonJsonResponder = { prompt ->
                if (prompt.contains("NO_MATCH")) {
                    chunkCalls++
                    "A"
                } else {
                    "A"
                }
            }
        )

        assertEquals(MultiAgentRunStatus.DONE, scenario.summary.runStatus)
        assertTrue(chunkCalls >= 2)
        assertTrue(
            scenario.events.any {
                it.channel == MultiAgentEventChannel.TRACE &&
                    it.actorKey == "mcp_filter" &&
                    it.message.contains("MCP_FILTER_CHUNK")
            }
        )
    }

    @Test
    fun mcpExecutor_filterFailure_doesNotBreakRun() = runBlocking {
        val scenario = runMcpScenario(
            toolParamsJson = """{"toolName":"search_docs","arguments":{},"output_filter":"оставь только KEEP"}""",
            toolOutput = "KEEP:one\nDROP:two",
            nonJsonResponder = { prompt ->
                if (prompt.contains("NO_MATCH")) error("filter model failure")
                "ok"
            }
        )

        assertEquals(MultiAgentRunStatus.DONE, scenario.summary.runStatus)
        val stepOutput = scenario.summary.steps.first().output
        assertTrue(stepOutput.contains("KEEP:one"))
        assertTrue(
            scenario.events.any {
                it.channel == MultiAgentEventChannel.TRACE &&
                    it.actorKey == "mcp_filter_error"
            }
        )
    }

    private fun runMcpScenario(
        toolParamsJson: String,
        toolOutput: String,
        nonJsonResponder: (String) -> String
    ): ScenarioResult = runBlocking {
        val orchestrator = MultiAgentOrchestrator()
        val events = mutableListOf<MultiAgentEvent>()
        var jsonCallCount = 0

        val summary = orchestrator.execute(
            request = MultiAgentRequest(
                userRequest = "Собери данные через MCP",
                projectFolderPath = "C:/tmp/project",
                subagents = defaultMultiAgentSubagents().filter {
                    it.key in setOf("mcp_executor", "validator", "diagnostic")
                }
            ),
            callModel = { call ->
                if (call.responseAsJson) {
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
                                "params":$toolParamsJson,
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
                } else {
                    nonJsonResponder(call.messages.last().content)
                }
            },
            executeTool = { toolRequest ->
                ToolGatewayResult(
                    success = true,
                    toolKind = toolRequest.toolKind,
                    rawOutput = toolOutput,
                    normalizedOutput = toolOutput,
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

        ScenarioResult(summary = summary, events = events)
    }

    private data class ScenarioResult(
        val summary: MultiAgentRunSummary,
        val events: List<MultiAgentEvent>
    )
}
