package com.rehabgrip.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.rehabgrip.core.designsystem.theme.RgColors
import com.rehabgrip.core.domain.model.Finger
import com.rehabgrip.core.domain.model.Hand

private val Skin = Color(0xFFF2C6A3)
private val SkinShade = Color(0xFFDDA27E)

/** Finger geometry in a 100x120 box for a RIGHT palm facing the viewer (thumb on the left). */
private data class FingerShape(val finger: Finger, val x: Float, val y: Float, val w: Float, val h: Float, val angle: Float)

private val rightHandFingers = listOf(
    FingerShape(Finger.THUMB, 6f, 52f, 15f, 36f, -38f),
    FingerShape(Finger.INDEX, 24f, 14f, 14f, 46f, -6f),
    FingerShape(Finger.MIDDLE, 41f, 6f, 15f, 52f, 0f),
    FingerShape(Finger.RING, 59f, 11f, 14f, 48f, 5f),
    FingerShape(Finger.LITTLE, 75f, 26f, 12f, 38f, 12f),
)

/**
 * Open hand drawing; tap a finger to toggle it. Mirrored for the left hand so the
 * thumb is always on the correct side.
 */
@Composable
fun HandFingerPicker(
    hand: Hand,
    selected: Set<Finger>,
    onToggle: (Finger) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = if (hand == Hand.RIGHT) rightHandFingers else rightHandFingers.map { it.copy(x = 100f - it.x - it.w, angle = -it.angle) }
    Canvas(
        modifier.aspectRatio(100f / 120f).pointerInput(hand) {
            detectTapGestures { tap ->
                val sx = size.width / 100f
                val sy = size.height / 120f
                val p = Offset(tap.x / sx, tap.y / sy)
                shapes.minByOrNull { s -> (Rect(s.x, s.y, s.x + s.w, s.y + s.h).center - p).getDistance() }
                    ?.takeIf { s -> Rect(s.x - 6, s.y - 6, s.x + s.w + 6, s.y + s.h + 6).contains(p) }
                    ?.let { onToggle(it.finger) }
            }
        },
    ) {
        val sx = size.width / 100f
        val sy = size.height / 120f
        // palm + wrist
        drawRoundRect(SkinShade, Offset(30 * sx, 92 * sy), Size(40 * sx, 28 * sy), CornerRadius(6 * sx))
        drawRoundRect(Skin, Offset(20 * sx, 50 * sy), Size(64 * sx, 56 * sy), CornerRadius(22 * sx))
        shapes.forEach { s -> drawFinger(s, s.finger in selected, sx, sy) }
    }
}

private fun DrawScope.drawFinger(s: FingerShape, isSelected: Boolean, sx: Float, sy: Float) {
    val pivot = Offset((s.x + s.w / 2) * sx, (s.y + s.h) * sy)
    rotate(s.angle, pivot) {
        val color = if (isSelected) RgColors.Danger else Skin
        drawRoundRect(color, Offset(s.x * sx, s.y * sy), Size(s.w * sx, (s.h + 10) * sy), CornerRadius(s.w / 2 * sx))
        drawRoundRect(
            Color.White.copy(alpha = 0.6f), Offset((s.x + 3) * sx, (s.y + 2) * sy),
            Size((s.w - 6) * sx, 8 * sy), CornerRadius(3 * sx),
        )
        listOf(0.38f, 0.68f).forEach { f ->
            val y = (s.y + s.h * f) * sy
            drawLine(SkinShade.copy(alpha = if (isSelected) 0f else 0.8f), Offset((s.x + 3) * sx, y), Offset((s.x + s.w - 3) * sx, y), 1.4f * sx)
        }
    }
}
