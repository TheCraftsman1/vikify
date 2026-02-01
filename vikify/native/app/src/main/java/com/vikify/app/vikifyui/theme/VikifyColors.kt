package com.vikify.app.vikifyui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Vikify Dual Identity Design System
 * 
 * 🌞 Studio Mode (Light): "Digital Paper" - Clarity, Freshness, Tactility
 * 🌙 Aurora Mode (Dark): "Liquid Glass" - Immersion, Depth, Glow
 * 
 * Semantic tokens adapt not just by Light/Dark, but by physics and feeling.
 */

/**
 * Defines the visual "physics" of UI elements
 */
enum class VisualPhysics {
    STUDIO,   // Paper, Ink, Shadows (Light Mode)
    AURORA    // Glass, Glow, Reflection (Dark Mode)
}

/**
 * Atmosphere type for dynamic backgrounds
 */
enum class AtmosphereType {
    PASTEL_GRADIENT,  // Soft warm/cool gradients for Light Mode
    AURORA_MESH,      // Deep mesh gradients for Dark Mode
    ALBUM_INFUSED     // Album art color-based atmosphere
}

/**
 * Core semantic color system with Dual Identity extensions
 */
@Immutable
data class VikifyColors(
    // ═══════════════════════════════════════════════════════════
    // IDENTITY FOUNDATION
    // ═══════════════════════════════════════════════════════════
    val physics: VisualPhysics,
    val atmosphereType: AtmosphereType,
    
    // ═══════════════════════════════════════════════════════════
    // 1. BRAND & ACTIONS
    // ═══════════════════════════════════════════════════════════
    val brandPrimary: Color,
    val brandSecondary: Color,
    val accent: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 2. SURFACES (The "Canvas")
    // ═══════════════════════════════════════════════════════════
    val surfaceBackground: Color,      // Main canvas background
    val surfaceCard: Color,             // Card surfaces
    val surfaceSheet: Color,            // Modal/sheet backgrounds
    val surface: Color,
    val surfaceElevated: Color,
    val background: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 3. TEXT & CONTENT (The "Ink")
    // ═══════════════════════════════════════════════════════════
    val textPrimary: Color,             // Main readable text
    val textSecondary: Color,           // Supporting text
    val textTertiary: Color,            // Subtle/meta text
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onAccent: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 4. GLASSMORPHISM & DEPTH
    // ═══════════════════════════════════════════════════════════
    val glassBackground: Color,
    val glassBorder: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 5. BORDERS & DIVIDERS
    // ═══════════════════════════════════════════════════════════
    val border: Color,
    val divider: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 6. FEEDBACK & STATUS
    // ═══════════════════════════════════════════════════════════
    val error: Color,
    val success: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 7. DUAL IDENTITY ACCENTS
    // ═══════════════════════════════════════════════════════════
    val glow: Color,                    // Primary glow/highlight color
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    
    // ═══════════════════════════════════════════════════════════
    // 8. LYRICS SCREEN SPECIFIC
    // ═══════════════════════════════════════════════════════════
    val lyricsActive: Color,            // Current lyric line
    val lyricsInactive: Color,          // Upcoming lyrics
    val lyricsPassed: Color,            // Past lyrics
    val lyricsHighlight: Color,         // Background pill for active
    
    // ═══════════════════════════════════════════════════════════
    // 9. PLAYER SPECIFIC
    // ═══════════════════════════════════════════════════════════
    val playerSurface: Color,
    val progressTrack: Color,
    val progressFill: Color
)

/**
 * CompositionLocal key for accessing VikifyColors
 */
val LocalVikifyColors = staticCompositionLocalOf<VikifyColors> {
    error("No VikifyColors provided")
}
