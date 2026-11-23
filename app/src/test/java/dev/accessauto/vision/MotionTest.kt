package dev.accessauto.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    /** Feeds (pitch, yaw, frames) segments at 30 ms per frame and collects whatever fires. */
    private fun run(vararg segments: Triple<Float, Float, Int>): List<Gesture> {
        val d = HeadMotionDetector()
        var t = 0L
        val fired = mutableListOf<Gesture>()
        for ((pitch, yaw, frames) in segments) repeat(frames) {
            d.update(pitch, yaw, t).let { if (it != Gesture.NONE) fired += it }
            t += 30
        }
        return fired
    }

    @Test fun quickDipAndReturnIsANod() {
        assertEquals(listOf(Gesture.NOD), run(Triple(0f, 0f, 10), Triple(12f, 0f, 5), Triple(0f, 0f, 10)))
    }

    @Test fun longLookDownIsNotANod() {
        assertTrue(run(Triple(0f, 0f, 10), Triple(12f, 0f, 70), Triple(0f, 0f, 70)).isEmpty())
    }

    @Test fun leftThenRightIsAShake() {
        assertEquals(listOf(Gesture.SHAKE), run(Triple(0f, 0f, 10), Triple(0f, 15f, 5), Triple(0f, -15f, 5), Triple(0f, 0f, 10)))
    }

    @Test fun quickTurnToOneSideIsNotAShake() {
        assertTrue(run(Triple(0f, 0f, 10), Triple(0f, 15f, 5), Triple(0f, 0f, 10)).isEmpty())
    }

    @Test fun mirrorGlanceIsNotAShake() {
        assertTrue(run(Triple(0f, 0f, 10), Triple(0f, 20f, 20), Triple(0f, 0f, 40)).isEmpty())
    }

    @Test fun singleFrameSpikeIsIgnored() {
        assertTrue(run(Triple(0f, 0f, 10), Triple(-14f, 0f, 1), Triple(0f, 0f, 10)).isEmpty())
    }

    /**
     * Replays head angles recorded from a real person on the phone ("t pitch yaw" per line).
     * The phone was upright while the engine still read angles in the camera sensor's sideways
     * frame, so the two columns were stored the other way round and are swapped back here.
     */
    private fun replay(resource: String): List<Gesture> {
        val d = HeadMotionDetector()
        return javaClass.getResource("/$resource")!!.readText().lines().filter { it.isNotBlank() }.mapNotNull {
            val (t, pitch, yaw) = it.split(" ")
            d.update(pitch.toFloat(), yaw.toFloat(), t.toLong()).takeIf { g -> g != Gesture.NONE }
        }
    }

    /**
     * The recording is a run of wide, fast head shakes followed directly by three small nods.
     * Neither may be mistaken for the other.
     */
    @Test fun recordedShakesThenNodsAreToldApart() {
        val fired = replay("head_shakes_then_nods.txt")
        val shakes = fired.takeWhile { it == Gesture.SHAKE }
        val nods = fired.drop(shakes.size)
        assertTrue("got $fired", shakes.size >= 3)
        // The first nod starts 0.2 s after the last shake and the third 0.5 s after the second,
        // so they can fall inside the cooldown that stops one gesture firing twice.
        assertTrue("got $fired", nods.size >= 2 && nods.all { it == Gesture.NOD })
    }

    @Test fun recordedStillHeadWithTrackingGlitchFiresNothing() {
        assertEquals(emptyList<Gesture>(), replay("head_glitch.txt"))
    }

    @Test fun stillHeadFiresNothing() {
        assertTrue(run(Triple(2f, -3f, 200)).isEmpty())
    }

    @Test fun gestureMustBeHeldBeforeItCounts() {
        val s = Stabilizer(holdMs = 250)
        s.update(Gesture.TWO, 0)
        assertEquals(Gesture.NONE, s.stable)
        assertEquals(0.4f, s.update(Gesture.TWO, 100), 0.001f)
        s.update(Gesture.ONE, 200) // flicker restarts the hold
        s.update(Gesture.TWO, 230)
        s.update(Gesture.TWO, 400)
        assertEquals(Gesture.NONE, s.stable)
        s.update(Gesture.TWO, 480)
        assertEquals(Gesture.TWO, s.stable)
    }
}
