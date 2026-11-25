package com.rehabgrip.core.domain.model

/** Which hand a measurement belongs to. Mandatory on every record. */
enum class Hand { RIGHT, LEFT }

/** Grip exercises. [usesScale] = measured with the Crane WH-C06 on a 20 mm wooden edge. */
enum class Exercise(val usesScale: Boolean) {
    /** Half crimp: four fingers flexed. */
    HALF_CRIMP(usesScale = true),

    /** Three-finger drag (extension). */
    THREE_FINGER(usesScale = true),

    /** Open hand: four fingers almost straight. */
    OPEN_HAND(usesScale = true),

    /** Hand dynamometer squeezed; value typed by hand. */
    DYNAMOMETER(usesScale = false),
}

enum class MeasurementSource { SCALE, MANUAL }

data class Measurement(
    val id: Long = 0,
    val exercise: Exercise,
    val hand: Hand,
    val kg: Double,
    val epochMillis: Long,
    val source: MeasurementSource,
    val note: String = "",
)

data class UserProfile(val name: String)

/** One force reading over time. [tMillis] is relative to the start of the session. */
data class ForceSample(val tMillis: Long, val kg: Double)
