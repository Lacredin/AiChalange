package com.example.aiadventchalengetestllmapi.embedinggenerationdb

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Paths

actual class EmbedingGenerationDatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = defaultDatabasePath()
        val driver = JdbcSqliteDriver(url = "jdbc:sqlite:$dbPath")
        runCatching { EmbedingGenerationDatabase.Schema.create(driver) }
        return driver
    }

    private fun defaultDatabasePath() = Paths.get(
        System.getProperty("user.home").orEmpty(),
        ".aiadventchalengetestllmapi",
        "embeding_generation.db"
    ).also {
        it.parent?.toFile()?.mkdirs()
    }
}
