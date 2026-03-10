package com.example.aiadventchalengetestllmapi.aiagentmaindb

import app.cash.sqldelight.db.SqlDriver

expect class AiAgentMainDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createAiAgentMainDatabase(driverFactory: AiAgentMainDatabaseDriverFactory): AiAgentMainDatabase {
    return AiAgentMainDatabase(driverFactory.createDriver())
}
