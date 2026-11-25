package com.rehabgrip.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.rehabgrip.core.data.db.RehabGripDatabase
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.model.UserProfile
import com.rehabgrip.core.domain.port.MeasurementRepository
import com.rehabgrip.core.domain.port.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.rehabgrip.core.data.db.Measurement as Row

/** Platform factory for the SQLite driver (Android context / iOS native). */
expect class DriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DriverFactory) = RehabGripDatabase(factory.create())

class SqlMeasurementRepository(private val db: RehabGripDatabase) : MeasurementRepository {
    private val q get() = db.rehabGripQueries

    override fun observeAll(): Flow<List<Measurement>> =
        q.selectAll().asFlow().mapToList(Dispatchers.IO).map { rows -> rows.mapNotNull { it.toDomain() } }

    override suspend fun add(measurement: Measurement): Long = withContext(Dispatchers.IO) {
        q.transactionWithResult {
            q.insertRow(measurement)
            q.lastId().executeAsOne()
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { q.deleteById(id) }

    override suspend fun replaceAll(measurements: List<Measurement>) = withContext(Dispatchers.IO) {
        q.transaction {
            q.deleteAll()
            measurements.forEach { q.insertRow(it) }
        }
    }

    private fun com.rehabgrip.core.data.db.RehabGripQueries.insertRow(m: Measurement) =
        insert(m.exercise.name, m.hand.name, m.kg, m.epochMillis, m.source.name, m.note)

    /** Unknown enum values (future versions) are skipped instead of crashing. */
    private fun Row.toDomain(): Measurement? {
        val ex = Exercise.entries.firstOrNull { it.name == exercise } ?: return null
        val h = Hand.entries.firstOrNull { it.name == hand } ?: return null
        val src = MeasurementSource.entries.firstOrNull { it.name == source } ?: MeasurementSource.MANUAL
        return Measurement(id, ex, h, kg, epoch_millis, src, note)
    }
}

/** Key/value settings: user name and the simulated-scale switch. */
class SqlSettingsStore(private val db: RehabGripDatabase) : ProfileRepository {
    private val q get() = db.rehabGripQueries

    override fun observe(): Flow<UserProfile?> =
        q.selectSetting(KEY_NAME).asFlow().mapToOneOrNull(Dispatchers.IO)
            .map { name -> name?.takeIf { it.isNotBlank() }?.let(::UserProfile) }

    override suspend fun save(profile: UserProfile) =
        withContext(Dispatchers.IO) { q.upsertSetting(KEY_NAME, profile.name.trim()) }

    fun observeFlag(key: String, default: Boolean): Flow<Boolean> =
        q.selectSetting(key).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toBooleanStrictOrNull() ?: default }

    suspend fun setFlag(key: String, value: Boolean) =
        withContext(Dispatchers.IO) { q.upsertSetting(key, value.toString()) }

    companion object {
        private const val KEY_NAME = "user_name"
        const val KEY_SIMULATED_SCALE = "simulated_scale"
    }
}
