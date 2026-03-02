package com.example.aiadventchalengetestllmapi.aiweek3db

import app.cash.sqldelight.db.SqlDriver

expect class AiWeek3DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createAiWeek3Database(driverFactory: AiWeek3DatabaseDriverFactory): AiWeek3Database {
    return AiWeek3Database(driverFactory.createDriver())
}
