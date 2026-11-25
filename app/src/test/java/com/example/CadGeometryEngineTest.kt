package com.example

import com.example.cad.export.CadModelExporter
import com.example.cad.model.AxisLock
import com.example.cad.model.CadGeometryEngine
import com.example.cad.model.CadMaterial
import com.example.cad.model.CadMath
import com.example.cad.model.CadScene
import com.example.cad.model.SampleSceneFactory
import com.example.cad.model.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadGeometryEngineTest {

    @Test
    fun rectangleAndPushPull_createsWatertightVolumeWithExactHeight() {
        val rect = CadGeometryEngine.createRectangleFace(
            origin = Vec3(0f, 0f, 0f),
            uAxis = Vec3.UNIT_X,
            vAxis = Vec3.UNIT_Y,
            normal = Vec3.UNIT_Z,
            width = 4.0f,
            depth = 2.5f
        )
        val initialScene = CadScene(faces = listOf(rect))
        val extruded = CadGeometryEngine.applyPushPull(initialScene, rect, distance = 3.5f)

        // Base + Cap + 4 Side faces = 6 faces in one watertight volume
        assertEquals(6, extruded.scene.faces.size)
        val cap = extruded.scene.faces.first { it.id == rect.id }
        assertTrue(cap.vertices.all { kotlin.math.abs(it.z - 3.5f) < 0.01f })

        // Pushing/pulling existing volume cap further shifts top face cleanly
        val stretched = CadGeometryEngine.applyPushPull(extruded.scene, cap, distance = 1.5f)
        val movedCap = stretched.scene.faces.first { it.id == rect.id }
        assertTrue(movedCap.vertices.all { kotlin.math.abs(it.z - 5.0f) < 0.01f })
    }

    @Test
    fun lineTool_automaticallyClosesPlanarLoopIntoFace() {
        var scene = CadScene()
        val r1 = CadGeometryEngine.addEdgeAndDetectClosedFace(scene, Vec3(0f, 0f, 0f), Vec3(3f, 0f, 0f))
        scene = r1.scene
        assertEquals(1, scene.edges.size)
        assertEquals(0, scene.faces.size)

        val r2 = CadGeometryEngine.addEdgeAndDetectClosedFace(scene, Vec3(3f, 0f, 0f), Vec3(3f, 2f, 0f))
        scene = r2.scene
        val r3 = CadGeometryEngine.addEdgeAndDetectClosedFace(scene, Vec3(3f, 2f, 0f), Vec3(0f, 2f, 0f))
        scene = r3.scene
        val r4 = CadGeometryEngine.addEdgeAndDetectClosedFace(scene, Vec3(0f, 2f, 0f), Vec3(0f, 0f, 0f))
        scene = r4.scene

        assertNotNull("Expected automatic closed face creation", r4.createdFace)
        assertEquals(1, scene.faces.size)
        assertEquals(0, scene.edges.size)
        assertEquals(4, scene.faces.first().vertices.size)
    }

    @Test
    fun axisSnap_locksOntoRedGreenBlueAxes() {
        val (_, lockX) = CadMath.applyAxisSnap(Vec3.ZERO, Vec3(4.0f, 0.2f, 0f))
        val (_, lockY) = CadMath.applyAxisSnap(Vec3.ZERO, Vec3(0.1f, 3.5f, 0f))
        val (_, lockZ) = CadMath.applyAxisSnap(Vec3.ZERO, Vec3(0.1f, 0.1f, 3.0f))
        assertEquals(AxisLock.X, lockX)
        assertEquals(AxisLock.Y, lockY)
        assertEquals(AxisLock.Z, lockZ)
    }

    @Test
    fun exporters_generateValidObjOutputForSampleScene() {
        val sample = SampleSceneFactory.createSampleScene()
        val obj = CadModelExporter.exportToObj(sample, "Pabellon Test")
        assertTrue(obj.contains("o Pabellon_Test"))
        assertTrue(obj.contains("v "))
        assertTrue(obj.contains("vn "))
        assertTrue(obj.contains("f "))
        assertTrue(obj.contains("usemtl ${CadMaterial.WOOD.name.lowercase()}"))
        assertTrue(obj.contains("usemtl ${CadMaterial.GLASS.name.lowercase()}"))
    }
}
