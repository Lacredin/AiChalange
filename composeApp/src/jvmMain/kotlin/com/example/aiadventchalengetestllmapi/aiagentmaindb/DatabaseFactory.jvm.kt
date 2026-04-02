package com.example.aiadventchalengetestllmapi.aiagentmaindb

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.DriverManager

actual class AiAgentMainDatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = defaultDatabasePath()
        recreateDatabaseIfSchemaMismatch(dbPath)
        ensureAppSettingsTable(dbPath)
        var driver = openDriver(dbPath)
        if (!hasCompatibleSchema(dbPath)) {
            runCatching { AiAgentMainDatabase.Schema.create(driver) }
        }
        if (!hasCompatibleSchema(dbPath)) {
            driver.close()
            deleteDatabaseFiles(dbPath)
            driver = openDriver(dbPath)
            AiAgentMainDatabase.Schema.create(driver)
        }
        return driver
    }

    private fun openDriver(dbPath: Path): SqlDriver {
        return JdbcSqliteDriver(url = "jdbc:sqlite:$dbPath")
    }

    private fun defaultDatabasePath(): Path {
        val userHome = System.getProperty("user.home").orEmpty()
        return Paths.get(userHome, ".aiadventchalengetestllmapi", "ai_agent_main.db").also {
            it.parent?.toFile()?.mkdirs()
        }
    }

    private fun recreateDatabaseIfSchemaMismatch(dbPath: Path) {
        if (!Files.exists(dbPath)) return

        if (!hasCompatibleSchema(dbPath)) {
            deleteDatabaseFiles(dbPath)
        }
    }

    private fun hasCompatibleSchema(dbPath: Path): Boolean {
        val expectedColumns = mapOf(
            "chats" to setOf("id", "title", "created_at", "selected_profile_id"),
            "chat_messages" to setOf("id", "chat_id", "api", "model", "role", "message", "params_info", "created_at"),
            "chat_profiles" to setOf(
                "id",
                "name",
                "is_long_term_memory_enabled",
                "is_system_prompt_enabled",
                "system_prompt_text",
                "is_summarization_enabled",
                "summarize_after_tokens",
                "is_sliding_window_enabled",
                "sliding_window_size",
                "is_sticky_facts_enabled",
                "sticky_facts_window_size",
                "sticky_facts_system_message",
                "is_branching_enabled",
                "show_raw_history"
            ),
            "chat_branch_messages" to setOf(
                "id",
                "chat_id",
                "branch_number",
                "role",
                "message",
                "params_info",
                "stream",
                "epoch",
                "created_at"
            ),
            "memory_entries" to setOf("id", "profile_id", "entry_key", "entry_value", "created_at", "updated_at"),
            "invariant_entries" to setOf("id", "entry_key", "entry_value", "created_at", "updated_at"),
            "multi_agent_subagents" to setOf(
                "id",
                "agent_key",
                "title",
                "description",
                "is_enabled",
                "system_prompt",
                "created_at",
                "updated_at"
            ),
            "multi_agent_runs" to setOf(
                "id",
                "chat_id",
                "parent_run_id",
                "user_request",
                "status",
                "resolution_type",
                "pending_question",
                "state_json",
                "created_at",
                "updated_at"
            ),
            "multi_agent_steps" to setOf(
                "id",
                "run_id",
                "step_index",
                "title",
                "assignee_agent_key",
                "status",
                "input_payload",
                "output_payload",
                "validation_note",
                "created_at",
                "updated_at"
            ),
            "multi_agent_events" to setOf(
                "id",
                "run_id",
                "chat_id",
                "channel",
                "actor_type",
                "actor_key",
                "role",
                "message",
                "metadata_json",
                "created_at"
            ),
            "multi_agent_tool_calls" to setOf(
                "id",
                "run_id",
                "step_id",
                "tool_kind",
                "request_payload",
                "response_payload",
                "status",
                "error_code",
                "error_message",
                "latency_ms",
                "created_at"
            )
        )
        val actualTables = mutableSetOf<String>()
        val connectionUrl = "jdbc:sqlite:$dbPath"
        DriverManager.getConnection(connectionUrl).use { connection ->
            connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('chats', 'chat_messages', 'chat_profiles', 'chat_branch_messages', 'memory_entries', 'invariant_entries', 'multi_agent_subagents', 'multi_agent_runs', 'multi_agent_steps', 'multi_agent_events', 'multi_agent_tool_calls');"
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        actualTables += resultSet.getString("name")
                    }
                }
            }

            if (!actualTables.containsAll(expectedColumns.keys)) {
                return false
            }

            expectedColumns.forEach { (table, columns) ->
                val actualColumns = mutableSetOf<String>()
                connection.prepareStatement("PRAGMA table_info($table);").use { statement ->
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            actualColumns += resultSet.getString("name")
                        }
                    }
                }
                if (!actualColumns.containsAll(columns)) {
                    return false
                }
            }
        }
        return true
    }

    private fun ensureAppSettingsTable(dbPath: Path) {
        val connectionUrl = "jdbc:sqlite:$dbPath"
        DriverManager.getConnection(connectionUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS app_settings (
                        setting_key TEXT NOT NULL PRIMARY KEY,
                        setting_value TEXT NOT NULL
                    );
                    """.trimIndent()
                )
            }
        }
    }

    private fun deleteDatabaseFiles(dbPath: Path) {
        Files.deleteIfExists(dbPath)
        Files.deleteIfExists(Paths.get("${dbPath}-wal"))
        Files.deleteIfExists(Paths.get("${dbPath}-shm"))
    }
}
