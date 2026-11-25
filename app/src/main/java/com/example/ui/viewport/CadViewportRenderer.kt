package com.example.ui.viewport

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.cad.model.AxisLock
import com.example.cad.model.CadFace
import com.example.cad.model.CadMaterial
import com.example.cad.model.CadMath
import com.example.cad.model.CameraBasis
import com.example.cad.model.SnapResult
import com.example.cad.model.SnapType
import com.example.cad.model.Vec3
import com.example.ui.theme.AxisBlue
import com.example.ui.theme.AxisGreen
import com.example.ui.theme.AxisRed
import com.example.ui.theme.CadViewportPalette
import com.example.ui.theme.SelectionAmber
import com.example.ui.theme.SelectionCyan
import com.example.ui.workspace.ActiveInteraction
import com.example.ui.workspace.CadWorkspaceUiState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * SketchUp-inspired real-time 3D architectural renderer on Jetpack Compose DrawScope.
 */
object CadViewportRenderer {

    private val sunDirection = Vec3(0.44f, -0.58f, 0.69f).normalized()
    private val dashedAxisEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
    private val dashedGuideEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
    private val dashedMeasureEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)

    fun DrawScope.renderCadScene(
        state: CadWorkspaceUiState,
        palette: CadViewportPalette
    ) {
        val basis = CadMath.buildCameraBasis(state.camera, size.width, size.height)

        // 1. SketchUp sky gradient & horizon ground fill
        drawSkyAndGround(basis, palette)

        // 2. Configurable meter grid on Z=0
        if (state.showGrid) {
            drawGroundGrid(basis, state.gridSpacingMeters, palette)
        }

        // 3. Red (X), Green (Y), Blue (Z) Cartesian Axes
        drawCartesianAxes(basis, palette)

        // 4. Painter-sorted 3D faces (opaque first, translucent glass after)
        drawSceneFaces(state, basis, palette)

        // 5. Loose edges drawn with Line tool
        drawLooseEdges(state, basis, palette)

        // 6. Persistent Tape Measure dimension guides
        drawMeasurements(state, basis, palette)

        // 7. Live interactive tool previews (Rectangle, Line + Axis Lock, Push/Pull arrow, Measure)
        drawActiveInteractionOverlay(state, basis, palette)
    }

    private fun DrawScope.drawSkyAndGround(basis: CameraBasis, palette: CadViewportPalette) {
        // Project a far horizon point in the horizontal camera look direction
        val horizDir = Vec3(basis.forward.x, basis.forward.y, 0f)
        val horizonY = if (horizDir.length() > 0.05f) {
            val farHorizon = basis.eye + horizDir.normalized() * 400f
            val proj = CadMath.project(Vec3(farHorizon.x, farHorizon.y, 0f), basis)
            if (proj.inFront) proj.screen.y.coerceIn(-size.height * 0.4f, size.height * 1.2f)
            else size.height * 0.35f
        } else {
            // Top-down view: fill entirely with ground tone
            -10f
        }

        val clampedHorizon = horizonY.coerceIn(0f, size.height)
        if (clampedHorizon > 1f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.skyTop, palette.skyHorizon),
                    startY = 0f,
                    endY = clampedHorizon
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, clampedHorizon)
            )
        }
        if (clampedHorizon < size.height) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.groundFill.copy(alpha = 0.96f),
                        palette.groundFill
                    ),
                    startY = clampedHorizon,
                    endY = size.height
                ),
                topLeft = Offset(0f, clampedHorizon),
                size = Size(size.width, size.height - clampedHorizon)
            )
        }
        if (clampedHorizon in 1f..(size.height - 1f)) {
            drawLine(
                color = palette.gridMinor.copy(alpha = 0.45f),
                start = Offset(0f, clampedHorizon),
                end = Offset(size.width, clampedHorizon),
                strokeWidth = 1.5f
            )
        }
    }

    private fun DrawScope.drawGroundGrid(
        basis: CameraBasis,
        spacing: Float,
        palette: CadViewportPalette
    ) {
        val safeStep = spacing.coerceIn(0.25f, 5.0f)
        val halfSteps = (12f / safeStep).toInt().coerceIn(8, 24)
        val extent = halfSteps * safeStep

        for (i in -halfSteps..halfSteps) {
            if (i == 0) continue // Main axes drawn separately in RGB
            val coord = i * safeStep
            val isMajor = (i % 5 == 0)
            val color = if (isMajor) palette.gridMajor.copy(alpha = 0.72f) else palette.gridMinor.copy(alpha = 0.55f)
            val width = if (isMajor) 1.6f else 1.0f

            // Line parallel to Y
            CadMath.clipAndProjectSegment(Vec3(coord, -extent, 0f), Vec3(coord, extent, 0f), basis)
                ?.let { (a, b) -> drawLine(color = color, start = a, end = b, strokeWidth = width) }

            // Line parallel to X
            CadMath.clipAndProjectSegment(Vec3(-extent, coord, 0f), Vec3(extent, coord, 0f), basis)
                ?.let { (a, b) -> drawLine(color = color, start = a, end = b, strokeWidth = width) }
        }
    }

    private fun DrawScope.drawCartesianAxes(basis: CameraBasis, palette: CadViewportPalette) {
        val axisExtent = 14f
        val zExtent = 9f

        // Positive solid rays
        drawAxisSegment(Vec3.ZERO, Vec3(axisExtent, 0f, 0f), AxisRed, dashed = false, basis = basis)
        drawAxisSegment(Vec3.ZERO, Vec3(0f, axisExtent, 0f), AxisGreen, dashed = false, basis = basis)
        drawAxisSegment(Vec3.ZERO, Vec3(0f, 0f, zExtent), AxisBlue, dashed = false, basis = basis)

        // Negative dashed rays (classic SketchUp convention)
        drawAxisSegment(Vec3.ZERO, Vec3(-axisExtent, 0f, 0f), AxisRed.copy(alpha = 0.58f), dashed = true, basis = basis)
        drawAxisSegment(Vec3.ZERO, Vec3(0f, -axisExtent, 0f), AxisGreen.copy(alpha = 0.58f), dashed = true, basis = basis)
        drawAxisSegment(Vec3.ZERO, Vec3(0f, 0f, -5f), AxisBlue.copy(alpha = 0.52f), dashed = true, basis = basis)

        // Origin marker dot
        val originProj = CadMath.project(Vec3.ZERO, basis)
        if (originProj.inFront) {
            drawCircle(color = palette.edgeStroke, radius = 4f, center = originProj.screen)
        }

        // Crisp axis labels at 6m mark
        drawAxisLabel(Vec3(6.2f, 0f, 0.1f), "X", AxisRed, basis)
        drawAxisLabel(Vec3(0f, 6.2f, 0.1f), "Y", AxisGreen, basis)
        drawAxisLabel(Vec3(0f, 0f, 4.6f), "Z", AxisBlue, basis)
    }

    private fun DrawScope.drawAxisSegment(
        start: Vec3,
        end: Vec3,
        color: Color,
        dashed: Boolean,
        basis: CameraBasis
    ) {
        val seg = CadMath.clipAndProjectSegment(start, end, basis) ?: return
        drawLine(
            color = color,
            start = seg.first,
            end = seg.second,
            strokeWidth = if (dashed) 2.0f else 2.8f,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) dashedAxisEffect else null
        )
    }

    private fun DrawScope.drawAxisLabel(
        worldPos: Vec3,
        label: String,
        color: Color,
        basis: CameraBasis
    ) {
        val proj = CadMath.project(worldPos, basis)
        if (!proj.inFront) return
        if (proj.screen.x !in 24f..(size.width - 24f) || proj.screen.y !in 24f..(size.height - 24f)) return

        drawCircle(color = color, radius = 14f, center = proj.screen)
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = 20f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(label, proj.screen.x, proj.screen.y + 7f, paint)
        }
    }

    private data class ProjectedFaceItem(
        val face: CadFace,
        val screenPoints: List<Offset>,
        val avgDepth: Float,
        val isTranslucent: Boolean
    )

    private fun DrawScope.drawSceneFaces(
        state: CadWorkspaceUiState,
        basis: CameraBasis,
        palette: CadViewportPalette
    ) {
        val projectedFaces = state.scene.faces.mapNotNull { face ->
            if (face.vertices.size < 3) return@mapNotNull null
            val screenPts = CadMath.clipAndProjectPolygon(face.vertices, basis)
            if (screenPts.size < 3) return@mapNotNull null
            val centroidView = CadMath.worldToView(face.centroid(), basis)
            // Slight depth pull for standalone inset faces (windows/sub-rects) so they render cleanly on walls
            val depthBias = if (face.volumeId == null) -0.04f else 0f
            ProjectedFaceItem(
                face = face,
                screenPoints = screenPts,
                avgDepth = centroidView.z + depthBias,
                isTranslucent = face.material.alpha < 0.99f
            )
        }

        // Draw opaque faces far-to-near, then translucent glass faces far-to-near
        val sorted = projectedFaces.sortedWith(
            compareBy<ProjectedFaceItem> { it.isTranslucent }
                .thenByDescending { it.avgDepth }
        )

        for (item in sorted) {
            val face = item.face
            val path = Path().apply {
                moveTo(item.screenPoints[0].x, item.screenPoints[0].y)
                for (i in 1 until item.screenPoints.size) {
                    lineTo(item.screenPoints[i].x, item.screenPoints[i].y)
                }
                close()
            }

            val shadedFill = shadeMaterialColor(face, palette.isDark)
            drawPath(path = path, color = shadedFill)

            // Architectural procedural surface details (Wood planks, Concrete seams, Glass sheen)
            drawMaterialDetailLines(face, basis, shadedFill)

            val isSelected = (face.id == state.selectedFaceId) ||
                (state.selectedVolumeId != null && face.volumeId == state.selectedVolumeId)

            if (isSelected) {
                drawPath(
                    path = path,
                    color = palette.selectionAccent.copy(alpha = 0.24f)
                )
            }

            val strokeColor = if (isSelected) palette.selectionAccent else palette.edgeStroke
            val strokeWidth = if (isSelected) 4.2f else 2.2f
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = strokeWidth)
            )
        }
    }

    private fun shadeMaterialColor(face: CadFace, isDarkTheme: Boolean): Color {
        val base = face.material.baseColor
        val n = face.normal.normalized()
        // Lambertian directional sunlight + soft ambient hemisphere light
        val ndotl = abs(n.dot(sunDirection))
        val topBoost = if (n.z > 0.5f) 0.11f else if (n.z < -0.5f) -0.14f else 0f
        val lightFactor = (0.64f + 0.34f * ndotl + topBoost).coerceIn(0.45f, 1.06f)
        val themeMod = if (isDarkTheme) 0.90f else 1.0f
        return Color(
            red = (base.red * lightFactor * themeMod).coerceIn(0f, 1f),
            green = (base.green * lightFactor * themeMod).coerceIn(0f, 1f),
            blue = (base.blue * lightFactor * themeMod).coerceIn(0f, 1f),
            alpha = face.material.alpha
        )
    }

    private fun DrawScope.drawMaterialDetailLines(
        face: CadFace,
        basis: CameraBasis,
        shadedFill: Color
    ) {
        if (face.vertices.size != 4) return
        val v0 = face.vertices[0]
        val v1 = face.vertices[1]
        val v2 = face.vertices[2]
        val v3 = face.vertices[3]

        when (face.material) {
            CadMaterial.WOOD -> {
                // Draw subtle architectural wood plank seams along the face
                val grainColor = Color(0xFF6B4226).copy(alpha = 0.28f)
                val planks = 5
                for (i in 1 until planks) {
                    val t = i.toFloat() / planks
                    val a = lerpVec3(v0, v3, t)
                    val b = lerpVec3(v1, v2, t)
                    CadMath.clipAndProjectSegment(a, b, basis)?.let { (sa, sb) ->
                        drawLine(color = grainColor, start = sa, end = sb, strokeWidth = 1.4f)
                    }
                }
            }

            CadMaterial.CONCRETE -> {
                // Subtle architectural concrete formwork seam cross-line
                val seamColor = Color(0xFF334155).copy(alpha = 0.22f)
                val midA = lerpVec3(v0, v1, 0.5f)
                val midB = lerpVec3(v3, v2, 0.5f)
                CadMath.clipAndProjectSegment(midA, midB, basis)?.let { (sa, sb) ->
                    drawLine(color = seamColor, start = sa, end = sb, strokeWidth = 1.2f)
                }
            }

            CadMaterial.GLASS -> {
                // Crisp diagonal specular reflection streaks on semi-transparent glass
                val sheenColor = Color.White.copy(alpha = 0.45f)
                val s1a = lerpVec3(v0, v1, 0.20f)
                val s1b = lerpVec3(v3, v2, 0.55f)
                val s2a = lerpVec3(v0, v1, 0.45f)
                val s2b = lerpVec3(v3, v2, 0.80f)
                CadMath.clipAndProjectSegment(s1a, s1b, basis)?.let { (sa, sb) ->
                    drawLine(color = sheenColor, start = sa, end = sb, strokeWidth = 2.6f, cap = StrokeCap.Round)
                }
                CadMath.clipAndProjectSegment(s2a, s2b, basis)?.let { (sa, sb) ->
                    drawLine(color = sheenColor.copy(alpha = 0.30f), start = sa, end = sb, strokeWidth = 1.8f, cap = StrokeCap.Round)
                }
            }

            CadMaterial.MATTE_WHITE -> Unit
        }
    }

    private fun DrawScope.drawLooseEdges(
        state: CadWorkspaceUiState,
        basis: CameraBasis,
        palette: CadViewportPalette
    ) {
        for (edge in state.scene.edges) {
            val seg = CadMath.clipAndProjectSegment(edge.start, edge.end, basis) ?: continue
            val isSelected = edge.id == state.selectedEdgeId
            drawLine(
                color = if (isSelected) palette.selectionAccent else palette.edgeStroke,
                start = seg.first,
                end = seg.second,
                strokeWidth = if (isSelected) 5.0f else 3.2f,
                cap = StrokeCap.Round
            )
            // Endpoint dots so user can easily snap and close planar loops
            drawCircle(
                color = if (isSelected) palette.selectionAccent else AxisGreen,
                radius = 4.5f,
                center = seg.first
            )
            drawCircle(
                color = if (isSelected) palette.selectionAccent else AxisGreen,
                radius = 4.5f,
                center = seg.second
            )
        }
    }

    private fun DrawScope.drawMeasurements(
        state: CadWorkspaceUiState,
        basis: CameraBasis,
        palette: CadViewportPalette
    ) {
        for (m in state.scene.measurements) {
            drawDimensionLine(m.start, m.end, m.distanceMeters(), basis, palette, isPreview = false)
        }
    }

    private fun DrawScope.drawDimensionLine(
        start: Vec3,
        end: Vec3,
        distanceMeters: Float,
        basis: CameraBasis,
        palette: CadViewportPalette,
        isPreview: Boolean
    ) {
        val seg = CadMath.clipAndProjectSegment(start, end, basis) ?: return
        val lineColor = if (isPreview) SelectionAmber else palette.selectionAccent
        drawLine(
            color = lineColor,
            start = seg.first,
            end = seg.second,
            strokeWidth = 3.0f,
            pathEffect = dashedMeasureEffect,
            cap = StrokeCap.Round
        )
        drawCircle(color = lineColor, radius = 6f, center = seg.first)
        drawCircle(color = lineColor, radius = 6f, center = seg.second)

        val mid = Offset((seg.first.x + seg.second.x) * 0.5f, (seg.first.y + seg.second.y) * 0.5f - 18f)
        drawFloatingPillLabel(mid, CadMath.formatMeters(distanceMeters), lineColor, Color.White)
    }

    private fun DrawScope.drawActiveInteractionOverlay(
        state: CadWorkspaceUiState,
        basis: CameraBasis,
        palette: CadViewportPalette
    ) {
        when (val active = state.interaction) {
            is ActiveInteraction.DrawingRectangle -> {
                val screenPts = CadMath.clipAndProjectPolygon(active.previewCorners, basis)
                if (screenPts.size >= 3) {
                    val path = Path().apply {
                        moveTo(screenPts[0].x, screenPts[0].y)
                        for (i in 1 until screenPts.size) lineTo(screenPts[i].x, screenPts[i].y)
                        close()
                    }
                    drawPath(path = path, color = SelectionCyan.copy(alpha = 0.24f))
                    drawPath(path = path, color = palette.selectionAccent, style = Stroke(width = 3.4f))

                    val center = CadMath.project(CadMath.polygonCentroid(active.previewCorners), basis)
                    if (center.inFront) {
                        val label = String.format(
                            java.util.Locale.US,
                            "%.2f × %.2f m",
                            abs(active.width),
                            abs(active.depth)
                        )
                        drawFloatingPillLabel(center.screen, label, palette.selectionAccent, Color.White)
                    }
                }
                active.snapResult?.let { drawSnapMarker(it, basis) }
            }

            is ActiveInteraction.DrawingLine -> {
                val color = axisLockColor(active.axisLock, palette.edgeStroke)
                // Draw extended dashed axis inference guide when locked to X, Y, or Z
                if (active.axisLock != AxisLock.NONE) {
                    val dir = when (active.axisLock) {
                        AxisLock.X -> Vec3.UNIT_X
                        AxisLock.Y -> Vec3.UNIT_Y
                        AxisLock.Z -> Vec3.UNIT_Z
                        AxisLock.NONE -> Vec3.UNIT_X
                    }
                    CadMath.clipAndProjectSegment(active.start - dir * 8f, active.start + dir * 8f, basis)
                        ?.let { (ga, gb) ->
                            drawLine(
                                color = color.copy(alpha = 0.45f),
                                start = ga,
                                end = gb,
                                strokeWidth = 2.0f,
                                pathEffect = dashedGuideEffect
                            )
                        }
                }
                CadMath.clipAndProjectSegment(active.start, active.end, basis)?.let { (a, b) ->
                    drawLine(color = color, start = a, end = b, strokeWidth = 4.2f, cap = StrokeCap.Round)
                    val mid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f - 20f)
                    val dist = active.start.distanceTo(active.end)
                    if (dist > 0.04f) {
                        drawFloatingPillLabel(mid, CadMath.formatMeters(dist), color, Color.White)
                    }
                }
                active.snapResult?.let { drawSnapMarker(it, basis) }
            }

            is ActiveInteraction.PushingPulling -> {
                val movedCenter = active.sourceFace.centroid() + active.sourceFace.normal.normalized() * active.currentDistance
                val arrowTip = movedCenter + active.sourceFace.normal.normalized() * 0.9f
                CadMath.clipAndProjectSegment(movedCenter, arrowTip, basis)?.let { (a, b) ->
                    drawLine(color = SelectionAmber, start = a, end = b, strokeWidth = 4.5f, cap = StrokeCap.Round)
                    drawCircle(color = SelectionAmber, radius = 7f, center = b)
                    drawFloatingPillLabel(
                        Offset(b.x, b.y - 22f),
                        CadMath.formatMeters(active.currentDistance),
                        SelectionAmber,
                        Color(0xFF111827)
                    )
                }
            }

            is ActiveInteraction.Measuring -> {
                val dist = active.start.distanceTo(active.end)
                drawDimensionLine(active.start, active.end, dist, basis, palette, isPreview = true)
                active.snapResult?.let { drawSnapMarker(it, basis) }
            }

            ActiveInteraction.Idle -> Unit
        }
    }

    private fun DrawScope.drawSnapMarker(snap: SnapResult, basis: CameraBasis) {
        val proj = CadMath.project(snap.point, basis)
        if (!proj.inFront) return
        val markerColor = when (snap.snapType) {
            SnapType.VERTEX -> AxisGreen
            SnapType.GRID -> SelectionCyan
            SnapType.FACE -> AxisBlue
            SnapType.NONE -> SelectionAmber
        }
        drawCircle(color = Color.White, radius = 11f, center = proj.screen)
        drawCircle(color = markerColor, radius = 7.5f, center = proj.screen)
    }

    private fun DrawScope.drawFloatingPillLabel(
        center: Offset,
        text: String,
        bgColor: Color,
        textColor: Color
    ) {
        drawIntoCanvas { canvas ->
            val textPaint = Paint().apply {
                color = textColor.toArgb()
                textSize = 26f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                isAntiAlias = true
            }
            val textWidth = textPaint.measureText(text)
            val padX = 16f
            val padY = 10f
            val rect = android.graphics.RectF(
                center.x - textWidth * 0.5f - padX,
                center.y - 22f - padY,
                center.x + textWidth * 0.5f + padX,
                center.y + 8f + padY
            )
            val bgPaint = Paint().apply {
                color = bgColor.toArgb()
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawRoundRect(rect, 18f, 18f, bgPaint)
            canvas.nativeCanvas.drawText(text, center.x, center.y + 4f, textPaint)
        }
    }

    private fun axisLockColor(lock: AxisLock, fallback: Color): Color {
        return when (lock) {
            AxisLock.X -> AxisRed
            AxisLock.Y -> AxisGreen
            AxisLock.Z -> AxisBlue
            AxisLock.NONE -> fallback
        }
    }

    private fun lerpVec3(a: Vec3, b: Vec3, t: Float): Vec3 {
        return Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        )
    }
}
