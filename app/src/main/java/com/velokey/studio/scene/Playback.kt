package com.velokey.studio.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.pow

data class CamKey(
    val t: Long,
    val px: Float, val py: Float, val pz: Float,
    val rx: Float, val ry: Float, val rz: Float
)

data class V3(val x: Float, val y: Float, val z: Float)

object Playback {
    const val DURATION_MS = 10_000L

    var timeMs by mutableStateOf(0L)
    var live by mutableStateOf(CamKey(0L, 0f, 2f, 12f, 0f, 0f, 0f))
    var nullPos by mutableStateOf(V3(0f, 0f, 0f))
    val keys = mutableStateListOf<CamKey>()

    // true quando o usuario mexeu na camera manualmente (ignora keyframes ate dar seek/+KEY)
    var override = false

    // Curva de velocidade: Bezier de (0,0.5) ate (1,0.5). y=0.5 => 1x
    var cp1 by mutableStateOf(Offset(0.33f, 0.5f))
    var cp2 by mutableStateOf(Offset(0.66f, 0.5f))

    fun editLive(k: CamKey) {
        live = k
        override = true
    }

    fun seek(t: Long) {
        timeMs = t.coerceIn(0L, DURATION_MS)
        override = false
        if (keys.isNotEmpty()) live = poseAt(timeMs)
    }

    fun addKey() {
        val k = live.copy(t = timeMs)
        val i = keys.indexOfFirst { abs(it.t - k.t) < 50L }
        if (i >= 0) {
            keys[i] = k
        } else {
            keys.add(k)
            keys.sortBy { it.t }
        }
        override = false
    }

    fun clearKeys() {
        keys.clear()
        override = false
    }

    fun currentPose(): CamKey {
        if (override || keys.isEmpty()) return live
        return poseAt(timeMs)
    }

    private fun lerp(a: Float, b: Float, f: Float): Float = a + (b - a) * f

    fun poseAt(t: Long): CamKey {
        val ks = keys.toList()
        if (ks.isEmpty()) return live
        if (t <= ks.first().t) return ks.first()
        if (t >= ks.last().t) return ks.last()
        for (i in 0 until ks.size - 1) {
            val a = ks[i]
            val b = ks[i + 1]
            if (t >= a.t && t <= b.t) {
                val f = (t - a.t).toFloat() / (b.t - a.t).coerceAtLeast(1L).toFloat()
                return CamKey(
                    t,
                    lerp(a.px, b.px, f), lerp(a.py, b.py, f), lerp(a.pz, b.pz, f),
                    lerp(a.rx, b.rx, f), lerp(a.ry, b.ry, f), lerp(a.rz, b.rz, f)
                )
            }
        }
        return live
    }

    private fun bx(t: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * cp1.x + 3f * u * t * t * cp2.x + t * t * t
    }

    private fun by(t: Float): Float {
        val u = 1f - t
        return u * u * u * 0.5f + 3f * u * u * t * cp1.y + 3f * u * t * t * cp2.y + t * t * t * 0.5f
    }

    fun speedAt(t01: Float): Float {
        var lo = 0f
        var hi = 1f
        for (i in 0 until 20) {
            val mid = (lo + hi) / 2f
            if (bx(mid) < t01) lo = mid else hi = mid
        }
        val y = by((lo + hi) / 2f)
        val s = 2.0.pow(((y - 0.5f) * 6f).toDouble()).toFloat()
        return s.coerceIn(0.05f, 8f)
    }
}
