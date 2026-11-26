package com.rehabgrip.core.domain.grip

import com.rehabgrip.core.domain.model.Exercise
import com.rehabgrip.core.domain.workout.GripSpec

/** One colour of the palette: [base] (right hand, borders), [tint] (left hand fill) and [dark] (letters on the tint). ARGB. */
data class GripColor(val base: Long, val tint: Long, val dark: Long)

/** Colour and letters that identify a grip in every Evolution view. */
data class GripStyle(val code: String, val colorIndex: Int) {
    val color: GripColor get() = GripPalette.colors[colorIndex.mod(GripPalette.size)]
}

/** The eight colours, in the agreed order: blue, orange, aqua, yellow, magenta, green, violet, red. */
object GripPalette {
    val colors = listOf(
        GripColor(0xFF2A78D6, 0xFFC6DCF8, 0xFF14407C),
        GripColor(0xFFEB6834, 0xFFFBD2C0, 0xFF8F3A0E),
        GripColor(0xFF1BAF7A, 0xFFBFEAD9, 0xFF0B5E41),
        GripColor(0xFFEDA100, 0xFFFBEAC7, 0xFF775100),
        GripColor(0xFFE87BA4, 0xFFFAE2EB, 0xFF743E52),
        GripColor(0xFF008300, 0xFFC7E4C7, 0xFF004200),
        GripColor(0xFF6250D6, 0xFFDCD9F6, 0xFF31286B),
        GripColor(0xFFE34948, 0xFFF9D7D7, 0xFF722524),
    )
    val size get() = colors.size
    val names = listOf("Azul", "Naranja", "Aqua", "Amarillo", "Magenta", "Verde", "Violeta", "Rojo")
}

/** Stable colour and letters per grip: stored values win, then the predefined ones, then the first free colour. */
object GripStyles {
    const val DYNAMOMETER_ID = -1L
    const val MAX_CODE = 3

    private val presetColor = mapOf(
        GripSpec.KEY_BIDEDO_STRETCHED to 3, GripSpec.KEY_BIDEDO_CRIMP to 4,
        GripSpec.KEY_PISTOL to 6, GripSpec.KEY_PISTOL_CRIMP to 7,
    )
    private val presetCode = mapOf(
        GripSpec.KEY_BIDEDO_STRETCHED to "BE", GripSpec.KEY_BIDEDO_CRIMP to "BA",
        GripSpec.KEY_PISTOL to "BP", GripSpec.KEY_PISTOL_CRIMP to "PA",
    )

    fun dynamometer() = GripStyle("DM", 5)

    /** Colour a predefined grip has by default; null for the user's own. */
    fun defaultColorIndex(g: GripSpec): Int? = when (g.builtIn) {
        Exercise.HALF_CRIMP -> 0
        Exercise.THREE_FINGER -> 1
        Exercise.OPEN_HAND -> 2
        else -> g.presetKey?.let { presetColor[it] }
    }

    /** Letters a predefined grip has by default; null for the user's own. */
    fun defaultCode(g: GripSpec): String? = when (g.builtIn) {
        Exercise.HALF_CRIMP -> "SA"
        Exercise.THREE_FINGER -> "E"
        Exercise.OPEN_HAND -> "SE"
        else -> g.presetKey?.let { presetCode[it] }
    }

    /** Up to [MAX_CODE] upper-case letters or digits typed by the user. */
    fun sanitize(raw: String): String = raw.filter { it.isLetterOrDigit() }.take(MAX_CODE).uppercase()

    /** Initials of the name (Regleta mala gives RM); an unnamed grip uses its edge width. */
    fun initialsOf(g: GripSpec): String {
        val words = g.name.trim().split(Regex("\\s+")).filter { w -> w.any { it.isLetterOrDigit() } }
        if (words.isEmpty()) return g.edgeMm.toString()
        return sanitize(words.joinToString("") { w -> w.first { it.isLetterOrDigit() }.toString() })
    }

    fun codeOf(g: GripSpec): String = sanitize(g.initials).ifEmpty { defaultCode(g) ?: initialsOf(g) }

    /** First colour no grip of [library] uses; when all eight are taken they are shared out again (the letters tell them apart). */
    fun nextFreeColor(library: List<GripSpec>): Int {
        val used = resolve(library).filterKeys { it != DYNAMOMETER_ID }.values.map { it.colorIndex }
        return (0 until GripPalette.size).firstOrNull { it !in used } ?: (library.size % GripPalette.size)
    }

    /** Colour the editor offers first for [grip]: its own, its default, or the next free one. */
    fun suggestedColor(grip: GripSpec, library: List<GripSpec>): Int =
        grip.colorIndex ?: defaultColorIndex(grip) ?: resolve(library)[grip.id]?.colorIndex ?: nextFreeColor(library)

    /** Style of every library grip by id, plus the dynamometer. Never changes with the selection or the range. */
    fun resolve(library: List<GripSpec>): Map<Long, GripStyle> {
        val fixed = library.associate { it.id to (it.colorIndex ?: defaultColorIndex(it)) }
        val used = fixed.values.filterNotNull().toMutableSet()
        var shared = 0
        val out = LinkedHashMap<Long, GripStyle>()
        library.sortedBy { it.id }.forEach { g ->
            val idx = fixed[g.id] ?: (0 until GripPalette.size).firstOrNull { it !in used }?.also { used += it }
                ?: (shared++ % GripPalette.size)
            out[g.id] = GripStyle(codeOf(g), idx.mod(GripPalette.size))
        }
        out[DYNAMOMETER_ID] = dynamometer()
        return out
    }
}
