package dev.accessauto.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.asin
import kotlin.math.atan2

/**
 * Runs MediaPipe hand and face models on camera frames, entirely on-device, and publishes
 * what it sees as [frames].
 */
class GestureEngine(context: Context) : AutoCloseable {

    data class Frame(
        /** Hand landmarks normalised to 0..1 of the upright image; empty when no hand is visible. */
        val hand: List<Pt> = emptyList(),
        /** Width / height of the upright camera image, for drawing the landmarks over the preview. */
        val aspect: Float = 1f,
        /** What the driver is showing right now, before the hold requirement. */
        val raw: Gesture = Gesture.NONE,
        /** How far [raw] is through its hold, 0..1. */
        val hold: Float = 0f,
        /** The last gesture that was held long enough to count. */
        val stable: Gesture = Gesture.NONE,
        /** Last momentary head gesture (nod or shake) and when it happened. */
        val headEvent: Gesture = Gesture.NONE,
        val headEventAt: Long = 0,
    )

    private val _frames = MutableStateFlow(Frame())
    val frames: StateFlow<Frame> = _frames

    private val stabilizer = Stabilizer()
    private val headMotion = HeadMotionDetector()
    @Volatile private var aspect = 1f
    @Volatile private var frameRotation = 0

    private val hands: GestureRecognizer = GestureRecognizer.createFromOptions(
        context,
        GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("gesture_recognizer.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(1)
            .setResultListener { result, _ -> onHands(result) }
            .build(),
    )

    private val face: FaceLandmarker = FaceLandmarker.createFromOptions(
        context,
        FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFacialTransformationMatrixes(true)
            .setResultListener { result, _ -> onFace(result) }
            .build(),
    )

    /** CameraX analyzer entry point. Expects RGBA_8888 frames. */
    fun analyze(proxy: ImageProxy) {
        val sensor = proxy.toBitmap()
        val rotation = proxy.imageInfo.rotationDegrees
        proxy.close()
        // Turn the frame upright ourselves instead of asking MediaPipe to. With MediaPipe's own
        // rotation option the head pose came back in the sensor's sideways frame, which swapped
        // nod and shake whenever the phone was held upright.
        val upright = if (rotation == 0) sensor else Bitmap.createBitmap(
            sensor, 0, 0, sensor.width, sensor.height, Matrix().apply { postRotate(rotation.toFloat()) }, false,
        )
        aspect = upright.width.toFloat() / upright.height
        frameRotation = rotation
        val image = BitmapImageBuilder(upright).build()
        val t = SystemClock.uptimeMillis()
        hands.recognizeAsync(image, t)
        face.detectAsync(image, t)
    }

    private fun onHands(result: GestureRecognizerResult) {
        val t = result.timestampMs()
        val a = aspect
        val lm = result.landmarks().firstOrNull()?.map { Pt(it.x(), it.y()) }.orEmpty()
        val top = result.gestures().firstOrNull()?.firstOrNull()
        val raw = HandClassifier.classify(lm.map { Pt(it.x * a, it.y) }, top?.categoryName(), top?.score() ?: 0f)
        val hold = stabilizer.update(raw, t)
        _frames.update { it.copy(hand = lm, aspect = a, raw = raw, hold = hold, stable = stabilizer.stable) }
    }

    private fun onFace(result: FaceLandmarkerResult) {
        // 4x4 column-major head pose matrix; r(i, j) = m[j * 4 + i].
        val m = result.facialTransformationMatrixes().orElse(null)?.firstOrNull() ?: return
        val yaw = Math.toDegrees(atan2(m[8], m[10]).toDouble()).toFloat()
        val pitch = Math.toDegrees(asin((-m[9]).coerceIn(-1f, 1f)).toDouble()).toFloat()
        val event = headMotion.update(pitch, yaw, result.timestampMs())
        // Calibration trace: head angles per frame, plus any nod or shake that fired.
        Log.d("HeadMotion", "t=${result.timestampMs()} rot=$frameRotation pitch=%.1f yaw=%.1f %s".format(pitch, yaw, if (event != Gesture.NONE) event.name else ""))
        if (event != Gesture.NONE) {
            _frames.update { it.copy(headEvent = event, headEventAt = result.timestampMs()) }
        }
    }

    override fun close() {
        hands.close()
        face.close()
    }
}
