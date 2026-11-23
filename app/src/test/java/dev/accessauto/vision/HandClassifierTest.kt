package dev.accessauto.vision

import org.junit.Assert.assertEquals
import org.junit.Test

class HandClassifierTest {
    /** An upright hand: wrist at the bottom, each of the four fingers either straight up or curled into the palm. */
    private fun upright(vararg extended: Boolean, thumbOut: Boolean = false): List<Pt> {
        val lm = MutableList(21) { Pt(0f, 0f) }
        lm[0] = Pt(0.5f, 0.9f)
        if (thumbOut) { lm[1] = Pt(0.42f, 0.82f); lm[2] = Pt(0.36f, 0.74f); lm[3] = Pt(0.32f, 0.68f); lm[4] = Pt(0.28f, 0.62f) }
        else { lm[1] = Pt(0.45f, 0.8f); lm[2] = Pt(0.42f, 0.7f); lm[3] = Pt(0.46f, 0.64f); lm[4] = Pt(0.5f, 0.62f) }
        for (f in 0..3) {
            val x = 0.4f + 0.1f * f
            val base = 5 + 4 * f
            lm[base] = Pt(x, 0.6f)
            if (extended[f]) { lm[base + 1] = Pt(x, 0.45f); lm[base + 2] = Pt(x, 0.35f); lm[base + 3] = Pt(x, 0.25f) }
            else { lm[base + 1] = Pt(x, 0.5f); lm[base + 2] = Pt(x, 0.55f); lm[base + 3] = Pt(x, 0.62f) }
        }
        return lm
    }

    /** A fist turned sideways with the thumb pointing straight up. */
    private fun thumbsUp(): List<Pt> {
        val lm = MutableList(21) { Pt(0f, 0f) }
        lm[0] = Pt(0.7f, 0.6f)
        lm[1] = Pt(0.62f, 0.52f); lm[2] = Pt(0.55f, 0.44f); lm[3] = Pt(0.53f, 0.34f); lm[4] = Pt(0.52f, 0.24f)
        for (f in 0..3) {
            val y = 0.5f + 0.06f * f
            val base = 5 + 4 * f
            lm[base] = Pt(0.45f, y); lm[base + 1] = Pt(0.38f, y); lm[base + 2] = Pt(0.42f, y + 0.03f); lm[base + 3] = Pt(0.48f, y + 0.03f)
        }
        return lm
    }

    @Test fun countsFingers() {
        assertEquals(Gesture.ONE, HandClassifier.classify(upright(true, false, false, false)))
        assertEquals(Gesture.TWO, HandClassifier.classify(upright(true, true, false, false)))
        assertEquals(Gesture.THREE, HandClassifier.classify(upright(true, true, true, false)))
        assertEquals(Gesture.OPEN_PALM, HandClassifier.classify(upright(true, true, true, true, thumbOut = true)))
    }

    @Test fun thumbPositionDoesNotChangeTheCount() {
        assertEquals(Gesture.TWO, HandClassifier.classify(upright(true, true, false, false, thumbOut = true)))
    }

    @Test fun fistAndThumbsUpFromGeometry() {
        assertEquals(Gesture.FIST, HandClassifier.classify(upright(false, false, false, false)))
        assertEquals(Gesture.THUMB_UP, HandClassifier.classify(thumbsUp()))
    }

    @Test fun modelLabelSettlesFistVersusThumb() {
        val fist = upright(false, false, false, false)
        assertEquals(Gesture.THUMB_UP, HandClassifier.classify(fist, "Thumb_Up", 0.9f))
        assertEquals(Gesture.FIST, HandClassifier.classify(thumbsUp(), "Closed_Fist", 0.9f))
        assertEquals(Gesture.FIST, HandClassifier.classify(fist, "Thumb_Up", 0.2f))
    }

    @Test fun unknownShapesAndMissingHandsAreNone() {
        assertEquals(Gesture.NONE, HandClassifier.classify(upright(false, true, false, true)))
        assertEquals(Gesture.NONE, HandClassifier.classify(emptyList()))
    }
}
