package com.vikify.app.vikifyui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism Effects for Vikify
 * 
 * Premium frosted glass effects for dark mode surfaces
 * Uses blur on Android 12+ with fallback for older devices
 */

/**
 * Apply glassmorphism effect to a surface
 * 
 * @param enabled Whether to apply the effect (typically only in dark mode)
 * @param blurRadius Blur radius for frosted effect (Android 12+ only)
 */
@Composable
fun Modifier.glassmorphism(
    enabled: Boolean = LocalIsDarkTheme.current,
    blurRadius: Dp = 20.dp,
    backgroundColor: Color = if (LocalIsDarkTheme.current) DarkColors.GlassBackground else LightColors.GlassBackground,
    borderColor: Color = if (LocalIsDarkTheme.current) DarkColors.GlassBorder else LightColors.GlassBorder
): Modifier {
    return if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Android 12+ with real blur
        this
            .blur(blurRadius)
            .background(backgroundColor)
            .border(0.5.dp, borderColor)
    } else if (enabled) {
        // Fallback: semi-transparent background without blur
        this
            .background(backgroundColor)
            .border(0.5.dp, borderColor)
    } else {
        this
    }
}

/**
 * Simple glass background without blur
 * For use when blur is not needed or causes performance issues
 */
@Composable
fun Modifier.glassBackground(
    enabled: Boolean = LocalIsDarkTheme.current
): Modifier {
    val backgroundColor = if (enabled) DarkColors.GlassBackground else LightColors.GlassBackground
    val borderColor = if (enabled) DarkColors.GlassBorder else LightColors.GlassBorder
    
    return this
        .background(backgroundColor)
        .border(0.5.dp, borderColor)
}

/**
 * Dark surface with proper border separation
 * For cards and elevated surfaces in dark mode
 */
@Composable
fun Modifier.darkSurface(
    elevation: Int = 0 // 0, 1, 2 for increasing elevation
): Modifier {
    val isDark = LocalIsDarkTheme.current
    
    if (!isDark) return this
    
    val backgroundColor = when (elevation) {
        0 -> DarkColors.Surface
        1 -> DarkColors.SurfaceElevated
        else -> Color(0xFF242424)
    }
    
    val borderColor = DarkColors.Border
    
    return this
        .background(backgroundColor)
        .border(1.dp, borderColor)
}

/**
 * Gradient glow effect for dark mode
 * Creates a subtle violet glow behind elements
 */
@Composable
fun Modifier.accentGlow(
    enabled: Boolean = LocalIsDarkTheme.current,
    intensity: Float = 0.3f
): Modifier {
    if (!enabled) return this
    
    val glowColor = DarkColors.Accent.copy(alpha = intensity)
    
    return this.background(
        androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(glowColor, Color.Transparent)
        )
    )
}
