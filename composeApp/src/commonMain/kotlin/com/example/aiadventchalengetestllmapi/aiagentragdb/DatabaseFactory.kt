package com.example.aiadventchalengetestllmapi.aiagentragdb

import app.cash.sqldelight.db.SqlDriver

expect class AiAgentRagDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createAiAgentRagDatabase(driverFactory: AiAgentRagDatabaseDriverFactory): AiAgentRagDatabase {
    return AiAgentRagDatabase(driverFactory.createDriver())
}
