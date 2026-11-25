package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cad.model.CameraPreset
import com.example.ui.theme.LocalViewportPalette

@Composable
fun CadTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    darkTheme: Boolean,
    showGrid: Boolean,
    isExpanded: Boolean,
    onOpenProjects: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectCameraPreset: (CameraPreset) -> Unit,
    onResetCamera: () -> Unit,
    onOpenGridSettings: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current
    var viewsMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = palette.panelSurface,
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Projects Button
            Surface(
                onClick = onOpenProjects,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("projects_button"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = stringResource(R.string.action_projects),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    if (isExpanded) {
                        Text(
                            text = projectName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Undo / Redo
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = stringResource(R.string.action_undo),
                    tint = if (canUndo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }

            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = stringResource(R.string.action_redo),
                    tint = if (canRedo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }

            // Quick Camera Views (Isometric, Top/Planta, Front/Frontal)
            Box {
                Surface(
                    onClick = { viewsMenuExpanded = true },
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("camera_views_button"),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = stringResource(R.string.action_views),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_views),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                DropdownMenu(
                    expanded = viewsMenuExpanded,
                    onDismissRequest = { viewsMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_isometric)) },
                        onClick = {
                            viewsMenuExpanded = false
                            onSelectCameraPreset(CameraPreset.ISOMETRIC)
                        },
                        modifier = Modifier.testTag("view_preset_isometric")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_top)) },
                        onClick = {
                            viewsMenuExpanded = false
                            onSelectCameraPreset(CameraPreset.TOP)
                        },
                        modifier = Modifier.testTag("view_preset_top")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_front)) },
                        onClick = {
                            viewsMenuExpanded = false
                            onSelectCameraPreset(CameraPreset.FRONT)
                        },
                        modifier = Modifier.testTag("view_preset_front")
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_reset)) },
                        onClick = {
                            viewsMenuExpanded = false
                            onResetCamera()
                        },
                        modifier = Modifier.testTag("view_preset_reset")
                    )
                }
            }

            // Grid Settings
            IconButton(
                onClick = onOpenGridSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("grid_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.GridOn,
                    contentDescription = stringResource(R.string.action_grid),
                    tint = if (showGrid) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Theme Toggle (Light SketchUp / Dark CAD)
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = stringResource(R.string.action_theme_toggle),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Export .OBJ / .GLTF Button
            Surface(
                onClick = onOpenExport,
                modifier = Modifier
                    .height(48.dp)
                    .sizeIn(minWidth = 48.dp)
                    .testTag("export_button"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.action_export),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.action_export),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
