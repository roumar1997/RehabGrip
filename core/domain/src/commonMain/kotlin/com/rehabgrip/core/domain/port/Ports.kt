package com.rehabgrip.core.domain.port

import com.rehabgrip.core.domain.model.Injury
import com.rehabgrip.core.domain.model.Measurement
import com.rehabgrip.core.domain.model.UserProfile
import com.rehabgrip.core.domain.reminder.Reminder
import com.rehabgrip.core.domain.grip.GripEvaluation
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlinx.coroutines.flow.Flow

/** Output port: persistence of measurements (implemented in :core:data). */
interface MeasurementRepository {
    fun observeAll(): Flow<List<Measurement>>
    suspend fun add(measurement: Measurement): Long
    suspend fun delete(id: Long)
    /** Rewrites hand, kg, grip and above all the date of a stored measurement. */
    suspend fun update(measurement: Measurement)
    suspend fun replaceAll(measurements: List<Measurement>)
}

/** Output port: the single local user and the injured hand/fingers. */
interface ProfileRepository {
    fun observe(): Flow<UserProfile?>
    suspend fun save(profile: UserProfile)
    fun observeInjury(): Flow<Injury>
    suspend fun saveInjury(injury: Injury)
}

interface ReminderRepository {
    fun observeAll(): Flow<List<Reminder>>
    suspend fun all(): List<Reminder>
    suspend fun upsert(reminder: Reminder): Long
    suspend fun delete(id: Long)
}

interface WorkoutRepository {
    fun observeAll(): Flow<List<WorkoutTemplate>>
    suspend fun upsert(template: WorkoutTemplate): Long
    suspend fun delete(id: Long)
}

interface WorkoutResultRepository {
    fun observeAll(): Flow<List<WorkoutResultEntry>>
    suspend fun add(entry: WorkoutResultEntry)
    suspend fun delete(epochMillis: Long)

    /** Moves a saved session to another date. */
    suspend fun changeDate(oldEpochMillis: Long, newEpochMillis: Long)
}

/** Output port: the user's grip library (predefined + own). */
interface GripRepository {
    fun observeAll(): Flow<List<GripSpec>>
    suspend fun upsert(grip: GripSpec): Long
    /** Deletes an own grip; sets that used it switch to [replacementId] first (never silently dropped). */
    suspend fun delete(id: Long, replacementId: Long? = null)

    /** Inserts the predefined grips when missing (idempotent). */
    suspend fun ensureDefaults()
}

interface GripEvaluationRepository {
    fun observeAll(): Flow<List<GripEvaluation>>
    suspend fun add(evaluation: GripEvaluation): Long
}

/** Output port: OS local notifications (AlarmManager / UNUserNotificationCenter). */
interface ReminderScheduler {
    /** Replaces every scheduled reminder with [reminders] (only enabled ones fire). */
    suspend fun reschedule(reminders: List<Reminder>)

    /** Asks for notification permission; true when granted. */
    suspend fun ensurePermission(): Boolean

    /** Fires one notification right now (to check that they arrive). */
    fun showTest()
}

/** Output port: sounds and haptics during guided workouts. */
interface CuePlayer {
    fun countdownTick()
    fun pullStart()
    fun pullEnd()
    fun aboveMax()
    fun belowMin()
    fun finished()
}

/** Output port: hands a CSV file to the platform share sheet. */
fun interface ExportSharer {
    fun share(fileName: String, content: String)
}
