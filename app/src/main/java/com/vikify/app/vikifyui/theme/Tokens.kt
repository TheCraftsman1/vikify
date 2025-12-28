package com.vikify.app.vikifyui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/**
 * Vikify Spacing Tokens
 * 
 * RULE: No random dp values. Everything snaps to these.
 * This makes UI feel designed, not assembled.
 */
object Spacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 16.dp
    val LG = 24.dp
    val XL = 32.dp
    val XXL = 48.dp
}

/**
 * Vikify Motion System
 * 
 * RULE: Use this easing everywhere or nowhere.
 * Only animate: player height transitions.
 * Everything else: instant.
 */
object Motion {
    // Single easing curve for all animations
    val Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    
    // Duration for player expand/collapse only
    val PlayerDuration = 300
    
    // No other animations should exist
}

/**
 * Vikify Sizing
 */
object Sizing {
    val MiniPlayerHeight = 64.dp
    val ArtworkSmall = 48.dp
    val ArtworkMedium = 64.dp
    val ArtworkLarge = 140.dp
    val IconSmall = 20.dp
    val IconMedium = 24.dp
    val IconLarge = 32.dp
    val CardRadius = 8.dp
    val CardRadiusLarge = 12.dp
    val BorderWidth = 1.dp
    val XXL = 48.dp  // For larger icon buttons
}

