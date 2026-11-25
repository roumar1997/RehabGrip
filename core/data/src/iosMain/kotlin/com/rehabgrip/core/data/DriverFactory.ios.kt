package com.rehabgrip.core.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.rehabgrip.core.data.db.RehabGripDatabase

actual class DriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(RehabGripDatabase.Schema, "rehabgrip.db")
}
