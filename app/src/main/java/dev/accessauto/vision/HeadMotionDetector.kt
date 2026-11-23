package dev.accessauto.vision

import kotlin.math.abs
import kotlin.math.sign

/**
 * Detects a nod and a head shake from head angles in degrees.
 *
 * A nod is a quick dip in pitch that comes straight back. A shake is a swing in yaw to one
 * side and then past the resting pose to the other side; a one-sided turn is not a shake,
 * because drivers turn their head a little all the time (to the mirror, to their own hand).
 * A slow look dwells past the window, and the detector re-baselines instead of firing.
 *
 * Thresholds were calibrated against a recorded trace of real head movement
 * (see src/test/resources/head_*.txt).
 */
class HeadMotionDetector(
    nodDeg: Float = 10f,
    shakeDeg: Float = 12f,
    nodWindowMs: Long = 900,
    shakeWindowMs: Long = 700,
    private val cooldownMs: Long = 400,
) {
    private val pitch = Axis(nodDeg, nodWindowMs, mustCross = false)
    private val yaw = Axis(shakeDeg, shakeWindowMs, mustCross = true)
    private var quietUntil = 0L

    fun update(pitchDeg: Float, yawDeg: Float, tMs: Long): Gesture {
        if (tMs < quietUntil) return Gesture.NONE
        val nod = pitch.update(pitchDeg, tMs)
        val shake = yaw.update(yawDeg, tMs)
        // Real head movement leaks into the other axis; the larger flick is the intended one.
        val event = when {
            nod && shake -> if (pitch.lastPeak >= yaw.lastPeak) Gesture.NOD else Gesture.SHAKE
            nod -> if (yaw.peak > pitch.lastPeak) Gesture.NONE else Gesture.NOD
            shake -> if (pitch.peak > yaw.lastPeak) Gesture.NONE else Gesture.SHAKE
            else -> Gesture.NONE
        }
        if (event != Gesture.NONE) {
            pitch.cancel(); yaw.cancel()
            quietUntil = tMs + cooldownMs
        }
        return event
    }

    /** One angle: tracks its resting value and reports a completed there-and-back flick. */
    private class Axis(private val threshold: Float, private val windowMs: Long, private val mustCross: Boolean) {
        private var rest = Float.NaN
        private var pending = 0          // consecutive frames beyond the threshold, before a flick starts
        private var start = -1L
        private var direction = 0f
        /** Largest deviation of the flick in progress, 0 when idle. */
        var peak = 0f; private set
        /** Size of the flick that was just reported. */
        var lastPeak = 0f; private set

        fun update(v: Float, tMs: Long): Boolean {
            if (rest.isNaN()) { rest = v; return false }
            val d = v - rest
            if (start < 0) {
                if (abs(d) > threshold) {
                    // A single-frame spike is tracking noise, not movement.
                    if (++pending >= 2) { start = tMs; direction = sign(d); peak = abs(d) }
                } else {
                    pending = 0
                    rest += 0.05f * d
                }
                return false
            }
            if (tMs - start > windowMs) { rest = v; cancel(); return false }
            if (sign(d) == direction && abs(d) > peak) peak = abs(d)
            val crossed = sign(d) != direction && abs(d) >= threshold * CROSS_FRACTION
            val cameBack = if (mustCross) crossed else sign(d) != direction || abs(d) < peak * RETURN_FRACTION
            if (!cameBack) return false
            lastPeak = peak
            cancel()
            return true
        }

        fun cancel() { start = -1; pending = 0; peak = 0f }
    }

    private companion object {
        const val RETURN_FRACTION = 0.4f
        const val CROSS_FRACTION = 0.6f
    }
}
