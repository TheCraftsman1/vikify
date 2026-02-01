package com.vikify.app.vikifyui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sonic Typography & Effects
 * The "Energy" of Vikify
 */

object SonicTheme {
    
    // Wide/Extended Font Simulation (Using Default Sans Serif with spacing or Monospace if preferred)
    // Ideally we'd import "Orbitron" or "Syne" here. 
    // For now, we simulate the "Tech" look with Monospace or distinct spacing.
    val TechFontFamily = FontFamily.SansSerif // Placeholder for custom font
    
    // The "Sonic Glow" Text Style
    fun getGlowingHeaderStyle(color: Color = Color(0xFF00E5FF)): TextStyle {
        return TextStyle(
            fontFamily = TechFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp, // Slightly larger base
            letterSpacing = (-0.5).sp, // Tighten spacing for modern look
            shadow = Shadow(
                color = color.copy(alpha = 0.6f), // Reduce intensity to avoid muddy look
                offset = Offset(0f, 4f), // Drop shadow direction
                blurRadius = 8f // Sharper glow (was 12f)
            )
        )
    }
}

// Extension for simple glowing text
fun TextStyle.withSonicGlow(color: Color): TextStyle {
    return this.copy(
        shadow = Shadow(
            color = color,
            offset = Offset(0f, 0f),
            blurRadius = 12f
        )
    )
}
