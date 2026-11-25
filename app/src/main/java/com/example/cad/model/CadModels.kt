package com.example.cad.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.MatConcreteBase
import com.example.ui.theme.MatGlassBase
import com.example.ui.theme.MatWhiteBase
import com.example.ui.theme.MatWoodBase
import java.util.UUID

enum class CadTool {
    ORBIT,
    SELECT,
    LINE,
    RECTANGLE,
    PUSH_PULL,
    ERASER,
    PAINT,
    MEASURE
}

enum class CameraPreset {
    ISOMETRIC,
    TOP,
    FRONT
}

enum class CadMaterial(
    val displayNameEs: String,
    val baseColor: Color,
    val alpha: Float,
    val roughness: Float,
    val metallic: Float
) {
    MATTE_WHITE(
        displayNameEs = "Blanco Mate",
        baseColor = MatWhiteBase,
        alpha = 1.0f,
        roughness = 0.88f,
        metallic = 0.0f
    ),
    CONCRETE(
        displayNameEs = "Concreto",
        baseColor = MatConcreteBase,
        alpha = 1.0f,
        roughness = 0.92f,
        metallic = 0.02f
    ),
    WOOD(
        displayNameEs = "Madera",
        baseColor = MatWoodBase,
        alpha = 1.0f,
        roughness = 0.72f,
        metallic = 0.0f
    ),
    GLASS(
        displayNameEs = "Vidrio",
        baseColor = MatGlassBase,
        alpha = 0.44f,
        roughness = 0.12f,
        metallic = 0.1f
    )
}

data class CadFace(
    val id: String = UUID.randomUUID().toString(),
    val vertices: List<Vec3>,
    val normal: Vec3,
    val material: CadMaterial = CadMaterial.MATTE_WHITE,
    val volumeId: String? = null
) {
    fun centroid(): Vec3 = CadMath.polygonCentroid(vertices)
}

data class CadEdge(
    val id: String = UUID.randomUUID().toString(),
    val start: Vec3,
    val end: Vec3
) {
    fun length(): Float = start.distanceTo(end)
}

data class CadMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val start: Vec3,
    val end: Vec3
) {
    fun distanceMeters(): Float = start.distanceTo(end)
}

data class CadScene(
    val faces: List<CadFace> = emptyList(),
    val edges: List<CadEdge> = emptyList(),
    val measurements: List<CadMeasurement> = emptyList()
)

data class CameraState(
    val target: Vec3 = Vec3(0f, 0f, 1.0f),
    val azimuthDeg: Float = 42f,
    val elevationDeg: Float = 28f,
    val distance: Float = 11.5f,
    val fovDeg: Float = 46f
) {
    companion object {
        fun forPreset(preset: CameraPreset, currentTarget: Vec3 = Vec3(0f, 0f, 1.0f)): CameraState {
            return when (preset) {
                CameraPreset.ISOMETRIC -> CameraState(
                    target = currentTarget,
                    azimuthDeg = 42f,
                    elevationDeg = 28f,
                    distance = 11.5f
                )
                CameraPreset.TOP -> CameraState(
                    target = Vec3(currentTarget.x, currentTarget.y, 0f),
                    azimuthDeg = 0f,
                    elevationDeg = 88.5f,
                    distance = 13.0f
                )
                CameraPreset.FRONT -> CameraState(
                    target = currentTarget,
                    azimuthDeg = -90f,
                    elevationDeg = 4f,
                    distance = 11.5f
                )
            }
        }
    }
}

sealed class LastPrecisionOperation {
    data class RectangleOp(
        val faceId: String,
        val origin: Vec3,
        val uAxis: Vec3,
        val vAxis: Vec3,
        val normal: Vec3,
        val width: Float,
        val depth: Float,
        val material: CadMaterial
    ) : LastPrecisionOperation()

    data class PushPullOp(
        val volumeId: String,
        val movingFaceId: String,
        val baseSceneBeforeDrag: CadScene,
        val sourceFace: CadFace,
        val currentDistance: Float
    ) : LastPrecisionOperation()

    data class LineOp(
        val edgeId: String,
        val start: Vec3,
        val direction: Vec3,
        val currentLength: Float
    ) : LastPrecisionOperation()
}

object SampleSceneFactory {
    fun createEmptyScene(): CadScene = CadScene()

    /**
     * Creates an architectural pavilion starter block ready to orbit, push/pull, paint, or edit.
     */
    fun createSampleScene(): CadScene {
        val faces = mutableListOf<CadFace>()

        // Main architectural volume: 3.6m x 2.6m x 2.4m (Matte White walls + Concrete roof)
        val mainVol = CadGeometryEngine.createBoxFaces(
            minX = -1.8f,
            maxX = 1.8f,
            minY = -1.3f,
            maxY = 1.3f,
            minZ = 0f,
            maxZ = 2.4f,
            defaultMaterial = CadMaterial.MATTE_WHITE,
            topMaterial = CadMaterial.CONCRETE,
            volumeId = "sample_main_volume"
        )
        faces.addAll(mainVol)

        // Warm wooden terrace deck beside the main volume: 2.0m x 2.6m x 0.35m
        val deckVol = CadGeometryEngine.createBoxFaces(
            minX = 1.8f,
            maxX = 3.8f,
            minY = -1.3f,
            maxY = 1.3f,
            minZ = 0f,
            maxZ = 0.35f,
            defaultMaterial = CadMaterial.WOOD,
            topMaterial = CadMaterial.WOOD,
            volumeId = "sample_wood_deck"
        )
        faces.addAll(deckVol)

        // Subtle glass window inset panel on the front face (-Y) ready to push/pull or inspect
        val glassPanel = CadFace(
            id = "sample_front_glass",
            vertices = listOf(
                Vec3(-1.1f, -1.312f, 0.7f),
                Vec3(1.1f, -1.312f, 0.7f),
                Vec3(1.1f, -1.312f, 1.95f),
                Vec3(-1.1f, -1.312f, 1.95f)
            ),
            normal = Vec3(0f, -1f, 0f),
            material = CadMaterial.GLASS,
            volumeId = null
        )
        faces.add(glassPanel)

        return CadScene(faces = faces)
    }
}
