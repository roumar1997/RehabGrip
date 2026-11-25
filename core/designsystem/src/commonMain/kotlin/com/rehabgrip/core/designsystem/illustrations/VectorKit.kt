package com.rehabgrip.core.designsystem.illustrations

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path

/** Tiny drawing kit for the hand-made illustrations (viewport units). */
internal object Palette {
    val Sky = Color(0xFFE6F0FC)
    val WoodTop = Color(0xFFE2B47E)
    val WoodFront = Color(0xFFC48A52)
    val WoodShadow = Color(0xFF9A6634)
    val WoodGrain = Color(0xFFAD7742)
    val Skin = Color(0xFFF2C6A3)
    val SkinShade = Color(0xFFDDA27E)
    val SkinDeep = Color(0xFFC98762)
    val Nail = Color(0xFFF9E2D2)
    val Device = Color(0xFF2B3340)
    val DeviceLight = Color(0xFF424C5C)
    val Lcd = Color(0xFFB9C7A6)
    val LcdInk = Color(0xFF2C3524)
    val Navy = Color(0xFF13285A)
    val Blue = Color(0xFF1E6FE0)
    val Snow = Color(0xFFFFFFFF)
}

internal fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    val rr = minOf(r, w / 2, h / 2)
    moveTo(x + rr, y)
    lineTo(x + w - rr, y)
    arcTo(rr, rr, 0f, false, true, x + w, y + rr)
    lineTo(x + w, y + h - rr)
    arcTo(rr, rr, 0f, false, true, x + w - rr, y + h)
    lineTo(x + rr, y + h)
    arcTo(rr, rr, 0f, false, true, x, y + h - rr)
    lineTo(x, y + rr)
    arcTo(rr, rr, 0f, false, true, x + rr, y)
    close()
}

internal fun ImageVector.Builder.rect(
    x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, alpha: Float = 1f,
) = path(fill = SolidColor(color), fillAlpha = alpha) { roundRect(x, y, w, h, r) }

internal fun ImageVector.Builder.line(
    color: Color, width: Float, alpha: Float = 1f, block: PathBuilder.() -> Unit,
) = path(stroke = SolidColor(color), strokeLineWidth = width, strokeAlpha = alpha, strokeLineCap = StrokeCap.Round, pathBuilder = block)

internal fun ImageVector.Builder.shape(color: Color, alpha: Float = 1f, block: PathBuilder.() -> Unit) =
    path(fill = SolidColor(color), fillAlpha = alpha, pathBuilder = block)

internal fun ImageVector.Builder.rotated(
    degrees: Float, px: Float, py: Float, block: ImageVector.Builder.() -> Unit,
) = group(rotate = degrees, pivotX = px, pivotY = py) { block() }

/** Soft rounded background used behind every exercise illustration. */
internal fun ImageVector.Builder.backdrop(w: Float, h: Float) {
    rect(0f, 0f, w, h, 18f, Palette.Sky)
    shape(Color.White, 0.55f) {
        moveTo(0f, h * 0.72f)
        curveTo(w * 0.3f, h * 0.6f, w * 0.6f, h * 0.85f, w, h * 0.7f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
}

/** 20 mm wooden edge seen slightly from above: top face + front face + grain. */
internal fun ImageVector.Builder.woodenEdge(left: Float, right: Float, topY: Float) {
    val w = right - left
    rect(left + 4f, topY + 6f, w, 52f, 10f, Color.Black, alpha = 0.10f)
    rect(left, topY, w, 22f, 8f, Palette.WoodTop)
    rect(left, topY + 16f, w, 40f, 8f, Palette.WoodFront)
    rect(left, topY + 16f, w, 6f, 3f, Palette.WoodShadow, alpha = 0.55f)
    listOf(0.12f, 0.33f, 0.58f, 0.81f).forEachIndexed { i, f ->
        line(Palette.WoodGrain, 1.4f, alpha = 0.7f) {
            val gx = left + w * f
            moveTo(gx, topY + 26f + i % 2 * 6f)
            curveTo(gx + 22f, topY + 30f, gx + 40f, topY + 22f + i % 2 * 8f, gx + 62f, topY + 34f)
        }
    }
    line(Palette.WoodGrain, 1.1f, alpha = 0.6f) {
        moveTo(left + 14f, topY + 8f)
        curveTo(left + w * 0.3f, topY + 4f, left + w * 0.6f, topY + 12f, right - 16f, topY + 7f)
    }
}
