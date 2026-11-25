package com.rehabgrip.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.rehabgrip.core.data.db.RehabGripDatabase
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Finger
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.model.UserProfile
import com.rehabgrip.core.domain.port.MeasurementRepository
import com.rehabgrip.core.domain.port.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.rehabgrip.core.data.db.Measurement as Row

/** Platform factory for the SQLite driver (Android context / iOS native). */
expect class DriverFactory {
    fun create(): SqlDriver
}

fun createDatabase(factory: DriverFactory) = RehabGripDatabase(factory.create())

/** Enum lookup that tolerates values written by future versions. */
internal inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

class SqlMeasurementRepository(private val db: RehabGripDatabase) : MeasurementRepository {
    private val q get() = db.rehabGripQueries
    private val d get() = db.round2Queries

    override fun observeAll(): Flow<List<Measurement>> = combine(
        q.selectAll().asFlow().mapToList(Dispatchers.IO),
        d.selectAllDiscomforts().asFlow().mapToList(Dispatchers.IO),
    ) { rows, notes ->
        val byMeasurement = notes.groupBy { it.measurement_id }
        rows.mapNotNull { row ->
            row.toDomain()?.copy(
                discomforts = byMeasurement[row.id].orEmpty().mapNotNull { n ->
                    runCatching { Discomfort(n.zone, n.level.toInt()) }.getOrNull()
                },
            )
        }
    }

    override suspend fun add(measurement: Measurement): Long = withContext(Dispatchers.IO) {
        q.transactionWithResult { insertRow(measurement) }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        q.transaction {
            d.deleteDiscomfortsOf(id)
            q.deleteById(id)
        }
    }

    override suspend fun replaceAll(measurements: List<Measurement>) = withContext(Dispatchers.IO) {
        q.transaction {
            d.deleteAllDiscomforts()
            q.deleteAll()
            measurements.forEach { insertRow(it) }
        }
    }

    private fun insertRow(m: Measurement): Long {
        q.insert(m.exercise.name, m.hand.name, m.kg, m.epochMillis, m.source.name, m.note, m.gripId)
        val id = q.lastId().executeAsOne()
        m.discomforts.forEach { d.insertDiscomfort(id, it.zone.trim(), it.level.toLong()) }
        return id
    }

    private fun Row.toDomain(): Measurement? {
        val ex = enumOrNull<Exercise>(exercise) ?: return null
        val h = enumOrNull<Hand>(hand) ?: return null
        val src = enumOrNull<MeasurementSource>(source) ?: MeasurementSource.MANUAL
        return Measurement(id, ex, h, kg, epoch_millis, src, note, gripId = grip_id)
    }
}

/** Key/value settings: user name, injury and the simulated-scale switch. */
class SqlSettingsStore(private val db: RehabGripDatabase) : ProfileRepository {
    private val q get() = db.rehabGripQueries

    override fun observe(): Flow<UserProfile?> =
        q.selectSetting(KEY_NAME).asFlow().mapToOneOrNull(Dispatchers.IO)
            .map { name -> name?.takeIf { it.isNotBlank() }?.let(::UserProfile) }

    override suspend fun save(profile: UserProfile) =
        withContext(Dispatchers.IO) { q.upsertSetting(KEY_NAME, profile.name.trim()) }

    /** Stored as "RIGHT|INDEX,RING"; "NONE|" = no injured hand. Missing = not answered yet. */
    fun observeInjuryOrNull(): Flow<Injury?> =
        q.selectSetting(KEY_INJURY).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.let(::decodeInjury) }

    override fun observeInjury(): Flow<Injury> = observeInjuryOrNull().map { it ?: Injury.NONE }

    override suspend fun saveInjury(injury: Injury) = withContext(Dispatchers.IO) {
        val hand = injury.hand?.name ?: "NONE"
        q.upsertSetting(KEY_INJURY, hand + "|" + injury.fingers.joinToString(",") { it.name })
    }

    fun observeFlag(key: String, default: Boolean): Flow<Boolean> =
        q.selectSetting(key).asFlow().mapToOneOrNull(Dispatchers.IO).map { it?.toBooleanStrictOrNull() ?: default }

    suspend fun setFlag(key: String, value: Boolean) =
        withContext(Dispatchers.IO) { q.upsertSetting(key, value.toString()) }

    private fun decodeInjury(text: String): Injury {
        val (hand, fingers) = text.split('|').let { it[0] to it.getOrElse(1) { "" } }
        return Injury(
            hand = enumOrNull<Hand>(hand),
            fingers = fingers.split(',').mapNotNull { enumOrNull<Finger>(it) }.toSet(),
        )
    }

    companion object {
        private const val KEY_NAME = "user_name"
        private const val KEY_INJURY = "injury"
        const val KEY_SIMULATED_SCALE = "simulated_scale"
    }
}
