package com.velokey.studio

import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.velokey.studio.scene.CamKey
import com.velokey.studio.scene.Playback
import com.velokey.studio.scene.VeloRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.rajawali3d.view.ISurface
import org.rajawali3d.view.SurfaceView

val BgDark = Color(0xFF0A0A0E)
val PanelDark = Color(0xFF14141C)
val AccentCyan = Color(0xFF00F0FF)
val AccentPurple = Color(0xFF7B2CBF)
val AccentGreen = Color(0xFF00E676)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgDark)) {
                VeloKeyEditorScreen()
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalS = ms / 1000
    return "%02d:%02d:%03d".format(totalS / 60, totalS % 60, ms % 1000)
}

@Composable
fun VeloKeyEditorScreen() {
    val context = LocalContext.current

    var px by remember { mutableStateOf(0f) }
    var py by remember { mutableStateOf(2f) }
    var pz by remember { mutableStateOf(12f) }
    var rx by remember { mutableStateOf(0f) }
    var ry by remember { mutableStateOf(0f) }
    var rz by remember { mutableStateOf(0f) }
    var nx by remember { mutableStateOf(0f) }
    var ny by remember { mutableStateOf(0f) }
    var nz by remember { mutableStateOf(0f) }

    var timeMsUi by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var showCurve by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Playback.setLivePose(px, py, pz, rx, ry, rz)
        Playback.setNull(nx, ny, nz)
    }
    LaunchedEffect(px, py, pz, rx, ry, rz) { Playback.setLivePose(px, py, pz, rx, ry, rz) }
    LaunchedEffect(nx, ny, nz) { Playback.setNull(nx, ny, nz) }

    LaunchedEffect(playing) {
        if (playing) {
            Playback.playing.set(true)
            var last = SystemClock.elapsedRealtime()
            while (playing && isActive) {
                val now = SystemClock.elapsedRealtime()
                val dt = now - last
                last = now
                val t = Playback.timeMs.get()
                val t01 = (t.toFloat() / Playback.DURATION_MS).coerceIn(0f, 1f)
                val sp = Playback.speedAt(t01)
                var nt = t + (dt * sp).toLong()
                if (nt >= Playback.DURATION_MS) nt = 0
                Playback.timeMs.set(nt)
                timeMsUi = nt
                delay(16)
            }
            Playback.playing.set(false)
        }
    }

    val renderer = remember { VeloRenderer(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VeloKey Studio", color = Color.White, fontSize = 17.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("3D • v0.3", color = AccentCyan, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { Toast.makeText(context, "Export MP4: em breve", Toast.LENGTH_SHORT).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) { Text("Export", color = Color.Black, fontSize = 12.sp) }
            }
        }

        Box(
            modifier = Modifier
                .weight(1.25f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF22222E), RoundedCornerShape(14.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    val sv = SurfaceView(ctx)
                    sv.setFrameRate(60.0)
                    sv.setRenderMode(ISurface.RENDERMODE_CONTINUOUSLY)
                    sv.setSurfaceRenderer(renderer)

                    var lastX = 0f
                    var lastY = 0f
                    sv.setOnTouchListener { _, ev: MotionEvent ->
                        when (ev.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                lastX = ev.x
                                lastY = ev.y
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val dx = ev.x - lastX
                                val dy = ev.y - lastY
                                lastX = ev.x
                                lastY = ev.y
                                ry = (ry + dx * 0.25f) % 360f
                                rx = (rx + dy * 0.25f).coerceIn(-89f, 89f)
                            }
                        }
                        true
                    }
                    sv
                },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                "Arraste na cena: orbita a câmera 3D",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(timeMsUi), color = AccentGreen, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { playing = !playing },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) { Text(if (playing) "❚❚" else "▶", color = Color.White, fontSize = 13.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { Playback.addKey(CamKey(Playback.timeMs.get(), px, py, pz, rx, ry, rz)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("+ KEY", color = AccentCyan, fontSize = 11.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { Playback.keys.clear() },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("Limpar", color = Color.Gray, fontSize = 11.sp) }
                Spacer(Modifier.width(6.dp))
                OutlinedButton(
                    onClick = { showCurve = !showCurve },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) { Text(if (showCurve) "Timeline" else "Curva", color = Color.White, fontSize = 11.sp) }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(10.dp)).background(PanelDark).padding(8.dp)
            ) {
                Text("Câmera 3D (Position / Rotation)", color = AccentCyan, fontSize = 11.sp)
                PoseSlider("Pos X", px, -30f..30f, { px = it })
                PoseSlider("Pos Y", py, -30f..30f, { py = it })
                PoseSlider("Pos Z", pz, -40f..40f, { pz = it })
                PoseSlider("Rot X", rx, -89f..89f, { rx = it })
                PoseSlider("Rot Y", ry, -180f..180f, { ry = it })
                PoseSlider("Rot Z", rz, -180f..180f, { rz = it })
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(10.dp)).background(PanelDark).padding(8.dp)
            ) {
                Text("Null Object (move o cubo filho)", color = AccentGreen, fontSize = 11.sp)
                PoseSlider("Null X", nx, -20f..20f, { nx = it }, AccentGreen)
                PoseSlider("Null Y", ny, -20f..20f, { ny = it }, AccentGreen)
                PoseSlider("Null Z", nz, -20f..20f, { nz = it }, AccentGreen)
                Spacer(Modifier.height(6.dp))
                Text("Keyframes: ${Playback.keys.size}", color = Color.Gray, fontSize = 10.sp)
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(10.dp)
                .clip(RoundedCornerShape(10.dp)).background(PanelDark)
        ) {
            if (showCurve) {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Text("Curva de Velocidade (controla a velocidade do play)", color = AccentCyan, fontSize = 11.sp)
                    SpeedCurveView(Modifier.weight(1f).fillMaxWidth())
                }
            } else {
                Column(Modifier.fillMaxSize().padding(10.dp)) {
                    Text("Timeline — toque p/ buscar • bolinhas = keyframes", color = AccentGreen, fontSize = 11.sp)
                    TimelineView(timeMsUi, Modifier.weight(1f).fillMaxWidth()) { t ->
                        Playback.timeMs.set(t)
                        timeMsUi = t
                    }
                }
            }
        }
    }
}

@Composable
fun PoseSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    accent: Color = AccentCyan
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = accent, fontSize = 10.sp)
            Text("%.1f".format(value), color = Color.White, fontSize = 10.sp)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(22.dp),
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent)
        )
    }
}

@Composable
fun TimelineView(timeMs: Long, modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { off ->
                val w = size.width.toFloat()
                if (w > 0) onSeek(((off.x / w).coerceIn(0f, 1f) * Playback.DURATION_MS).toLong())
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        drawLine(Color(0xFF2A2A38), Offset(0f, mid), Offset(w, mid), 5f)
        for (i in 0..10) {
            val x = w * i / 10f
            drawLine(Color(0xFF3A3A48), Offset(x, mid - 8f), Offset(x, mid + 8f), 2f)
        }
        for (k in Playback.keys) {
            drawCircle(AccentCyan, 9f, Offset((k.t.toFloat() / Playback.DURATION_MS) * w, mid))
        }
        val ph = (timeMs.toFloat() / Playback.DURATION_MS) * w
        drawLine(AccentGreen, Offset(ph, 0f), Offset(ph, h), 5f)
    }
}

@Composable
fun SpeedCurveView(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, _ ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                if (w <= 0f || h <= 0f) return@detectDragGestures

                val n = Offset(
                    (change.position.x / w).coerceIn(0.05f, 0.95f),
                    (1f - change.position.y / h).coerceIn(0.05f, 0.95f)
                )
                val d1 = (n - Playback.cp1.value).getDistance()
                val d2 = (n - Playback.cp2.value).getDistance()
                if (d1 < d2) Playback.cp1.value = n else Playback.cp2.value = n
            }
        }
    ) {
        val w = size.width
        val h = size.height
        fun px(o: Offset) = Offset(o.x * w, (1f - o.y) * h)
        val p0 = px(Offset(0f, 0f))
        val p3 = px(Offset(1f, 1f))
        val c1 = px(Playback.cp1.value)
        val c2 = px(Playback.cp2.value)
        val path = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(c1.x, c1.y, c2.x, c2.y, p3.x, p3.y)
        }
        drawPath(path, AccentCyan, style = Stroke(width = 6f))
        drawLine(Color.Gray, p0, c1, strokeWidth = 2f)
        drawLine(Color.Gray, p3, c2, strokeWidth = 2f)
        drawCircle(Color.White, radius = 14f, center = c1)
        drawCircle(Color.White, radius = 14f, center = c2)
    }
}
