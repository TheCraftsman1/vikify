package com.vikify.app.vikifyui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Vikify Semantic Color System
 * 
 * Defines abstract semantic tokens that map to concrete colors 
 * based on the active theme (Light/Dark).
 */
@Immutable
data class VikifyColors(
    // 1. Brand & Actions
    val brandPrimary: Color,
    val brandSecondary: Color,
    val accent: Color,
    
    // 2. Surfaces
    val surfaceBackground: Color,
    val surfaceCard: Color,
    val surfaceSheet: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val background: Color,
    
    // 3. Text & Content
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onAccent: Color,
    
    // 4. Glassmorphism
    val glassBackground: Color,
    val glassBorder: Color,
    
    // 5. Borders & Dividers
    val border: Color,
    val divider: Color,
    
    // 6. Feedback & Status
    val error: Color,
    val success: Color,
    
    // 7. Accents & Glows
    val glow: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color
)

/**
 * CompositionLocal key for accessing VikifyColors
 */
val LocalVikifyColors = staticCompositionLocalOf<VikifyColors> {
    error("No VikifyColors provided")
}
