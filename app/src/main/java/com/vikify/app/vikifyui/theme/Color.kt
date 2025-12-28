package com.vikify.app.vikifyui.theme

import androidx.compose.ui.graphics.Color

/**
 * Vikify Color Palette
 * 
 * Organized as:
 * 1. New Primitive Tokens (for Theme.kt)
 * 2. Legacy Compatibility Tokens (for existing code)
 * 3. LightColors object
 * 4. DarkColors object
 */

// ============================================
// NEW PRIMITIVE PALETTE
// ============================================

// Brand Colors
val BrandCyan = Color(0xFF0EA5E9)
val BrandPurple = Color(0xFF8B5CF6)
val BrandRed = Color(0xFFED5564)

// Neutral - Light
val NeutralWhite = Color(0xFFFFFFFF)
val NeutralOffWhite = Color(0xFFF7F9FC)
val NeutralLightGray = Color(0xFFF2F2F6)
val NeutralGray100 = Color(0xFFF3F4F6)
val NeutralGray200 = Color(0xFFE5E7EB)
val NeutralGray300 = Color(0xFFD1D5DB)
val NeutralGray400 = Color(0xFF9CA3AF)
val NeutralGray500 = Color(0xFF6B7280)
val NeutralGray600 = Color(0xFF4B5563)
val NeutralGray800 = Color(0xFF1A1A1A)
val NeutralBlack = Color(0xFF000000)

// Neutral - Dark (OLED/Midnight)
val VoidBlack = Color(0xFF000000)
val VoidDark = Color(0xFF0A0A0F)
val VoidCard = Color(0xFF12121A)
val VoidCardHover = Color(0xFF1A1A25)
val VoidBorder = Color(0xFF2A2A35)

// Status
val StatusError = Color(0xFFEF4444)
val StatusSuccess = Color(0xFF22C55E)

// ============================================
// LEGACY COMPATIBILITY TOKENS
// ============================================

// Backgrounds
val BackgroundBase = Color(0xFFF7F9FC)
val Surface = Color(0xFFFFFFFF)
val IOSBg = Color(0xFFF2F2F6)
val SoftSurface = Color(0xFFFFFFFF)

// Text
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF6B7280)
val TextTertiary = Color(0xFF9CA3AF)
val TextBlack = Color(0xFF000000)
val TextGray = Color(0xFF8E8E93)

// Accent
val Accent = Color(0xFF0EA5E9)
val AccentLight = Color(0xFFE0F2FE)

// Dividers
val Divider = Color(0xFFE5E7EB)
val DividerLight = Color(0xFFF3F4F6)

// Player
val PlayerBackground = Color(0xFFFAFBFC)
val ProgressBackground = Color(0xFFE5E7EB)
val ProgressFill = Accent

// Navigation
val NavBackground = Color(0xFFFFFFFE)
val NavIconActive = TextPrimary
val NavIconInactive = TextSecondary

// Raataan Premium Theme
val RaataanPurple = Color(0xFF6C5CE7)
val RaataanPurpleLight = Color(0xFF8E81F1)
val RaataanPink = Color(0xFFFF4D6D)
val RaataanShadow = Color(0x33000000)
val RaataanSurface = Color(0xFFFFFFFF)
val RaataanText = Color(0xFF1A1A1A)
val RaataanTextGray = Color(0xFF717171)

// iOS Accent & Glow
val GlowBlue = Color(0xFF00C2FF)
val GlowPurple = Color(0xFFA855F7)
val GlowGreen = Color(0xFF22C55E)
val GlowCoral = Color(0xFFFF6B6B)
val GlowTeal = Color(0xFF4ECDC4)
val GlowYellow = Color(0xFFFFE66D)

// Shadows
val CardShadow = Color(0x1A000000)
val DeepShadow = Color(0x33000000)

// Shimmer Loading
val ShimmerBase = Color(0xFFE0E0E0)
val ShimmerHighlight = Color(0xFFF5F5F5)

// Vikify Premium Dark Theme
val VikifyRed = Color(0xFFED5564)
val VikifyPink = Color(0xFFFF6B9D)
val VikifyPurple = Color(0xFF8B5CF6)
val VikifyDark = Color(0xFF0A0A0F)
val VikifyCard = Color(0xFF12121A)
val VikifyCardHover = Color(0xFF1A1A25)
val VikifyBorder = Color(0xFF2A2A35)

// Ethereal Day Colors
val MistBlue = Color(0xFFF0F4F8)
val SoftCloud = Color(0xFFF5F7FA)
val DeepSlate = Color(0xFF1A202C)
val CoolGrey = Color(0xFF718096)
val PrismaticGradient = listOf(
    Color(0xFFFF9A9E),
    Color(0xFFA18CD1),
    Color(0xFFFBC2EB)
)

// Gradients
object GenreGradients {
    val Pop = listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB))
    val HipHop = listOf(Color(0xFFFF9966), Color(0xFFFF5E62))
    val Rock = listOf(Color(0xFF232526), Color(0xFF414345))
    val Indie = listOf(Color(0xFF56CCF2), Color(0xFF2F80ED))
    val Chill = listOf(Color(0xFFB2FEFA), Color(0xFF0ED2F7))
    val Party = listOf(Color(0xFFCAC531), Color(0xFFF3F9A7))
}

// Neon Colors
object NeonColors {
    val Purple = Color(0xFF8B5CF6)
    val Cyan = Color(0xFF06B6D4)
    val Pink = Color(0xFFEC4899)
    val Green = Color(0xFF10B981)
    val PurpleGlow = Color(0x668B5CF6)
    val CyanGlow = Color(0x6606B6D4)
}

// ============================================
// LIGHT COLORS OBJECT
// ============================================
object LightColors {
    val Background = MistBlue
    val Surface = Color(0xFFFFFFFF)
    val SurfaceElevated = SoftCloud
    val SurfaceVariant = Color(0xFFF0F0FF)
    
    val TextPrimary = DeepSlate
    val TextSecondary = CoolGrey
    val TextTertiary = Color(0xFFA0AEC0)
    
    val Accent = Color(0xFF8B5CF6)
    val AccentMuted = Color(0xFFC4B5FD)
    val AccentGlow = Color(0xFFDDD6FE)
    
    val Border = Color(0x0D000000)
    val BorderLight = Color(0x05000000)
    val Divider = Color(0x0D000000)
    
    val PlayerBackground = Color.White.copy(alpha = 0.8f)
    val PlayerSurface = Color.White
    val ProgressBackground = Color(0xFFE2E8F0)
    val ProgressFill = Accent
    
    val NavBackground = Color.White.copy(alpha = 0.9f)
    val NavIconActive = DeepSlate
    val NavIconInactive = CoolGrey
    
    val ShimmerBase = Color(0xFFEDF2F7)
    val ShimmerHighlight = Color.White
    
    val Success = Color(0xFF34D399)
    val Error = Color(0xFFF87171)
    val Warning = Color(0xFFFBBF24)
    
    val GlassBackground = Color.White.copy(alpha = 0.6f)
    val GlassBackgroundLight = Color.White.copy(alpha = 0.4f)
    val GlassBorder = Color(0x1FFFFFFF)
}

// ============================================
// DARK COLORS OBJECT - OLED-GRADE SPOTIFY PALETTE
// ============================================
object DarkColors {
    // ═══════════════════════════════════════════════════════════════
    // THE "4K" OLED PALETTE
    // Not pure black (#000000) - Spotify uses warm grays for depth
    // ═══════════════════════════════════════════════════════════════
    
    // Backgrounds - The "Spotify" depths
    val Background = Color(0xFF121212)        // Deep warm gray (THE Spotify black)
    val Surface = Color(0xFF282828)           // Card background (for depth)
    val SurfaceElevated = Color(0xFF3E3E3E)   // Elevated cards/dialogs
    val SurfaceVariant = Color(0xFF1A1A1A)    // Subtle variation
    
    // Text - High-fidelity contrast hierarchy
    val TextPrimary = Color(0xFFFFFFFF)       // 100% White (Headlines)
    val TextSecondary = Color(0xFFB3B3B3)     // 70% Gray - Industry standard for dark UI readability
    val TextTertiary = Color(0xFF777777)      // 40% Gray (Icons/Meta)
    
    // Accent - Keep saturation high
    val Accent = Color(0xFF1DB954)            // Spotify Green (or brand color)
    val AccentMuted = Color(0xFF169C46)
    val AccentGlow = Color(0xFF1ED760)
    
    // Borders & Dividers
    val Border = Color(0xFF333333)
    val BorderLight = Color(0xFF404040)
    val Divider = Color(0xFF2A2A2A)
    
    // Player
    val PlayerBackground = Color(0xFF181818)
    val PlayerSurface = Color(0xFF282828)
    val ProgressBackground = Color(0xFF535353)
    val ProgressFill = Accent
    
    // Navigation
    val NavBackground = Color(0xFF121212)
    val NavIconActive = TextPrimary
    val NavIconInactive = TextSecondary
    
    // Shimmer
    val ShimmerBase = Color(0xFF282828)
    val ShimmerHighlight = Color(0xFF3E3E3E)
    
    // Status
    val Success = Color(0xFF1DB954)
    val Error = Color(0xFFE91429)
    val Warning = Color(0xFFF59B23)
    
    // Glass/Blur effects
    val GlassBackground = Color(0xE6121212)
    val GlassBackgroundLight = Color(0xCC181818)
    val GlassBorder = Color(0x1AFFFFFF)
}

