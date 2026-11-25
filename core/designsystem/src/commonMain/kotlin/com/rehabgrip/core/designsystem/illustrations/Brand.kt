package com.rehabgrip.core.designsystem.illustrations

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** RehabGrip mark: navy mountain range with snow caps. */
object Brand {
    val Mountain: ImageVector by lazy {
        ImageVector.Builder("Mountain", 200.dp, 110.dp, 200f, 110f).apply {
            shape(Color(0xFF3A64A8)) { moveTo(0f, 110f); lineTo(58f, 42f); lineTo(96f, 82f); lineTo(120f, 110f); close() }
            shape(Palette.Navy) { moveTo(40f, 110f); lineTo(110f, 8f); lineTo(200f, 110f); close() }
            shape(Palette.Snow) {
                moveTo(110f, 8f); lineTo(134f, 36f); lineTo(122f, 32f); lineTo(114f, 44f)
                lineTo(104f, 34f); lineTo(92f, 40f); close()
            }
            shape(Palette.Snow, 0.85f) { moveTo(58f, 42f); lineTo(72f, 57f); lineTo(62f, 54f); lineTo(52f, 60f); lineTo(48f, 54f); close() }
            line(Color(0xFF6E93CF), 2f, alpha = 0.7f) { moveTo(110f, 44f); lineTo(126f, 76f); lineTo(118f, 96f) }
        }.build()
    }

    /** Bottom landscape used on welcome / profile footers. */
    val Ridge: ImageVector by lazy {
        ImageVector.Builder("Ridge", 360.dp, 90.dp, 360f, 90f).apply {
            shape(Color(0xFFBFD7F6)) { moveTo(0f, 90f); lineTo(0f, 50f); lineTo(70f, 18f); lineTo(140f, 52f); lineTo(220f, 10f); lineTo(300f, 46f); lineTo(360f, 26f); lineTo(360f, 90f); close() }
            shape(Color(0xFF8DB6EC)) { moveTo(0f, 90f); lineTo(0f, 70f); lineTo(90f, 44f); lineTo(170f, 72f); lineTo(260f, 40f); lineTo(360f, 66f); lineTo(360f, 90f); close() }
            shape(Color(0xFF3A64A8)) { moveTo(0f, 90f); lineTo(60f, 74f); lineTo(140f, 86f); lineTo(230f, 70f); lineTo(360f, 84f); lineTo(360f, 90f); close() }
        }.build()
    }
}
