package com.rehabgrip.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.rehabgrip.core.data.db.RehabGripDatabase
import com.rehabgrip.core.data.db.Workout_set
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.port.GripRepository
import com.rehabgrip.core.domain.port.ReminderRepository
import com.rehabgrip.core.domain.port.WorkoutRepository
import com.rehabgrip.core.domain.port.WorkoutResultRepository
import com.rehabgrip.core.domain.reminder.Reminder
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.HandWorkoutResult
import com.rehabgrip.core.domain.workout.SetHand
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.StartMode
import com.rehabgrip.core.domain.workout.ToFailure
import com.rehabgrip.core.domain.workout.WorkoutResultEntry
import com.rehabgrip.core.domain.workout.WorkoutSet
import com.rehabgrip.core.domain.workout.WorkoutTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlReminderRepository(private val db: RehabGripDatabase) : ReminderRepository {
    private val q get() = db.round2Queries

    override fun observeAll(): Flow<List<Reminder>> =
        q.selectReminders().asFlow().mapToList(Dispatchers.IO).map { rows ->
            rows.map { Reminder(it.id, Reminder.decodeDays(it.days), it.hour.toInt(), it.minute.toInt(), it.enabled != 0L) }
        }

    override suspend fun all(): List<Reminder> = observeAll().first()

    override suspend fun upsert(reminder: Reminder): Long = withContext(Dispatchers.IO) {
        val days = Reminder.encodeDays(reminder.days)
        val on = if (reminder.enabled) 1L else 0L
        if (reminder.id == 0L) {
            q.transactionWithResult {
                q.insertReminder(days, reminder.hour.toLong(), reminder.minute.toLong(), on)
                db.rehabGripQueries.lastId().executeAsOne()
            }
        } else {
            q.updateReminder(days, reminder.hour.toLong(), reminder.minute.toLong(), on, reminder.id)
            reminder.id
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { q.deleteReminder(id) }
}

class SqlWorkoutRepository(private val db: RehabGripDatabase, private val grips: GripRepository) : WorkoutRepository {
    private val q get() = db.round2Queries

    override fun observeAll(): Flow<List<WorkoutTemplate>> = combine(
        q.selectTemplates().asFlow().mapToList(Dispatchers.IO),
        q.selectAllSets().asFlow().mapToList(Dispatchers.IO),
        grips.observeAll(),
    ) { templates, sets, customGrips ->
        val byTemplate = sets.groupBy { it.template_id }
        val gripById = customGrips.associateBy { it.id }
        val gripByExercise = customGrips.filter { it.builtIn != null }.associateBy { it.builtIn }
        templates.map { t ->
            WorkoutTemplate(
                id = t.id,
                name = t.name,
                sets = byTemplate[t.id].orEmpty().map { it.toDomain(gripById, gripByExercise) },
                handMode = enumOrNull<HandMode>(t.hand_mode) ?: HandMode.SINGLE,
                firstHand = enumOrNull<Hand>(t.first_hand) ?: Hand.RIGHT,
                handoverSeconds = t.handover_s.toInt(),
            )
        }
    }

    override suspend fun upsert(template: WorkoutTemplate): Long = withContext(Dispatchers.IO) {
        q.transactionWithResult {
            val id = if (template.id == 0L) {
                q.insertTemplate(template.name.trim(), template.handMode.name, template.firstHand.name, template.handoverSeconds.toLong())
                db.rehabGripQueries.lastId().executeAsOne()
            } else {
                q.updateTemplate(template.name.trim(), template.handMode.name, template.firstHand.name, template.handoverSeconds.toLong(), template.id)
                template.id
            }
            q.deleteSetsOf(id)
            template.sets.forEachIndexed { i, s -> insertSet(id, i, s) }
            id
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        q.transaction {
            q.deleteSetsOf(id)
            q.deleteTemplate(id)
        }
    }

    private fun insertSet(templateId: Long, position: Int, s: WorkoutSet) {
        val (type, min, max) = encodeTarget(s.target)
        val left = s.leftTarget?.let(::encodeTarget)
        q.insertSet(
            templateId, position.toLong(), s.reps.toLong(), s.workSeconds.toLong(), s.restSeconds.toLong(),
            (s.grip as? Grip.Standard)?.exercise?.name, (s.grip as? Grip.Library)?.grip?.id,
            type, min, max, s.toFailure.name, s.startMode.name, s.thresholdKg, s.hand.name,
            left?.first, left?.second, left?.third, s.leftToFailure?.name,
        )
    }

    private fun Workout_set.toDomain(library: Map<Long, GripSpec>, byExercise: Map<Exercise?, GripSpec>): WorkoutSet {
        val exercise = enumOrNull<Exercise>(grip_exercise) ?: Exercise.HALF_CRIMP
        val grip = (grip_custom_id?.let(library::get) ?: byExercise[exercise])?.let { Grip.Library(it) } ?: Grip.Standard(exercise)
        val target = decodeTarget(target_type, target_min, target_max)
        return WorkoutSet(
            reps = reps.toInt(),
            workSeconds = work_s.toInt(),
            restSeconds = rest_s.toInt(),
            grip = grip,
            target = target,
            toFailure = enumOrNull<ToFailure>(to_failure) ?: ToFailure.OFF,
            startMode = enumOrNull<StartMode>(start_mode) ?: StartMode.TIME,
            thresholdKg = threshold_kg,
            hand = enumOrNull<SetHand>(hand) ?: SetHand.SESSION,
            leftTarget = left_target_type?.let { decodeTarget(it, left_target_min, left_target_max) },
            leftToFailure = enumOrNull<ToFailure>(left_to_failure),
        )
    }

    private fun encodeTarget(t: SetTarget): Triple<String, Double?, Double?> = when (t) {
        SetTarget.Peak -> Triple("PEAK", null, null)
        is SetTarget.Kg -> Triple("KG", t.minKg, t.maxKg)
        is SetTarget.PercentOfRecord -> Triple("PERCENT", t.minPercent?.toDouble(), t.maxPercent?.toDouble())
    }

    private fun decodeTarget(type: String, min: Double?, max: Double?): SetTarget = when (type) {
        "KG" -> SetTarget.Kg(min, max)
        "PERCENT" -> SetTarget.PercentOfRecord(min?.toInt(), max?.toInt())
        else -> SetTarget.Peak
    }
}

class SqlWorkoutResultRepository(private val db: RehabGripDatabase) : WorkoutResultRepository {
    private val q get() = db.round2Queries

    override fun observeAll(): Flow<List<WorkoutResultEntry>> = q.selectResults().asFlow().mapToList(Dispatchers.IO)
        .map { rows ->
            rows.groupBy { it.epoch_millis }.map { (at, group) ->
                WorkoutResultEntry(
                    name = group.first().name,
                    epochMillis = at,
                    hands = group.mapNotNull { r ->
                        enumOrNull<Hand>(r.hand)?.let { HandWorkoutResult(it, r.max_kg, r.avg_kg, r.in_zone, r.reps.toInt()) }
                    },
                )
            }
        }

    override suspend fun add(entry: WorkoutResultEntry) = withContext(Dispatchers.IO) {
        q.transaction {
            entry.hands.forEach {
                q.insertResult(entry.name, entry.epochMillis, it.hand.name, it.maxKg, it.avgPullKg, it.inZonePercent, it.reps.toLong())
            }
        }
    }

    override suspend fun delete(epochMillis: Long) = withContext(Dispatchers.IO) { q.deleteResultsAt(epochMillis) }

    override suspend fun changeDate(oldEpochMillis: Long, newEpochMillis: Long) =
        withContext(Dispatchers.IO) { q.moveResults(newEpochMillis, oldEpochMillis) }
}
