package com.vikify.app.ui.models

/**
 * Vikify Ambient Mode - Contextual UI Density
 * 
 * The app adapts its visual presence based on:
 * - Time of day (10PM-5AM = Ambient)
 * - Listening duration (15+ min continuous)
 * - Device brightness (< 30% - future)
 */
enum class AmbientModeType {
    ACTIVE,    // Normal daytime mode - full UI
    AMBIENT,   // Night mode - reduced, calm UI
    MINIMAL    // Post-pause mode - ultra-compact
}

/**
 * User preference for ambient mode behavior
 * Respects user agency while providing intelligent defaults
 */
enum class AmbientModePreference {
    AUTO,           // Time-based (10PM-5AM) + listening duration
    ALWAYS_ACTIVE,  // Never enter ambient mode
    ALWAYS_AMBIENT  // Stay in calm mode always
}

/**
 * Ambient Mode State - visual multipliers for UI elements
 */
data class AmbientModeState(
    val mode: AmbientModeType = AmbientModeType.ACTIVE,
    val navOpacity: Float = 1f,           // 0.6f in Ambient
    val textBrightness: Float = 1f,       // 0.8f in Ambient
    val glowRadiusMultiplier: Float = 1f, // 2f in Ambient
    val backgroundSpeedMultiplier: Float = 1f, // 0.6f in Ambient
    val transitionProgress: Float = 1f    // 0-1 for smooth transitions
)
