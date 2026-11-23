package dev.accessauto.sim

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.accessauto.grammar.DriveState
import dev.accessauto.grammar.GestureMachine
import dev.accessauto.vision.Gesture
import dev.accessauto.vision.GestureEngine
import kotlinx.coroutines.delay

/**
 * Connects the camera to the gesture grammar and plays the demo situations one after another
 * whenever the screen has been idle for a few seconds.
 */
class DriveSession(private val engine: GestureEngine) {
    var state: DriveState by mutableStateOf(DriveState.Idle)
        private set

    private val machine = GestureMachine(DemoScenarios.menu) {
        // Rehearsal log: which situation, how it ended, how long it took, how many picks it needed.
        Log.i("Scenario", "id=${it.promptId.ifEmpty { "-" }} outcome=${it.outcome} cause=${it.cause} ms=${it.tookMs} picks=${it.picks} detail=${it.detail}")
    }
    private var lastHeadAt = engine.frames.value.headEventAt
    private var idleSince = SystemClock.uptimeMillis()
    private var next = 0
    private var loggedStable = Gesture.NONE
    private var loggedRaw = Gesture.NONE
    private var loggedState = ""

    suspend fun run() {
        while (true) {
            step(SystemClock.uptimeMillis())
            delay(33)
        }
    }

    private fun step(tMs: Long) {
        val frame = engine.frames.value
        val head = if (frame.headEventAt != lastHeadAt) { lastHeadAt = frame.headEventAt; frame.headEvent } else Gesture.NONE
        // Trace for working out why something happened: what the camera saw, what counted, what the screen did.
        if (frame.raw != loggedRaw) { loggedRaw = frame.raw; Log.d(TAG, "raw=${frame.raw}") }
        if (frame.stable != loggedStable) { loggedStable = frame.stable; Log.i(TAG, "held=${frame.stable}") }
        if (head != Gesture.NONE) Log.i(TAG, "head=$head")
        val wasIdle = state == DriveState.Idle
        var now = machine.update(frame.stable, head, tMs)
        if (now == DriveState.Idle) {
            if (!wasIdle) idleSince = tMs
            if (tMs - idleSince >= GAP_MS) {
                val prompt = DemoScenarios.incoming[next++ % DemoScenarios.incoming.size]
                Log.i("Scenario", "show id=${prompt.id}")
                machine.present(prompt, tMs)
                now = machine.state
            }
        }
        state = now
        val summary = when (now) {
            DriveState.Idle -> "Idle"
            is DriveState.Asking -> "Asking ${now.prompt.id} selected=${now.selected}"
            is DriveState.Finished -> "Finished ${now.outcome} effect=${now.effect?.javaClass?.simpleName}"
            is DriveState.SosHold -> "SosHold"
        }
        if (summary != loggedState) { loggedState = summary; Log.i(TAG, "state=$summary") }
    }

    private companion object {
        const val GAP_MS = 6_000L
        const val TAG = "Scenario"
    }
}
