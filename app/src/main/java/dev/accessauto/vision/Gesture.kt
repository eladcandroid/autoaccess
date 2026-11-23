package dev.accessauto.vision

/** A point in image space. Callers scale x by the image aspect ratio so distances are isotropic. */
data class Pt(val x: Float, val y: Float)

enum class Gesture { NONE, OPEN_PALM, ONE, TWO, THREE, THUMB_UP, FIST, NOD, SHAKE }
