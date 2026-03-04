package com.example.aiadventchalengetestllmapi.aistateagentdb

import app.cash.sqldelight.db.SqlDriver

expect class AiStateAgentDatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createAiStateAgentDatabase(driverFactory: AiStateAgentDatabaseDriverFactory): AiStateAgentDatabase {
    return AiStateAgentDatabase(driverFactory.createDriver())
}
