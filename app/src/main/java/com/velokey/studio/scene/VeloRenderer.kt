package com.velokey.studio.scene

import android.content.Context
import org.rajawali3d.Object3D
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod
import org.rajawali3d.primitives.Cube
import org.rajawali3d.primitives.Plane
import org.rajawali3d.primitives.Sphere
import org.rajawali3d.renderer.Renderer
import org.rajawali3d.scene.Scene
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VeloRenderer(context: Context) : Renderer(context) {
    private var scene: Scene? = null
    private var nullObj: Object3D? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        super.onSurfaceCreated(gl, config)
        val sc = Scene()
        val cam = sc.camera
        cam.setPosition(0.0, 2.0, 12.0)
        cam.setFarPlane(1000.0)

        val light = DirectionalLight(0.5f, -1.0f, -0.6f)
        light.setColor(1f, 1f, 1f)
        light.setPower(1.4f)
        sc.addLight(light)

        val gm = Material()
        gm.enableLighting()
        gm.setDiffuseMethod(DiffuseMethod.Lambert())
        gm.setColor(0xFF191921)
        val ground = Plane(80f, 80f, 8, 8)
        ground.material = gm
        ground.setRotX(90.0)
        ground.setPosition(0.0, -3.0, 0.0)
        sc.addChild(ground)

        val nm = Material()
        nm.enableLighting()
        nm.setDiffuseMethod(DiffuseMethod.Lambert())
        nm.setColor(0xFF00E676)
        val nul = Sphere(0.4f, 16, 16)
        nul.material = nm
        sc.addChild(nul)

        val cm = Material()
        cm.enableLighting()
        cm.setDiffuseMethod(DiffuseMethod.Lambert())
        cm.setColor(0xFFFF5722)
        val cube = Cube(2.0f)
        cube.material = cm
        nul.addChild(cube)

        scene = sc
        nullObj = nul
    }

    override fun onDrawFrame(gl: GL10?) {
        super.onDrawFrame(gl)
        val sc = scene ?: return
        val p = Playback.cameraPoseAt(Playback.timeMs.get())
        val cam = sc.camera
        cam.setPosition(p[0].toDouble(), p[1].toDouble(), p[2].toDouble())
        cam.setRotX(p[3].toDouble())
        cam.setRotY(p[4].toDouble())
        cam.setRotZ(p[5].toDouble())
        val n = Playback.nullPos()
        nullObj?.setPosition(n[0].toDouble(), n[1].toDouble(), n[2].toDouble())
    }
}
