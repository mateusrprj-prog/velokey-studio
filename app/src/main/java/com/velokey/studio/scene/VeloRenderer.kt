package com.velokey.studio.scene

import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Viewport
import com.google.android.filament.View as FView
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class VeloRenderer(surfaceView: SurfaceView) {

    companion object {
        init {
            Utils.init()
        }
    }

    private val choreographer: Choreographer = Choreographer.getInstance()
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: FView = engine.createView()
    private val cameraEntity: Int = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val lightEntity: Int = EntityManager.get().create()
    private val assetLoader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
    private val resourceLoader = ResourceLoader(engine)

    private var swapChain: SwapChain? = null
    private var skybox: Skybox? = null
    private var indirectLight: IndirectLight? = null
    private val assets = ArrayList<FilamentAsset>()
    private var nullRoot: Int = 0
    private var importCount = 0
    private var aspect = 1.0

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            renderFrame(frameTimeNanos)
        }
    }

    init {
        camera.setExposure(16f, 1f / 125f, 100f)
        view.scene = scene
        view.camera = camera

        skybox = Skybox.Builder().color(0.07f, 0.08f, 0.10f, 1f).build(engine)
        scene.skybox = skybox

        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(1f, 1f, 1f))
            .intensity(30_000f)
            .build(engine)
        scene.indirectLight = indirectLight

        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 1f, 1f)
            .intensity(110_000f)
            .direction(0.5f, -1f, -0.6f)
            .castShadows(false)
            .build(engine, lightEntity)
        scene.addEntity(lightEntity)

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                aspect = width.toDouble() / height.toDouble()
                camera.setProjection(45.0, aspect, 0.1, 500.0, Camera.Fov.VERTICAL)
            }
        }
        uiHelper.attachTo(surfaceView)

        // Cena inicial: chao, Null (esfera verde) e cubo laranja filho do Null
        val ground = addAsset(GlbFactory.plane(60f, 0.13f, 0.13f, 0.15f))
        if (ground != null) setTransform(ground.root, mat(1f, 0f, -3f, 0f))

        val nul = addAsset(GlbFactory.sphere(0.4f, 0.2f, 0.9f, 0.3f))
        if (nul != null) nullRoot = nul.root

        val cube = addAsset(GlbFactory.cube(2f, 1f, 0.5f, 0.1f))
        if (nul != null && cube != null) {
            setParent(cube.root, nul.root)
            setTransform(cube.root, mat(1f, 3f, 0f, 0f))
        }
    }

    fun start() {
        choreographer.removeFrameCallback(frameCallback)
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        choreographer.removeFrameCallback(frameCallback)
    }

    fun destroy() {
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        for (a in assets) {
            scene.removeEntities(a.entities)
            assetLoader.destroyAsset(a)
        }
        assets.clear()
        assetLoader.destroy()
        resourceLoader.destroy()
        engine.destroyEntity(lightEntity)
        EntityManager.get().destroy(lightEntity)
        indirectLight?.let { engine.destroyIndirectLight(it) }
        skybox?.let { engine.destroySkybox(it) }
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    /** Importa um GLB e pendura no Null. Retorna false se o arquivo nao for GLB valido. */
    fun importGlb(bytes: ByteArray): Boolean {
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(bytes)
        buf.flip()
        val asset = addAsset(buf) ?: return false

        val box = asset.boundingBox
        val c = box.center
        val h = box.halfExtent
        val m = maxOf(h[0], h[1], h[2])
        val s = if (m > 0.0001f) 1f / m else 1f
        importCount += 1
        val offsetX = -3f * importCount

        if (nullRoot != 0) setParent(asset.root, nullRoot)
        setTransform(asset.root, mat(s, offsetX - c[0] * s, -c[1] * s, -c[2] * s))
        return true
    }

    private fun addAsset(buffer: ByteBuffer): FilamentAsset? {
        val asset = assetLoader.createAsset(buffer) ?: return null
        resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        assets.add(asset)
        return asset
    }

    private fun setTransform(entity: Int, m: FloatArray) {
        val tm = engine.transformManager
        val inst = tm.getInstance(entity)
        if (inst != 0) tm.setTransform(inst, m)
    }

    private fun setParent(child: Int, parent: Int) {
        val tm = engine.transformManager
        val ci = tm.getInstance(child)
        val pi = tm.getInstance(parent)
        if (ci != 0 && pi != 0) tm.setParent(ci, pi)
    }

    private fun mat(s: Float, tx: Float, ty: Float, tz: Float): FloatArray =
        floatArrayOf(
            s, 0f, 0f, 0f,
            0f, s, 0f, 0f,
            0f, 0f, s, 0f,
            tx, ty, tz, 1f
        )

    private fun rotate(v: DoubleArray, rxDeg: Double, ryDeg: Double, rzDeg: Double): DoubleArray {
        val rx = Math.toRadians(rxDeg)
        val ry = Math.toRadians(ryDeg)
        val rz = Math.toRadians(rzDeg)
        var x = v[0]
        var y = v[1]
        var z = v[2]
        // Z (roll)
        val x1 = x * cos(rz) - y * sin(rz)
        val y1 = x * sin(rz) + y * cos(rz)
        x = x1
        y = y1
        // X (pitch)
        val y2 = y * cos(rx) - z * sin(rx)
        val z2 = y * sin(rx) + z * cos(rx)
        y = y2
        z = z2
        // Y (yaw)
        val x3 = x * cos(ry) + z * sin(ry)
        val z3 = -x * sin(ry) + z * cos(ry)
        x = x3
        z = z3
        return doubleArrayOf(x, y, z)
    }

    private fun applyCamera() {
        val p = Playback.currentPose()
        val f = rotate(doubleArrayOf(0.0, 0.0, -1.0), p.rx.toDouble(), p.ry.toDouble(), p.rz.toDouble())
        val u = rotate(doubleArrayOf(0.0, 1.0, 0.0), p.rx.toDouble(), p.ry.toDouble(), p.rz.toDouble())
        val ex = p.px.toDouble()
        val ey = p.py.toDouble()
        val ez = p.pz.toDouble()
        camera.lookAt(ex, ey, ez, ex + f[0], ey + f[1], ez + f[2], u[0], u[1], u[2])
    }

    private fun renderFrame(frameTimeNanos: Long) {
        val sc = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        applyCamera()
        if (nullRoot != 0) {
            val n = Playback.nullPos
            setTransform(nullRoot, mat(1f, n.x, n.y, n.z))
        }
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }
}

/** Gera GLBs simples em memoria (cubo, esfera, plano) para nao precisar compilar materiais. */
object GlbFactory {

    private class MeshData(val pos: FloatArray, val nrm: FloatArray, val idx: ShortArray)

    fun cube(size: Float, r: Float, g: Float, b: Float): ByteBuffer =
        build(cubeMesh(size / 2f), r, g, b, 0.5f)

    fun sphere(radius: Float, r: Float, g: Float, b: Float): ByteBuffer =
        build(sphereMesh(radius, 16, 16), r, g, b, 0.4f)

    fun plane(half: Float, r: Float, g: Float, b: Float): ByteBuffer =
        build(planeMesh(half), r, g, b, 0.9f)

    private fun planeMesh(s: Float): MeshData {
        val pos = floatArrayOf(
            -s, 0f, -s,
            -s, 0f, s,
            s, 0f, s,
            s, 0f, -s
        )
        val nrm = floatArrayOf(
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        )
        val idx = shortArrayOf(0, 1, 2, 0, 2, 3)
        return MeshData(pos, nrm, idx)
    }

    private fun cubeMesh(h: Float): MeshData {
        // cada face: normal n, tangentes u e v (u x v = n)
        val faces = arrayOf(
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
            floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f),
            floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        )
        val signs = arrayOf(
            floatArrayOf(-1f, -1f),
            floatArrayOf(1f, -1f),
            floatArrayOf(1f, 1f),
            floatArrayOf(-1f, 1f)
        )
        val pos = FloatArray(24 * 3)
        val nrm = FloatArray(24 * 3)
        val idx = ShortArray(36)
        var vi = 0
        var ii = 0
        for (fi in 0 until 6) {
            val f = faces[fi]
            val base = fi * 4
            for (si in 0 until 4) {
                val su = signs[si][0]
                val sv = signs[si][1]
                for (a in 0 until 3) {
                    pos[vi * 3 + a] = (f[a] + f[3 + a] * su + f[6 + a] * sv) * h
                    nrm[vi * 3 + a] = f[a]
                }
                vi++
            }
            idx[ii++] = base.toShort()
            idx[ii++] = (base + 1).toShort()
            idx[ii++] = (base + 2).toShort()
            idx[ii++] = base.toShort()
            idx[ii++] = (base + 2).toShort()
            idx[ii++] = (base + 3).toShort()
        }
        return MeshData(pos, nrm, idx)
    }

    private fun sphereMesh(rad: Float, stacks: Int, slices: Int): MeshData {
        val nv = (stacks + 1) * (slices + 1)
        val pos = FloatArray(nv * 3)
        val nrm = FloatArray(nv * 3)
        var k = 0
        for (i in 0..stacks) {
            val th = Math.PI * i / stacks
            for (j in 0..slices) {
                val ph = 2.0 * Math.PI * j / slices
                val x = (sin(th) * cos(ph)).toFloat()
                val y = cos(th).toFloat()
                val z = (sin(th) * sin(ph)).toFloat()
                nrm[k] = x
                nrm[k + 1] = y
                nrm[k + 2] = z
                pos[k] = x * rad
                pos[k + 1] = y * rad
                pos[k + 2] = z * rad
                k += 3
            }
        }
        val idx = ShortArray(stacks * slices * 6)
        var q = 0
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val a = i * (slices + 1) + j
                val b = a + slices + 1
                idx[q++] = a.toShort()
                idx[q++] = (a + 1).toShort()
                idx[q++] = b.toShort()
                idx[q++] = (a + 1).toShort()
                idx[q++] = (b + 1).toShort()
                idx[q++] = b.toShort()
            }
        }
        return MeshData(pos, nrm, idx)
    }

    private fun build(m: MeshData, r: Float, g: Float, b: Float, rough: Float): ByteBuffer {
        val n = m.pos.size / 3
        val mn = FloatArray(3) { Float.MAX_VALUE }
        val mx = FloatArray(3) { -Float.MAX_VALUE }
        for (i in 0 until n) {
            for (a in 0 until 3) {
                val v = m.pos[i * 3 + a]
                if (v < mn[a]) mn[a] = v
                if (v > mx[a]) mx[a] = v
            }
        }
        val idxCount = m.idx.size
        val posBytes = n * 12
        val idxBytes = idxCount * 2
        val binLen = posBytes * 2 + idxBytes
        val binPad = (4 - binLen % 4) % 4
        val nrmOffset = posBytes
        val idxOffset = posBytes * 2

        val json = """
{"asset":{"version":"2.0"},"scene":0,"scenes":[{"nodes":[0]}],"nodes":[{"mesh":0}],
"meshes":[{"primitives":[{"attributes":{"POSITION":0,"NORMAL":1},"indices":2,"material":0}]}],
"materials":[{"pbrMetallicRoughness":{"baseColorFactor":[$r,$g,$b,1.0],"metallicFactor":0.0,"roughnessFactor":$rough},"doubleSided":true}],
"accessors":[
{"bufferView":0,"componentType":5126,"count":$n,"type":"VEC3","min":[${mn[0]},${mn[1]},${mn[2]}],"max":[${mx[0]},${mx[1]},${mx[2]}]},
{"bufferView":1,"componentType":5126,"count":$n,"type":"VEC3"},
{"bufferView":2,"componentType":5123,"count":$idxCount,"type":"SCALAR"}],
"bufferViews":[
{"buffer":0,"byteOffset":0,"byteLength":$posBytes,"target":34962},
{"buffer":0,"byteOffset":$nrmOffset,"byteLength":$posBytes,"target":34962},
{"buffer":0,"byteOffset":$idxOffset,"byteLength":$idxBytes,"target":34963}],
"buffers":[{"byteLength":$binLen}]}
""".trim()

        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - jsonBytes.size % 4) % 4
        val jsonLen = jsonBytes.size + jsonPad
        val binChunkLen = binLen + binPad
        val total = 12 + 8 + jsonLen + 8 + binChunkLen

        val bb = ByteBuffer.allocateDirect(total).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(0x46546C67)
        bb.putInt(2)
        bb.putInt(total)
        bb.putInt(jsonLen)
        bb.putInt(0x4E4F534A)
        bb.put(jsonBytes)
        for (i in 0 until jsonPad) bb.put(0x20.toByte())
        bb.putInt(binChunkLen)
        bb.putInt(0x004E4942)
        for (f in m.pos) bb.putFloat(f)
        for (f in m.nrm) bb.putFloat(f)
        for (s in m.idx) bb.putShort(s)
        for (i in 0 until binPad) bb.put(0.toByte())
        bb.flip()
        return bb
    }
}
