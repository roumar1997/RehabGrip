package com.rehabgrip.core.domain.grip

import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.GripSpec
import com.rehabgrip.core.domain.workout.WorkoutTemplate

/** Which saved sessions rely on a grip; needed before deleting it. */
object GripUsage {
    fun usedBy(grip: GripSpec, templates: List<WorkoutTemplate>): List<WorkoutTemplate> =
        templates.filter { t -> t.sets.any { (it.grip as? Grip.Library)?.grip?.id == grip.id } }

    /** Grips a session can switch to when [grip] goes away. */
    fun replacements(grip: GripSpec, library: List<GripSpec>): List<GripSpec> = library.filter { it.id != grip.id }
}
