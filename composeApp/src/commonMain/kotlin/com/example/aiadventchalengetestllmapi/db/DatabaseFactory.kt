package com.example.aiadventchalengetestllmapi.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    constructor(dbFileName: String = "app.db")
    fun createDriver(): SqlDriver
}

fun createAppDatabase(driverFactory: DatabaseDriverFactory): AppDatabase {
    return AppDatabase(driverFactory.createDriver())
}
