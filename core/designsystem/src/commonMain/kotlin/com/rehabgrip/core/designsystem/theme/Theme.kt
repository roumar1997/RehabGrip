package com.rehabgrip.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rehabgrip.core.designsystem.resources.Res
import com.rehabgrip.core.designsystem.resources.nunito_400
import com.rehabgrip.core.designsystem.resources.nunito_600
import com.rehabgrip.core.designsystem.resources.nunito_700
import com.rehabgrip.core.designsystem.resources.nunito_800
import org.jetbrains.compose.resources.Font

/** Palette taken from docs/maqueta.webp. */
object RgColors {
    val Primary = Color(0xFF1E6FE0)
    val PrimaryDark = Color(0xFF1456B8)
    val Navy = Color(0xFF13285A)
    val TextSecondary = Color(0xFF5B6B8C)
    val Background = Color(0xFFEFF4FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceTint = Color(0xFFE3EEFC)
    val Outline = Color(0xFFD5E2F5)
    val RightHand = Color(0xFF1E6FE0)
    val LeftHand = Color(0xFF8EC5F5)
    val Success = Color(0xFF2BB673)
    val SuccessBg = Color(0xFFE2F6EA)
    val Danger = Color(0xFFE5484D)
    val Warning = Color(0xFFF2A33A)
    val Gold = Color(0xFFF5B400)
}

private val colors = lightColorScheme(
    primary = RgColors.Primary,
    onPrimary = Color.White,
    primaryContainer = RgColors.SurfaceTint,
    onPrimaryContainer = RgColors.Navy,
    secondary = RgColors.LeftHand,
    background = RgColors.Background,
    onBackground = RgColors.Navy,
    surface = RgColors.Surface,
    onSurface = RgColors.Navy,
    surfaceVariant = RgColors.SurfaceTint,
    onSurfaceVariant = RgColors.TextSecondary,
    outline = RgColors.Outline,
    error = RgColors.Danger,
)

@Composable
private fun nunito(): FontFamily {
    return FontFamily(
        Font(Res.font.nunito_400, FontWeight.Normal),
        Font(Res.font.nunito_600, FontWeight.SemiBold),
        Font(Res.font.nunito_700, FontWeight.Bold),
        Font(Res.font.nunito_800, FontWeight.ExtraBold),
    )
}

@Composable
private fun typography(): Typography {
    val f = nunito()
    fun s(size: Int, weight: Int, line: Int = size + 6) =
        TextStyle(fontFamily = f, fontSize = size.sp, fontWeight = FontWeight(weight), lineHeight = line.sp)
    return Typography(
        displayLarge = s(56, 800, 60),
        displayMedium = s(44, 800, 50),
        headlineLarge = s(32, 800),
        headlineMedium = s(26, 800),
        headlineSmall = s(22, 800),
        titleLarge = s(20, 800),
        titleMedium = s(17, 700),
        titleSmall = s(15, 700),
        bodyLarge = s(16, 600),
        bodyMedium = s(14, 600),
        bodySmall = s(12, 600),
        labelLarge = s(16, 800),
        labelMedium = s(13, 700),
        labelSmall = s(11, 700),
    )
}

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun RehabGripTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography(), shapes = shapes, content = content)
}
