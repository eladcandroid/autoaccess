package dev.accessauto.vision

/** A raw per-frame gesture only counts once it has been held steadily for [holdMs]. */
class Stabilizer(private val holdMs: Long = 250) {
    private var candidate = Gesture.NONE
    private var since = 0L
    var stable = Gesture.NONE
        private set

    /** Returns how far the current candidate is through its hold, 0..1. */
    fun update(raw: Gesture, tMs: Long): Float {
        if (raw != candidate) { candidate = raw; since = tMs }
        val p = ((tMs - since).toFloat() / holdMs).coerceIn(0f, 1f)
        if (p >= 1f) stable = candidate
        return p
    }
}
