package com.example.cad.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID
import kotlin.math.abs

data class FaceHitResult(
    val face: CadFace,
    val hitPoint: Vec3,
    val distance: Float
)

data class SnapResult(
    val point: Vec3,
    val snapType: SnapType,
    val axisLock: AxisLock = AxisLock.NONE
)

enum class SnapType {
    NONE,
    GRID,
    VERTEX,
    FACE
}

data class PushPullResult(
    val scene: CadScene,
    val volumeId: String,
    val activeFaceId: String
)

data class LineCommitResult(
    val scene: CadScene,
    val addedEdgeId: String?,
    val createdFace: CadFace?
)

/**
 * Core geometry operations for ArquiSketch 3D:
 * - Creating rectangles on ground or existing faces
 * - Extruding / Push-Pulling flat faces and existing 3D volume faces
 * - Automatic planar closed-loop face detection when drawing edges with the Line tool
 * - Raycasting and touch-friendly vertex/grid snapping
 */
object CadGeometryEngine {

    fun createBoxFaces(
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
        minZ: Float,
        maxZ: Float,
        defaultMaterial: CadMaterial = CadMaterial.MATTE_WHITE,
        topMaterial: CadMaterial = defaultMaterial,
        volumeId: String = "vol_${UUID.randomUUID().toString().take(8)}"
    ): List<CadFace> {
        val p000 = Vec3(minX, minY, minZ)
        val p100 = Vec3(maxX, minY, minZ)
        val p110 = Vec3(maxX, maxY, minZ)
        val p010 = Vec3(minX, maxY, minZ)

        val p001 = Vec3(minX, minY, maxZ)
        val p101 = Vec3(maxX, minY, maxZ)
        val p111 = Vec3(maxX, maxY, maxZ)
        val p011 = Vec3(minX, maxY, maxZ)

        return listOf(
            // Bottom (-Z)
            CadFace(
                vertices = listOf(p000, p010, p110, p100),
                normal = Vec3(0f, 0f, -1f),
                material = defaultMaterial,
                volumeId = volumeId
            ),
            // Top (+Z)
            CadFace(
                vertices = listOf(p001, p101, p111, p011),
                normal = Vec3(0f, 0f, 1f),
                material = topMaterial,
                volumeId = volumeId
            ),
            // Front (-Y)
            CadFace(
                vertices = listOf(p000, p100, p101, p001),
                normal = Vec3(0f, -1f, 0f),
                material = defaultMaterial,
                volumeId = volumeId
            ),
            // Back (+Y)
            CadFace(
                vertices = listOf(p110, p010, p011, p111),
                normal = Vec3(0f, 1f, 0f),
                material = defaultMaterial,
                volumeId = volumeId
            ),
            // Left (-X)
            CadFace(
                vertices = listOf(p010, p000, p001, p011),
                normal = Vec3(-1f, 0f, 0f),
                material = defaultMaterial,
                volumeId = volumeId
            ),
            // Right (+X)
            CadFace(
                vertices = listOf(p100, p110, p111, p101),
                normal = Vec3(1f, 0f, 0f),
                material = defaultMaterial,
                volumeId = volumeId
            )
        )
    }

    fun createRectangleFace(
        origin: Vec3,
        uAxis: Vec3,
        vAxis: Vec3,
        normal: Vec3,
        width: Float,
        depth: Float,
        material: CadMaterial = CadMaterial.MATTE_WHITE,
        id: String = UUID.randomUUID().toString()
    ): CadFace {
        val u = uAxis.normalized()
        val v = vAxis.normalized()
        val n = normal.normalized()

        val p0 = origin
        val p1 = origin + u * width
        val p2 = origin + u * width + v * depth
        val p3 = origin + v * depth

        val rawVerts = listOf(p0, p1, p2, p3)
        val computedNormal = CadMath.computePolygonNormal(rawVerts)
        val orderedVerts = if (computedNormal.dot(n) >= 0f) rawVerts else rawVerts.reversed()

        return CadFace(
            id = id,
            vertices = orderedVerts,
            normal = n,
            material = material,
            volumeId = null
        )
    }

    /**
     * Real-time Push/Pull extrusion:
     * - If [sourceFace] is a standalone flat face (volumeId == null), extrudes a new watertight 3D prism volume.
     * - If [sourceFace] belongs to an existing volume, translates its vertices along the normal and updates
     *   all connected adjacent faces of that volume smoothly.
     */
    fun applyPushPull(
        baseScene: CadScene,
        sourceFace: CadFace,
        distance: Float
    ): PushPullResult {
        if (abs(distance) < 0.01f) {
            return PushPullResult(
                scene = baseScene,
                volumeId = sourceFace.volumeId ?: "vol_${sourceFace.id}",
                activeFaceId = sourceFace.id
            )
        }

        val normal = sourceFace.normal.normalized()
        val offset = normal * distance

        // Case 1: Extrude standalone flat face into a full 3D volume
        if (sourceFace.volumeId == null) {
            val newVolumeId = "vol_${sourceFace.id.take(8)}"
            val baseVerts = sourceFace.vertices
            val capVerts = baseVerts.map { it + offset }
            val flipWinding = distance < 0f

            val baseFaceVerts = if (flipWinding) baseVerts else baseVerts.reversed()
            val baseFaceNormal = if (flipWinding) normal else -normal
            val capFaceVerts = if (flipWinding) capVerts.reversed() else capVerts
            val capFaceNormal = if (flipWinding) -normal else normal

            val generatedFaces = mutableListOf<CadFace>()
            // Base face
            generatedFaces.add(
                CadFace(
                    id = "${sourceFace.id}_base",
                    vertices = baseFaceVerts,
                    normal = baseFaceNormal,
                    material = sourceFace.material,
                    volumeId = newVolumeId
                )
            )
            // Extruded moving cap face keeps sourceFace.id for continuous tracking
            generatedFaces.add(
                CadFace(
                    id = sourceFace.id,
                    vertices = capFaceVerts,
                    normal = capFaceNormal,
                    material = sourceFace.material,
                    volumeId = newVolumeId
                )
            )

            // Side quad faces connecting base perimeter to cap perimeter
            val count = baseVerts.size
            for (i in 0 until count) {
                val next = (i + 1) % count
                val b0 = baseVerts[i]
                val b1 = baseVerts[next]
                val t1 = capVerts[next]
                val t0 = capVerts[i]
                val sideVerts = if (flipWinding) listOf(b1, b0, t0, t1) else listOf(b0, b1, t1, t0)
                val sideNormal = CadMath.computePolygonNormal(sideVerts)
                generatedFaces.add(
                    CadFace(
                        id = "${sourceFace.id}_side_$i",
                        vertices = sideVerts,
                        normal = sideNormal,
                        material = sourceFace.material,
                        volumeId = newVolumeId
                    )
                )
            }

            val updatedFaces = baseScene.faces.filterNot { it.id == sourceFace.id } + generatedFaces
            return PushPullResult(
                scene = baseScene.copy(faces = updatedFaces),
                volumeId = newVolumeId,
                activeFaceId = sourceFace.id
            )
        }

        // Case 2: Push/Pull an existing face belonging to a 3D volume
        val volId = sourceFace.volumeId
        val targetVerts = sourceFace.vertices

        val updatedFaces = baseScene.faces.map { face ->
            if (face.volumeId != volId) {
                face
            } else if (face.id == sourceFace.id) {
                val moved = face.vertices.map { it + offset }
                face.copy(vertices = moved)
            } else {
                // Stretch adjacent faces sharing vertices with the pushed face
                var changed = false
                val newVerts = face.vertices.map { v ->
                    if (targetVerts.any { tv -> tv.nearEquals(v, 0.02f) }) {
                        changed = true
                        v + offset
                    } else {
                        v
                    }
                }
                if (changed) {
                    val recomputed = CadMath.computePolygonNormal(newVerts)
                    val stableNormal = if (recomputed.length() > 0.5f && recomputed.dot(face.normal) >= 0f) {
                        recomputed
                    } else {
                        face.normal
                    }
                    face.copy(vertices = newVerts, normal = stableNormal)
                } else {
                    face
                }
            }
        }

        return PushPullResult(
            scene = baseScene.copy(faces = updatedFaces),
            volumeId = volId,
            activeFaceId = sourceFace.id
        )
    }

    /**
     * Adds a line segment and automatically detects if the loose edges now form a closed planar loop.
     * If a planar loop (>= 3 edges) is closed, converts it automatically into a selectable/extrudable CadFace!
     */
    fun addEdgeAndDetectClosedFace(
        scene: CadScene,
        start: Vec3,
        end: Vec3,
        defaultMaterial: CadMaterial = CadMaterial.MATTE_WHITE
    ): LineCommitResult {
        if (start.distanceTo(end) < 0.04f) {
            return LineCommitResult(scene, null, null)
        }

        val newEdge = CadEdge(start = start, end = end)
        val allEdges = scene.edges + newEdge

        // Build vertex cluster list so nearby endpoints weld together cleanly
        val nodes = mutableListOf<Vec3>()
        fun getNodeIndex(pt: Vec3): Int {
            val existing = nodes.indexOfFirst { it.nearEquals(pt, 0.06f) }
            if (existing >= 0) return existing
            nodes.add(pt)
            return nodes.lastIndex
        }

        data class GraphEdge(val edge: CadEdge, val u: Int, val v: Int)
        val graphEdges = allEdges.map { e ->
            GraphEdge(e, getNodeIndex(e.start), getNodeIndex(e.end))
        }

        val newGraphEdge = graphEdges.last()
        val startNode = newGraphEdge.u
        val targetNode = newGraphEdge.v

        // BFS from targetNode back to startNode WITHOUT using newGraphEdge to find shortest closed cycle
        val adj = HashMap<Int, MutableList<Pair<Int, GraphEdge>>>()
        for (i in 0 until graphEdges.size - 1) {
            val ge = graphEdges[i]
            if (ge.u == ge.v) continue
            adj.getOrPut(ge.u) { mutableListOf() }.add(ge.v to ge)
            adj.getOrPut(ge.v) { mutableListOf() }.add(ge.u to ge)
        }

        val queue = ArrayDeque<Int>()
        val parentNode = HashMap<Int, Int>()
        val parentEdge = HashMap<Int, GraphEdge>()
        val visited = HashSet<Int>()

        queue.add(targetNode)
        visited.add(targetNode)

        var foundCycle = false
        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == startNode) {
                foundCycle = true
                break
            }
            for ((next, ge) in adj[curr].orEmpty()) {
                if (next !in visited) {
                    visited.add(next)
                    parentNode[next] = curr
                    parentEdge[next] = ge
                    queue.add(next)
                }
            }
        }

        if (foundCycle) {
            val cycleNodeIndices = mutableListOf<Int>()
            val usedEdgeIds = HashSet<String>()
            usedEdgeIds.add(newEdge.id)

            var curr = startNode
            cycleNodeIndices.add(curr)
            while (curr != targetNode) {
                val pe = parentEdge[curr] ?: break
                usedEdgeIds.add(pe.edge.id)
                curr = parentNode[curr] ?: break
                cycleNodeIndices.add(curr)
            }

            if (cycleNodeIndices.size >= 3) {
                val cycleVerts = cycleNodeIndices.map { nodes[it] }
                val normal = CadMath.computePolygonNormal(cycleVerts)
                val origin = cycleVerts[0]
                val isCoplanar = cycleVerts.all { pt ->
                    abs((pt - origin).dot(normal)) <= 0.08f
                }

                if (isCoplanar) {
                    // Orient ground faces upward (+Z) for intuitive Push/Pull
                    val finalVerts = if (abs(normal.z) > 0.75f && normal.z < 0f) {
                        cycleVerts.reversed()
                    } else {
                        cycleVerts
                    }
                    val finalNormal = CadMath.computePolygonNormal(finalVerts)
                    val newFace = CadFace(
                        vertices = finalVerts,
                        normal = finalNormal,
                        material = defaultMaterial,
                        volumeId = null
                    )
                    val remainingEdges = allEdges.filterNot { it.id in usedEdgeIds }
                    return LineCommitResult(
                        scene = scene.copy(
                            faces = scene.faces + newFace,
                            edges = remainingEdges
                        ),
                        addedEdgeId = null,
                        createdFace = newFace
                    )
                }
            }
        }

        return LineCommitResult(
            scene = scene.copy(edges = allEdges),
            addedEdgeId = newEdge.id,
            createdFace = null
        )
    }

    fun raycastFaces(ray: Ray3D, scene: CadScene): FaceHitResult? {
        var best: FaceHitResult? = null
        for (face in scene.faces) {
            val hit = CadMath.intersectRayFace(ray, face) ?: continue
            // Slight tie-breaker preferring non-volume inset panels (like windows/sub-rectangles) on top of walls
            val bias = if (face.volumeId == null) -0.025f else 0f
            val score = hit.second + bias
            if (best == null || score < best.distance) {
                best = FaceHitResult(face = face, hitPoint = hit.first, distance = score)
            }
        }
        return best
    }

    fun findNearestVertexSnap(
        screenPos: Offset,
        scene: CadScene,
        basis: CameraBasis,
        maxPixelRadius: Float = 38f
    ): Vec3? {
        var bestDist = maxPixelRadius
        var bestVertex: Vec3? = null

        fun checkVertex(v: Vec3) {
            val proj = CadMath.project(v, basis)
            if (!proj.inFront) return
            val d = (proj.screen - screenPos).getDistance()
            if (d < bestDist) {
                bestDist = d
                bestVertex = v
            }
        }

        for (face in scene.faces) {
            for (v in face.vertices) checkVertex(v)
        }
        for (edge in scene.edges) {
            checkVertex(edge.start)
            checkVertex(edge.end)
        }
        return bestVertex
    }

    fun pickLooseEdge(
        screenPos: Offset,
        scene: CadScene,
        basis: CameraBasis,
        maxPixelDist: Float = 32f
    ): CadEdge? {
        var best: CadEdge? = null
        var bestDist = maxPixelDist
        for (edge in scene.edges) {
            val seg = CadMath.clipAndProjectSegment(edge.start, edge.end, basis) ?: continue
            val d = CadMath.distancePointToSegment2D(screenPos, seg.first, seg.second)
            if (d < bestDist) {
                bestDist = d
                best = edge
            }
        }
        return best
    }

    fun pickMeasurement(
        screenPos: Offset,
        scene: CadScene,
        basis: CameraBasis,
        maxPixelDist: Float = 34f
    ): CadMeasurement? {
        var best: CadMeasurement? = null
        var bestDist = maxPixelDist
        for (m in scene.measurements) {
            val seg = CadMath.clipAndProjectSegment(m.start, m.end, basis) ?: continue
            val d = CadMath.distancePointToSegment2D(screenPos, seg.first, seg.second)
            if (d < bestDist) {
                bestDist = d
                best = m
            }
        }
        return best
    }
}
