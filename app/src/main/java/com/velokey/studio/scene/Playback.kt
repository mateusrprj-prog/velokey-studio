package com.velokey.studio.scene

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class CamKey(
    val t: Long,
    val px: Float, val py: Float, val pz: Float,
    val rx: Float, val ry: Float, val rz: Float
) { fun toArray() = floatArrayOf(px, py, pz, rx, ry, rz) }

object Playback {
    const val DURATION_MS: Long = 10_000L
    val timeMs = AtomicLong(0)
    val playing = AtomicBoolean(false)
    val keys = CopyOnWriteArrayList<CamKey>()

    private val liveLock = Any()
    val livePose = FloatArray(6)
    fun setLivePose(px: Float, py: Float, pz: Float, rx: Float, ry: Float, rz: Float) {
        synchronized(liveLock) {
            livePose[0]=px; livePose[1]=py; livePose[2]=pz
            livePose[3]=rx; livePose[4]=ry; livePose[5]=rz
        }
    }

    private val nullLock = Any()
    private val nullArr = FloatArray(3)
    fun setNull(x: Float, y: Float, z: Float) { synchronized(nullLock){ nullArr[0]=x; nullArr[1]=y; nullArr[2]=z } }
    fun nullPos(): FloatArray = synchronized(nullLock) { nullArr.copyOf() }

    val cp1 = mutableStateOf(Offset(0.25f, 0.75f))
    val cp2 = mutableStateOf(Offset(0.75f, 0.25f))

    fun bezierPoint(t: Float): Offset {
        val c = 1f - t
        val x = 3*c*c*t*cp1.value.x + 3*c*t*t*cp2.value.x + t*t*t
        val y = 3*c*c*t*cp1.value.y + 3*c*t*t*cp2.value.y + t*t*t
        return Offset(x, y)
    }

    fun speedAt(t01: Float): Float {
        val e = 0.002f
        val a = bezierPoint(t01.coerceIn(0f, 1f))
        val b = bezierPoint((t01 + e).coerceIn(0f, 1f))
        val dx = b.x - a.x
        if (dx <= 0.0001f) return 1f
        return ((b.y - a.y) / dx).coerceIn(0.05f, 8f)
    }

    fun addKey(k: CamKey) {
        keys.removeAll { it.t == k.t }
        keys.add(k)
    }

    fun cameraPoseAt(tMs: Long): FloatArray {
        if (keys.isEmpty()) return synchronized(liveLock) { livePose.copyOf() }
        val sorted = keys.sortedBy { it.t }
        val first = sorted.first(); val last = sorted.last()
        if (tMs <= first.t) return first.toArray()
        if (tMs >= last.t) return last.toArray()
        val a = sorted.last { it.t <= tMs }
        val b = sorted.first { it.t > tMs }
        val f = ((tMs - a.t).toFloat() / (b.t - a.t).toFloat()).coerceIn(0f, 1f)
        return floatArrayOf(
            lerp(a.px, b.px, f), lerp(a.py, b.py, f), lerp(a.pz, b.pz, f),
            lerp(a.rx, b.rx, f), lerp(a.ry, b.ry, f), lerp(a.rz, b.rz, f)
        )
    }
    private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f
}
