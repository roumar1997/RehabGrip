package com.rehabgrip.shared

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.SimulatedForceSource
import com.rehabgrip.core.data.DriverFactory
import com.rehabgrip.core.data.SampleData
import com.rehabgrip.core.data.SqlCustomGripRepository
import com.rehabgrip.core.data.SqlMeasurementRepository
import com.rehabgrip.core.data.SqlReminderRepository
import com.rehabgrip.core.data.SqlSettingsStore
import com.rehabgrip.core.data.SqlWorkoutRepository
import com.rehabgrip.core.data.SqlWorkoutResultRepository
import com.rehabgrip.core.data.createDatabase
import com.rehabgrip.core.domain.export.CsvExporter
import com.rehabgrip.core.domain.model.Discomfort
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.MeasurementSource
import com.rehabgrip.core.domain.port.CuePlayer
import com.rehabgrip.core.domain.port.ExportSharer
import com.rehabgrip.core.domain.port.ReminderScheduler
import com.rehabgrip.core.domain.reminder.Reminder
import com.rehabgrip.core.domain.usecase.SaveMeasurement
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.HandWorkoutResult
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

/** What each platform must provide (Android activity / iOS entry point). */
interface PlatformServices {
    val driverFactory: DriverFactory
    val exportSharer: ExportSharer
    val cuePlayer: CuePlayer
    val reminderScheduler: ReminderScheduler
    val isDebug: Boolean
    fun realForceSource(): ForceSource
    suspend fun ensureBluetoothPermission(): Boolean
}

/** Manual dependency injection: one instance per process. */
class AppContainer(val platform: PlatformServices) {
    private val db = createDatabase(platform.driverFactory)
    val measurements = SqlMeasurementRepository(db)
    val settings = SqlSettingsStore(db)
    val reminders = SqlReminderRepository(db)
    val customGrips = SqlCustomGripRepository(db)
    val workouts = SqlWorkoutRepository(db, customGrips)
    val workoutResults = SqlWorkoutResultRepository(db)
    val saveMeasurement = SaveMeasurement(measurements)
    private val csv = CsvExporter()

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    fun forceSource(simulated: Boolean): ForceSource =
        if (simulated) SimulatedForceSource() else platform.realForceSource()

    fun export(list: List<Measurement>) =
        platform.exportSharer.share("rehabgrip-${Clock.System.now().toEpochMilliseconds()}.csv", csv.export(list))

    suspend fun loadSampleData() = measurements.replaceAll(SampleData.generate(now()))

    /** Adds the FREZ-like example session the first time. */
    suspend fun seedExampleWorkout() {
        if (settings.observeFlag(KEY_SEEDED, false).first()) return
        if (workouts.observeAll().first().isEmpty()) workouts.upsert(WorkoutTemplate.example())
        settings.setFlag(KEY_SEEDED, true)
    }

    suspend fun saveReminder(reminder: Reminder) {
        if (reminder.enabled && !platform.reminderScheduler.ensurePermission()) return
        reminders.upsert(reminder)
        platform.reminderScheduler.reschedule(reminders.all())
    }

    suspend fun deleteReminder(reminder: Reminder) {
        reminders.delete(reminder.id)
        platform.reminderScheduler.reschedule(reminders.all())
    }

    /** Last record of a hand for a standard grip (base for % targets). */
    fun recordKg(all: List<Measurement>, hand: Hand, grip: Grip): Double? =
        (grip as? Grip.Standard)?.let { g ->
            all.filter { it.hand == hand && it.exercise == g.exercise }.maxByOrNull { it.epochMillis }?.kg
        }

    /**
     * Stores the session and, for standard grips, the max of each hand as a
     * measurement so it shows in Evolution. Discomforts go with the first one.
     */
    suspend fun saveWorkout(template: WorkoutTemplate, results: List<HandWorkoutResult>, discomforts: List<Discomfort>) {
        val at = now()
        workoutResults.add(WorkoutResultEntry(template.name, at, results))
        val exercise = template.sets.mapNotNull { (it.grip as? Grip.Standard)?.exercise }.firstOrNull() ?: return
        results.filter { it.maxKg > 0 }.forEachIndexed { i, r ->
            saveMeasurement(
                exercise, r.hand, r.maxKg, MeasurementSource.SCALE, at,
                note = "Entreno «${template.name}»", discomforts = if (i == 0) discomforts else emptyList(),
            )
        }
    }

    private companion object {
        const val KEY_SEEDED = "seeded_example_workout"
    }
}
