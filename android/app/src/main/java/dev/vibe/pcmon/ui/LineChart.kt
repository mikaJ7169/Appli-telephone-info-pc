package dev.vibe.pcmon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.vibe.pcmon.theme.PcmonAccent
import dev.vibe.pcmon.theme.PcmonGrid
import dev.vibe.pcmon.theme.PcmonWhite

@Composable
fun LineChart(
    primary: List<Float>,
    secondary: List<Float>? = null,
    max: Float,
    modifier: Modifier = Modifier.fillMaxWidth().height(110.dp),
    primaryColor: Color = PcmonWhite,
    secondaryColor: Color = PcmonAccent,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 2f
        val padY = 6f
        val plotH = h - 2 * padY
        for (i in 1..3) {
            val y = padY + plotH * i / 4f
            drawLine(PcmonGrid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        fun points(values: List<Float>): List<Offset> {
            if (values.isEmpty()) return emptyList()
            val n = values.size
            return values.mapIndexed { i, v ->
                val x = if (n == 1) w / 2f else padX + (w - 2 * padX) * i / (n - 1).toFloat()
                val y = padY + plotH * (1f - (v / max).coerceIn(0f, 1f))
                Offset(x, y)
            }
        }

        fun pathOf(pts: List<Offset>): Path =
            Path().apply {
                if (pts.isNotEmpty()) {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                }
            }

        fun stroke(pts: List<Offset>, color: Color, width: Float) {
            if (pts.size < 2) return
            drawPath(
                pathOf(pts),
                color = color,
                style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        fun area(pts: List<Offset>, top: Color) {
            if (pts.size < 2) return
            val p = Path().apply {
                moveTo(pts[0].x, h)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, h)
                close()
            }
            drawPath(
                p,
                brush = Brush.verticalGradient(
                    colors = listOf(top, Color.Transparent),
                    startY = padY,
                    endY = h,
                ),
            )
        }

        secondary?.let { stroke(points(it), secondaryColor, 1.5f) }
        val pp = points(primary)
        area(pp, primaryColor.copy(alpha = 0.16f))
        stroke(pp, primaryColor, 2.5f)
        pp.lastOrNull()?.let { drawCircle(primaryColor, radius = 3.5f, center = it) }
    }
}
