package dev.accessauto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.accessauto.R
import dev.accessauto.grammar.Effect

private val Title = Cockpit.Readout.copy(fontSize = 58.sp, lineHeight = 56.sp)
private val Big = Cockpit.Label.copy(fontSize = 22.sp, lineHeight = 28.sp)

/** Shows what an action did: the sent message, the route, the declined call or the player. */
@Composable
fun EffectScreen(effect: Effect, modifier: Modifier = Modifier) {
    Column(modifier.background(Cockpit.Dial).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.demo_badge), style = Cockpit.Body.copy(fontSize = 13.sp))
        when (effect) {
            is Effect.Message -> {
                Text(effect.to, style = Title)
                effect.replyTo?.let { Bubble(it, mine = false) }
                Bubble(effect.text, mine = true)
                Sent()
            }
            is Effect.CallDeclined -> {
                Text(effect.caller, style = Title)
                Text(stringResource(R.string.call_declined), style = Big.copy(color = Cockpit.Danger))
                effect.text?.let { Bubble(it, mine = true); Sent() }
            }
            is Effect.Navigation -> Navigation(effect)
            is Effect.Music -> Music(effect)
        }
    }
}

/** The result screen on its own, inside the instrument bezel. */
@Composable
fun EffectPreview(effect: Effect) {
    val shape = RoundedCornerShape(28.dp)
    Box(Modifier.fillMaxSize().background(Cockpit.Aluminium).systemBarsPadding().padding(16.dp).clip(shape).border(8.dp, Cockpit.Ink, shape)) {
        EffectScreen(effect, Modifier.fillMaxSize())
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
        Text(
            text, style = Big.copy(color = if (mine) Cockpit.Dial else Cockpit.Ink),
            modifier = Modifier.background(if (mine) Cockpit.Ink else Cockpit.Aluminium, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Sent() = Text(stringResource(R.string.sent), style = Cockpit.Label.copy(color = Cockpit.Ok))

@Composable
private fun ColumnScope.Navigation(effect: Effect.Navigation) {
    Text(effect.title, style = Title)
    Canvas(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(Cockpit.Aluminium)) {
        val w = size.width; val h = size.height
        fun p(x: Float, y: Float) = Offset(w * x, h * y)
        // Side streets.
        listOf(p(0f, .3f) to p(1f, .22f), p(0f, .68f) to p(1f, .6f), p(.25f, 0f) to p(.3f, 1f), p(.72f, 0f) to p(.66f, 1f))
            .forEach { (a, b) -> drawLine(Cockpit.Dial, a, b, strokeWidth = h * .035f) }
        fun route(a: Offset, b: Offset, c: Offset, d: Offset) = Path().apply { moveTo(a.x, a.y); listOf(b, c, d).forEach { lineTo(it.x, it.y) } }
        val line = Stroke(h * .05f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val start = p(.28f, .88f); val end = p(.7f, .14f)
        if (effect.rerouted) {
            // The jammed route stays visible in grey next to the new one.
            drawPath(route(start, p(.27f, .66f), p(.26f, .29f), end), Cockpit.Muted, style = Stroke(h * .03f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(route(start, p(.27f, .66f), p(.68f, .62f), end), Cockpit.Ok, style = line)
        } else {
            drawPath(route(start, p(.27f, .66f), p(.68f, .62f), end), Cockpit.Needle, style = line)
        }
        drawCircle(Cockpit.Dial, h * .05f, start); drawCircle(Cockpit.Ink, h * .03f, start)
        drawCircle(Cockpit.Ink, h * .06f, end); drawCircle(Cockpit.Dial, h * .025f, end)
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(effect.eta, style = Title.copy(fontSize = 48.sp, lineHeight = 46.sp, color = if (effect.rerouted) Cockpit.Ok else Cockpit.Needle))
        Column { Text(stringResource(R.string.eta_label), style = Cockpit.Body); Text(effect.distance, style = Cockpit.Label) }
    }
}

@Composable
private fun ColumnScope.Music(effect: Effect.Music) {
    Text(effect.status, style = Big.copy(color = if (effect.playing) Cockpit.Ok else Cockpit.Muted))
    Text(effect.track, style = Title)
    Text(effect.artist, style = Cockpit.Body.copy(fontSize = 18.sp))
    Spacer(Modifier.weight(1f))
    Canvas(Modifier.size(120.dp).align(Alignment.CenterHorizontally)) {
        val r = size.minDimension / 2
        drawCircle(Cockpit.Ink, r)
        if (effect.playing) {
            // Playing: the button offers pause.
            val bar = Size(r * .22f, r * .8f)
            drawRoundRect(Cockpit.Dial, Offset(r * .62f, r * .6f), bar, CornerRadius(r * .06f))
            drawRoundRect(Cockpit.Dial, Offset(r * 1.16f, r * .6f), bar, CornerRadius(r * .06f))
        } else {
            drawPath(Path().apply { moveTo(r * .78f, r * .55f); lineTo(r * 1.5f, r); lineTo(r * .78f, r * 1.45f); close() }, Cockpit.Dial)
        }
    }
    Spacer(Modifier.weight(1f))
    Box(Modifier.fillMaxWidth().height(10.dp).background(Cockpit.Aluminium, RoundedCornerShape(5.dp))) {
        Box(Modifier.fillMaxWidth(0.3f).height(10.dp).background(Cockpit.Needle, RoundedCornerShape(5.dp)))
    }
}
