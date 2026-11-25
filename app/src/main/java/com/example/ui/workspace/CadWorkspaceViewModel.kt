package com.example.ui.workspace

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.cad.export.CadModelExporter
import com.example.cad.model.AxisLock
import com.example.cad.model.CadFace
import com.example.cad.model.CadGeometryEngine
import com.example.cad.model.CadMaterial
import com.example.cad.model.CadMath
import com.example.cad.model.CadMeasurement
import com.example.cad.model.CadScene
import com.example.cad.model.CadTool
import com.example.cad.model.CameraBasis
import com.example.cad.model.CameraPreset
import com.example.cad.model.CameraState
import com.example.cad.model.LastPrecisionOperation
import com.example.cad.model.Plane3D
import com.example.cad.model.SampleSceneFactory
import com.example.cad.model.SnapResult
import com.example.cad.model.SnapType
import com.example.cad.model.Vec3
import com.example.data.ProjectEntity
import com.example.data.ProjectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class ExportFormat(val extension: String, val mimeType: String) {
    OBJ("obj", "text/plain"),
    GLTF("gltf", "model/gltf+json")
}

sealed class ActiveInteraction {
    data object Idle : ActiveInteraction()

    data class DrawingRectangle(
        val origin: Vec3,
        val uAxis: Vec3,
        val vAxis: Vec3,
        val normal: Vec3,
        val width: Float,
        val depth: Float,
        val previewCorners: List<Vec3>,
        val snapResult: SnapResult?
    ) : ActiveInteraction()

    data class DrawingLine(
        val start: Vec3,
        val end: Vec3,
        val axisLock: AxisLock,
        val snapResult: SnapResult?
    ) : ActiveInteraction()

    data class PushingPulling(
        val baseScene: CadScene,
        val sourceFace: CadFace,
        val startScreen: Offset,
        val screenNormalDir: Offset,
        val pixelsPerMeter: Float,
        val currentDistance: Float
    ) : ActiveInteraction()

    data class Measuring(
        val start: Vec3,
        val end: Vec3,
        val axisLock: AxisLock,
        val snapResult: SnapResult?
    ) : ActiveInteraction()
}

data class CadWorkspaceUiState(
    val scene: CadScene = SampleSceneFactory.createSampleScene(),
    val camera: CameraState = CameraState(),
    val activeTool: CadTool = CadTool.ORBIT,
    val activeMaterial: CadMaterial = CadMaterial.CONCRETE,
    val paintWholeVolume: Boolean = false,
    val selectedFaceId: String? = null,
    val selectedVolumeId: String? = null,
    val selectedEdgeId: String? = null,
    val interaction: ActiveInteraction = ActiveInteraction.Idle,
    val vcbDisplayText: String = "0.00 m",
    val vcbLabel: String = "Medidas",
    val vcbInputOpen: Boolean = false,
    val vcbInputDraft: String = "",
    val lastPrecisionOp: LastPrecisionOperation? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val darkTheme: Boolean = false,
    val gridSpacingMeters: Float = 1.0f,
    val showGrid: Boolean = true,
    val snapToGrid: Boolean = true,
    val currentProjectId: Long? = null,
    val currentProjectName: String = "Pabellón Inicial",
    val snackbarMessage: String? = null
)

class CadWorkspaceViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CadWorkspaceUiState())
    val uiState: StateFlow<CadWorkspaceUiState> = _uiState.asStateFlow()

    val savedProjects: StateFlow<List<ProjectEntity>> = repository.projects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val undoStack = ArrayDeque<CadScene>()
    private val redoStack = ArrayDeque<CadScene>()
    private val maxHistorySteps = 40

    init {
        viewModelScope.launch {
            repository.preferences.collect { prefs ->
                _uiState.update { state ->
                    state.copy(
                        darkTheme = prefs.darkTheme,
                        gridSpacingMeters = prefs.gridSpacingMeters,
                        showGrid = prefs.showGrid,
                        snapToGrid = prefs.snapToGrid
                    )
                }
            }
        }
    }

    private fun pushHistory(previousScene: CadScene) {
        undoStack.addLast(previousScene)
        while (undoStack.size > maxHistorySteps) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        syncHistoryFlags()
    }

    private fun syncHistoryFlags() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun selectTool(tool: CadTool) {
        _uiState.update {
            it.copy(
                activeTool = tool,
                interaction = ActiveInteraction.Idle,
                vcbInputOpen = false
            )
        }
    }

    fun selectMaterial(material: CadMaterial) {
        _uiState.update { it.copy(activeMaterial = material) }
    }

    fun setPaintWholeVolume(wholeVolume: Boolean) {
        _uiState.update { it.copy(paintWholeVolume = wholeVolume) }
    }

    fun toggleTheme() {
        val next = !_uiState.value.darkTheme
        _uiState.update { it.copy(darkTheme = next) }
        viewModelScope.launch { repository.setDarkTheme(next) }
    }

    fun updateGridSettings(spacingMeters: Float, showGrid: Boolean, snapToGrid: Boolean) {
        _uiState.update {
            it.copy(
                gridSpacingMeters = spacingMeters,
                showGrid = showGrid,
                snapToGrid = snapToGrid
            )
        }
        viewModelScope.launch {
            repository.setGridSettings(spacingMeters, showGrid, snapToGrid)
        }
    }

    fun applyCameraPreset(preset: CameraPreset) {
        val target = _uiState.value.camera.target
        _uiState.update {
            it.copy(camera = CameraState.forPreset(preset, target))
        }
    }

    fun resetCamera() {
        _uiState.update {
            it.copy(camera = CameraState())
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.scene
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        _uiState.update {
            it.copy(
                scene = previous,
                interaction = ActiveInteraction.Idle,
                lastPrecisionOp = null,
                selectedFaceId = null,
                selectedVolumeId = null,
                selectedEdgeId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.scene
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _uiState.update {
            it.copy(
                scene = next,
                interaction = ActiveInteraction.Idle,
                lastPrecisionOp = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // ---- Camera Navigation (Orbit + 2-finger Zoom/Pan) ----

    fun onOrbitDrag(deltaX: Float, deltaY: Float) {
        _uiState.update { state ->
            val cam = state.camera
            val newAzimuth = (cam.azimuthDeg - deltaX * 0.34f) % 360f
            val newElevation = (cam.elevationDeg + deltaY * 0.28f).coerceIn(2f, 89f)
            state.copy(
                camera = cam.copy(
                    azimuthDeg = newAzimuth,
                    elevationDeg = newElevation
                )
            )
        }
    }

    fun onTwoFingerZoomPan(
        zoomRatio: Float,
        panDelta: Offset,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        _uiState.update { state ->
            val cam = state.camera
            val clampedRatio = zoomRatio.coerceIn(0.82f, 1.22f)
            val newDist = (cam.distance / clampedRatio).coerceIn(2.2f, 45f)

            val basis = CadMath.buildCameraBasis(cam, viewportWidth, viewportHeight)
            val metersPerPixel = (cam.distance / max(1f, basis.focalLength))
            val worldShift = (basis.right * (-panDelta.x * metersPerPixel)) +
                (basis.up * (panDelta.y * metersPerPixel))
            val newTarget = Vec3(
                x = (cam.target.x + worldShift.x).coerceIn(-25f, 25f),
                y = (cam.target.y + worldShift.y).coerceIn(-25f, 25f),
                z = (cam.target.z + worldShift.z).coerceIn(-2f, 20f)
            )
            state.copy(
                camera = cam.copy(
                    distance = newDist,
                    target = newTarget
                )
            )
        }
    }

    // ---- Single-finger Tap Handling (Select, Eraser, Paint) ----

    fun onSingleTap(
        screenPos: Offset,
        isDoubleTap: Boolean,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        val state = _uiState.value
        val basis = CadMath.buildCameraBasis(state.camera, viewportWidth, viewportHeight)
        val ray = CadMath.screenToRay(screenPos, basis)

        when (state.activeTool) {
            CadTool.SELECT -> {
                val faceHit = CadGeometryEngine.raycastFaces(ray, state.scene)
                if (faceHit != null) {
                    val volId = if (isDoubleTap) faceHit.face.volumeId else null
                    val info = if (volId != null) "Volumen" else "Cara"
                    _uiState.update {
                        it.copy(
                            selectedFaceId = faceHit.face.id,
                            selectedVolumeId = volId,
                            selectedEdgeId = null,
                            vcbLabel = info,
                            vcbDisplayText = "${faceHit.face.vertices.size} vértices"
                        )
                    }
                } else {
                    val edgeHit = CadGeometryEngine.pickLooseEdge(screenPos, state.scene, basis)
                    if (edgeHit != null) {
                        _uiState.update {
                            it.copy(
                                selectedFaceId = null,
                                selectedVolumeId = null,
                                selectedEdgeId = edgeHit.id,
                                vcbLabel = "Arista",
                                vcbDisplayText = CadMath.formatMeters(edgeHit.length())
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                selectedFaceId = null,
                                selectedVolumeId = null,
                                selectedEdgeId = null
                            )
                        }
                    }
                }
            }

            CadTool.ERASER -> {
                val measureHit = CadGeometryEngine.pickMeasurement(screenPos, state.scene, basis)
                if (measureHit != null) {
                    pushHistory(state.scene)
                    _uiState.update {
                        it.copy(
                            scene = it.scene.copy(
                                measurements = it.scene.measurements.filterNot { m -> m.id == measureHit.id }
                            ),
                            snackbarMessage = "Cota eliminada"
                        )
                    }
                    return
                }
                val edgeHit = CadGeometryEngine.pickLooseEdge(screenPos, state.scene, basis)
                if (edgeHit != null) {
                    pushHistory(state.scene)
                    _uiState.update {
                        it.copy(
                            scene = it.scene.copy(
                                edges = it.scene.edges.filterNot { e -> e.id == edgeHit.id }
                            ),
                            selectedEdgeId = null,
                            snackbarMessage = "Arista eliminada"
                        )
                    }
                    return
                }
                val faceHit = CadGeometryEngine.raycastFaces(ray, state.scene)
                if (faceHit != null) {
                    pushHistory(state.scene)
                    val volId = faceHit.face.volumeId
                    val remainingFaces = if (volId != null) {
                        state.scene.faces.filterNot { it.volumeId == volId }
                    } else {
                        state.scene.faces.filterNot { it.id == faceHit.face.id }
                    }
                    _uiState.update {
                        it.copy(
                            scene = it.scene.copy(faces = remainingFaces),
                            selectedFaceId = null,
                            selectedVolumeId = null,
                            snackbarMessage = if (volId != null) "Volumen eliminado" else "Cara eliminada"
                        )
                    }
                }
            }

            CadTool.PAINT -> {
                val faceHit = CadGeometryEngine.raycastFaces(ray, state.scene) ?: return
                val targetMat = state.activeMaterial
                pushHistory(state.scene)
                val volId = faceHit.face.volumeId
                val updatedFaces = state.scene.faces.map { face ->
                    val shouldPaint = if (state.paintWholeVolume && volId != null) {
                        face.volumeId == volId
                    } else {
                        face.id == faceHit.face.id
                    }
                    if (shouldPaint) face.copy(material = targetMat) else face
                }
                _uiState.update {
                    it.copy(
                        scene = it.scene.copy(faces = updatedFaces),
                        selectedFaceId = faceHit.face.id,
                        vcbLabel = "Material",
                        vcbDisplayText = targetMat.displayNameEs
                    )
                }
            }

            else -> Unit
        }
    }

    // ---- Single-finger Drag Handling (Rectangle, Line, Push/Pull, Measure) ----

    fun onToolDragStart(screenPos: Offset, viewportWidth: Float, viewportHeight: Float) {
        val state = _uiState.value
        val basis = CadMath.buildCameraBasis(state.camera, viewportWidth, viewportHeight)
        val ray = CadMath.screenToRay(screenPos, basis)

        when (state.activeTool) {
            CadTool.RECTANGLE -> {
                val faceHit = CadGeometryEngine.raycastFaces(ray, state.scene)
                val (origin, uAxis, vAxis, normal, snapType) = if (faceHit != null) {
                    val n = faceHit.face.normal.normalized()
                    val (u, v) = CadMath.buildPlaneBasis(n)
                    val snappedHit = snapOnPlane(faceHit.hitPoint, faceHit.face.vertices[0], u, v, state)
                    // Offset slightly along normal to avoid z-fighting on existing faces
                    val offsetOrigin = snappedHit + n * 0.008f
                    Tuple5(offsetOrigin, u, v, n, if (state.snapToGrid) SnapType.GRID else SnapType.FACE)
                } else {
                    val groundPt = CadMath.intersectRayPlane(ray, Plane3D(Vec3.ZERO, Vec3.UNIT_Z))
                        ?: return
                    val snap = resolveGroundOrVertexSnap(screenPos, groundPt, basis, state)
                    Tuple5(snap.point, Vec3.UNIT_X, Vec3.UNIT_Y, Vec3.UNIT_Z, snap.snapType)
                }

                _uiState.update {
                    it.copy(
                        interaction = ActiveInteraction.DrawingRectangle(
                            origin = origin,
                            uAxis = uAxis,
                            vAxis = vAxis,
                            normal = normal,
                            width = 0f,
                            depth = 0f,
                            previewCorners = listOf(origin, origin, origin, origin),
                            snapResult = SnapResult(origin, snapType)
                        ),
                        vcbLabel = "Rectángulo",
                        vcbDisplayText = "0.00 × 0.00 m"
                    )
                }
            }

            CadTool.LINE -> {
                val startSnap = resolvePoint3D(screenPos, basis, state) ?: return
                _uiState.update {
                    it.copy(
                        interaction = ActiveInteraction.DrawingLine(
                            start = startSnap.point,
                            end = startSnap.point,
                            axisLock = AxisLock.NONE,
                            snapResult = startSnap
                        ),
                        vcbLabel = "Línea",
                        vcbDisplayText = "0.00 m"
                    )
                }
            }

            CadTool.PUSH_PULL -> {
                val faceHit = CadGeometryEngine.raycastFaces(ray, state.scene) ?: return
                val face = faceHit.face
                val centroid = face.centroid()
                val p0 = CadMath.project(centroid, basis)
                val p1 = CadMath.project(centroid + face.normal.normalized(), basis)
                if (!p0.inFront || !p1.inFront) return

                val screenDelta = p1.screen - p0.screen
                val pxPerMeter = max(24f, screenDelta.getDistance())
                val screenDir = if (screenDelta.getDistance() > 4f) {
                    Offset(screenDelta.x / screenDelta.getDistance(), screenDelta.y / screenDelta.getDistance())
                } else {
                    // Fallback when looking nearly head-on at a face: drag upward pulls outward
                    Offset(0f, -1f)
                }

                _uiState.update {
                    it.copy(
                        selectedFaceId = face.id,
                        selectedVolumeId = null,
                        interaction = ActiveInteraction.PushingPulling(
                            baseScene = state.scene,
                            sourceFace = face,
                            startScreen = screenPos,
                            screenNormalDir = screenDir,
                            pixelsPerMeter = pxPerMeter,
                            currentDistance = 0f
                        ),
                        vcbLabel = "Empujar/Tirar",
                        vcbDisplayText = "0.00 m"
                    )
                }
            }

            CadTool.MEASURE -> {
                val startSnap = resolvePoint3D(screenPos, basis, state) ?: return
                _uiState.update {
                    it.copy(
                        interaction = ActiveInteraction.Measuring(
                            start = startSnap.point,
                            end = startSnap.point,
                            axisLock = AxisLock.NONE,
                            snapResult = startSnap
                        ),
                        vcbLabel = "Distancia",
                        vcbDisplayText = "0.00 m"
                    )
                }
            }

            else -> Unit
        }
    }

    fun onToolDragMove(screenPos: Offset, viewportWidth: Float, viewportHeight: Float) {
        val state = _uiState.value
        val basis = CadMath.buildCameraBasis(state.camera, viewportWidth, viewportHeight)
        val ray = CadMath.screenToRay(screenPos, basis)

        when (val active = state.interaction) {
            is ActiveInteraction.DrawingRectangle -> {
                val plane = Plane3D(active.origin, active.normal)
                val hit = CadMath.intersectRayPlane(ray, plane) ?: return
                val rel = hit - active.origin
                val rawW = rel.dot(active.uAxis)
                val rawD = rel.dot(active.vAxis)
                val step = if (state.snapToGrid) state.gridSpacingMeters * 0.5f else 0.05f
                val w = CadMath.snapToGrid(rawW, step)
                val d = CadMath.snapToGrid(rawD, step)

                val p0 = active.origin
                val p1 = active.origin + active.uAxis * w
                val p2 = active.origin + active.uAxis * w + active.vAxis * d
                val p3 = active.origin + active.vAxis * d

                _uiState.update {
                    it.copy(
                        interaction = active.copy(
                            width = w,
                            depth = d,
                            previewCorners = listOf(p0, p1, p2, p3),
                            snapResult = SnapResult(p2, if (state.snapToGrid) SnapType.GRID else SnapType.NONE)
                        ),
                        vcbLabel = "Rectángulo",
                        vcbDisplayText = String.format(
                            java.util.Locale.US,
                            "%.2f × %.2f m",
                            abs(w),
                            abs(d)
                        )
                    )
                }
            }

            is ActiveInteraction.DrawingLine -> {
                val resolved = resolveLineEndPoint(screenPos, active.start, basis, state)
                val len = active.start.distanceTo(resolved.point)
                _uiState.update {
                    it.copy(
                        interaction = active.copy(
                            end = resolved.point,
                            axisLock = resolved.axisLock,
                            snapResult = resolved
                        ),
                        vcbLabel = axisLabel("Línea", resolved.axisLock),
                        vcbDisplayText = CadMath.formatMeters(len)
                    )
                }
            }

            is ActiveInteraction.PushingPulling -> {
                val deltaPx = screenPos - active.startScreen
                val projPx = deltaPx.x * active.screenNormalDir.x + deltaPx.y * active.screenNormalDir.y
                val rawDist = projPx / active.pixelsPerMeter
                val step = if (state.snapToGrid) 0.10f else 0.02f
                val snappedDist = (round(rawDist / step) * step).coerceIn(-15f, 20f)

                val ppResult = CadGeometryEngine.applyPushPull(
                    baseScene = active.baseScene,
                    sourceFace = active.sourceFace,
                    distance = snappedDist
                )

                _uiState.update {
                    it.copy(
                        scene = ppResult.scene,
                        selectedFaceId = ppResult.activeFaceId,
                        interaction = active.copy(currentDistance = snappedDist),
                        vcbLabel = "Empujar/Tirar",
                        vcbDisplayText = CadMath.formatMeters(snappedDist)
                    )
                }
            }

            is ActiveInteraction.Measuring -> {
                val resolved = resolveLineEndPoint(screenPos, active.start, basis, state)
                val dist = active.start.distanceTo(resolved.point)
                _uiState.update {
                    it.copy(
                        interaction = active.copy(
                            end = resolved.point,
                            axisLock = resolved.axisLock,
                            snapResult = resolved
                        ),
                        vcbLabel = axisLabel("Distancia", resolved.axisLock),
                        vcbDisplayText = CadMath.formatMeters(dist)
                    )
                }
            }

            ActiveInteraction.Idle -> Unit
        }
    }

    fun onToolDragEnd() {
        val state = _uiState.value
        when (val active = state.interaction) {
            is ActiveInteraction.DrawingRectangle -> {
                if (abs(active.width) >= 0.08f && abs(active.depth) >= 0.08f) {
                    pushHistory(state.scene)
                    val newFace = CadGeometryEngine.createRectangleFace(
                        origin = active.origin,
                        uAxis = active.uAxis,
                        vAxis = active.vAxis,
                        normal = active.normal,
                        width = active.width,
                        depth = active.depth,
                        material = CadMaterial.MATTE_WHITE
                    )
                    val op = LastPrecisionOperation.RectangleOp(
                        faceId = newFace.id,
                        origin = active.origin,
                        uAxis = active.uAxis,
                        vAxis = active.vAxis,
                        normal = active.normal,
                        width = active.width,
                        depth = active.depth,
                        material = newFace.material
                    )
                    _uiState.update {
                        it.copy(
                            scene = it.scene.copy(faces = it.scene.faces + newFace),
                            selectedFaceId = newFace.id,
                            selectedVolumeId = null,
                            interaction = ActiveInteraction.Idle,
                            lastPrecisionOp = op,
                            vcbLabel = "Rectángulo",
                            vcbDisplayText = String.format(
                                java.util.Locale.US,
                                "%.2f × %.2f m",
                                abs(active.width),
                                abs(active.depth)
                            )
                        )
                    }
                } else {
                    _uiState.update { it.copy(interaction = ActiveInteraction.Idle) }
                }
            }

            is ActiveInteraction.DrawingLine -> {
                val length = active.start.distanceTo(active.end)
                if (length >= 0.05f) {
                    pushHistory(state.scene)
                    val commit = CadGeometryEngine.addEdgeAndDetectClosedFace(
                        scene = state.scene,
                        start = active.start,
                        end = active.end,
                        defaultMaterial = CadMaterial.MATTE_WHITE
                    )
                    val op = commit.addedEdgeId?.let { eid ->
                        LastPrecisionOperation.LineOp(
                            edgeId = eid,
                            start = active.start,
                            direction = (active.end - active.start).normalized(),
                            currentLength = length
                        )
                    }
                    _uiState.update {
                        it.copy(
                            scene = commit.scene,
                            selectedFaceId = commit.createdFace?.id,
                            selectedEdgeId = commit.addedEdgeId,
                            interaction = ActiveInteraction.Idle,
                            lastPrecisionOp = op,
                            vcbLabel = if (commit.createdFace != null) "Cara cerrada" else "Línea",
                            vcbDisplayText = CadMath.formatMeters(length),
                            snackbarMessage = if (commit.createdFace != null) {
                                "Cara plana cerrada automáticamente — lista para Empujar/Tirar"
                            } else null
                        )
                    }
                } else {
                    _uiState.update { it.copy(interaction = ActiveInteraction.Idle) }
                }
            }

            is ActiveInteraction.PushingPulling -> {
                if (abs(active.currentDistance) >= 0.02f) {
                    pushHistory(active.baseScene)
                    val volId = active.sourceFace.volumeId ?: "vol_${active.sourceFace.id.take(8)}"
                    val op = LastPrecisionOperation.PushPullOp(
                        volumeId = volId,
                        movingFaceId = active.sourceFace.id,
                        baseSceneBeforeDrag = active.baseScene,
                        sourceFace = active.sourceFace,
                        currentDistance = active.currentDistance
                    )
                    _uiState.update {
                        it.copy(
                            interaction = ActiveInteraction.Idle,
                            lastPrecisionOp = op,
                            vcbLabel = "Empujar/Tirar",
                            vcbDisplayText = CadMath.formatMeters(active.currentDistance)
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            scene = active.baseScene,
                            interaction = ActiveInteraction.Idle
                        )
                    }
                }
            }

            is ActiveInteraction.Measuring -> {
                val dist = active.start.distanceTo(active.end)
                if (dist >= 0.04f) {
                    pushHistory(state.scene)
                    val m = CadMeasurement(start = active.start, end = active.end)
                    _uiState.update {
                        it.copy(
                            scene = it.scene.copy(measurements = it.scene.measurements + m),
                            interaction = ActiveInteraction.Idle,
                            vcbLabel = "Medida",
                            vcbDisplayText = CadMath.formatMeters(dist)
                        )
                    }
                } else {
                    _uiState.update { it.copy(interaction = ActiveInteraction.Idle) }
                }
            }

            ActiveInteraction.Idle -> Unit
        }
    }

    fun cancelActiveInteraction() {
        val state = _uiState.value
        val restoredScene = when (val active = state.interaction) {
            is ActiveInteraction.PushingPulling -> active.baseScene
            else -> state.scene
        }
        _uiState.update {
            it.copy(
                scene = restoredScene,
                interaction = ActiveInteraction.Idle
            )
        }
    }

    // ---- VCB (Value Control Box) Exact Numeric Precision ----

    fun openVcbKeypad() {
        val prefill = when (val op = _uiState.value.lastPrecisionOp) {
            is LastPrecisionOperation.PushPullOp -> String.format(java.util.Locale.US, "%.2f", op.currentDistance)
            is LastPrecisionOperation.RectangleOp -> String.format(
                java.util.Locale.US,
                "%.2f, %.2f",
                abs(op.width),
                abs(op.depth)
            )
            is LastPrecisionOperation.LineOp -> String.format(java.util.Locale.US, "%.2f", op.currentLength)
            null -> ""
        }
        _uiState.update {
            it.copy(vcbInputOpen = true, vcbInputDraft = prefill)
        }
    }

    fun closeVcbKeypad() {
        _uiState.update { it.copy(vcbInputOpen = false) }
    }

    fun updateVcbDraft(draft: String) {
        _uiState.update { it.copy(vcbInputDraft = draft) }
    }

    fun applyVcbInput(rawInput: String) {
        val clean = rawInput.trim()
            .lowercase()
            .replace("m", "")
            .replace("×", ",")
            .replace("x", ",")
            .replace(";", ",")
            .trim()
        val parts = clean.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (parts.isEmpty()) {
            _uiState.update {
                it.copy(vcbInputOpen = false, snackbarMessage = "Formato inválido. Usa ej. 3.50 o 4.0, 2.5")
            }
            return
        }

        val state = _uiState.value
        when (val op = state.lastPrecisionOp) {
            is LastPrecisionOperation.PushPullOp -> {
                val exactDist = parts[0].coerceIn(-25f, 30f)
                val ppResult = CadGeometryEngine.applyPushPull(
                    baseScene = op.baseSceneBeforeDrag,
                    sourceFace = op.sourceFace,
                    distance = exactDist
                )
                _uiState.update {
                    it.copy(
                        scene = ppResult.scene,
                        lastPrecisionOp = op.copy(currentDistance = exactDist),
                        vcbDisplayText = CadMath.formatMeters(exactDist),
                        vcbInputOpen = false,
                        snackbarMessage = "Extrusión ajustada a ${CadMath.formatMeters(exactDist)}"
                    )
                }
            }

            is LastPrecisionOperation.RectangleOp -> {
                val newW = (parts[0].coerceIn(0.05f, 40f)) * (if (op.width < 0f) -1f else 1f)
                val newD = ((parts.getOrNull(1) ?: parts[0]).coerceIn(0.05f, 40f)) *
                    (if (op.depth < 0f) -1f else 1f)
                val resized = CadGeometryEngine.createRectangleFace(
                    origin = op.origin,
                    uAxis = op.uAxis,
                    vAxis = op.vAxis,
                    normal = op.normal,
                    width = newW,
                    depth = newD,
                    material = op.material,
                    id = op.faceId
                )
                val updatedFaces = state.scene.faces.map { if (it.id == op.faceId) resized else it }
                _uiState.update {
                    it.copy(
                        scene = it.scene.copy(faces = updatedFaces),
                        lastPrecisionOp = op.copy(width = newW, depth = newD),
                        vcbDisplayText = String.format(
                            java.util.Locale.US,
                            "%.2f × %.2f m",
                            abs(newW),
                            abs(newD)
                        ),
                        vcbInputOpen = false,
                        snackbarMessage = "Rectángulo ajustado a ${String.format(java.util.Locale.US, "%.2f × %.2f m", abs(newW), abs(newD))}"
                    )
                }
            }

            is LastPrecisionOperation.LineOp -> {
                val exactLen = parts[0].coerceIn(0.05f, 40f)
                val newEnd = op.start + op.direction * exactLen
                val baseWithoutEdge = state.scene.copy(
                    edges = state.scene.edges.filterNot { it.id == op.edgeId }
                )
                val commit = CadGeometryEngine.addEdgeAndDetectClosedFace(
                    scene = baseWithoutEdge,
                    start = op.start,
                    end = newEnd,
                    defaultMaterial = CadMaterial.MATTE_WHITE
                )
                val nextOp = commit.addedEdgeId?.let { newId ->
                    op.copy(edgeId = newId, currentLength = exactLen)
                }
                _uiState.update {
                    it.copy(
                        scene = commit.scene,
                        lastPrecisionOp = nextOp,
                        vcbDisplayText = CadMath.formatMeters(exactLen),
                        vcbInputOpen = false,
                        snackbarMessage = "Línea ajustada a ${CadMath.formatMeters(exactLen)}"
                    )
                }
            }

            null -> {
                // If no previous op, and user typed 2 numbers, create a centered rectangle right away!
                if (parts.size >= 2) {
                    val w = parts[0].coerceIn(0.1f, 30f)
                    val d = parts[1].coerceIn(0.1f, 30f)
                    pushHistory(state.scene)
                    val origin = Vec3(-w * 0.5f, -d * 0.5f, 0f)
                    val rect = CadGeometryEngine.createRectangleFace(
                        origin = origin,
                        uAxis = Vec3.UNIT_X,
                        vAxis = Vec3.UNIT_Y,
                        normal = Vec3.UNIT_Z,
                        width = w,
                        depth = d
                    )
                    _uiState.update {
                        it.copy(
                            scene = it.scene.copy(faces = it.scene.faces + rect),
                            selectedFaceId = rect.id,
                            lastPrecisionOp = LastPrecisionOperation.RectangleOp(
                                faceId = rect.id,
                                origin = origin,
                                uAxis = Vec3.UNIT_X,
                                vAxis = Vec3.UNIT_Y,
                                normal = Vec3.UNIT_Z,
                                width = w,
                                depth = d,
                                material = rect.material
                            ),
                            vcbDisplayText = String.format(java.util.Locale.US, "%.2f × %.2f m", w, d),
                            vcbInputOpen = false,
                            snackbarMessage = "Rectángulo creado (${String.format(java.util.Locale.US, "%.2f × %.2f m", w, d)})"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            vcbInputOpen = false,
                            snackbarMessage = "Primero traza una línea, rectángulo o Empujar/Tirar"
                        )
                    }
                }
            }
        }
    }

    // ---- Local Room Project Persistence ----

    fun saveCurrentProject(projectName: String, saveAsNew: Boolean = false) {
        val state = _uiState.value
        val cleanName = projectName.trim().ifEmpty { state.currentProjectName }
        viewModelScope.launch {
            val savedId = repository.saveProject(
                existingId = if (saveAsNew) null else state.currentProjectId,
                name = cleanName,
                scene = state.scene,
                camera = state.camera,
                gridSpacingMeters = state.gridSpacingMeters
            )
            _uiState.update {
                it.copy(
                    currentProjectId = savedId,
                    currentProjectName = cleanName,
                    snackbarMessage = "Proyecto \"$cleanName\" guardado en el dispositivo"
                )
            }
        }
    }

    fun loadProject(projectId: Long) {
        viewModelScope.launch {
            val loaded = repository.loadProject(projectId) ?: return@launch
            val (entity, pair) = loaded
            undoStack.clear()
            redoStack.clear()
            _uiState.update {
                it.copy(
                    scene = pair.first,
                    camera = pair.second,
                    currentProjectId = entity.id,
                    currentProjectName = entity.name,
                    gridSpacingMeters = entity.gridSpacingMeters,
                    selectedFaceId = null,
                    selectedVolumeId = null,
                    selectedEdgeId = null,
                    interaction = ActiveInteraction.Idle,
                    lastPrecisionOp = null,
                    canUndo = false,
                    canRedo = false,
                    snackbarMessage = "Proyecto \"${entity.name}\" cargado"
                )
            }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            _uiState.update {
                it.copy(
                    currentProjectId = if (it.currentProjectId == projectId) null else it.currentProjectId,
                    snackbarMessage = "Proyecto eliminado"
                )
            }
        }
    }

    fun createNewProject(withSample: Boolean) {
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                scene = if (withSample) SampleSceneFactory.createSampleScene() else SampleSceneFactory.createEmptyScene(),
                camera = CameraState(),
                currentProjectId = null,
                currentProjectName = if (withSample) "Pabellón Inicial" else "Nuevo Modelo",
                selectedFaceId = null,
                selectedVolumeId = null,
                selectedEdgeId = null,
                interaction = ActiveInteraction.Idle,
                lastPrecisionOp = null,
                canUndo = false,
                canRedo = false,
                snackbarMessage = if (withSample) "Escena de ejemplo restaurada" else "Lienzo vacío listo"
            )
        }
    }

    // ---- Model Export (.OBJ & .GLTF) ----

    fun buildExportContent(format: ExportFormat): String {
        val state = _uiState.value
        return when (format) {
            ExportFormat.OBJ -> CadModelExporter.exportToObj(state.scene, state.currentProjectName)
            ExportFormat.GLTF -> CadModelExporter.exportToGltf(state.scene, state.currentProjectName)
        }
    }

    fun suggestedExportFileName(format: ExportFormat): String {
        val base = _uiState.value.currentProjectName
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_")
            .ifEmpty { "arqui_model" }
        return "$base.${format.extension}"
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ---- Snapping Helpers ----

    private fun snapOnPlane(
        point: Vec3,
        origin: Vec3,
        u: Vec3,
        v: Vec3,
        state: CadWorkspaceUiState
    ): Vec3 {
        if (!state.snapToGrid) return point
        val step = state.gridSpacingMeters * 0.5f
        val rel = point - origin
        val su = CadMath.snapToGrid(rel.dot(u), step)
        val sv = CadMath.snapToGrid(rel.dot(v), step)
        return origin + u * su + v * sv
    }

    private fun resolveGroundOrVertexSnap(
        screenPos: Offset,
        groundPt: Vec3,
        basis: CameraBasis,
        state: CadWorkspaceUiState
    ): SnapResult {
        val vertexSnap = CadGeometryEngine.findNearestVertexSnap(screenPos, state.scene, basis)
        if (vertexSnap != null && abs(vertexSnap.z) < 0.05f) {
            return SnapResult(Vec3(vertexSnap.x, vertexSnap.y, 0f), SnapType.VERTEX)
        }
        return if (state.snapToGrid) {
            val step = state.gridSpacingMeters * 0.5f
            SnapResult(CadMath.snapPointToGridXY(groundPt, step), SnapType.GRID)
        } else {
            SnapResult(groundPt, SnapType.NONE)
        }
    }

    private fun resolvePoint3D(
        screenPos: Offset,
        basis: CameraBasis,
        state: CadWorkspaceUiState
    ): SnapResult? {
        val vertexSnap = CadGeometryEngine.findNearestVertexSnap(screenPos, state.scene, basis)
        if (vertexSnap != null) {
            return SnapResult(vertexSnap, SnapType.VERTEX)
        }
        val ray = CadMath.screenToRay(screenPos, basis)
        val faceHit = CadGeometryEngine.raycastFaces(ray, state.scene)
        if (faceHit != null) {
            return SnapResult(faceHit.hitPoint, SnapType.FACE)
        }
        val groundHit = CadMath.intersectRayPlane(ray, Plane3D(Vec3.ZERO, Vec3.UNIT_Z)) ?: return null
        return if (state.snapToGrid) {
            val step = state.gridSpacingMeters * 0.5f
            SnapResult(CadMath.snapPointToGridXY(groundHit, step), SnapType.GRID)
        } else {
            SnapResult(groundHit, SnapType.NONE)
        }
    }

    private fun resolveLineEndPoint(
        screenPos: Offset,
        start: Vec3,
        basis: CameraBasis,
        state: CadWorkspaceUiState
    ): SnapResult {
        // 1. Vertex snap always takes top priority (makes closing faces effortless!)
        val vertexSnap = CadGeometryEngine.findNearestVertexSnap(screenPos, state.scene, basis, maxPixelRadius = 42f)
        if (vertexSnap != null && !vertexSnap.nearEquals(start, 0.04f)) {
            val (_, axis) = CadMath.applyAxisSnap(start, vertexSnap, angleThresholdDeg = 10f)
            return SnapResult(vertexSnap, SnapType.VERTEX, axis)
        }

        val ray = CadMath.screenToRay(screenPos, basis)

        // 2. Check if dragging predominantly vertically on screen -> allow Blue Z-axis vertical line!
        val startProj = CadMath.project(start, basis)
        val zUpProj = CadMath.project(start + Vec3.UNIT_Z, basis)
        if (startProj.inFront && zUpProj.inFront) {
            val zScreenVec = zUpProj.screen - startProj.screen
            val dragVec = screenPos - startProj.screen
            val zLen = zScreenVec.getDistance()
            val dLen = dragVec.getDistance()
            if (zLen > 8f && dLen > 24f) {
                val cosAngle = abs((dragVec.x * zScreenVec.x + dragVec.y * zScreenVec.y) / (dLen * zLen))
                if (cosAngle > 0.94f) {
                    // Intersect ray with vertical plane passing through start facing camera horizontally
                    val camHoriz = Vec3(basis.forward.x, basis.forward.y, 0f).normalized()
                    val vPlane = Plane3D(start, camHoriz)
                    val vHit = CadMath.intersectRayPlane(ray, vPlane)
                    if (vHit != null) {
                        val step = if (state.snapToGrid) state.gridSpacingMeters * 0.5f else 0.05f
                        val snappedZ = max(0f, CadMath.snapToGrid(vHit.z, step))
                        return SnapResult(
                            point = Vec3(start.x, start.y, snappedZ),
                            snapType = if (state.snapToGrid) SnapType.GRID else SnapType.NONE,
                            axisLock = AxisLock.Z
                        )
                    }
                }
            }
        }

        // 3. Otherwise project onto the horizontal plane at start.z
        val horizPlane = Plane3D(start, Vec3.UNIT_Z)
        val planeHit = CadMath.intersectRayPlane(ray, horizPlane) ?: start
        val rawPt = if (state.snapToGrid) {
            val step = state.gridSpacingMeters * 0.5f
            Vec3(CadMath.snapToGrid(planeHit.x, step), CadMath.snapToGrid(planeHit.y, step), start.z)
        } else {
            Vec3(planeHit.x, planeHit.y, start.z)
        }
        val (axisSnapped, lock) = CadMath.applyAxisSnap(start, rawPt, angleThresholdDeg = 16f)
        return SnapResult(
            point = axisSnapped,
            snapType = if (state.snapToGrid) SnapType.GRID else SnapType.NONE,
            axisLock = lock
        )
    }

    private fun axisLabel(prefix: String, axis: AxisLock): String {
        return when (axis) {
            AxisLock.X -> "$prefix · Eje X"
            AxisLock.Y -> "$prefix · Eje Y"
            AxisLock.Z -> "$prefix · Eje Z"
            AxisLock.NONE -> prefix
        }
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    class Factory(private val repository: ProjectRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CadWorkspaceViewModel(repository) as T
        }
    }
}
