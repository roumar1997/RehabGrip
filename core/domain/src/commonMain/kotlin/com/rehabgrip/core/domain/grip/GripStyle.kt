package com.rehabgrip.core.domain.grip

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.workout.GripSpec

/** Colour (base ARGB) and letters that identify a grip in every Evolution view. */
data class GripStyle(val code: String, val base: Long) {
    val color: GripColor get() = GripPalette.of(base)
}

/**
 * Stable colour and letters per grip. Every grip has its OWN colour: predefined grips keep theirs and those are
 * reserved, an own grip takes the first colour that is neither reserved nor too close to one already used.
 */
object GripStyles {
    const val DYNAMOMETER_ID = -1L
    const val MAX_CODE = 3

    private val palette get() = GripPalette.bases
    private val presetColor get() = mapOf(
        GripSpec.KEY_BIDEDO_STRETCHED to palette[3], GripSpec.KEY_BIDEDO_CRIMP to palette[4],
        GripSpec.KEY_PISTOL to palette[6], GripSpec.KEY_PISTOL_CRIMP to palette[7],
    )
    private val presetCode = mapOf(
        GripSpec.KEY_BIDEDO_STRETCHED to "BE", GripSpec.KEY_BIDEDO_CRIMP to "BA",
        GripSpec.KEY_PISTOL to "BP", GripSpec.KEY_PISTOL_CRIMP to "PA",
    )

    fun dynamometer() = GripStyle("DM", palette[5])

    /** Colour a predefined grip has by default; null for the user's own. */
    fun defaultColor(g: GripSpec): Long? = when (g.builtIn) {
        Exercise.HALF_CRIMP -> palette[0]
        Exercise.THREE_FINGER -> palette[1]
        Exercise.OPEN_HAND -> palette[2]
        else -> g.presetKey?.let { presetColor[it] }
    }

    /** Letters a predefined grip has by default; null for the user's own. */
    fun defaultCode(g: GripSpec): String? = when (g.builtIn) {
        Exercise.HALF_CRIMP -> "SA"
        Exercise.THREE_FINGER -> "E"
        Exercise.OPEN_HAND -> "SE"
        else -> g.presetKey?.let { presetCode[it] }
    }

    /** Colours of the eight predefined identities (seven grips and the dynamometer): never given to an own grip. */
    val reserved: List<Long> get() = palette.take(8)

    /** Up to [MAX_CODE] upper-case letters or digits typed by the user. */
    fun sanitize(raw: String): String = raw.filter { it.isLetterOrDigit() }.take(MAX_CODE).uppercase()

    /** Initials of the name (Regleta mala gives RM); an unnamed grip uses its edge width. */
    fun initialsOf(g: GripSpec): String {
        val words = g.name.trim().split(Regex("\\s+")).filter { w -> w.any { it.isLetterOrDigit() } }
        if (words.isEmpty()) return g.edgeMm.toString()
        return sanitize(words.joinToString("") { w -> w.first { it.isLetterOrDigit() }.toString() })
    }

    fun codeOf(g: GripSpec): String = sanitize(g.initials).ifEmpty { defaultCode(g) ?: initialsOf(g) }

    /** Style of every library grip by id, plus the dynamometer. Never depends on the selection or the range. */
    fun resolve(library: List<GripSpec>): Map<Long, GripStyle> {
        val taken = reserved.toMutableList()
        val out = LinkedHashMap<Long, GripStyle>()
        library.filter { it.isPredefined }.sortedBy { it.id }.forEach { g ->
            val c = g.color ?: defaultColor(g)!!
            taken += c
            out[g.id] = GripStyle(codeOf(g), c)
        }
        library.filter { !it.isPredefined }.sortedBy { it.id }.forEach { g ->
            val c = g.color?.takeIf { ColorMath.farFrom(it, taken) } ?: GripPalette.nextFree(taken)
            taken += c
            out[g.id] = GripStyle(codeOf(g), c)
        }
        out[DYNAMOMETER_ID] = dynamometer()
        return out
    }

    /** Own grips whose stored colour is missing, repeated or too close to another: id to the colour they must be given. */
    fun repairs(library: List<GripSpec>): Map<Long, Long> {
        val resolved = resolve(library)
        return library.filter { !it.isPredefined && it.color != resolved.getValue(it.id).base }.associate { it.id to resolved.getValue(it.id).base }
    }

    /** First colour free for a new grip. */
    fun nextFreeColor(library: List<GripSpec>): Long =
        GripPalette.nextFree(resolve(library).values.map { it.base } + reserved)

    /** Colour the editor offers first for [grip]: its own, or the next free one. */
    fun suggestedColor(grip: GripSpec, library: List<GripSpec>): Long =
        if (grip.id != 0L) resolve(library)[grip.id]?.base ?: nextFreeColor(library) else grip.color ?: nextFreeColor(library)

    /** Colours used by the other grips and by the dynamometer, with who uses them, for the editor. */
    fun takenBy(library: List<GripSpec>, excludeId: Long): List<Pair<Long, String>> {
        val resolved = resolve(library)
        return library.filter { it.id != excludeId }.map { resolved.getValue(it.id).base to it.label } + (dynamometer().base to "Dinamómetro de mano")
    }

    /** Name of the grip whose colour is too close to [color], or null when it is free to use. */
    fun conflict(color: Long, library: List<GripSpec>, excludeId: Long): String? =
        takenBy(library, excludeId).firstOrNull { ColorMath.deltaE(color, it.first) < ColorMath.MIN_DELTA_E }?.second
}
