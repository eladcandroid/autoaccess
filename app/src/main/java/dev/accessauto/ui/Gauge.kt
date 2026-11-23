package dev.accessauto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

private const val START = 150f
private const val SWEEP = 240f
private const val TICKS = 20

/** Instrument gauge. [progress] 0..1 moves the needle across a 240-degree sweep. */
@Composable
fun Gauge(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        fun at(deg: Float, radius: Float): Offset {
            val a = Math.toRadians(deg.toDouble())
            return Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
        }
        for (i in 0..TICKS) {
            val deg = START + SWEEP * i / TICKS
            val long = i % 5 == 0
            drawLine(Cockpit.Ink, at(deg, r * (if (long) 0.78f else 0.84f)), at(deg, r * 0.96f), strokeWidth = r * (if (long) 0.035f else 0.02f))
        }
        val arcR = r * 0.66f
        drawArc(
            color, START, SWEEP * progress.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(c.x - arcR, c.y - arcR), size = Size(arcR * 2, arcR * 2),
            style = Stroke(width = r * 0.08f, cap = StrokeCap.Round),
        )
        drawLine(color, c, at(START + SWEEP * progress.coerceIn(0f, 1f), r * 0.7f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
        drawCircle(Cockpit.Ink, r * 0.12f, c)
    }
}
