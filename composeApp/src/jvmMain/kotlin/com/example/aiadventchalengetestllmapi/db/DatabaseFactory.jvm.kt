package com.example.aiadventchalengetestllmapi.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.DriverManager

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = defaultDatabasePath()
        recreateDatabaseIfSchemaMismatch(dbPath)
        var driver = openDriver(dbPath)
        if (!hasCompatibleSchema(dbPath)) {
            runCatching { AppDatabase.Schema.create(driver) }
        }
        if (!hasCompatibleSchema(dbPath)) {
            driver.close()
            deleteDatabaseFiles(dbPath)
            driver = openDriver(dbPath)
            AppDatabase.Schema.create(driver)
        }
        return driver
    }

    private fun openDriver(dbPath: Path): SqlDriver {
        return JdbcSqliteDriver(url = "jdbc:sqlite:$dbPath")
    }

    private fun defaultDatabasePath(): Path {
        val userHome = System.getProperty("user.home").orEmpty()
        return Paths.get(userHome, ".aiadventchalengetestllmapi", "app.db").also {
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
            "chats" to setOf("id", "title", "created_at"),
            "chat_messages" to setOf("id", "chat_id", "api", "model", "role", "message", "params_info", "created_at"),
            "chat_feature_state" to setOf(
                "chat_id",
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
            )
        )
        val actualTables = mutableSetOf<String>()
        val connectionUrl = "jdbc:sqlite:$dbPath"
        DriverManager.getConnection(connectionUrl).use { connection ->
            connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN ('chats', 'chat_messages', 'chat_feature_state', 'chat_branch_messages');"
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

    private fun deleteDatabaseFiles(dbPath: Path) {
        Files.deleteIfExists(dbPath)
        Files.deleteIfExists(Paths.get("${dbPath}-wal"))
        Files.deleteIfExists(Paths.get("${dbPath}-shm"))
    }
}
