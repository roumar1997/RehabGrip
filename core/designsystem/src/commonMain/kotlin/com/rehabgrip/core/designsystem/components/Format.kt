package com.rehabgrip.core.designsystem.components

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Hand
import com.rehabgrip.core.domain.workout.GripType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.round

/** Spanish UI texts for domain enums (kept out of the domain on purpose). */
object Labels {
    fun exercise(e: Exercise) = when (e) {
        Exercise.HALF_CRIMP -> "Semiarqueo"
        Exercise.THREE_FINGER -> "Extensión"
        Exercise.OPEN_HAND -> "Semiestirado"
        Exercise.DYNAMOMETER -> "Dinamómetro de mano"
        Exercise.CUSTOM -> "Agarre propio"
    }

    fun exerciseDetail(e: Exercise) = when (e) {
        Exercise.HALF_CRIMP -> "4 dedos flexionados · regleta 20 mm"
        Exercise.THREE_FINGER -> "3 dedos en extensión · regleta 20 mm"
        Exercise.OPEN_HAND -> "4 dedos estirados · regleta 20 mm"
        Exercise.DYNAMOMETER -> "Aprieta y apunta el valor"
        Exercise.CUSTOM -> "Agarre de tu biblioteca"
    }

    fun gripType(t: GripType) = when (t) {
        GripType.FULL_CRIMP -> "Arqueado"
        GripType.HALF_CRIMP -> "Semiarqueado"
        GripType.OPEN -> "Extendido / abierto"
        GripType.DRAG -> "Drag"
        GripType.OTHER -> "Otro"
    }

    fun hand(h: Hand) = when (h) {
        Hand.RIGHT -> "Derecha"
        Hand.LEFT -> "Izquierda"
    }

    fun handShort(h: Hand) = when (h) {
        Hand.RIGHT -> "Dcha"
        Hand.LEFT -> "Izda"
    }
}

/** "32,7" — one decimal, Spanish comma. */
fun formatKg(kg: Double): String {
    val tenths = round(kg * 10).toLong()
    return "${tenths / 10},${abs(tenths % 10)}"
}

/** "12/11/2026". */
fun formatShortDate(epochMillis: Long): String {
    val d = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${d.dayOfMonth}/${d.monthNumber}/${d.year}"
}

/** "+12 %" / "−3 %". */
fun formatPercent(value: Double, signed: Boolean = true): String {
    val r = round(value).toLong()
    val sign = when {
        !signed -> ""
        r > 0 -> "+"
        r < 0 -> "−"
        else -> ""
    }
    return "$sign${abs(r)} %"
}
