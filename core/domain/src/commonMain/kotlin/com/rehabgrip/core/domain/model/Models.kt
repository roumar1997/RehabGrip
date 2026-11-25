package com.rehabgrip.core.domain.model

/** Which hand a measurement belongs to. Mandatory on every record. */
enum class Hand {
    RIGHT, LEFT;

    val other: Hand get() = if (this == RIGHT) LEFT else RIGHT
}

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
    /** Optional discomforts noted when saving (zone + level 0-10). */
    val discomforts: List<Discomfort> = emptyList(),
)

/** One discomfort: predefined or free-text zone and level 0-10. */
data class Discomfort(val zone: String, val level: Int) {
    init {
        require(level in 0..10) { "level out of range: $level" }
        require(zone.isNotBlank()) { "zone is blank" }
    }
}

enum class Finger { THUMB, INDEX, MIDDLE, RING, LITTLE }

/** Injured/operated hand (null = none) and, optionally, which fingers. */
data class Injury(val hand: Hand?, val fingers: Set<Finger> = emptySet()) {
    /** Hand compared against the healthy one; right when nothing is injured. */
    val focusHand: Hand get() = hand ?: Hand.RIGHT

    companion object {
        val NONE = Injury(null)
    }
}

data class UserProfile(val name: String)

/** One force reading over time. [tMillis] is relative to the start of the session. */
data class ForceSample(val tMillis: Long, val kg: Double)
