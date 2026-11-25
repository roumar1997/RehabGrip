package com.rehabgrip.core.domain.model

/** Predefined discomfort zones (Spanish labels are the stored value, so history stays readable). */
object DiscomfortZones {
    private val fingers = listOf(
        Finger.THUMB to "pulgar",
        Finger.INDEX to "índice",
        Finger.MIDDLE to "corazón",
        Finger.RING to "anular",
        Finger.LITTLE to "meñique",
    )

    fun handSuffix(hand: Hand) = if (hand == Hand.RIGHT) "dcha" else "izda"

    fun fingerName(finger: Finger) = fingers.first { it.first == finger }.second

    /** General zones of one hand. */
    fun general(hand: Hand): List<String> {
        val s = handSuffix(hand)
        return listOf("Muñeca $s", "Cicatriz $s", "Antebrazo/pronador $s", "Palma $s")
    }

    /** Finger plus its A1-A4 pulleys (the thumb only has A1 and A2). */
    fun finger(hand: Hand, finger: Finger): List<String> {
        val s = handSuffix(hand)
        val name = fingerName(finger)
        val pulleys = if (finger == Finger.THUMB) listOf("A1", "A2") else listOf("A1", "A2", "A3", "A4")
        return listOf("Dedo $name $s") + pulleys.map { "Polea $it $name $s" }
    }
}
