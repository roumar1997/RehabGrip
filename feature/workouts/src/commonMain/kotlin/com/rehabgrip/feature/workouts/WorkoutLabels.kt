package com.rehabgrip.feature.workouts

import com.rehabgrip.core.designsystem.components.Labels
import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.workout.Grip
import com.rehabgrip.core.domain.workout.HandMode
import com.rehabgrip.core.domain.workout.SetHand
import com.rehabgrip.core.domain.workout.SetTarget
import com.rehabgrip.core.domain.workout.StartMode
import com.rehabgrip.core.domain.workout.ToFailure
import com.rehabgrip.core.domain.workout.WorkoutEngine
import com.rehabgrip.core.domain.workout.WorkoutTemplate

internal object WorkoutLabels {
    fun grip(g: Grip): String = when (g) {
        is Grip.Standard -> when (g.exercise) {
            Exercise.HALF_CRIMP -> "Semiarqueo 4 dedos"
            Exercise.THREE_FINGER -> "Extensión 3 dedos"
            Exercise.OPEN_HAND -> "Semiestirado 4 dedos"
            Exercise.DYNAMOMETER -> Labels.exercise(g.exercise)
        }
        is Grip.Custom -> "${g.grip.edgeMm} mm / ${g.grip.fingers} / ${g.grip.type}"
    }

    fun handMode(m: HandMode) = when (m) {
        HandMode.SINGLE -> "Una mano"
        HandMode.BOTH_SEQUENTIAL -> "Ambas seguidas"
        HandMode.ALTERNATING -> "Alternando"
    }

    fun handModeHelp(m: HandMode) = when (m) {
        HandMode.SINGLE -> "Toda la sesión con la misma mano."
        HandMode.BOTH_SEQUENTIAL -> "Haces todas las series con una mano y luego con la otra."
        HandMode.ALTERNATING -> "Mientras una mano descansa tira la otra. El turno es estricto: I, D, I, D…"
    }

    fun setHand(h: SetHand) = when (h) {
        SetHand.SESSION -> "Como la sesión"
        SetHand.RIGHT -> "Derecha"
        SetHand.LEFT -> "Izquierda"
    }

    fun failure(f: ToFailure) = when (f) {
        ToFailure.OFF -> "Desactivado"
        ToFailure.REPS -> "Repetición"
        ToFailure.TIME -> "Tiempo"
    }

    fun failureHelp(f: ToFailure) = when (f) {
        ToFailure.OFF -> "Completa las repeticiones y el tiempo configurados normalmente."
        ToFailure.REPS -> "Sigue sumando repeticiones hasta que una no aguante el tiempo de trabajo; ahí termina la serie."
        ToFailure.TIME -> "Cada repetición dura hasta que la fuerza baja del umbral de parada."
    }

    fun start(s: StartMode) = when (s) {
        StartMode.TIME -> "Por tiempo"
        StartMode.WEIGHT -> "Por peso"
    }

    fun startHelp(s: StartMode) = when (s) {
        StartMode.TIME -> "El contador arranca a su hora: empieza a tirar y mantén el tiempo de trabajo."
        StartMode.WEIGHT -> "La repetición solo empieza a contar cuando superas el umbral (o el mínimo del objetivo). Después el temporizador sigue aunque la fuerza baje."
    }

    fun target(t: SetTarget) = when (t) {
        SetTarget.Peak -> "Máximo"
        is SetTarget.Kg -> "${t.minKg?.let { kg(it) } ?: "—"}–${t.maxKg?.let { kg(it) } ?: "—"} kg"
        is SetTarget.PercentOfRecord -> "${t.minPercent ?: "—"}–${t.maxPercent ?: "—"} % registro"
    }

    fun kg(v: Double): String {
        val c = kotlin.math.round(v * 100).toLong()
        return "${c / 100},${(c % 100).toString().padStart(2, '0')}"
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
    }

    fun estimate(t: WorkoutTemplate) = duration(WorkoutEngine.estimateDurationMs(t))
}
