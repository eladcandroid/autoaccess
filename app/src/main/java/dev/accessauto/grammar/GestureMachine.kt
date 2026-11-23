package dev.accessauto.grammar

import dev.accessauto.vision.Gesture

/** What carrying out an option does in the world, so the screen can show it. */
sealed interface Effect {
    data class Message(val to: String, val text: String, val replyTo: String? = null) : Effect
    data class Navigation(val title: String, val eta: String, val distance: String, val rerouted: Boolean = false) : Effect
    data class CallDeclined(val caller: String, val text: String? = null) : Effect
    data class Music(val status: String, val track: String, val artist: String, val playing: Boolean) : Effect
    /** A player command; whoever owns the player turns it into the [Music] state that results. */
    data class MusicCommand(val command: Command) : Effect {
        enum class Command { TOGGLE, NEXT, PREVIOUS }
    }
}

/** One choice in a prompt. Choosing it either opens [next] or finishes with [result] and [effect]. */
data class Option(val label: String, val next: Prompt? = null, val result: String = label, val effect: Effect? = null)

/** A question with at most three answers, picked by showing one, two or three fingers. */
data class Prompt(val id: String, val title: String, val options: List<Option>, val timeoutMs: Long = 8_000) {
    init { require(options.size in 1..3) { "a prompt has 1 to 3 options" } }
}

enum class Outcome { DONE, CANCELLED, TIMED_OUT, SOS }

sealed interface DriveState {
    data object Idle : DriveState
    /**
     * [timeLeft] runs from 1 down to 0, when an unanswered prompt closes by itself.
     * Once an option is [selected], [commit] fills 0..1 and the option is carried out when it is full.
     * While a fist is held, [cancel] fills 0..1 and the prompt is cancelled when it is full.
     */
    data class Asking(val prompt: Prompt, val selected: Int? = null, val timeLeft: Float = 1f, val commit: Float = 0f, val cancel: Float = 0f) : DriveState
    data class Finished(val outcome: Outcome, val detail: String, val effect: Effect? = null) : DriveState
    data class SosHold(val progress: Float) : DriveState
}

/** What happened to a prompt, for logging and for carrying out the chosen action. */
data class Report(val promptId: String, val outcome: Outcome, val detail: String, val tookMs: Long, val picks: Int, val cause: String)

/**
 * The gesture grammar: turns held gestures and head movements into menu navigation.
 *
 *  - open palm wakes the menu (only when nothing else is on screen)
 *  - one / two / three fingers pick an option; the pick is carried out by itself after [commitMs]
 *  - while that countdown runs, showing another number changes the pick and restarts it
 *  - a fist held for [cancelHoldMs] cancels, and pauses the pick's countdown while it is held; a head shake cancels at once
 *  - thumb up or a nod are optional shortcuts that carry out the pick at once
 *  - a fist held for [sosMs] sends an emergency alert, except while a prompt is open, where the fist cancels
 *
 * Nothing is carried out without a pick, and every prompt closes by itself, so a stray
 * movement cannot send anything.
 */
class GestureMachine(
    private val menu: Prompt,
    private val commitMs: Long = 1_400,
    private val cancelHoldMs: Long = 1_200,
    private val sosShowMs: Long = 1_000,
    private val sosMs: Long = 3_000,
    private val finishedMs: Long = 2_500,
    private val effectMs: Long = 5_000,
    private val onReport: (Report) -> Unit = {},
) {
    var state: DriveState = DriveState.Idle
        private set

    private var held = Gesture.NONE
    private var heldSince = 0L
    private var sosSent = false
    private var root = ""
    private var openedAt = 0L
    private var lastInputAt = 0L
    private var selectedAt = 0L
    private var finishedAt = 0L
    private var picks = 0
    private var lastUpdateAt = 0L

    /** Shows a prompt the driver did not ask for, such as an incoming message. Refused unless idle. */
    fun present(prompt: Prompt, tMs: Long): Boolean {
        if (state != DriveState.Idle) return false
        open(prompt, tMs, isRoot = true)
        return true
    }

    /**
     * Feed the gesture currently held steady, any head gesture that just happened
     * ([Gesture.NOD], [Gesture.SHAKE] or [Gesture.NONE]), and the time.
     */
    fun update(stable: Gesture, head: Gesture, tMs: Long): DriveState {
        val changed = stable != held
        if (changed) { held = stable; heldSince = tMs; sosSent = false }
        val heldMs = tMs - heldSince
        val dt = tMs - lastUpdateAt
        lastUpdateAt = tMs

        if (stable == Gesture.FIST && !sosSent && heldMs >= sosShowMs && state !is DriveState.Asking) {
            if (heldMs >= sosMs) {
                sosSent = true
                finish(Outcome.SOS, "", tMs, "fist held")
            } else {
                state = DriveState.SosHold(heldMs.toFloat() / sosMs)
            }
            return state
        }
        if (state is DriveState.SosHold) state = DriveState.Idle

        when (val s = state) {
            DriveState.Idle -> if (changed && stable == Gesture.OPEN_PALM) open(menu, tMs, isRoot = true)
            is DriveState.Asking -> ask(s, stable, changed, heldMs, dt, head, tMs)
            is DriveState.Finished -> if (tMs - finishedAt >= (if (s.effect != null) effectMs else finishedMs)) state = DriveState.Idle
            is DriveState.SosHold -> Unit
        }
        return state
    }

    private fun ask(s: DriveState.Asking, stable: Gesture, changed: Boolean, heldMs: Long, dt: Long, head: Gesture, tMs: Long) {
        val pick = when (stable) { Gesture.ONE -> 0; Gesture.TWO -> 1; Gesture.THREE -> 2; else -> null }
        val shortcut = (changed && stable == Gesture.THUMB_UP) || head == Gesture.NOD
        val fist = stable == Gesture.FIST
        // Fingers fold through a fist on the way between gestures, so a fist only cancels once
        // it has clearly been held, and the screen shows that hold filling up.
        val cancel = (fist && heldMs >= cancelHoldMs) || head == Gesture.SHAKE
        if (fist && !cancel) {
            // A pick must not go out while the driver is in the middle of cancelling it.
            selectedAt += dt
            lastInputAt += dt
            state = s.copy(cancel = heldMs.toFloat() / cancelHoldMs)
            return
        }
        when {
            changed && pick != null && pick < s.prompt.options.size -> {
                picks++
                lastInputAt = tMs
                selectedAt = tMs
                state = s.copy(selected = pick, timeLeft = 1f, commit = 0f, cancel = 0f)
            }
            cancel -> {
                // One fist hold does one thing: it must be released before it can start an emergency alert.
                if (fist) sosSent = true
                finish(Outcome.CANCELLED, "", tMs, if (head == Gesture.SHAKE) "head shake" else "fist")
            }
            s.selected != null -> {
                val elapsed = tMs - selectedAt
                if (shortcut || elapsed >= commitMs) {
                    val option = s.prompt.options[s.selected]
                    val cause = when { !shortcut -> "countdown"; head == Gesture.NOD -> "nod"; else -> "thumb up" }
                    if (option.next != null) open(option.next, tMs, isRoot = false) else finish(Outcome.DONE, option.result, tMs, cause, option.effect)
                } else {
                    state = s.copy(commit = elapsed.toFloat() / commitMs, cancel = 0f)
                }
            }
            tMs - lastInputAt >= s.prompt.timeoutMs -> finish(Outcome.TIMED_OUT, "", tMs, "timeout")
            else -> state = s.copy(timeLeft = 1f - (tMs - lastInputAt).toFloat() / s.prompt.timeoutMs, cancel = 0f)
        }
    }

    private fun open(prompt: Prompt, tMs: Long, isRoot: Boolean) {
        if (isRoot) { root = prompt.id; openedAt = tMs; picks = 0 }
        lastInputAt = tMs
        state = DriveState.Asking(prompt)
    }

    private fun finish(outcome: Outcome, detail: String, tMs: Long, cause: String, effect: Effect? = null) {
        val wasAsking = state is DriveState.Asking
        onReport(Report(if (wasAsking) root else "", outcome, detail, if (wasAsking) tMs - openedAt else 0, if (wasAsking) picks else 0, cause))
        finishedAt = tMs
        state = DriveState.Finished(outcome, detail, effect)
    }
}
