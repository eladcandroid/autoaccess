package dev.accessauto.ui

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.accessauto.R
import dev.accessauto.vision.Gesture
import dev.accessauto.vision.GestureEngine
import java.util.concurrent.Executors
import kotlin.math.max

/** The gestures a driver practises before the first trip, in teaching order. */
private val LESSON = listOf(
    Gesture.OPEN_PALM, Gesture.ONE, Gesture.TWO, Gesture.THREE, Gesture.THUMB_UP,
    Gesture.FIST, Gesture.NOD, Gesture.SHAKE,
)

fun Gesture.labelRes(): Int = when (this) {
    Gesture.OPEN_PALM -> R.string.g_open_palm
    Gesture.ONE -> R.string.g_one
    Gesture.TWO -> R.string.g_two
    Gesture.THREE -> R.string.g_three
    Gesture.THUMB_UP -> R.string.g_thumb_up
    Gesture.FIST -> R.string.g_fist
    Gesture.NOD -> R.string.g_nod
    Gesture.SHAKE -> R.string.g_shake
    Gesture.NONE -> R.string.waiting
}

@Composable
fun TrainingScreen(engine: GestureEngine, onDrive: () -> Unit) {
    val frame by engine.frames.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(0) }
    val target = LESSON.getOrNull(step)

    // Advance when the target is held (hand) or happens (nod, shake).
    LaunchedEffect(frame.stable, frame.headEventAt, target) {
        if (target != null && (frame.stable == target || frame.headEvent == target)) step++
    }

    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Cockpit.Aluminium).systemBarsPadding().padding(16.dp)
            .clip(shape).border(8.dp, Cockpit.Ink, shape).background(Cockpit.Dial),
    ) {
        // The phone may sit on its mount either way round: side by side when wide, stacked when tall.
        if (maxWidth > maxHeight) {
            Row {
                Readout(frame, target, step, onDrive, Modifier.weight(1f).fillMaxHeight())
                Camera(engine, frame, Modifier.weight(0.8f).fillMaxHeight())
            }
        } else {
            Column {
                Camera(engine, frame, Modifier.weight(1f).fillMaxWidth())
                Readout(frame, target, step, onDrive, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Readout(frame: GestureEngine.Frame, target: Gesture?, step: Int, onDrive: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 28.dp, vertical = 22.dp)) {
        Text(stringResource(if (target != null) R.string.show_now else R.string.ready_title), style = Cockpit.Body)
        Text(
            stringResource(target?.labelRes() ?: R.string.ready),
            style = Cockpit.Readout.copy(color = if (target != null) Cockpit.Ink else Cockpit.Ok),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val matching = target != null && frame.raw == target
            Gauge(if (matching) frame.hold else 0f, if (matching) Cockpit.Ok else Cockpit.Needle, Modifier.size(96.dp))
            Column {
                Text(stringResource(R.string.camera_sees), style = Cockpit.Body)
                Text(stringResource(frame.raw.labelRes()), style = Cockpit.Label)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LESSON.indices.forEach { i ->
                val tick = RoundedCornerShape(3.dp)
                Box(
                    Modifier.weight(1f).height(14.dp).border(2.dp, Cockpit.Ink, tick)
                        .background(if (i < step) Cockpit.Ok else Cockpit.Dial, tick),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.progress, step, LESSON.size), style = Cockpit.Label)
            ModeButton(stringResource(R.string.to_drive), onDrive)
        }
    }
}

@Composable
internal fun Camera(engine: GestureEngine, frame: GestureEngine.Frame, modifier: Modifier) {
    Box(modifier.clipToBounds().background(Cockpit.Ink)) {
        CameraPreview(engine, Modifier.fillMaxSize())
        HandOverlay(frame, Modifier.fillMaxSize())
    }
}

@Composable
private fun CameraPreview(engine: GestureEngine, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val preview = remember { Preview.Builder().build() }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    // The activity is not recreated on rotation, so tell CameraX when the display turns.
    // Otherwise the models would keep receiving frames rotated for the old orientation.
    DisposableEffect(Unit) {
        val displays = context.getSystemService(DisplayManager::class.java)
        fun sync() {
            val rotation = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: return
            preview.targetRotation = rotation
            analysis.targetRotation = rotation
        }
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayChanged(displayId: Int) = sync()
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
        }
        sync()
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose {
            displays.unregisterDisplayListener(listener)
            executor.shutdown()
        }
    }

    AndroidView(modifier = modifier, factory = { ctx ->
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView mode: a SurfaceView ignores Compose clipping and spills over the readout panel.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            preview.surfaceProvider = surfaceProvider
            analysis.setAnalyzer(executor, engine::analyze)
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                future.get().run {
                    unbindAll()
                    bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
    })
}

private val BONES = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8, 9 to 10, 10 to 11, 11 to 12,
    13 to 14, 14 to 15, 15 to 16, 0 to 17, 17 to 18, 18 to 19, 19 to 20, 5 to 9, 9 to 13, 13 to 17,
)

/** Draws the detected hand skeleton over the mirrored, centre-cropped front-camera preview. */
@Composable
private fun HandOverlay(frame: GestureEngine.Frame, modifier: Modifier) {
    Canvas(modifier) {
        if (frame.hand.size < 21) return@Canvas
        val scale = max(size.width / frame.aspect, size.height)
        val w = frame.aspect * scale
        val dx = (size.width - w) / 2
        val dy = (size.height - scale) / 2
        val pts = frame.hand.map { Offset(dx + (1f - it.x) * w, dy + it.y * scale) }
        BONES.forEach { (a, b) -> drawLine(Cockpit.Needle, pts[a], pts[b], strokeWidth = 5f, cap = StrokeCap.Round) }
        pts.forEach { drawCircle(Cockpit.Dial, 6f, it) }
    }
}
