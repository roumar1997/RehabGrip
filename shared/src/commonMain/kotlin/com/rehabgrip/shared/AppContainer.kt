package com.rehabgrip.shared

import com.rehabgrip.core.ble.ForceSource
import com.rehabgrip.core.ble.SimulatedForceSource
import com.rehabgrip.core.data.DriverFactory
import com.rehabgrip.core.data.SampleData
import com.rehabgrip.core.data.SqlGripEvaluationRepository
import com.rehabgrip.core.data.SqlGripRepository
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
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.usecase.SaveGripEvaluation
import com.rehabgrip.core.domain.usecase.SaveMeasurement
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.HandWorkoutResult
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** What each platform must provide (Android activity / iOS entry point). */
interface PlatformServices {
    val driverFactory: DriverFactory
    val exportSharer: ExportSharer
    val cuePlayer: CuePlayer
    val reminderScheduler: ReminderScheduler
    val isDebug: Boolean
    fun realForceSource(): ForceSource
    fun advertisementScanner(): com.rehabgrip.core.ble.AdvertisementScanner
    suspend fun ensureBluetoothPermission(): Boolean
}

/** Manual dependency injection: one instance per process. */
class AppContainer(val platform: PlatformServices) {
    private val db = createDatabase(platform.driverFactory)
    val measurements = SqlMeasurementRepository(db)
    val settings = SqlSettingsStore(db)
    val reminders = SqlReminderRepository(db)
    val grips = SqlGripRepository(db)
    val gripEvaluations = SqlGripEvaluationRepository(db)
    val workouts = SqlWorkoutRepository(db, grips)
    val workoutResults = SqlWorkoutResultRepository(db)
    val saveMeasurement = SaveMeasurement(measurements)
    val updateMeasurement = com.rehabgrip.core.domain.usecase.UpdateMeasurement(measurements)
    val saveGripEvaluation = SaveGripEvaluation(gripEvaluations, measurements)
    private val csv = CsvExporter()

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    /** The one scale this phone listens to (iOS UUID / Android MAC); null = auto-select the strongest. */
    var lockedScaleId: String? = null

    /** Debug: the simulated scale also mixes in frames of another device and spikes. */
    var contaminatedSim: Boolean = false

    fun forceSource(simulated: Boolean): ForceSource =
        if (simulated) SimulatedForceSource(contaminated = contaminatedSim)
        else platform.realForceSource().also { s ->
            s.configureLock(lockedScaleId) { id, name -> background.launch { useScale(id, name) } }
        }

    suspend fun loadScale() {
        lockedScaleId = settings.observeString(KEY_SCALE_ID).first()
    }

    /** «Usar esta báscula»: from the diagnostics, or chosen automatically. */
    suspend fun useScale(id: String, name: String?) {
        lockedScaleId = id
        settings.setString(KEY_SCALE_ID, id)
        settings.setString(KEY_SCALE_NAME, name.orEmpty())
        saveBook(com.rehabgrip.core.ble.ScaleBook.upsert(loadBook(), id, name))
    }

    /** Forgets one scale; if it was the active one the lock is released (auto-selection again). */
    suspend fun forgetScale(id: String) {
        saveBook(com.rehabgrip.core.ble.ScaleBook.remove(loadBook(), id))
        if (lockedScaleId == id) {
            lockedScaleId = null
            settings.setString(KEY_SCALE_ID, "")
            settings.setString(KEY_SCALE_NAME, "")
        }
    }

    suspend fun renameScale(id: String, name: String) = saveBook(com.rehabgrip.core.ble.ScaleBook.rename(loadBook(), id, name))

    private suspend fun loadBook() = com.rehabgrip.core.ble.ScaleBook.decode(settings.observeString(KEY_SCALES).first())

    private suspend fun saveBook(list: List<com.rehabgrip.core.ble.SavedScale>) = settings.setString(KEY_SCALES, com.rehabgrip.core.ble.ScaleBook.encode(list))

    fun export(list: List<Measurement>) =
        platform.exportSharer.share("rehabgrip-${Clock.System.now().toEpochMilliseconds()}.csv", csv.export(list))

    suspend fun loadSampleData() = measurements.replaceAll(SampleData.generate(now()))

    /** Adds the FREZ-like example session the first time, and the predefined grips when missing. */
    suspend fun seedExampleWorkout() {
        grips.ensureDefaults()
        if (!settings.observeFlag(KEY_SEEDED, false).first()) {
            if (workouts.observeAll().first().isEmpty()) workouts.upsert(WorkoutTemplate.example())
            settings.setFlag(KEY_SEEDED, true)
        }
        seedGripSession()
    }

    /** The default grip session (once; deleting it does not bring it back). */
    private suspend fun seedGripSession() {
        if (settings.observeFlag(KEY_SEEDED_GRIP_SESSION, false).first()) return
        val existing = workouts.observeAll().first()
        if (existing.none { it.name == WorkoutTemplate.GRIP_SESSION_NAME }) {
            WorkoutTemplate.gripSession(grips.observeAll().first())?.let { workouts.upsert(it) } ?: return
        }
        settings.setFlag(KEY_SEEDED_GRIP_SESSION, true)
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

    /**
     * Stores the session and, for library grips, the max of each hand as a
     * measurement so it shows in Evolution. Discomforts go with the first one.
     */
    suspend fun saveWorkout(template: WorkoutTemplate, results: List<HandWorkoutResult>, discomforts: List<Discomfort>, picked: Long? = null) {
        val at = com.rehabgrip.core.domain.model.RecordDate.resolve(picked, now())
        workoutResults.add(WorkoutResultEntry(template.name, at, results))
        val grip = (template.sets.firstOrNull()?.grip as? Grip.Library)?.grip
        val exercise = grip?.builtIn ?: (template.sets.firstOrNull()?.grip as? Grip.Standard)?.exercise
            ?: if (grip != null) Exercise.CUSTOM else return
        results.filter { it.maxKg > 0 }.forEachIndexed { i, r ->
            saveMeasurement(
                exercise, r.hand, r.maxKg, MeasurementSource.SCALE, at,
                note = "Entreno «${template.name}»", discomforts = if (i == 0) discomforts else emptyList(),
                gripId = grip?.id,
            )
        }
    }

    companion object {
        const val KEY_SEEDED = "seeded_example_workout"
        const val KEY_SEEDED_GRIP_SESSION = "seeded_grip_session"
        const val KEY_SCALE_ID = "scale_device_id"
        const val KEY_SCALE_NAME = "scale_device_name"
        const val KEY_SCALES = "scales_book"
    }
}
