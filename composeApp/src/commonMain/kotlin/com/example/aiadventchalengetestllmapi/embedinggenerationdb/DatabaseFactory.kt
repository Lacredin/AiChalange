package com.example.aiadventchalengetestllmapi.embedinggenerationdb

import app.cash.sqldelight.db.SqlDriver

expect class EmbedingGenerationDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createEmbedingGenerationDatabase(
    driverFactory: EmbedingGenerationDatabaseDriverFactory
): EmbedingGenerationDatabase {
    return EmbedingGenerationDatabase(driverFactory.createDriver())
}
