package com.velokey.studio.scene

import android.content.Context
import android.view.MotionEvent
import org.rajawali3d.Object3D
import org.rajawali3d.lights.DirectionalLight
import org.rajawali3d.materials.Material
import org.rajawali3d.materials.methods.DiffuseMethod
import org.rajawali3d.primitives.Cube
import org.rajawali3d.primitives.Plane
import org.rajawali3d.primitives.Sphere
import org.rajawali3d.renderer.Renderer

class VeloRenderer(context: Context) : Renderer(context) {
    private var nullObj: Object3D? = null

    // Na Rajawali, a cena é configurada no initScene
    override fun initScene() {
        val sc = currentScene
        
        // Câmera inicial
        sc.camera.setPosition(0.0, 2.0, 12.0)
        sc.camera.farPlane = 1000.0

        // Luz (Usa Double para as direções)
        val light = DirectionalLight(0.5, -1.0, -0.6)
        light.setColor(1.0f, 1.0f, 1.0f)
        light.power = 1.5f
        sc.addLight(light)

        // Chão escuro
        val gm = Material()
        gm.enableLighting(true)
        gm.diffuseMethod = DiffuseMethod.Lambert()
        gm.color = 0xFF191921.toInt()
        val ground = Plane(80.0, 80.0, 1, 1)
        ground.material = gm
        ground.rotX = 90.0
        ground.y = -3.0
        sc.addChild(ground)

        // Null Object (Esfera Verde)
        val nm = Material()
        nm.enableLighting(true)
        nm.diffuseMethod = DiffuseMethod.Lambert()
        nm.color = 0xFF00E676.toInt()
        nullObj = Sphere(0.4f, 16, 16)
        nullObj!!.material = nm
        sc.addChild(nullObj)

        // Cubo Laranja (Filho do Null Object) - Isso é Parenting!
        val cm = Material()
        cm.enableLighting(true)
        cm.diffuseMethod = DiffuseMethod.Lambert()
        cm.color = 0xFFFF5722.toInt()
        val cube = Cube(2.0f)
        cube.material = cm
        nullObj!!.addChild(cube) // O cubo segue a esfera verde
    }

    // Na Rajawali, a animação acontece no onRender
    override fun onRender(elapsedTime: Double, deltaTime: Double) {
        super.onRender(elapsedTime, deltaTime)
        
        // Atualiza Câmera pelos Keyframes/Live Pose
        val p = Playback.cameraPoseAt(Playback.timeMs.get())
        currentCamera.setPosition(p[0].toDouble(), p[1].toDouble(), p[2].toDouble())
        currentCamera.rotX = p[3].toDouble()
        currentCamera.rotY = p[4].toDouble()
        currentCamera.rotZ = p[5].toDouble()

        // Atualiza posição do Null Object
        val n = Playback.nullPos()
        nullObj?.setPosition(n[0].toDouble(), n[1].toDouble(), n[2].toDouble())
    }

    override fun onOffsetsChanged(xOffset: Float, yOffset: Float, xOffsetStep: Float, yOffsetStep: Float, xPixels: Int, yPixels: Int) {}
    override fun onTouchEvent(event: MotionEvent?) {}
}
