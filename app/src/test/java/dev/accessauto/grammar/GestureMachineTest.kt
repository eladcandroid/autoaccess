package dev.accessauto.grammar

import dev.accessauto.vision.Gesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMachineTest {
    private val places = Prompt("nav", "Where to?", listOf(Option("Home", result = "Navigating home"), Option("Work")))
    private val menu = Prompt("menu", "Menu", listOf(Option("Navigate", next = places), Option("Music")))
    private val message = Prompt("msg", "Dana asks when", listOf(Option("10 min"), Option("Late"), Option("Later")))

    private val reports = mutableListOf<Report>()
    private val m = GestureMachine(menu, commitMs = 2_000, onReport = { reports += it })
    private var t = 0L

    /** Holds a gesture for [ms], ticking every 50 ms like the camera loop does. */
    private fun hold(g: Gesture, ms: Long = 300): DriveState {
        repeat((ms / 50).toInt()) { t += 50; m.update(g, Gesture.NONE, t) }
        return m.state
    }
    private fun head(g: Gesture): DriveState { t += 50; return m.update(Gesture.NONE, g, t) }

    @Test fun pickIsCarriedOutByItselfAfterTheCountdown() {
        m.present(message, t)
        val picked = hold(Gesture.TWO, 1_000) as DriveState.Asking
        assertEquals(1, picked.selected)
        assertTrue(picked.commit > 0.4f && picked.commit < 0.6f)
        assertTrue(reports.isEmpty())
        // The countdown runs on even when the hand goes back to the wheel.
        assertEquals(DriveState.Finished(Outcome.DONE, "Late"), hold(Gesture.NONE, 1_100))
        assertEquals("countdown", reports.single().cause)
    }

    @Test fun wakeAndPickWalkTheMenu() {
        assertEquals(DriveState.Idle, hold(Gesture.NONE))
        assertEquals(menu, (hold(Gesture.OPEN_PALM) as DriveState.Asking).prompt)
        // Still holding one finger when the submenu opens must not pick in it too.
        val sub = hold(Gesture.ONE, 2_500) as DriveState.Asking
        assertEquals(places, sub.prompt)
        assertEquals(null, sub.selected)
        hold(Gesture.NONE)
        assertEquals(DriveState.Finished(Outcome.DONE, "Navigating home"), hold(Gesture.ONE, 2_100))
        assertEquals("menu", reports.single().promptId)
        assertEquals(2, reports.single().picks)
        assertEquals(DriveState.Idle, hold(Gesture.NONE, 3_000))
    }

    @Test fun changingThePickRestartsTheCountdown() {
        m.present(message, t)
        hold(Gesture.THREE, 1_500)
        val changed = hold(Gesture.TWO, 1_500) as DriveState.Asking
        assertEquals(1, changed.selected)
        assertEquals(DriveState.Finished(Outcome.DONE, "Late"), hold(Gesture.TWO, 600))
    }

    @Test fun thumbOrNodCarryOutThePickAtOnce() {
        m.present(message, t); hold(Gesture.ONE)
        assertEquals(DriveState.Finished(Outcome.DONE, "10 min"), hold(Gesture.THUMB_UP, 100))

        hold(Gesture.NONE, 3_000)
        m.present(message, t); hold(Gesture.THREE)
        assertEquals(DriveState.Finished(Outcome.DONE, "Later"), head(Gesture.NOD))
    }

    @Test fun nothingHappensWithoutAPick() {
        m.present(message, t)
        assertTrue(hold(Gesture.THUMB_UP) is DriveState.Asking)
        assertTrue(head(Gesture.NOD) is DriveState.Asking)
        assertTrue(reports.isEmpty())
    }

    @Test fun pickBeyondTheOptionsIsIgnored() {
        hold(Gesture.OPEN_PALM)
        assertEquals(null, (hold(Gesture.THREE) as DriveState.Asking).selected)
    }

    @Test fun shakeCancelsDuringTheCountdown() {
        m.present(message, t); hold(Gesture.ONE, 1_000)
        assertEquals(DriveState.Finished(Outcome.CANCELLED, ""), head(Gesture.SHAKE))
        assertEquals(listOf(Outcome.CANCELLED), reports.map { it.outcome })
    }

    @Test fun fistCancelsDuringTheCountdownOnceClearlyHeld() {
        m.present(message, t); hold(Gesture.ONE)
        assertTrue(hold(Gesture.FIST, 400) is DriveState.Asking)
        assertEquals(DriveState.Finished(Outcome.CANCELLED, ""), hold(Gesture.FIST, 300))
    }

    @Test fun promptClosesByItself() {
        m.present(message, t)
        assertTrue(hold(Gesture.NONE, 7_000) is DriveState.Asking)
        assertEquals(DriveState.Finished(Outcome.TIMED_OUT, ""), hold(Gesture.NONE, 1_100))
    }

    @Test fun incomingPromptDoesNotInterruptAnotherOne() {
        hold(Gesture.OPEN_PALM)
        assertFalse(m.present(message, t))
        assertEquals(menu, (m.state as DriveState.Asking).prompt)
    }

    @Test fun longFistSendsSosOnceAndShortFistDoesNot() {
        assertTrue(hold(Gesture.FIST, 2_000) is DriveState.SosHold)
        assertEquals(DriveState.Idle, hold(Gesture.NONE))
        assertTrue(reports.isEmpty())

        // The hold is timed from the first frame the fist is seen, hence the extra tick.
        assertEquals(DriveState.Finished(Outcome.SOS, ""), hold(Gesture.FIST, 3_050))
        hold(Gesture.FIST, 6_000) // still holding: no second alert
        assertEquals(listOf(Outcome.SOS), reports.map { it.outcome })
    }

    @Test fun palmOnlyWakesFromIdle() {
        m.present(message, t)
        assertEquals(message, (hold(Gesture.OPEN_PALM) as DriveState.Asking).prompt)
    }
}
