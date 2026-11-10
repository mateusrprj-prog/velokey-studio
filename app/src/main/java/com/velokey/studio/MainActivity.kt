package com.velokey.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val BgDark = Color(0xFF0A0A0E)
val PanelDark = Color(0xFF14141C)
val AccentCyan = Color(0xFF00F0FF)
val AccentPurple = Color(0xFF7B2CBF)

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

@Composable
fun VeloKeyEditorScreen() {
    var isCurveEditorOpen by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .systemBarsPadding()
    ) {
        // Barra Superior
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("VeloKey Studio", color = Color.White, fontSize = 18.sp)
            Text("v0.1 • 120Hz", color = AccentCyan, fontSize = 12.sp)
        }

        // Preview Multi-Touch (Use 2 dedos para girar e dar zoom!)
        Box(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .border(1.dp, Color(0xFF22222E), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            InteractiveVideoPreview()
        }

        // Controles Centrais
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isPlaying) "PLAYING" else "00:00:00",
                color = if (isPlaying) AccentCyan else Color.Gray,
                fontSize = 13.sp
            )

            Button(
                onClick = { isPlaying = !isPlaying },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)
            ) {
                Text(if (isPlaying) "Pausar" else "▶ Play", color = Color.White)
            }

            OutlinedButton(onClick = { isCurveEditorOpen = !isCurveEditorOpen }) {
                Text(
                    if (isCurveEditorOpen) "Ver Timeline" else "Ver Curvas",
                    color = AccentCyan,
                    fontSize = 12.sp
                )
            }
        }

        // Área Inferior: Curvas KeyFlow ou Timeline
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(PanelDark)
        ) {
            if (isCurveEditorOpen) {
                BezierCurveEditor()
            } else {
                MagneticTimeline()
            }
        }
    }
}

@Composable
fun InteractiveVideoPreview() {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var rotation by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rot ->
                    scale = (scale * zoom).coerceIn(0.3f, 4f)
                    rotation += rot
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer(
                    translationX = offsetX,
                    translationY = offsetY,
                    scaleX = scale,
                    scaleY = scale,
                    rotationZ = rotation
                )
                .size(160.dp, 90.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentPurple.copy(alpha = 0.65f))
                .border(2.dp, AccentCyan, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Camada de Vídeo\n(Mova / Gire / Zoom)",
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun BezierCurveEditor() {
    var p1 by remember { mutableStateOf(Offset(220f, 380f)) }
    var p2 by remember { mutableStateOf(Offset(620f, 120f)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Curva de Velocidade (Arraste os pontos brancos)",
            color = AccentCyan,
            fontSize = 12.sp
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        val touch = change.position
                        if ((touch - p1).getDistance() < 120f) {
                            p1 = touch
                        } else if ((touch - p2).getDistance() < 120f) {
                            p2 = touch
                        }
                    }
                }
        ) {
            val start = Offset(40f, size.height - 40f)
            val end = Offset(size.width - 40f, 40f)

            // Grade de fundo sutil
            drawLine(Color(0xFF252532), Offset(40f, size.height / 2), Offset(size.width - 40f, size.height / 2), 2f)
            drawLine(Color(0xFF252532), Offset(size.width / 2, 40f), Offset(size.width / 2, size.height - 40f), 2f)

            val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(p1.x, p1.y, p2.x, p2.y, end.x, end.y)
            }
            drawPath(path, color = AccentCyan, style = Stroke(width = 7f))

            drawLine(Color.Gray, start, p1, strokeWidth = 3f)
            drawLine(Color.Gray, end, p2, strokeWidth = 3f)
            drawCircle(Color.White, radius = 18f, center = p1)
            drawCircle(Color.White, radius = 18f, center = p2)
        }
    }
}

@Composable
fun MagneticTimeline() {
    Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TrackItem("V1 • Vídeo Base (4K Proxy)", AccentPurple, 0.9f)
            TrackItem("FX • Node Glow / Blur", AccentCyan, 0.55f)
            TrackItem("A1 • Áudio (Beat Sync)", Color(0xFF00E676), 0.85f)
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(Color.White)
                .align(Alignment.Center)
        )
    }
}

@Composable
fun TrackItem(label: String, color: Color, width: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(width)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.28f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}
