package com.vikify.app.vikifyui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Jam Mode Theme System
 * 
 * Two distinct visual styles for collaborative listening:
 * - CYBERPUNK: Neon purple/blue, futuristic DJ deck aesthetic
 * - LOFI: Warm sunset tones, illustrated chill vibes
 * - AUTO: Derives colors from album art
 */

enum class JamModeStyle {
    CYBERPUNK,
    LOFI,
    AUTO
}

/**
 * Color palette for Jam Mode UI elements
 */
@Immutable
data class JamModeColors(
    // Vinyl & Glow
    val vinylGlow: Color,
    val vinylBase: Color,
    val vinylGrooves: Color,
    
    // Background
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,
    
    // User Avatar Ring
    val userRingGlow: Color,
    val userRingIdle: Color,
    val hostBadge: Color,
    
    // Particles & Effects
    val particlePrimary: Color,
    val particleSecondary: Color,
    val bokehColor: Color,
    
    // Text & Controls
    val textPrimary: Color,
    val textSecondary: Color,
    val controlActive: Color,
    val controlInactive: Color,
    
    // Session Code
    val codeBackground: Color,
    val codeText: Color
)

/**
 * Cyberpunk Theme
 * 
 * Inspired by neon-lit DJ decks, purple/blue gradients,
 * and futuristic cosmic aesthetics
 */
val CyberpunkJamColors = JamModeColors(
    // Vinyl
    vinylGlow = Color(0xFF8B5CF6),           // Vivid Purple
    vinylBase = Color(0xFF0F0F0F),           // Deep black
    vinylGrooves = Color(0xFF1A1A2E),        // Dark purple-tinted
    
    // Background - Deep space with purple hints
    backgroundPrimary = Color(0xFF050508),   // Near black
    backgroundSecondary = Color(0xFF0A0A1A), // Dark blue-purple
    backgroundTertiary = Color(0xFF151530),  // Lighter accent
    
    // User Ring - Electric cyan glow
    userRingGlow = Color(0xFF00E5FF),        // Cyan
    userRingIdle = Color(0xFF006064),        // Muted teal
    hostBadge = Color(0xFFFFD700),           // Gold
    
    // Particles - Magenta/purple
    particlePrimary = Color(0xFFFF00FF),     // Magenta
    particleSecondary = Color(0xFF8B5CF6),   // Purple
    bokehColor = Color(0xFF00E5FF).copy(alpha = 0.3f),
    
    // Text
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB0B0B0),
    controlActive = Color(0xFF00E5FF),
    controlInactive = Color(0xFF4A4A4A),
    
    // Session Code
    codeBackground = Color(0xFF1A1A2E).copy(alpha = 0.8f),
    codeText = Color(0xFF00E5FF)
)

/**
 * Lo-Fi Theme
 * 
 * Warm, illustrated aesthetic with sunset tones,
 * golden highlights, and cozy atmosphere
 */
val LofiJamColors = JamModeColors(
    // Vinyl - Warm orange glow
    vinylGlow = Color(0xFFFF6B4A),           // Sunset orange
    vinylBase = Color(0xFF1A1512),           // Warm dark brown
    vinylGrooves = Color(0xFF2D261F),        // Warm mid-brown
    
    // Background - Cozy warm dark
    backgroundPrimary = Color(0xFF0D0A08),   // Warm black
    backgroundSecondary = Color(0xFF1A1512), // Dark chocolate
    backgroundTertiary = Color(0xFF2D261F),  // Warm brown
    
    // User Ring - Golden amber
    userRingGlow = Color(0xFFFFB800),        // Golden
    userRingIdle = Color(0xFF8B6914),        // Muted gold
    hostBadge = Color(0xFFFF6B4A),           // Sunset coral
    
    // Particles - Warm cream/gold
    particlePrimary = Color(0xFFFFE4B5),     // Soft cream
    particleSecondary = Color(0xFFFFB800),   // Gold
    bokehColor = Color(0xFFFF6B4A).copy(alpha = 0.25f),
    
    // Text
    textPrimary = Color(0xFFFFF8E7),         // Warm white
    textSecondary = Color(0xFFA89880),       // Warm gray
    controlActive = Color(0xFFFFB800),       // Gold
    controlInactive = Color(0xFF4A4038),     // Warm dark gray
    
    // Session Code
    codeBackground = Color(0xFF2D261F).copy(alpha = 0.8f),
    codeText = Color(0xFFFFB800)
)

/**
 * Jam Mode Theme Provider
 */
object JamModeTheme {
    
    /**
     * Get colors for a specific style
     */
    fun getColors(style: JamModeStyle, dynamicColor: Color? = null): JamModeColors {
        return when (style) {
            JamModeStyle.CYBERPUNK -> CyberpunkJamColors
            JamModeStyle.LOFI -> LofiJamColors
            JamModeStyle.AUTO -> {
                // If dynamic color provided, derive a palette from it
                // Otherwise default to Cyberpunk
                dynamicColor?.let { deriveColorsFromAccent(it) } ?: CyberpunkJamColors
            }
        }
    }
    
    /**
     * Derive a color palette from album art accent color
     */
    private fun deriveColorsFromAccent(accent: Color): JamModeColors {
        // Determine if accent is warm or cool
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(
            android.graphics.Color.argb(
                (accent.alpha * 255).toInt(),
                (accent.red * 255).toInt(),
                (accent.green * 255).toInt(),
                (accent.blue * 255).toInt()
            ),
            hsv
        )
        
        val isWarm = hsv[0] in 0f..60f || hsv[0] in 300f..360f
        
        return if (isWarm) {
            LofiJamColors.copy(
                vinylGlow = accent,
                userRingGlow = accent,
                particlePrimary = accent.copy(alpha = 0.8f)
            )
        } else {
            CyberpunkJamColors.copy(
                vinylGlow = accent,
                userRingGlow = accent,
                particlePrimary = accent.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * CompositionLocal for providing Jam Mode colors down the tree
 */
val LocalJamModeColors = staticCompositionLocalOf { CyberpunkJamColors }