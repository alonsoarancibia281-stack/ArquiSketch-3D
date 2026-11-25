package com.example.data

import com.example.cad.model.CadEdge
import com.example.cad.model.CadFace
import com.example.cad.model.CadMaterial
import com.example.cad.model.CadMeasurement
import com.example.cad.model.CadScene
import com.example.cad.model.CameraState
import com.example.cad.model.Vec3
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class Vec3Dto(val x: Float, val y: Float, val z: Float)

@JsonClass(generateAdapter = true)
data class FaceDto(
    val id: String,
    val vertices: List<Vec3Dto>,
    val normal: Vec3Dto,
    val material: String,
    val volumeId: String?
)

@JsonClass(generateAdapter = true)
data class EdgeDto(
    val id: String,
    val start: Vec3Dto,
    val end: Vec3Dto
)

@JsonClass(generateAdapter = true)
data class MeasurementDto(
    val id: String,
    val start: Vec3Dto,
    val end: Vec3Dto
)

@JsonClass(generateAdapter = true)
data class CameraDto(
    val target: Vec3Dto,
    val azimuthDeg: Float,
    val elevationDeg: Float,
    val distance: Float,
    val fovDeg: Float
)

@JsonClass(generateAdapter = true)
data class SavedProjectPayload(
    val faces: List<FaceDto>,
    val edges: List<EdgeDto>,
    val measurements: List<MeasurementDto>,
    val camera: CameraDto
)

object SceneSerializer {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(SavedProjectPayload::class.java)

    fun encode(scene: CadScene, camera: CameraState): String {
        val payload = SavedProjectPayload(
            faces = scene.faces.map { f ->
                FaceDto(
                    id = f.id,
                    vertices = f.vertices.map { Vec3Dto(it.x, it.y, it.z) },
                    normal = Vec3Dto(f.normal.x, f.normal.y, f.normal.z),
                    material = f.material.name,
                    volumeId = f.volumeId
                )
            },
            edges = scene.edges.map { e ->
                EdgeDto(
                    id = e.id,
                    start = Vec3Dto(e.start.x, e.start.y, e.start.z),
                    end = Vec3Dto(e.end.x, e.end.y, e.end.z)
                )
            },
            measurements = scene.measurements.map { m ->
                MeasurementDto(
                    id = m.id,
                    start = Vec3Dto(m.start.x, m.start.y, m.start.z),
                    end = Vec3Dto(m.end.x, m.end.y, m.end.z)
                )
            },
            camera = CameraDto(
                target = Vec3Dto(camera.target.x, camera.target.y, camera.target.z),
                azimuthDeg = camera.azimuthDeg,
                elevationDeg = camera.elevationDeg,
                distance = camera.distance,
                fovDeg = camera.fovDeg
            )
        )
        return adapter.toJson(payload)
    }

    fun decode(json: String): Pair<CadScene, CameraState>? {
        return runCatching {
            val payload = adapter.fromJson(json) ?: return null
            val scene = CadScene(
                faces = payload.faces.map { f ->
                    val mat = CadMaterial.entries.firstOrNull { it.name == f.material }
                        ?: CadMaterial.MATTE_WHITE
                    CadFace(
                        id = f.id,
                        vertices = f.vertices.map { Vec3(it.x, it.y, it.z) },
                        normal = Vec3(f.normal.x, f.normal.y, f.normal.z),
                        material = mat,
                        volumeId = f.volumeId
                    )
                },
                edges = payload.edges.map { e ->
                    CadEdge(
                        id = e.id,
                        start = Vec3(e.start.x, e.start.y, e.start.z),
                        end = Vec3(e.end.x, e.end.y, e.end.z)
                    )
                },
                measurements = payload.measurements.map { m ->
                    CadMeasurement(
                        id = m.id,
                        start = Vec3(m.start.x, m.start.y, m.start.z),
                        end = Vec3(m.end.x, m.end.y, m.end.z)
                    )
                }
            )
            val cam = CameraState(
                target = Vec3(payload.camera.target.x, payload.camera.target.y, payload.camera.target.z),
                azimuthDeg = payload.camera.azimuthDeg,
                elevationDeg = payload.camera.elevationDeg,
                distance = payload.camera.distance,
                fovDeg = payload.camera.fovDeg
            )
            scene to cam
        }.getOrNull()
    }
}
