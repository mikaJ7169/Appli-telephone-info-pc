package dev.vibe.pcmon.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PcmonBlack = Color(0xFF000000)
val PcmonPanel = Color(0xFF101010)
val PcmonGrid = Color(0xFF1C1C1C)
val PcmonBorder = Color(0xFF232323)
val PcmonWhite = Color(0xFFFFFFFF)
val PcmonLight = Color(0xFFE8E8E8)
val PcmonMid = Color(0xFF9A9A9A)
val PcmonDim = Color(0xFF6B6B6B)
val PcmonAccent = Color(0xFF8A8A8A)

private fun sans(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = PcmonLight,
    spacing: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    fontWeight = weight,
    color = color,
    letterSpacing = spacing.sp,
)

private val PcmonTypography =
    Typography(
        displayLarge = sans(30f, FontWeight.Bold, PcmonWhite),
        displayMedium = sans(26f, FontWeight.Bold, PcmonWhite),
        displaySmall = sans(22f, FontWeight.SemiBold, PcmonWhite),
        headlineLarge = sans(20f, FontWeight.Bold, PcmonWhite),
        headlineMedium = sans(18f, FontWeight.SemiBold, PcmonWhite),
        headlineSmall = sans(16f, FontWeight.SemiBold, PcmonWhite),
        titleLarge = sans(18f, FontWeight.SemiBold, PcmonWhite),
        titleMedium = sans(15f, FontWeight.SemiBold, PcmonWhite),
        titleSmall = sans(13f, FontWeight.Medium, PcmonWhite),
        bodyLarge = sans(15f, color = PcmonLight),
        bodyMedium = sans(13f, color = PcmonLight),
        bodySmall = sans(12f, color = PcmonMid),
        labelLarge = sans(13f, FontWeight.Medium, PcmonLight, 0.4f),
        labelMedium = sans(11f, FontWeight.Medium, PcmonMid, 0.6f),
        labelSmall = sans(10f, FontWeight.Medium, PcmonDim, 0.8f),
    )

private val PcmonColorScheme =
    darkColorScheme(
        primary = PcmonWhite,
        onPrimary = PcmonBlack,
        secondary = PcmonMid,
        onSecondary = PcmonBlack,
        background = PcmonBlack,
        onBackground = PcmonLight,
        surface = PcmonPanel,
        onSurface = PcmonLight,
        surfaceVariant = Color(0xFF181818),
        onSurfaceVariant = PcmonMid,
        outline = PcmonBorder,
        outlineVariant = PcmonGrid,
        error = PcmonWhite,
        onError = PcmonBlack,
    )

@Composable
fun PcmonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PcmonColorScheme,
        typography = PcmonTypography,
        content = content,
    )
}
