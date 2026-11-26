package com.rehabgrip.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.rehabgrip.core.data.db.RehabGripDatabase
import com.rehabgrip.core.domain.grip.GripEvaluation
import com.rehabgrip.core.domain.grip.GripStyles
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.port.GripEvaluationRepository
import com.rehabgrip.core.domain.port.GripRepository
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.GripType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Grip library. The `custom_grip` table keeps its round-2 name; predefined rows have `built_in`. */
class SqlGripRepository(private val db: RehabGripDatabase) : GripRepository {
    private val q get() = db.round2Queries

    override fun observeAll(): Flow<List<GripSpec>> = q.selectGrips().asFlow().mapToList(Dispatchers.IO).map { rows ->
        rows.map {
            GripSpec(
                id = it.id,
                name = it.name,
                edgeMm = it.edge_mm.toInt(),
                fingerCount = it.finger_count.toInt().coerceIn(1, 4),
                fingerNote = it.fingers,
                type = enumOrNull<GripType>(it.type) ?: GripType.OTHER,
                builtIn = enumOrNull<Exercise>(it.built_in),
                presetKey = it.built_in?.takeIf { k -> k.startsWith(GripSpec.PRESET_PREFIX) }?.removePrefix(GripSpec.PRESET_PREFIX),
                colorIndex = it.color_index?.toInt(),
                initials = it.initials,
            )
        }
    }

    override suspend fun upsert(grip: GripSpec): Long = withContext(Dispatchers.IO) {
        val name = grip.name.trim()
        val note = grip.fingerNote.trim()
        val initials = GripStyles.sanitize(grip.initials)
        // A new own grip gets the first free colour at once, so it never changes later.
        val color = grip.colorIndex ?: if (grip.id == 0L && !grip.isPredefined) GripStyles.nextFreeColor(observeAll().first()) else null
        if (grip.id == 0L) {
            q.transactionWithResult {
                q.insertGrip(name, grip.edgeMm.toLong(), grip.fingerCount.toLong(), note, grip.type.name, grip.builtIn?.name ?: grip.presetKey?.let { k -> GripSpec.PRESET_PREFIX + k }, color?.toLong(), initials)
                db.rehabGripQueries.lastId().executeAsOne()
            }
        } else {
            q.updateGrip(name, grip.edgeMm.toLong(), grip.fingerCount.toLong(), note, grip.type.name, grip.colorIndex?.toLong(), initials, grip.id)
            grip.id
        }
    }

    override suspend fun delete(id: Long, replacementId: Long?) = withContext(Dispatchers.IO) {
        q.transaction {
            if (replacementId != null) q.replaceGripInSets(replacementId, id)
            q.deleteGrip(id)
        }
    }

    override suspend fun ensureDefaults() {
        val grips = observeAll().first()
        val haveExercises = grips.mapNotNull { it.builtIn }.toSet()
        val havePresets = grips.mapNotNull { it.presetKey }.toSet()
        GripSpec.predefined().filter { if (it.builtIn != null) it.builtIn !in haveExercises else it.presetKey !in havePresets }
            .forEach { upsert(it) }
    }
}

class SqlGripEvaluationRepository(private val db: RehabGripDatabase) : GripEvaluationRepository {
    private val q get() = db.round2Queries

    override fun observeAll(): Flow<List<GripEvaluation>> = q.selectEvaluations().asFlow().mapToList(Dispatchers.IO).map { rows ->
        rows.mapNotNull { r ->
            enumOrNull<Hand>(r.hand)?.let { GripEvaluation(r.id, r.grip_id, it, r.max_kg, r.min_kg, r.avg_kg, r.epoch_millis) }
        }
    }

    override suspend fun add(evaluation: GripEvaluation): Long = withContext(Dispatchers.IO) {
        q.transactionWithResult {
            q.insertEvaluation(evaluation.gripId, evaluation.hand.name, evaluation.maxKg, evaluation.minKg, evaluation.avgKg, evaluation.epochMillis)
            db.rehabGripQueries.lastId().executeAsOne()
        }
    }
}
