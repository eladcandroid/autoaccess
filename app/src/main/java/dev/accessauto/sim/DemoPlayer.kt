package dev.accessauto.sim

import dev.accessauto.grammar.Effect
import dev.accessauto.grammar.Effect.MusicCommand.Command

/** A pretend music player that remembers what it is doing, so the demo stays consistent. */
class DemoPlayer {
    private val tracks = listOf("שיר לדרך", "כביש החוף", "אורות העיר", "בוקר טוב")
    private var index = 0
    private var playing = true

    fun apply(command: Command): Effect.Music {
        when (command) {
            Command.TOGGLE -> playing = !playing
            Command.NEXT -> { index = (index + 1) % tracks.size; playing = true }
            Command.PREVIOUS -> { index = (index - 1 + tracks.size) % tracks.size; playing = true }
        }
        return Effect.Music(if (playing) "מתנגן עכשיו" else "מושהה", tracks[index], "רשימת הנסיעה שלי", playing)
    }
}
