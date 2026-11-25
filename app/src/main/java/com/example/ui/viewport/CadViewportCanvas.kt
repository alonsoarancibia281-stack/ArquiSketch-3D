package com.example.ui.viewport

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import com.example.cad.model.CadTool
import com.example.ui.theme.LocalViewportPalette
import com.example.ui.viewport.CadViewportRenderer.renderCadScene
import com.example.ui.workspace.CadWorkspaceUiState
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Interactive 3D CAD Viewport Canvas supporting unified Android touch gestures:
 * - 1 finger: Executes the active CAD tool (or orbits camera when ORBIT is selected)
 * - 2 fingers (pinch): Smooth camera zoom regardless of active tool
 * - 2 fingers (drag): Smooth camera pan regardless of active tool
 */
@Composable
fun CadViewportCanvas(
    state: CadWorkspaceUiState,
    onOrbitDrag: (Float, Float) -> Unit,
    onTwoFingerZoomPan: (Float, Offset, Float, Float) -> Unit,
    onSingleTap: (Offset, Boolean, Float, Float) -> Unit,
    onToolDragStart: (Offset, Float, Float) -> Unit,
    onToolDragMove: (Offset, Float, Float) -> Unit,
    onToolDragEnd: () -> Unit,
    onCancelInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    val latestTool by rememberUpdatedState(state.activeTool)
    val latestOnOrbit by rememberUpdatedState(onOrbitDrag)
    val latestOnZoomPan by rememberUpdatedState(onTwoFingerZoomPan)
    val latestOnTap by rememberUpdatedState(onSingleTap)
    val latestOnDragStart by rememberUpdatedState(onToolDragStart)
    val latestOnDragMove by rememberUpdatedState(onToolDragMove)
    val latestOnDragEnd by rememberUpdatedState(onToolDragEnd)
    val latestOnCancel by rememberUpdatedState(onCancelInteraction)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("cad_3d_viewport")
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val downPos = firstDown.position
                    var prevSinglePos = downPos
                    var totalSingleTravel = 0f
                    var isMultiTouch = false
                    var toolDragStarted = false
                    var prevSpan = 0f
                    var prevCentroid = Offset.Zero

                    val tool = latestTool
                    val isDragTool = tool == CadTool.RECTANGLE ||
                        tool == CadTool.LINE ||
                        tool == CadTool.PUSH_PULL ||
                        tool == CadTool.MEASURE

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) {
                            break
                        }

                        if (pressed.size >= 2) {
                            // Transition cleanly into 2-finger Zoom + Pan
                            if (!isMultiTouch) {
                                isMultiTouch = true
                                if (toolDragStarted) {
                                    latestOnCancel()
                                    toolDragStarted = false
                                }
                                val p0 = pressed[0].position
                                val p1 = pressed[1].position
                                prevSpan = max(12f, (p0 - p1).getDistance())
                                prevCentroid = (p0 + p1) * 0.5f
                            } else {
                                val p0 = pressed[0].position
                                val p1 = pressed[1].position
                                val currSpan = max(12f, (p0 - p1).getDistance())
                                val currCentroid = (p0 + p1) * 0.5f
                                val zoomRatio = currSpan / prevSpan
                                val panDelta = currCentroid - prevCentroid
                                if (absDiff(zoomRatio, 1f) > 0.002f || panDelta.getDistance() > 0.5f) {
                                    latestOnZoomPan(
                                        zoomRatio,
                                        panDelta,
                                        canvasSize.width.toFloat(),
                                        canvasSize.height.toFloat()
                                    )
                                }
                                prevSpan = currSpan
                                prevCentroid = currCentroid
                            }
                            pressed.forEach { if (it.positionChanged()) it.consume() }
                        } else if (!isMultiTouch) {
                            val change = pressed.first()
                            val currPos = change.position
                            val delta = currPos - prevSinglePos
                            totalSingleTravel += delta.getDistance()

                            if (tool == CadTool.ORBIT) {
                                if (delta.getDistance() > 0.5f) {
                                    latestOnOrbit(delta.x, delta.y)
                                    change.consume()
                                }
                            } else if (isDragTool) {
                                if (!toolDragStarted && totalSingleTravel > 8f) {
                                    toolDragStarted = true
                                    latestOnDragStart(
                                        downPos,
                                        canvasSize.width.toFloat(),
                                        canvasSize.height.toFloat()
                                    )
                                }
                                if (toolDragStarted && delta.getDistance() > 0.5f) {
                                    latestOnDragMove(
                                        currPos,
                                        canvasSize.width.toFloat(),
                                        canvasSize.height.toFloat()
                                    )
                                    change.consume()
                                }
                            }
                            prevSinglePos = currPos
                        }
                    }

                    // Pointer up: finalize drag or dispatch single/double tap
                    if (!isMultiTouch) {
                        if (toolDragStarted) {
                            latestOnDragEnd()
                        } else if (totalSingleTravel < 18f) {
                            val now = System.currentTimeMillis()
                            val isDouble = (now - lastTapTimeMs) < 340L &&
                                (downPos - lastTapPos).getDistance() < 56f
                            lastTapTimeMs = now
                            lastTapPos = downPos
                            latestOnTap(
                                downPos,
                                isDouble,
                                canvasSize.width.toFloat(),
                                canvasSize.height.toFloat()
                            )
                        }
                    }
                }
            }
    ) {
        renderCadScene(state = state, palette = palette)
    }
}

private fun absDiff(a: Float, b: Float): Float = sqrt((a - b) * (a - b))
