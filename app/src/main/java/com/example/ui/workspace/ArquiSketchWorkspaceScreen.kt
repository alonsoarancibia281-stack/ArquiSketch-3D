package com.example.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cad.model.CadTool
import com.example.ui.components.CadToolDock
import com.example.ui.components.CadTopBar
import com.example.ui.components.ExportModelDialog
import com.example.ui.components.GridSettingsDialog
import com.example.ui.components.MaterialPalettePanel
import com.example.ui.components.ProjectsBottomSheet
import com.example.ui.components.ToolContextHintBanner
import com.example.ui.components.VcbFloatingPill
import com.example.ui.components.VcbNumericKeypadOverlay
import com.example.ui.viewport.CadViewportCanvas

@Composable
fun ArquiSketchWorkspaceScreen(
    viewModel: CadWorkspaceViewModel,
    onRequestExport: (ExportFormat) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showProjectsSheet by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.consumeSnackbar()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp)
            )
        }
    ) { _ ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 600.dp

            // 1. Full-bleed Interactive 3D CAD Viewport
            CadViewportCanvas(
                state = state,
                onOrbitDrag = viewModel::onOrbitDrag,
                onTwoFingerZoomPan = viewModel::onTwoFingerZoomPan,
                onSingleTap = viewModel::onSingleTap,
                onToolDragStart = viewModel::onToolDragStart,
                onToolDragMove = viewModel::onToolDragMove,
                onToolDragEnd = viewModel::onToolDragEnd,
                onCancelInteraction = viewModel::cancelActiveInteraction,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Floating Top Quick Bar + Contextual Hint Pill
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CadTopBar(
                    projectName = state.currentProjectName,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    darkTheme = state.darkTheme,
                    showGrid = state.showGrid,
                    isExpanded = isExpanded,
                    onOpenProjects = { showProjectsSheet = true },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onSelectCameraPreset = viewModel::applyCameraPreset,
                    onResetCamera = viewModel::resetCamera,
                    onOpenGridSettings = { showGridDialog = true },
                    onToggleTheme = viewModel::toggleTheme,
                    onOpenExport = { showExportDialog = true }
                )

                ToolContextHintBanner(
                    activeTool = state.activeTool,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 3. Adaptive Floating CAD Tool Dock (Left Rail on tablets, Bottom Dock on phones)
            CadToolDock(
                activeTool = state.activeTool,
                isVerticalRail = isExpanded,
                onSelectTool = viewModel::selectTool,
                modifier = Modifier.align(
                    if (isExpanded) Alignment.CenterStart else Alignment.BottomCenter
                )
            )

            // 4. Floating Material Palette (when Paint tool is active)
            MaterialPalettePanel(
                visible = state.activeTool == CadTool.PAINT && !state.vcbInputOpen,
                activeMaterial = state.activeMaterial,
                paintWholeVolume = state.paintWholeVolume,
                onSelectMaterial = viewModel::selectMaterial,
                onToggleScope = viewModel::setPaintWholeVolume,
                modifier = Modifier
                    .align(if (isExpanded) Alignment.BottomCenter else Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (isExpanded) 18.dp else 88.dp
                    )
            )

            // 5. Floating VCB (Value Control Box) in Bottom-Right Corner
            if (!state.vcbInputOpen) {
                VcbFloatingPill(
                    label = state.vcbLabel,
                    valueText = state.vcbDisplayText,
                    onClick = viewModel::openVcbKeypad,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(
                            end = 12.dp,
                            bottom = if (isExpanded) 18.dp else 88.dp
                        )
                )
            }

            // 6. Custom CAD Numeric Keypad Overlay for exact VCB precision entry
            VcbNumericKeypadOverlay(
                visible = state.vcbInputOpen,
                draft = state.vcbInputDraft,
                lastOp = state.lastPrecisionOp,
                onDraftChange = viewModel::updateVcbDraft,
                onApply = { viewModel.applyVcbInput(state.vcbInputDraft) },
                onDismiss = viewModel::closeVcbKeypad,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(
                        end = 12.dp,
                        bottom = if (isExpanded) 18.dp else 88.dp
                    )
            )
        }
    }

    if (showProjectsSheet) {
        ProjectsBottomSheet(
            currentProjectName = state.currentProjectName,
            projects = savedProjects,
            onDismiss = { showProjectsSheet = false },
            onSaveProject = viewModel::saveCurrentProject,
            onLoadProject = viewModel::loadProject,
            onDeleteProject = viewModel::deleteProject,
            onNewProject = viewModel::createNewProject
        )
    }

    if (showExportDialog) {
        ExportModelDialog(
            faceCount = state.scene.faces.size,
            onSelectFormat = { format ->
                showExportDialog = false
                onRequestExport(format)
            },
            onDismiss = { showExportDialog = false }
        )
    }

    if (showGridDialog) {
        GridSettingsDialog(
            gridSpacingMeters = state.gridSpacingMeters,
            showGrid = state.showGrid,
            snapToGrid = state.snapToGrid,
            onUpdate = viewModel::updateGridSettings,
            onDismiss = { showGridDialog = false }
        )
    }
}
