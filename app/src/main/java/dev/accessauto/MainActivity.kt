package dev.accessauto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.accessauto.sim.DemoScenarios
import dev.accessauto.ui.Cockpit
import dev.accessauto.ui.EffectPreview
import dev.accessauto.ui.DriveScreen
import dev.accessauto.ui.TrainingScreen
import dev.accessauto.vision.GestureEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The phone sits on a dash mount as the feedback surface, so the screen must stay on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { App(intent.getStringExtra("preview")) }
        }
    }
}

@Composable
private fun App(preview: String?) {
    // Debug hook: `am start -n dev.accessauto/.MainActivity --es preview message` shows one result screen.
    DemoScenarios.previews[preview]?.let { EffectPreview(it); return }
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    if (granted) {
        val engine = remember { GestureEngine(context.applicationContext) }
        DisposableEffect(Unit) { onDispose { engine.close() } }
        var training by rememberSaveable { mutableStateOf(false) }
        if (training) TrainingScreen(engine, onDrive = { training = false })
        else DriveScreen(engine, onTraining = { training = true })
    } else {
        Column(
            Modifier.fillMaxSize().background(Cockpit.Aluminium).padding(32.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_needed), style = Cockpit.Label)
            Text(
                stringResource(R.string.allow_camera),
                style = Cockpit.Label.copy(color = Color.White),
                modifier = Modifier.padding(top = 16.dp)
                    .background(Cockpit.Ink, RoundedCornerShape(12.dp))
                    .clickable { ask.launch(Manifest.permission.CAMERA) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
