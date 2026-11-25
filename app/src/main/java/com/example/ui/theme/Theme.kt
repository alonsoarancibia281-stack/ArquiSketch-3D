package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CadViewportPalette(
    val isDark: Boolean,
    val skyTop: Color,
    val skyHorizon: Color,
    val groundFill: Color,
    val gridMinor: Color,
    val gridMajor: Color,
    val edgeStroke: Color,
    val panelSurface: Color,
    val panelBorder: Color,
    val vcbBackground: Color,
    val vcbText: Color,
    val selectionAccent: Color
)

val LightViewportPalette = CadViewportPalette(
    isDark = false,
    skyTop = LightSkyTop,
    skyHorizon = LightSkyHorizon,
    groundFill = LightGroundFill,
    gridMinor = LightGridMinor,
    gridMajor = LightGridMajor,
    edgeStroke = LightEdgeStroke,
    panelSurface = LightSurface,
    panelBorder = LightSurfaceBorder,
    vcbBackground = Color(0xF5FFFFFF),
    vcbText = LightOnSurface,
    selectionAccent = BlueprintBlue
)

val DarkViewportPalette = CadViewportPalette(
    isDark = true,
    skyTop = DarkSkyTop,
    skyHorizon = DarkSkyHorizon,
    groundFill = DarkGroundFill,
    gridMinor = DarkGridMinor,
    gridMajor = DarkGridMajor,
    edgeStroke = DarkEdgeStroke,
    panelSurface = DarkSurface,
    panelBorder = DarkSurfaceBorder,
    vcbBackground = Color(0xF51E293B),
    vcbText = DarkOnSurface,
    selectionAccent = BlueprintBlueDark
)

val LocalViewportPalette = staticCompositionLocalOf { LightViewportPalette }

private val LightColorScheme = lightColorScheme(
    primary = BlueprintBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = BlueprintNavy,
    secondary = SelectionCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = SelectionAmber,
    background = LightSkyHorizon,
    onBackground = LightOnSurface,
    surface = LightSurfaceElevated,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = LightOnSurfaceMuted,
    outline = LightSurfaceBorder,
    error = AxisRed
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueprintBlueDark,
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = SelectionAmber,
    background = DarkSkyTop,
    onBackground = DarkOnSurface,
    surface = DarkSurfaceElevated,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF263447),
    onSurfaceVariant = DarkOnSurfaceMuted,
    outline = DarkSurfaceBorder,
    error = Color(0xFFF87171)
)

@Composable
fun ArquiSketchTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val viewportPalette = if (darkTheme) DarkViewportPalette else LightViewportPalette

    CompositionLocalProvider(LocalViewportPalette provides viewportPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ArquiSketchTypography,
            content = content
        )
    }
}
