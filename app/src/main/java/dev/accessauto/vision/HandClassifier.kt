package dev.accessauto.vision

import kotlin.math.hypot

/**
 * Turns the 21 MediaPipe hand landmarks into one of our hand gestures.
 *
 * Finger counts come from geometry, because the stock MediaPipe gesture model has no
 * "three fingers" class. The model's own label is only used to tell a thumbs-up from a
 * fist, which is the one case where geometry alone is shaky.
 */
object HandClassifier {
    private val TIPS = intArrayOf(8, 12, 16, 20)
    private val PIPS = intArrayOf(6, 10, 14, 18)
    private const val THUMB_MCP = 2
    private const val THUMB_TIP = 4
    private const val MIDDLE_MCP = 9

    fun classify(lm: List<Pt>, modelLabel: String? = null, modelScore: Float = 0f): Gesture {
        if (lm.size < 21) return Gesture.NONE
        val wrist = lm[0]
        // A finger is extended when its tip is clearly farther from the wrist than its middle joint.
        val ext = BooleanArray(4) { dist(lm[TIPS[it]], wrist) > dist(lm[PIPS[it]], wrist) * 1.15f }
        val count = ext.count { it }

        if (count == 0) {
            if (modelScore >= 0.5f && modelLabel == "Thumb_Up") return Gesture.THUMB_UP
            if (modelScore >= 0.5f && modelLabel == "Closed_Fist") return Gesture.FIST
            return if (thumbPointsUp(lm)) Gesture.THUMB_UP else Gesture.FIST
        }
        return when {
            count == 4 -> Gesture.OPEN_PALM
            ext[0] && !ext[1] && !ext[2] && !ext[3] -> Gesture.ONE
            ext[0] && ext[1] && !ext[2] && !ext[3] -> Gesture.TWO
            ext[0] && ext[1] && ext[2] && !ext[3] -> Gesture.THREE
            else -> Gesture.NONE
        }
    }

    /** Thumb is straight and its tip sits well above every other part of the hand (image y grows downward). */
    private fun thumbPointsUp(lm: List<Pt>): Boolean {
        val palm = dist(lm[0], lm[MIDDLE_MCP])
        val tip = lm[THUMB_TIP]
        val highestOther = lm.filterIndexed { i, _ -> i !in 1..4 }.minOf { it.y }
        return highestOther - tip.y > palm * 0.35f && dist(tip, lm[THUMB_MCP]) > palm * 0.5f
    }

    private fun dist(a: Pt, b: Pt) = hypot(a.x - b.x, a.y - b.y)
}
