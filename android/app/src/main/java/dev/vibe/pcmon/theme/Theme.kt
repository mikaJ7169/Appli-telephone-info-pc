package dev.vibe.pcmon.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val PcmonBlack = Color(0xFF000000)
val PcmonPanel = Color(0xFF0A0A0A)
val PcmonGrid = Color(0xFF1A1A1A)
val PcmonBorder = Color(0xFF2A2A2A)
val PcmonWhite = Color(0xFFFFFFFF)
val PcmonLight = Color(0xFFE8E8E8)
val PcmonMid = Color(0xFF9A9A9A)
val PcmonDim = Color(0xFF5C5C5C)
val PcmonAccent = Color(0xFF6E6E6E)

private fun mono(
    size: Float,
    color: Color = PcmonLight,
) = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size.sp, color = color)

private val PcmonTypography =
    Typography(
        displayLarge = mono(28f, PcmonWhite),
        displayMedium = mono(24f, PcmonWhite),
        displaySmall = mono(20f, PcmonWhite),
        headlineLarge = mono(18f, PcmonWhite),
        headlineMedium = mono(16f, PcmonWhite),
        headlineSmall = mono(14f, PcmonWhite),
        titleLarge = mono(16f, PcmonWhite),
        titleMedium = mono(14f, PcmonWhite),
        titleSmall = mono(12f, PcmonWhite),
        bodyLarge = mono(14f),
        bodyMedium = mono(12f),
        bodySmall = mono(11f, PcmonMid),
        labelLarge = mono(12f),
        labelMedium = mono(11f, PcmonMid),
        labelSmall = mono(10f, PcmonDim),
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
        surfaceVariant = Color(0xFF141414),
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
