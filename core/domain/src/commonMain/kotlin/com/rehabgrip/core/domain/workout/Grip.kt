package com.rehabgrip.core.domain.workout

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.model.Measurement

/** How the fingers hold the edge. */
enum class GripType { FULL_CRIMP, HALF_CRIMP, OPEN, DRAG, OTHER }

/**
 * One grip of the user's library: free name, edge width in mm, how many fingers
 * (and which) and the hold type. [builtIn] marks the three predefined ones, which
 * stay editable and are linked to the legacy [Exercise] measurements.
 */
data class GripSpec(
    val id: Long = 0,
    val name: String = "",
    val edgeMm: Int = 20,
    val fingerCount: Int = 4,
    /** Which fingers when not all of them: «centrales», «delanteros»… */
    val fingerNote: String = "",
    val type: GripType = GripType.HALF_CRIMP,
    val builtIn: Exercise? = null,
    /** Key of the newer predefined grips (they have no legacy exercise). Not deletable either. */
    val presetKey: String? = null,
    /** Palette slot chosen by the user (0..7); null = the predefined one or the first free. */
    val colorIndex: Int? = null,
    /** Letters shown in the charts (max 3); empty = the predefined ones or the initials of the name. */
    val initials: String = "",
) {
    /** Predefined grips can be edited but never deleted. */
    val isPredefined: Boolean get() = builtIn != null || presetKey != null

    /** «Semiarqueo 4 dedos · 20 mm»: the name always followed by the edge, or the summary when unnamed. */
    val fullLabel: String get() = if (name.isBlank()) summary else "${name.trim()} · $edgeMm mm"

    /** FREZ-like description: «20 mm / 3 dedos / drag». */
    val summary: String
        get() = buildString {
            append("$edgeMm mm / $fingerCount ${if (fingerCount == 1) "dedo" else "dedos"}")
            if (fingerNote.isNotBlank()) append(" ${fingerNote.trim()}")
            append(" / ").append(typeLabel(type))
        }

    /** Name shown everywhere; falls back to [summary] when the user left it empty. */
    val label: String get() = name.trim().ifBlank { summary }

    /** Problems blocking the save, or empty. */
    fun validate(): List<String> = buildList {
        if (edgeMm !in EDGE_RANGE) add("Regleta entre ${EDGE_RANGE.first} y ${EDGE_RANGE.last} mm")
        if (fingerCount !in 1..4) add("Entre 1 y 4 dedos")
        if (name.length > MAX_NAME) add("Nombre de $MAX_NAME letras como máximo")
    }

    /** True when [m] was measured with this grip (explicitly or, for predefined ones, by exercise). */
    fun matches(m: Measurement, library: List<GripSpec> = emptyList()): Boolean =
        if (library.isNotEmpty()) id != 0L && com.rehabgrip.core.domain.grip.GripKey.of(m, library) == com.rehabgrip.core.domain.grip.GripKey.Library(id)
        else (id != 0L && m.gripId == id) || (m.gripId == null && builtIn != null && m.exercise == builtIn)

    companion object {
        val EDGE_RANGE = 4..60
        const val MAX_NAME = 30

        fun typeLabel(t: GripType) = when (t) {
            GripType.FULL_CRIMP -> "arqueado"
            GripType.HALF_CRIMP -> "semiarqueado"
            GripType.OPEN -> "extendido"
            GripType.DRAG -> "drag"
            GripType.OTHER -> "otro"
        }

        /** Finger choices offered when not all of them are used. */
        fun fingerChoices(count: Int): List<String> = when (count) {
            1 -> listOf("índice", "corazón", "anular", "meñique")
            2 -> listOf("centrales", "delanteros", "traseros")
            3 -> listOf("delanteros", "traseros")
            else -> emptyList()
        }

        /** The three predefined grips (20 mm), in display order. */
        fun predefined(): List<GripSpec> = listOf(
            GripSpec(name = "Semiarqueo 4 dedos", fingerCount = 4, type = GripType.HALF_CRIMP, builtIn = Exercise.HALF_CRIMP),
            GripSpec(name = "Extensión 3 dedos", fingerCount = 3, type = GripType.DRAG, builtIn = Exercise.THREE_FINGER),
            GripSpec(name = "Semiestirado 4 dedos", fingerCount = 4, type = GripType.OPEN, builtIn = Exercise.OPEN_HAND),
        ) + presets()

        const val PRESET_PREFIX = "PRESET:"
        const val KEY_BIDEDO_STRETCHED = "BIDEDO_ESTIRADO"
        const val KEY_BIDEDO_CRIMP = "BIDEDO_ARQUEADO"
        const val KEY_PISTOL = "BIDEDO_PISTOLA"
        const val KEY_PISTOL_CRIMP = "BIDEDO_PISTOLA_ARQUEADO"

        /** The four two-finger grips of the FREZ session (20 mm), also predefined. */
        fun presets(): List<GripSpec> = listOf(
            GripSpec(name = "Bidedo estirado", fingerCount = 2, fingerNote = "delanteros", type = GripType.DRAG, presetKey = KEY_BIDEDO_STRETCHED),
            GripSpec(name = "Bidedo arqueado", fingerCount = 2, fingerNote = "delanteros", type = GripType.HALF_CRIMP, presetKey = KEY_BIDEDO_CRIMP),
            GripSpec(name = "Bidedo pistola", fingerCount = 2, fingerNote = "centrales", type = GripType.DRAG, presetKey = KEY_PISTOL),
            GripSpec(name = "Bidedo pistola arqueado", fingerCount = 2, fingerNote = "centrales", type = GripType.HALF_CRIMP, presetKey = KEY_PISTOL_CRIMP),
        )
    }
}

/** Where a set's grip comes from: a library entry, or (legacy, unresolved) a plain exercise. */
sealed interface Grip {
    data class Standard(val exercise: Exercise) : Grip
    data class Library(val grip: GripSpec) : Grip
}
