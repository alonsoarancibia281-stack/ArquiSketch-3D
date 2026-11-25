package com.example.cad.export

import android.util.Base64
import com.example.cad.model.CadFace
import com.example.cad.model.CadMaterial
import com.example.cad.model.CadMath
import com.example.cad.model.CadScene
import com.example.cad.model.Vec3
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Exports a CadScene to industry-standard Wavefront .OBJ and glTF 2.0 (.gltf with embedded PBR buffer).
 * Converts internal Z-up architectural coordinates (x, y, z) to standard Y-up export space (x, z, -y).
 */
object CadModelExporter {

    private fun toYUp(v: Vec3): Vec3 = Vec3(v.x, v.z, -v.y)

    fun exportToObj(scene: CadScene, projectName: String): String {
        val sb = StringBuilder()
        sb.appendLine("# Exported from ArquiSketch 3D")
        sb.appendLine("# Project: $projectName")
        sb.appendLine("# Units: meters (Y-up)")
        sb.appendLine("o ${sanitizeId(projectName)}")

        var vertexOffset = 1
        var normalOffset = 1

        val facesByMaterial = scene.faces.groupBy { it.material }
        for ((material, faces) in facesByMaterial) {
            sb.appendLine("g ${material.name.lowercase(Locale.US)}")
            sb.appendLine("usemtl ${material.name.lowercase(Locale.US)}")
            for (face in faces) {
                val tris = CadMath.triangulatePolygon(face.vertices, face.normal)
                if (tris.isEmpty()) continue
                val n = toYUp(face.normal).normalized()
                sb.appendLine(String.format(Locale.US, "vn %.5f %.5f %.5f", n.x, n.y, n.z))
                for (v in face.vertices) {
                    val p = toYUp(v)
                    sb.appendLine(String.format(Locale.US, "v %.5f %.5f %.5f", p.x, p.y, p.z))
                }
                for ((i0, i1, i2) in tris) {
                    val a = vertexOffset + i0
                    val b = vertexOffset + i1
                    val c = vertexOffset + i2
                    sb.appendLine("f $a//$normalOffset $b//$normalOffset $c//$normalOffset")
                }
                vertexOffset += face.vertices.size
                normalOffset += 1
            }
        }
        return sb.toString()
    }

    /**
     * Generates a self-contained glTF 2.0 JSON string with an embedded Base64 binary buffer
     * containing triangulated positions, normals, indices, and PBR materials.
     */
    fun exportToGltf(scene: CadScene, projectName: String): String {
        val bufferStream = ByteArrayOutputStream()
        val bufferViews = mutableListOf<String>()
        val accessors = mutableListOf<String>()
        val primitives = mutableListOf<String>()
        val materialList = CadMaterial.entries

        val facesByMaterial = scene.faces.groupBy { it.material }

        for ((matIndex, material) in materialList.withIndex()) {
            val faces = facesByMaterial[material].orEmpty()
            if (faces.isEmpty()) continue

            val positions = mutableListOf<Float>()
            val normals = mutableListOf<Float>()
            val indices = mutableListOf<Int>()
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY

            var localVertexBase = 0
            for (face: CadFace in faces) {
                val tris = CadMath.triangulatePolygon(face.vertices, face.normal)
                if (tris.isEmpty()) continue
                val n = toYUp(face.normal).normalized()
                for (v in face.vertices) {
                    val p = toYUp(v)
                    positions.add(p.x); positions.add(p.y); positions.add(p.z)
                    normals.add(n.x); normals.add(n.y); normals.add(n.z)
                    minX = min(minX, p.x); minY = min(minY, p.y); minZ = min(minZ, p.z)
                    maxX = max(maxX, p.x); maxY = max(maxY, p.y); maxZ = max(maxZ, p.z)
                }
                for ((i0, i1, i2) in tris) {
                    indices.add(localVertexBase + i0)
                    indices.add(localVertexBase + i1)
                    indices.add(localVertexBase + i2)
                }
                localVertexBase += face.vertices.size
            }

            if (indices.isEmpty()) continue

            // Helper to align buffer to 4 bytes
            fun align4() {
                while (bufferStream.size() % 4 != 0) bufferStream.write(0)
            }

            // 1. Positions
            align4()
            val posOffset = bufferStream.size()
            val posBytes = floatsToLeBytes(positions)
            bufferStream.write(posBytes)
            val posViewIdx = bufferViews.size
            bufferViews.add(
                """{"buffer":0,"byteOffset":$posOffset,"byteLength":${posBytes.size},"target":34962}"""
            )
            val posAccessorIdx = accessors.size
            accessors.add(
                String.format(
                    Locale.US,
                    """{"bufferView":%d,"componentType":5126,"count":%d,"type":"VEC3","min":[%.5f,%.5f,%.5f],"max":[%.5f,%.5f,%.5f]}""",
                    posViewIdx,
                    positions.size / 3,
                    minX, minY, minZ,
                    maxX, maxY, maxZ
                )
            )

            // 2. Normals
            align4()
            val normOffset = bufferStream.size()
            val normBytes = floatsToLeBytes(normals)
            bufferStream.write(normBytes)
            val normViewIdx = bufferViews.size
            bufferViews.add(
                """{"buffer":0,"byteOffset":$normOffset,"byteLength":${normBytes.size},"target":34962}"""
            )
            val normAccessorIdx = accessors.size
            accessors.add(
                """{"bufferView":$normViewIdx,"componentType":5126,"count":${normals.size / 3},"type":"VEC3"}"""
            )

            // 3. Indices (UNSIGNED_INT = 5125 to avoid 65k cap)
            align4()
            val idxOffset = bufferStream.size()
            val idxBytes = intsToLeBytes(indices)
            bufferStream.write(idxBytes)
            val idxViewIdx = bufferViews.size
            bufferViews.add(
                """{"buffer":0,"byteOffset":$idxOffset,"byteLength":${idxBytes.size},"target":34963}"""
            )
            val idxAccessorIdx = accessors.size
            accessors.add(
                """{"bufferView":$idxViewIdx,"componentType":5125,"count":${indices.size},"type":"SCALAR"}"""
            )

            primitives.add(
                """{"attributes":{"POSITION":$posAccessorIdx,"NORMAL":$normAccessorIdx},"indices":$idxAccessorIdx,"material":$matIndex}"""
            )
        }

        val rawBuffer = bufferStream.toByteArray()
        val encodedBuffer = Base64.encodeToString(rawBuffer, Base64.NO_WRAP)

        val materialsJson = materialList.joinToString(",") { mat ->
            val r = mat.baseColor.red
            val g = mat.baseColor.green
            val b = mat.baseColor.blue
            val a = mat.alpha
            val alphaMode = if (a < 0.99f) ""","alphaMode":"BLEND"""" else ""
            String.format(
                Locale.US,
                """{"name":"%s","doubleSided":true%s,"pbrMetallicRoughness":{"baseColorFactor":[%.3f,%.3f,%.3f,%.3f],"metallicFactor":%.2f,"roughnessFactor":%.2f}}""",
                mat.name.lowercase(Locale.US),
                alphaMode,
                r, g, b, a,
                mat.metallic,
                mat.roughness
            )
        }

        val safeName = sanitizeId(projectName)
        return buildString {
            append("{")
            append(""""asset":{"version":"2.0","generator":"ArquiSketch 3D Android"},""")
            append(""""scene":0,"scenes":[{"name":"$safeName","nodes":[0]}],""")
            append(""""nodes":[{"name":"$safeName","mesh":0}],""")
            append(""""meshes":[{"name":"$safeName","primitives":[${primitives.joinToString(",")}]}],""")
            append(""""materials":[$materialsJson],""")
            append(""""buffers":[{"uri":"data:application/octet-stream;base64,$encodedBuffer","byteLength":${rawBuffer.size}}],""")
            append(""""bufferViews":[${bufferViews.joinToString(",")}],""")
            append(""""accessors":[${accessors.joinToString(",")}]""")
            append("}")
        }
    }

    private fun floatsToLeBytes(values: List<Float>): ByteArray {
        val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in values) bb.putFloat(f)
        return bb.array()
    }

    private fun intsToLeBytes(values: List<Int>): ByteArray {
        val bb = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in values) bb.putInt(i)
        return bb.array()
    }

    private fun sanitizeId(name: String): String {
        val clean = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return clean.ifEmpty { "arqui_model" }
    }
}
