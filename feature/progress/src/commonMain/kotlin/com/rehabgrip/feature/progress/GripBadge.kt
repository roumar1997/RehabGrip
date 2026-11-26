package com.rehabgrip.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.domain.grip.GripStyle

/** Colours and letters of a grip as Compose values; the same everywhere in Evolution. */
internal class GripLook(val code: String, val color: Color, val tint: Color, val dark: Color) {
    companion object {
        fun of(s: GripStyle) = GripLook(s.code, Color(s.color.base), Color(s.color.tint), Color(s.color.dark))
    }
}

/** The letters in a circle: filled with white letters = right hand; tinted with border and dark letters = left hand. */
@Composable
internal fun GripBadge(look: GripLook, right: Boolean = true, modifier: Modifier = Modifier) {
    val fill = if (right) look.color else look.tint
    val border = if (right) null else BorderStroke(2.dp, look.color)
    Box(
        modifier.defaultMinSize(24.dp, 24.dp).clip(CircleShape).background(fill)
            .let { m -> if (border != null) m.border(border, CircleShape) else m }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            look.code, color = if (right) Color.White else look.dark, fontWeight = FontWeight.Medium,
            fontSize = if (look.code.length >= 3) 9.sp else 11.sp, maxLines = 1,
        )
    }
}

/** Right (filled) and left (tinted) badge side by side. */
@Composable
internal fun GripBadges(look: GripLook, modifier: Modifier = Modifier) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    GripBadge(look, true)
    GripBadge(look, false, Modifier.padding(start = 3.dp))
}
