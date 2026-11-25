package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.cad.model.CadMaterial
import com.example.cad.model.CadTool
import com.example.cad.model.LastPrecisionOperation
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalViewportPalette

@Composable
fun ToolContextHintBanner(
    activeTool: CadTool,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current
    val hintText = when (activeTool) {
        CadTool.ORBIT -> stringResource(R.string.hint_orbit)
        CadTool.SELECT -> stringResource(R.string.hint_select)
        CadTool.LINE -> stringResource(R.string.hint_line)
        CadTool.RECTANGLE -> stringResource(R.string.hint_rectangle)
        CadTool.PUSH_PULL -> stringResource(R.string.hint_push_pull)
        CadTool.ERASER -> stringResource(R.string.hint_eraser)
        CadTool.PAINT -> stringResource(R.string.hint_paint)
        CadTool.MEASURE -> stringResource(R.string.hint_measure)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = palette.panelSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, palette.panelBorder)
    ) {
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * Floating Value Control Box (VCB) anchored in the bottom-right corner.
 * Displays live dimensions in JetBrains Mono and opens a numeric CAD keypad on tap.
 */
@Composable
fun VcbFloatingPill(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current

    Surface(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 136.dp, minHeight = 52.dp)
            .testTag("vcb_floating_box"),
        shape = RoundedCornerShape(16.dp),
        color = palette.vcbBackground,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Straighten,
                contentDescription = stringResource(R.string.vcb_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = valueText,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = palette.vcbText,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Floating Architectural Material Palette shown when Paint tool is active.
 */
@Composable
fun MaterialPalettePanel(
    visible: Boolean,
    activeMaterial: CadMaterial,
    paintWholeVolume: Boolean,
    onSelectMaterial: (CadMaterial) -> Unit,
    onToggleScope: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = palette.panelSurface,
            border = BorderStroke(1.dp, palette.panelBorder),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CadMaterial.entries.forEach { mat ->
                        val selected = mat == activeMaterial
                        Surface(
                            onClick = { onSelectMaterial(mat) },
                            modifier = Modifier
                                .sizeIn(minWidth = 64.dp, minHeight = 52.dp)
                                .testTag("material_${mat.name.lowercase()}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else palette.panelBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(mat.baseColor.copy(alpha = if (mat == CadMaterial.GLASS) 0.55f else 1f))
                                        .border(1.dp, palette.edgeStroke.copy(alpha = 0.45f), CircleShape)
                                )
                                Text(
                                    text = mat.displayNameEs,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !paintWholeVolume,
                        onClick = { onToggleScope(false) },
                        label = { Text(stringResource(R.string.paint_scope_face)) },
                        modifier = Modifier.testTag("paint_scope_face_chip")
                    )
                    FilterChip(
                        selected = paintWholeVolume,
                        onClick = { onToggleScope(true) },
                        label = { Text(stringResource(R.string.paint_scope_volume)) },
                        modifier = Modifier.testTag("paint_scope_volume_chip")
                    )
                }
            }
        }
    }
}

/**
 * Custom compact CAD Numeric Keypad for entering exact dimensions (e.g. "3.50m" or "4.0, 2.5") into the VCB.
 */
@Composable
fun VcbNumericKeypadOverlay(
    visible: Boolean,
    draft: String,
    lastOp: LastPrecisionOperation?,
    onDraftChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current
    val helperHint = when (lastOp) {
        is LastPrecisionOperation.PushPullOp -> stringResource(R.string.vcb_hint_push_pull)
        is LastPrecisionOperation.RectangleOp -> stringResource(R.string.vcb_hint_rectangle)
        is LastPrecisionOperation.LineOp -> stringResource(R.string.vcb_hint_line)
        null -> stringResource(R.string.vcb_hint_default)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .testTag("vcb_keypad_panel"),
            shape = RoundedCornerShape(22.dp),
            color = palette.panelSurface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.vcb_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close)
                        )
                    }
                }

                // Numeric display box
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = draft.ifEmpty { "0.00" },
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = if (draft.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.testTag("vcb_draft_value")
                        )
                        Text(
                            text = "m",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = helperHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val keyRows = listOf(
                    listOf("7", "8", "9", "⌫"),
                    listOf("4", "5", "6", ","),
                    listOf("1", "2", "3", "-"),
                    listOf(".", "0", "C", "OK")
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    keyRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row.forEach { key ->
                                when (key) {
                                    "OK" -> {
                                        Button(
                                            onClick = onApply,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("vcb_apply_button"),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = stringResource(R.string.action_apply),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    "⌫" -> {
                                        Surface(
                                            onClick = {
                                                if (draft.isNotEmpty()) onDraftChange(draft.dropLast(1))
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("vcb_backspace_button"),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                                    contentDescription = "Borrar",
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    else -> {
                                        Surface(
                                            onClick = {
                                                if (key == "C") {
                                                    onDraftChange("")
                                                } else {
                                                    onDraftChange(draft + key)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("vcb_key_$key"),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (key == "C") {
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            border = BorderStroke(1.dp, palette.panelBorder)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = key,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    color = if (key == "C") {
                                                        MaterialTheme.colorScheme.error
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
