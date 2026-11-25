package dev.vibe.pcmon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import dev.vibe.pcmon.theme.PcmonAccent
import dev.vibe.pcmon.theme.PcmonGrid
import dev.vibe.pcmon.theme.PcmonWhite
import kotlin.math.roundToInt

@Composable
fun PixelChart(
    primary: List<Float>,
    modifier: Modifier = Modifier,
    secondary: List<Float>? = null,
    max: Float,
    rows: Int = 20,
    primaryColor: Color = PcmonWhite,
    secondaryColor: Color = PcmonAccent,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(96.dp)) {
        val w = size.width
        val h = size.height
        val cellH = h / rows
        for (r in 1 until rows) {
            val y = r * cellH
            drawLine(PcmonGrid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }
        secondary?.let { drawSeries(it, max, w, h, cellH, rows, secondaryColor) }
        drawSeries(primary, max, w, h, cellH, rows, primaryColor)
    }
}

private fun DrawScope.drawSeries(
    values: List<Float>,
    max: Float,
    w: Float,
    h: Float,
    cellH: Float,
    rows: Int,
    color: Color,
) {
    if (values.isEmpty() || max <= 0f) return
    val cw = w / values.size
    values.forEachIndexed { i, v ->
        val filled =
            ((v / max).coerceIn(0f, 1f) * rows)
                .roundToInt()
                .coerceAtLeast(if (v > 0f) 1 else 0)
        val x = i * cw
        drawRect(
            color = color,
            topLeft = Offset(x, h - filled * cellH),
            size = Size(cw, filled * cellH),
        )
    }
}
