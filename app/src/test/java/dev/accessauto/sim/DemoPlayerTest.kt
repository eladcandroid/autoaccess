package dev.accessauto.sim

import dev.accessauto.grammar.Effect.MusicCommand.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoPlayerTest {
    @Test fun toggleAlternatesAndKeepsTheTrack() {
        val p = DemoPlayer()
        val paused = p.apply(Command.TOGGLE)
        val resumed = p.apply(Command.TOGGLE)
        assertFalse(paused.playing)
        assertTrue(resumed.playing)
        assertEquals(paused.track, resumed.track)
    }

    @Test fun nextAndPreviousChangeTrackAndStartPlaying() {
        val p = DemoPlayer()
        val first = p.apply(Command.TOGGLE) // paused on the first track
        val second = p.apply(Command.NEXT)
        assertTrue(second.playing)
        assertNotEquals(first.track, second.track)
        assertEquals(first.track, p.apply(Command.PREVIOUS).track)
    }
}
