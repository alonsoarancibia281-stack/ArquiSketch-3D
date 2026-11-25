package com.example.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.cad.model.CadTool
import com.example.ui.theme.LocalViewportPalette

private data class ToolDescriptor(
    val tool: CadTool,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

private val cadToolItems = listOf(
    ToolDescriptor(CadTool.ORBIT, R.string.tool_orbit, Icons.Rounded.Autorenew, "tool_orbit"),
    ToolDescriptor(CadTool.SELECT, R.string.tool_select, Icons.Rounded.NearMe, "tool_select"),
    ToolDescriptor(CadTool.LINE, R.string.tool_line, Icons.Rounded.Edit, "tool_line"),
    ToolDescriptor(CadTool.RECTANGLE, R.string.tool_rectangle, Icons.Rounded.CropSquare, "tool_rectangle"),
    ToolDescriptor(CadTool.PUSH_PULL, R.string.tool_push_pull, Icons.Rounded.Height, "tool_push_pull"),
    ToolDescriptor(CadTool.ERASER, R.string.tool_eraser, Icons.Rounded.DeleteOutline, "tool_eraser"),
    ToolDescriptor(CadTool.PAINT, R.string.tool_paint, Icons.Rounded.FormatPaint, "tool_paint"),
    ToolDescriptor(CadTool.MEASURE, R.string.tool_measure, Icons.Rounded.Straighten, "tool_measure")
)

/**
 * Adaptive floating CAD tool dock:
 * - Compact screens (phones): Horizontal thumb-friendly floating dock at the bottom (respecting navigationBars).
 * - Expanded screens (tablets/foldables): Vertical floating side rail on the left.
 */
@Composable
fun CadToolDock(
    activeTool: CadTool,
    isVerticalRail: Boolean,
    onSelectTool: (CadTool) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalViewportPalette.current

    Surface(
        modifier = modifier
            .then(if (!isVerticalRail) Modifier.navigationBarsPadding() else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = palette.panelSurface,
        border = BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 8.dp
    ) {
        if (isVerticalRail) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                cadToolItems.forEach { item ->
                    ToolDockItem(
                        item = item,
                        selected = item.tool == activeTool,
                        onClick = { onSelectTool(item.tool) }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cadToolItems.forEach { item ->
                    ToolDockItem(
                        item = item,
                        selected = item.tool == activeTool,
                        onClick = { onSelectTool(item.tool) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolDockItem(
    item: ToolDescriptor,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = stringResource(item.labelRes)
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minWidth = 62.dp, minHeight = 54.dp)
            .testTag(item.tag),
        shape = RoundedCornerShape(15.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
