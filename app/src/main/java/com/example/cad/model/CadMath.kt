package com.example.cad.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Right-handed Z-up 3D vector in meters (SketchUp convention: X=Red, Y=Green, Z=Blue/Up).
 */
data class Vec3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float): Vec3 = Vec3(x / scalar, y / scalar, z / scalar)
    operator fun unaryMinus(): Vec3 = Vec3(-x, -y, -z)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun lengthSquared(): Float = x * x + y * y + z * z
    fun length(): Float = sqrt(lengthSquared())

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-6f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 1f)
    }

    fun distanceTo(other: Vec3): Float = (this - other).length()

    fun nearEquals(other: Vec3, eps: Float = 0.015f): Boolean {
        return abs(x - other.x) <= eps && abs(y - other.y) <= eps && abs(z - other.z) <= eps
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UNIT_X = Vec3(1f, 0f, 0f)
        val UNIT_Y = Vec3(0f, 1f, 0f)
        val UNIT_Z = Vec3(0f, 0f, 1f)
    }
}

data class Ray3D(
    val origin: Vec3,
    val direction: Vec3
)

data class Plane3D(
    val point: Vec3,
    val normal: Vec3
)

enum class AxisLock {
    NONE, X, Y, Z
}

data class ProjectedPoint(
    val screen: Offset,
    val depth: Float,
    val inFront: Boolean
)

data class CameraBasis(
    val eye: Vec3,
    val forward: Vec3,
    val right: Vec3,
    val up: Vec3,
    val focalLength: Float,
    val halfWidth: Float,
    val halfHeight: Float
)

object CadMath {
    const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()

    fun buildCameraBasis(
        camera: CameraState,
        viewportWidth: Float,
        viewportHeight: Float
    ): CameraBasis {
        val az = camera.azimuthDeg * DEG_TO_RAD
        val el = camera.elevationDeg * DEG_TO_RAD
        val cosEl = cos(el)
        val sinEl = sin(el)
        val eye = Vec3(
            x = camera.target.x + camera.distance * cosEl * cos(az),
            y = camera.target.y + camera.distance * cosEl * sin(az),
            z = camera.target.z + camera.distance * sinEl
        )
        val forward = (camera.target - eye).normalized()
        val worldUp = if (abs(forward.z) > 0.985f) Vec3(0f, 1f, 0f) else Vec3.UNIT_Z
        val right = forward.cross(worldUp).normalized()
        val up = right.cross(forward).normalized()
        val halfW = max(1f, viewportWidth * 0.5f)
        val halfH = max(1f, viewportHeight * 0.5f)
        val tanHalfFov = tan((camera.fovDeg * 0.5f) * DEG_TO_RAD)
        val focal = halfH / max(0.1f, tanHalfFov)
        return CameraBasis(
            eye = eye,
            forward = forward,
            right = right,
            up = up,
            focalLength = focal,
            halfWidth = halfW,
            halfHeight = halfH
        )
    }

    fun worldToView(point: Vec3, basis: CameraBasis): Vec3 {
        val rel = point - basis.eye
        return Vec3(
            x = rel.dot(basis.right),
            y = rel.dot(basis.up),
            z = rel.dot(basis.forward)
        )
    }

    fun project(point: Vec3, basis: CameraBasis, nearPlane: Float = 0.08f): ProjectedPoint {
        val view = worldToView(point, basis)
        if (view.z <= nearPlane) {
            return ProjectedPoint(Offset.Unspecified, view.z, inFront = false)
        }
        val sx = basis.halfWidth + (view.x / view.z) * basis.focalLength
        val sy = basis.halfHeight - (view.y / view.z) * basis.focalLength
        return ProjectedPoint(Offset(sx, sy), view.z, inFront = true)
    }

    fun screenToRay(screenPos: Offset, basis: CameraBasis): Ray3D {
        val nx = (screenPos.x - basis.halfWidth) / basis.focalLength
        val ny = (basis.halfHeight - screenPos.y) / basis.focalLength
        val dir = (basis.forward + basis.right * nx + basis.up * ny).normalized()
        return Ray3D(origin = basis.eye, direction = dir)
    }

    fun intersectRayPlane(ray: Ray3D, plane: Plane3D): Vec3? {
        val denom = ray.direction.dot(plane.normal)
        if (abs(denom) < 1e-5f) return null
        val t = (plane.point - ray.origin).dot(plane.normal) / denom
        if (t <= 0.02f) return null
        return ray.origin + ray.direction * t
    }

    fun computePolygonNormal(vertices: List<Vec3>): Vec3 {
        if (vertices.size < 3) return Vec3.UNIT_Z
        var nx = 0f
        var ny = 0f
        var nz = 0f
        for (i in vertices.indices) {
            val curr = vertices[i]
            val next = vertices[(i + 1) % vertices.size]
            nx += (curr.y - next.y) * (curr.z + next.z)
            ny += (curr.z - next.z) * (curr.x + next.x)
            nz += (curr.x - next.x) * (curr.y + next.y)
        }
        val n = Vec3(nx, ny, nz)
        return if (n.length() > 1e-5f) n.normalized() else Vec3.UNIT_Z
    }

    fun buildPlaneBasis(normal: Vec3): Pair<Vec3, Vec3> {
        val n = normal.normalized()
        val ref = if (abs(n.z) < 0.9f) Vec3.UNIT_Z else Vec3.UNIT_X
        val u = ref.cross(n).normalized()
        val v = n.cross(u).normalized()
        return u to v
    }

    fun polygonCentroid(vertices: List<Vec3>): Vec3 {
        if (vertices.isEmpty()) return Vec3.ZERO
        var sx = 0f
        var sy = 0f
        var sz = 0f
        for (p in vertices) {
            sx += p.x
            sy += p.y
            sz += p.z
        }
        val inv = 1f / vertices.size
        return Vec3(sx * inv, sy * inv, sz * inv)
    }

    fun pointInPolygon3D(point: Vec3, vertices: List<Vec3>, normal: Vec3): Boolean {
        if (vertices.size < 3) return false
        val origin = vertices[0]
        val (u, v) = buildPlaneBasis(normal)
        val px = (point - origin).dot(u)
        val py = (point - origin).dot(v)

        var inside = false
        var j = vertices.size - 1
        for (i in vertices.indices) {
            val vi = vertices[i] - origin
            val vj = vertices[j] - origin
            val xi = vi.dot(u)
            val yi = vi.dot(v)
            val xj = vj.dot(u)
            val yj = vj.dot(v)
            val intersect = ((yi > py) != (yj > py)) &&
                (px < (xj - xi) * (py - yi) / ((yj - yi).let { if (abs(it) < 1e-6f) 1e-6f else it }) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun intersectRayFace(ray: Ray3D, face: CadFace): Pair<Vec3, Float>? {
        if (face.vertices.size < 3) return null
        val plane = Plane3D(face.vertices[0], face.normal)
        val hit = intersectRayPlane(ray, plane) ?: return null
        if (!pointInPolygon3D(hit, face.vertices, face.normal)) return null
        val dist = (hit - ray.origin).length()
        return hit to dist
    }

    /**
     * Clips a 3D polygon against the camera near plane in view space, returning projected 2D screen points.
     */
    fun clipAndProjectPolygon(
        vertices: List<Vec3>,
        basis: CameraBasis,
        nearPlane: Float = 0.1f
    ): List<Offset> {
        if (vertices.size < 3) return emptyList()
        val viewPts = vertices.map { worldToView(it, basis) }
        val clipped = mutableListOf<Vec3>()
        for (i in viewPts.indices) {
            val a = viewPts[i]
            val b = viewPts[(i + 1) % viewPts.size]
            val aIn = a.z >= nearPlane
            val bIn = b.z >= nearPlane
            if (aIn) clipped.add(a)
            if (aIn != bIn) {
                val t = (nearPlane - a.z) / (b.z - a.z)
                clipped.add(
                    Vec3(
                        a.x + (b.x - a.x) * t,
                        a.y + (b.y - a.y) * t,
                        nearPlane
                    )
                )
            }
        }
        if (clipped.size < 3) return emptyList()
        return clipped.map { v ->
            Offset(
                basis.halfWidth + (v.x / v.z) * basis.focalLength,
                basis.halfHeight - (v.y / v.z) * basis.focalLength
            )
        }
    }

    /**
     * Clips a 3D segment against the camera near plane and returns projected 2D endpoints.
     */
    fun clipAndProjectSegment(
        start: Vec3,
        end: Vec3,
        basis: CameraBasis,
        nearPlane: Float = 0.1f
    ): Pair<Offset, Offset>? {
        var a = worldToView(start, basis)
        var b = worldToView(end, basis)
        if (a.z < nearPlane && b.z < nearPlane) return null
        if (a.z < nearPlane) {
            val t = (nearPlane - a.z) / (b.z - a.z)
            a = Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, nearPlane)
        } else if (b.z < nearPlane) {
            val t = (nearPlane - b.z) / (a.z - b.z)
            b = Vec3(b.x + (a.x - b.x) * t, b.y + (a.y - b.y) * t, nearPlane)
        }
        val pa = Offset(
            basis.halfWidth + (a.x / a.z) * basis.focalLength,
            basis.halfHeight - (a.y / a.z) * basis.focalLength
        )
        val pb = Offset(
            basis.halfWidth + (b.x / b.z) * basis.focalLength,
            basis.halfHeight - (b.y / b.z) * basis.focalLength
        )
        return pa to pb
    }

    fun distancePointToSegment2D(p: Offset, a: Offset, b: Offset): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-4f) return (p - a).getDistance()
        val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + abx * t
        val projY = a.y + aby * t
        val dx = p.x - projX
        val dy = p.y - projY
        return sqrt(dx * dx + dy * dy)
    }

    fun snapToGrid(value: Float, step: Float): Float {
        if (step <= 0.001f) return value
        return round(value / step) * step
    }

    fun snapPointToGridXY(point: Vec3, step: Float): Vec3 {
        return Vec3(
            x = snapToGrid(point.x, step),
            y = snapToGrid(point.y, step),
            z = point.z
        )
    }

    /**
     * Snaps a candidate end point from [start] to the closest X, Y, or Z axis if within angle threshold.
     */
    fun applyAxisSnap(start: Vec3, candidate: Vec3, angleThresholdDeg: Float = 16f): Pair<Vec3, AxisLock> {
        val delta = candidate - start
        val len = delta.length()
        if (len < 0.08f) return candidate to AxisLock.NONE
        val dir = delta / len
        val cosThreshold = cos(angleThresholdDeg * DEG_TO_RAD)

        val ax = abs(dir.x)
        val ay = abs(dir.y)
        val az = abs(dir.z)
        return when {
            ax >= ay && ax >= az && ax >= cosThreshold -> {
                Vec3(candidate.x, start.y, start.z) to AxisLock.X
            }
            ay >= ax && ay >= az && ay >= cosThreshold -> {
                Vec3(start.x, candidate.y, start.z) to AxisLock.Y
            }
            az >= ax && az >= ay && az >= cosThreshold -> {
                Vec3(start.x, start.y, candidate.z) to AxisLock.Z
            }
            else -> candidate to AxisLock.NONE
        }
    }

    /**
     * Triangulates a planar 3D polygon (convex or simple concave) into index triples referencing [vertices].
     */
    fun triangulatePolygon(vertices: List<Vec3>, normal: Vec3): List<Triple<Int, Int, Int>> {
        val n = vertices.size
        if (n < 3) return emptyList()
        if (n == 3) return listOf(Triple(0, 1, 2))

        val origin = vertices[0]
        val (u, v) = buildPlaneBasis(normal)
        val pts2D = vertices.map { p ->
            val rel = p - origin
            rel.dot(u) to rel.dot(v)
        }

        // Compute signed area to determine winding
        var area = 0f
        for (i in 0 until n) {
            val (x1, y1) = pts2D[i]
            val (x2, y2) = pts2D[(i + 1) % n]
            area += (x1 * y2 - x2 * y1)
        }
        val ccw = area >= 0f

        val remaining = (0 until n).toMutableList()
        val result = mutableListOf<Triple<Int, Int, Int>>()
        var guard = n * n

        while (remaining.size > 3 && guard-- > 0) {
            var earFound = false
            val m = remaining.size
            for (i in 0 until m) {
                val prevIdx = remaining[(i - 1 + m) % m]
                val currIdx = remaining[i]
                val nextIdx = remaining[(i + 1) % m]
                val a = pts2D[prevIdx]
                val b = pts2D[currIdx]
                val c = pts2D[nextIdx]
                val cross = (b.first - a.first) * (c.second - a.second) -
                    (b.second - a.second) * (c.first - a.first)
                val isConvex = if (ccw) cross > 1e-6f else cross < -1e-6f
                if (!isConvex) continue

                var containsOther = false
                for (k in 0 until m) {
                    val idx = remaining[k]
                    if (idx == prevIdx || idx == currIdx || idx == nextIdx) continue
                    if (pointInTriangle2D(pts2D[idx], a, b, c)) {
                        containsOther = true
                        break
                    }
                }
                if (!containsOther) {
                    result.add(Triple(prevIdx, currIdx, nextIdx))
                    remaining.removeAt(i)
                    earFound = true
                    break
                }
            }
            if (!earFound) break
        }

        if (remaining.size == 3) {
            result.add(Triple(remaining[0], remaining[1], remaining[2]))
        } else if (result.isEmpty()) {
            // Fallback fan triangulation
            for (i in 1 until n - 1) {
                result.add(Triple(0, i, i + 1))
            }
        }
        return result
    }

    private fun pointInTriangle2D(
        p: Pair<Float, Float>,
        a: Pair<Float, Float>,
        b: Pair<Float, Float>,
        c: Pair<Float, Float>
    ): Boolean {
        val d1 = sign2D(p, a, b)
        val d2 = sign2D(p, b, c)
        val d3 = sign2D(p, c, a)
        val hasNeg = (d1 < -1e-5f) || (d2 < -1e-5f) || (d3 < -1e-5f)
        val hasPos = (d1 > 1e-5f) || (d2 > 1e-5f) || (d3 > 1e-5f)
        return !(hasNeg && hasPos)
    }

    private fun sign2D(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>
    ): Float {
        return (p1.first - p3.first) * (p2.second - p3.second) -
            (p2.first - p3.first) * (p1.second - p3.second)
    }

    fun formatMeters(value: Float): String {
        val clean = if (abs(value) < 0.005f) 0f else value
        return String.format(java.util.Locale.US, "%.2f m", clean)
    }
}
