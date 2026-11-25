package com.rehabgrip.core.designsystem.illustrations

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rehabgrip.core.domain.model.Exercise

private const val W = 240f
private const val H = 160f
private const val EDGE_TOP = 34f

/** How a finger sits on the edge. */
private enum class Pose { CRIMP, OPEN, CURLED }

/** Illustration for each exercise (vector, no photos). */
object GripIllustrations {
    val HalfCrimp: ImageVector by lazy { hangboard("HalfCrimp", List(4) { Pose.CRIMP }) }
    val ThreeFinger: ImageVector by lazy { hangboard("ThreeFinger", listOf(Pose.CRIMP, Pose.CRIMP, Pose.CRIMP, Pose.CURLED)) }
    val OpenHand: ImageVector by lazy { hangboard("OpenHand", List(4) { Pose.OPEN }) }
    val Dynamometer: ImageVector by lazy { dynamometer() }

    fun of(exercise: Exercise) = when (exercise) {
        Exercise.HALF_CRIMP -> HalfCrimp
        Exercise.THREE_FINGER -> ThreeFinger
        Exercise.OPEN_HAND -> OpenHand
        Exercise.DYNAMOMETER -> Dynamometer
    }
}

private fun builder(name: String) = ImageVector.Builder(name, 240.dp, 160.dp, W, H)

/** Right hand from the palm side, fingers over a 20 mm edge. Index on the left. */
private fun hangboard(name: String, poses: List<Pose>): ImageVector = builder(name).apply {
    backdrop(W, H)
    woodenEdge(left = 22f, right = 218f, topY = EDGE_TOP)
    // forearm + palm
    shape(Palette.SkinShade) {
        moveTo(92f, 160f); lineTo(96f, 132f); lineTo(170f, 132f); lineTo(166f, 160f); close()
    }
    rect(74f, 92f, 112f, 54f, 26f, Palette.Skin)
    shape(Palette.SkinShade, 0.55f) {
        moveTo(84f, 128f); curveTo(110f, 140f, 150f, 140f, 178f, 126f)
        lineTo(178f, 134f); curveTo(150f, 148f, 110f, 148f, 84f, 136f); close()
    }
    line(Palette.SkinDeep, 1.6f, alpha = 0.5f) { moveTo(92f, 112f); curveTo(120f, 122f, 150f, 118f, 172f, 108f) }
    // thumb, wrapped low across the palm
    rotated(-58f, 80f, 118f) { rect(62f, 106f, 54f, 22f, 11f, Palette.Skin) }
    rotated(-58f, 80f, 118f) { rect(100f, 109f, 14f, 15f, 6f, Palette.Nail, alpha = 0.9f) }
    // fingers (index -> pinky)
    val xs = listOf(82f, 107f, 132f, 157f)
    poses.forEachIndexed { i, pose ->
        val width = if (i == 3) 19f else 22f
        finger(xs[i] + (22f - width) / 2, width, pose, lengthBias = if (i == 1) 4f else if (i == 3) -6f else 0f)
    }
}.build()

private fun ImageVector.Builder.finger(x: Float, w: Float, pose: Pose, lengthBias: Float) {
    when (pose) {
        Pose.CRIMP -> {
            // proximal + middle phalanx rising to the edge, distal pad on the edge
            rect(x, EDGE_TOP + 18f - lengthBias, w, 84f + lengthBias, w / 2, Palette.Skin)
            rect(x - 1f, EDGE_TOP + 4f - lengthBias, w + 2f, 22f, 9f, Palette.Skin)
            rect(x + 3f, EDGE_TOP + 4f - lengthBias, w - 6f, 9f, 4f, Palette.Nail)
            crease(x, w, EDGE_TOP + 30f)
            crease(x, w, EDGE_TOP + 52f)
            shape(Palette.SkinShade, 0.6f) { roundRect(x, EDGE_TOP + 22f - lengthBias, w, 5f, 2.5f) }
        }
        Pose.OPEN -> {
            // longer, straight finger lying over the edge; pads flat on top face
            rect(x, EDGE_TOP - 2f - lengthBias, w, 104f + lengthBias, w / 2, Palette.Skin)
            rect(x + 3f, EDGE_TOP - lengthBias, w - 6f, 10f, 5f, Palette.Nail)
            crease(x, w, EDGE_TOP + 26f)
            crease(x, w, EDGE_TOP + 50f)
        }
        Pose.CURLED -> {
            // pinky folded under, not touching the edge
            rect(x, EDGE_TOP + 60f, w, 40f, w / 2, Palette.SkinShade)
            rect(x + 1f, EDGE_TOP + 58f, w - 2f, 18f, 9f, Palette.Skin)
            crease(x, w, EDGE_TOP + 70f)
        }
    }
}

private fun ImageVector.Builder.crease(x: Float, w: Float, y: Float) =
    line(Palette.SkinDeep, 1.3f, alpha = 0.6f) { moveTo(x + 4f, y); curveTo(x + w / 2, y + 2.5f, x + w / 2, y + 2.5f, x + w - 4f, y) }

/** Hand dynamometer held and squeezed, LCD on top. */
private fun dynamometer(): ImageVector = builder("Dynamometer").apply {
    backdrop(W, H)
    // shadow + head with display
    rect(92f, 16f, 80f, 62f, 16f, Color.Black, alpha = 0.10f)
    rect(86f, 10f, 80f, 62f, 16f, Palette.Device)
    rect(96f, 20f, 60f, 30f, 6f, Palette.Lcd)
    sevenSegments(102f, 25f)
    rect(118f, 56f, 16f, 8f, 4f, Palette.DeviceLight)
    // fixed bar and moving handle
    rect(100f, 66f, 22f, 86f, 9f, Palette.Device)
    rect(132f, 72f, 20f, 78f, 9f, Palette.DeviceLight)
    // wrist + palm behind handle
    shape(Palette.SkinShade) { moveTo(40f, 160f); lineTo(52f, 110f); lineTo(104f, 112f); lineTo(100f, 160f); close() }
    rect(64f, 74f, 46f, 80f, 22f, Palette.Skin)
    // thumb over the fixed bar
    rotated(-12f, 90f, 80f) { rect(80f, 70f, 34f, 18f, 9f, Palette.Skin) }
    rect(104f, 70f, 11f, 12f, 5f, Palette.Nail, alpha = 0.9f)
    // four fingers wrapping the handle
    listOf(84f, 101f, 118f, 135f).forEachIndexed { i, y ->
        val len = if (i == 3) 54f else 60f
        rect(104f, y, len, 16f, 8f, Palette.Skin)
        line(Palette.SkinDeep, 1.2f, alpha = 0.5f) { moveTo(104f, y + 15.5f); lineTo(104f + len - 6f, y + 15.5f) }
        rect(104f + len - 10f, y + 2f, 8f, 12f, 4f, Palette.Nail)
        line(Palette.SkinDeep, 1.2f, alpha = 0.55f) { moveTo(130f, y + 3f); lineTo(130f, y + 13f) }
    }
}.build()

/** Draws "42.6" with block segments so the device reads as a dynamometer. */
private fun ImageVector.Builder.sevenSegments(x: Float, y: Float) {
    val digits = listOf(
        listOf(false, true, true, false, false, true, true), // 4
        listOf(true, true, false, true, true, false, true), // 2
        listOf(true, false, true, true, true, true, true), // 6
    )
    digits.forEachIndexed { i, seg ->
        val dx = x + i * 15f + if (i == 2) 5f else 0f
        val ink = Palette.LcdInk
        if (seg[0]) rect(dx + 2f, y, 8f, 2.4f, 1f, ink)
        if (seg[1]) rect(dx + 10f, y + 1f, 2.4f, 9f, 1f, ink)
        if (seg[2]) rect(dx + 10f, y + 11f, 2.4f, 9f, 1f, ink)
        if (seg[3]) rect(dx + 2f, y + 18f, 8f, 2.4f, 1f, ink)
        if (seg[4]) rect(dx, y + 11f, 2.4f, 9f, 1f, ink)
        if (seg[5]) rect(dx, y + 1f, 2.4f, 9f, 1f, ink)
        if (seg[6]) rect(dx + 2f, y + 9f, 8f, 2.4f, 1f, ink)
    }
    rect(x + 31f, y + 18f, 2.6f, 2.6f, 1.3f, Palette.LcdInk)
}
