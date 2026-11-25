package com.rehabgrip.core.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.rehabgrip.core.data.db.RehabGripDatabase

actual class DriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(RehabGripDatabase.Schema, context, "rehabgrip.db")
}
