package dev.accessauto.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessauto.R
import dev.accessauto.grammar.DriveState
import dev.accessauto.grammar.Outcome
import dev.accessauto.grammar.Prompt
import dev.accessauto.sim.DriveSession
import dev.accessauto.vision.GestureEngine

/** The screen the driver glances at: one dial, up to three numbered tiles, big words. */
@Composable
fun DriveScreen(engine: GestureEngine, onTraining: () -> Unit) {
    val session = remember(engine) { DriveSession(engine) }
    LaunchedEffect(session) { session.run() }
    val frame by engine.frames.collectAsStateWithLifecycle()
    val state = session.state

    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Cockpit.Aluminium).systemBarsPadding().padding(16.dp)
            .clip(shape).border(8.dp, Cockpit.Ink, shape).background(Cockpit.Dial),
    ) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Row {
                Panel(state, frame, wide, onTraining, Modifier.weight(1f).fillMaxHeight())
                Camera(engine, frame, Modifier.weight(0.4f).fillMaxHeight())
            }
        } else {
            Column {
                Camera(engine, frame, Modifier.weight(0.45f).fillMaxWidth())
                Panel(state, frame, wide, onTraining, Modifier.weight(1f).fillMaxWidth())
            }
        }
        // After an action goes out, the whole screen shows what it did. The camera keeps running underneath.
        (state as? DriveState.Finished)?.effect?.let { EffectScreen(it, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Panel(state: DriveState, frame: GestureEngine.Frame, wide: Boolean, onTraining: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (state) {
            DriveState.Idle -> Headline(0f, Cockpit.Needle, stringResource(R.string.idle_title), Cockpit.Ink, stringResource(R.string.idle_hint))
            is DriveState.Asking -> Asking(state, wide)
            is DriveState.SosHold -> Headline(state.progress, Cockpit.Danger, stringResource(R.string.sos_hold), Cockpit.Danger, stringResource(R.string.sos_hold_hint))
            is DriveState.Finished -> when (state.outcome) {
                Outcome.DONE -> Headline(1f, Cockpit.Ok, stringResource(R.string.out_done), Cockpit.Ok, state.detail)
                Outcome.CANCELLED -> Headline(0f, Cockpit.Danger, stringResource(R.string.out_cancelled), Cockpit.Danger, "")
                Outcome.TIMED_OUT -> Headline(0f, Cockpit.Muted, stringResource(R.string.out_timed_out), Cockpit.Muted, stringResource(R.string.out_timed_out_detail))
                Outcome.SOS -> Headline(1f, Cockpit.Danger, stringResource(R.string.out_sos), Cockpit.Danger, stringResource(R.string.out_sos_detail))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.camera_sees) + ": " + stringResource(frame.raw.labelRes()), style = Cockpit.Label)
                Text(stringResource(R.string.demo_note), style = Cockpit.Body.copy(fontSize = 13.sp))
            }
            ModeButton(stringResource(R.string.to_training), onTraining)
        }
    }
}

@Composable
private fun Headline(progress: Float, needle: Color, title: String, titleColor: Color, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Gauge(progress, needle, Modifier.size(104.dp))
        Text(title, style = Cockpit.Readout.copy(color = titleColor))
    }
    if (detail.isNotEmpty()) Text(detail, style = Cockpit.Body.copy(fontSize = 20.sp, lineHeight = 26.sp, color = Cockpit.Ink))
}

@Composable
private fun Asking(state: DriveState.Asking, wide: Boolean) {
    val cancelling = state.cancel > 0f
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (cancelling) {
            // A held fist fills this ring; when it closes the prompt is cancelled.
            Ring(state.cancel, Cockpit.Danger, Cockpit.Aluminium, Modifier.size(64.dp))
            Text(stringResource(R.string.cancel_hold), style = Cockpit.Label.copy(fontSize = 22.sp, lineHeight = 28.sp, color = Cockpit.Danger))
        } else {
            // The needle falls as the time to answer runs out.
            Gauge(state.timeLeft, Cockpit.Needle, Modifier.size(64.dp))
            Text(state.prompt.title, style = Cockpit.Label.copy(fontSize = 22.sp, lineHeight = 28.sp))
        }
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.prompt.options.forEachIndexed { i, _ -> Tile(state.prompt, i, state.selected == i, state.commit, stacked = true, Modifier.weight(1f)) }
        }
    } else {
        state.prompt.options.forEachIndexed { i, _ -> Tile(state.prompt, i, state.selected == i, state.commit, stacked = false, Modifier.fillMaxWidth()) }
    }
    Text(stringResource(if (state.selected == null) R.string.hint_pick else R.string.hint_confirm), style = Cockpit.Body)
}

/** A ring that closes clockwise from the top as [progress] goes 0..1. */
@Composable
private fun Ring(progress: Float, color: Color, track: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val inset = Offset(stroke / 2, stroke / 2)
        val ring = Size(size.width - stroke, size.height - stroke)
        drawArc(track, 0f, 360f, false, inset, ring, style = Stroke(stroke))
        drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, inset, ring, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/**
 * One numbered answer. The picked tile inverts so it reads at a glance, and a ring closes
 * around its number: when the ring is full the answer is carried out.
 */
@Composable
private fun Tile(prompt: Prompt, index: Int, picked: Boolean, commit: Float, stacked: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val box = modifier.border(3.dp, Cockpit.Ink, shape).background(if (picked) Cockpit.Ink else Cockpit.Dial, shape).padding(horizontal = 12.dp, vertical = 8.dp)
    val number = @Composable {
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            if (picked) Canvas(Modifier.fillMaxSize()) {
                val stroke = size.minDimension * 0.1f
                val inset = Offset(stroke / 2, stroke / 2)
                val ring = Size(size.width - stroke, size.height - stroke)
                drawArc(Cockpit.Muted, 0f, 360f, false, inset, ring, style = Stroke(stroke))
                drawArc(Cockpit.Needle, -90f, 360f * commit.coerceIn(0f, 1f), false, inset, ring, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("${index + 1}", style = Cockpit.Readout.copy(fontSize = 52.sp, lineHeight = 52.sp, color = Cockpit.Needle))
        }
    }
    val label = @Composable { Text(prompt.options[index].label, style = Cockpit.Label.copy(fontSize = 18.sp, lineHeight = 22.sp, color = if (picked) Cockpit.Dial else Cockpit.Ink)) }
    if (stacked) Column(box) { number(); label() }
    else Row(box, verticalAlignment = Alignment.CenterVertically) { number(); Spacer(Modifier.width(14.dp)); label() }
}

@Composable
internal fun ModeButton(text: String, onClick: () -> Unit) {
    Text(
        text, style = Cockpit.Label,
        modifier = Modifier.border(2.dp, Cockpit.Ink, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
