package com.velokey.studio

import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.velokey.studio.scene.Playback
import com.velokey.studio.scene.VeloRenderer

class MainActivity : ComponentActivity() {

    private var velo: VeloRenderer? = null

    private val pickGlb = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    Toast.makeText(this, "Nao consegui ler o arquivo", Toast.LENGTH_SHORT).show()
                } else {
                    val ok = velo?.importGlb(bytes) ?: false
                    val msg = if (ok) "Modelo importado (filho do Null)" else "Arquivo nao e um GLB valido"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppScreen(
                    onRenderer = { velo = it },
                    onImport = { pickGlb.launch(arrayOf("*/*")) },
                    onExport = {
                        Toast.makeText(this, "Export MP4: em breve", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        velo?.start()
    }

    override fun onPause() {
        velo?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        velo?.destroy()
        velo = null
        super.onDestroy()
    }
}

@Composable
fun AppScreen(
    onRenderer: (VeloRenderer) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit
) {
    var playing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(playing) {
        if (playing) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dtMs = (now - last) / 1_000_000.0
                last = now
                val t01 = Playback.timeMs.toFloat() / Playback.DURATION_MS.toFloat()
                var nt = Playback.timeMs + (dtMs * Playback.speedAt(t01)).toLong()
                if (nt >= Playback.DURATION_MS) nt = 0L
                Playback.seek(nt)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Barra superior
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "VeloKey Studio",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onImport, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Importar GLB", fontSize = 12.sp)
            }
            Button(onClick = onExport, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Export", fontSize = 12.sp)
            }
        }

        // Preview 3D
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
            factory = { ctx ->
                val sv = SurfaceView(ctx)
                val r = VeloRenderer(sv)
                onRenderer(r)
                r.start()
                var lx = 0f
                var ly = 0f
                sv.setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            lx = e.x
                            ly = e.y
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = e.x - lx
                            val dy = e.y - ly
                            lx = e.x
                            ly = e.y
                            val l = Playback.live
                            Playback.editLive(
                                l.copy(
                                    ry = l.ry + dx * 0.25f,
                                    rx = (l.rx + dy * 0.25f).coerceIn(-89f, 89f)
                                )
                            )
                        }
                    }
                    true
                }
                sv
            }
        )

        // Controles
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(onClick = { playing = !playing }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(if (playing) "❚❚" else "▶")
            }
            Button(onClick = { Playback.addKey() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("+ KEY", fontSize = 12.sp)
            }
            Button(onClick = { Playback.clearKeys() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text("Limpar", fontSize = 12.sp)
            }
            Button(onClick = { tab = if (tab == 0) 1 else 0 }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(if (tab == 0) "Curva" else "Timeline", fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                String.format("%.1f s", Playback.timeMs / 1000f),
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }

        // Timeline ou curva
        if (tab == 0) {
            TimelineView(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 10.dp)
            ) { t ->
                playing = false
                Playback.seek(t)
            }
        } else {
            SpeedCurveView(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 10.dp)
            )
        }

        // Sliders
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp)
        ) {
            val c = Playback.live
            Text("Camera", color = Color.White, fontSize = 13.sp)
            SliderRow("Pos X", c.px, -20f..20f) { Playback.editLive(Playback.live.copy(px = it)) }
            SliderRow("Pos Y", c.py, -20f..20f) { Playback.editLive(Playback.live.copy(py = it)) }
            SliderRow("Pos Z", c.pz, -30f..30f) { Playback.editLive(Playback.live.copy(pz = it)) }
            SliderRow("Rot X", c.rx, -89f..89f) { Playback.editLive(Playback.live.copy(rx = it)) }
            SliderRow("Rot Y", c.ry, -180f..180f) { Playback.editLive(Playback.live.copy(ry = it)) }
            SliderRow("Rot Z", c.rz, -180f..180f) { Playback.editLive(Playback.live.copy(rz = it)) }

            val n = Playback.nullPos
            Text("Null Object", color = Color.White, fontSize = 13.sp)
            SliderRow("Pos X", n.x, -8f..8f) { Playback.nullPos = Playback.nullPos.copy(x = it) }
            SliderRow("Pos Y", n.y, -2f..8f) { Playback.nullPos = Playback.nullPos.copy(y = it) }
            SliderRow("Pos Z", n.z, -8f..8f) { Playback.nullPos = Playback.nullPos.copy(z = it) }
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            String.format("%.1f", value),
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.width(44.dp)
        )
    }
}

@Composable
fun TimelineView(modifier: Modifier = Modifier, onSeek: (Long) -> Unit) {
    Canvas(
        modifier
            .background(Color(0xFF1E1E1E))
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    val t = (o.x / size.width * Playback.DURATION_MS).toLong()
                    onSeek(t.coerceIn(0L, Playback.DURATION_MS))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val t = (change.position.x / size.width * Playback.DURATION_MS).toLong()
                    onSeek(t.coerceIn(0L, Playback.DURATION_MS))
                }
            }
    ) {
        val w = size.width
        val h = size.height
        // marcas de segundo
        for (s in 0..10) {
            val x = w * s / 10f
            drawLine(Color(0xFF444444), Offset(x, h * 0.65f), Offset(x, h), strokeWidth = 2f)
        }
        // keyframes
        for (k in Playback.keys) {
            val x = k.t.toFloat() / Playback.DURATION_MS.toFloat() * w
            drawCircle(Color(0xFFFFC107), 9f, Offset(x, h * 0.4f))
        }
        // playhead verde
        val px = Playback.timeMs.toFloat() / Playback.DURATION_MS.toFloat() * w
        drawLine(Color(0xFF00E676), Offset(px, 0f), Offset(px, h), strokeWidth = 4f)
    }
}

@Composable
fun SpeedCurveView(modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(1) }
    Canvas(
        modifier
            .background(Color(0xFF1E1E1E))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val p1 = Offset(Playback.cp1.x * size.width, (1f - Playback.cp1.y) * size.height)
                        val p2 = Offset(Playback.cp2.x * size.width, (1f - Playback.cp2.y) * size.height)
                        active = if ((o - p1).getDistance() <= (o - p2).getDistance()) 1 else 2
                    },
                    onDrag = { change, _ ->
                        val x = (change.position.x / size.width).coerceIn(0f, 1f)
                        val y = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        if (active == 1) Playback.cp1 = Offset(x, y) else Playback.cp2 = Offset(x, y)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val start = Offset(0f, h * 0.5f)
        val end = Offset(w, h * 0.5f)
        val c1 = Offset(Playback.cp1.x * w, (1f - Playback.cp1.y) * h)
        val c2 = Offset(Playback.cp2.x * w, (1f - Playback.cp2.y) * h)

        // linha de 1x
        drawLine(Color(0xFF444444), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 2f)

        val path = Path()
        path.moveTo(start.x, start.y)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        drawPath(path, Color(0xFF00E676), style = Stroke(width = 5f))

        drawLine(Color(0xFF888888), start, c1, strokeWidth = 2f)
        drawLine(Color(0xFF888888), end, c2, strokeWidth = 2f)
        drawCircle(Color(0xFFFFC107), 14f, c1)
        drawCircle(Color(0xFFFFC107), 14f, c2)

        // marcador do tempo atual
        val px = Playback.timeMs.toFloat() / Playback.DURATION_MS.toFloat() * w
        drawLine(Color(0x8800E676), Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
    }
}
