package com.vikify.app.vikifyui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- M3 Color Schemes ---

private val VikifyLightColorScheme = lightColorScheme(
    primary = BrandCyan,
    background = NeutralOffWhite,
    surface = NeutralWhite,
    onPrimary = NeutralWhite,
    onBackground = NeutralBlack,
    onSurface = NeutralBlack,
    error = StatusError
)

private val VikifyDarkColorScheme = darkColorScheme(
    primary = BrandCyan,
    background = VoidDark,
    surface = VoidCard,
    onPrimary = NeutralWhite,
    onBackground = NeutralWhite,
    onSurface = NeutralWhite,
    error = StatusError
)

// --- Theme Mode ---

enum class ThemeMode {
    SUNLIGHT, MOON, COOL
}

object VikifyThemeState {
    var currentMode by mutableStateOf(ThemeMode.COOL) // Default to Premium Cool
        private set
        
    fun setMode(mode: ThemeMode) {
        currentMode = mode
    }

    fun toggleTheme() {
        currentMode = when (currentMode) {
            ThemeMode.SUNLIGHT -> ThemeMode.MOON
            ThemeMode.MOON -> ThemeMode.COOL
            ThemeMode.COOL -> ThemeMode.SUNLIGHT
        }
    }
}

// --- Semantic Token Sets ---

private val LightTokens = VikifyColors(
    physics = VisualPhysics.STUDIO,
    atmosphereType = AtmosphereType.PASTEL_GRADIENT,
    
    brandPrimary = BrandCyan,
    brandSecondary = BrandPurple,
    accent = LightColors.Accent,
    
    surfaceBackground = NeutralOffWhite,
    surfaceCard = NeutralWhite,
    surfaceSheet = NeutralWhite,
    surface = LightColors.Surface,
    surfaceElevated = LightColors.SurfaceElevated,
    background = LightColors.Background,
    
    textPrimary = NeutralBlack,
    textSecondary = NeutralGray500,
    textTertiary = NeutralGray400,
    
    // Fixed: Added missing parameters
    onSurface = LightColors.TextPrimary,
    onSurfaceVariant = LightColors.TextSecondary,
    onAccent = Color.White,
    
    glassBackground = NeutralWhite.copy(alpha = 0.7f),
    glassBorder = NeutralWhite.copy(alpha = 0.5f),
    
    border = NeutralGray200,
    divider = NeutralGray200,
    
    error = StatusError,
    success = StatusSuccess,
    // warning removed as it doesn't exist in VikifyColors
    
    glow = BrandCyan,
    shimmerBase = NeutralGray200,
    shimmerHighlight = NeutralOffWhite,
    
    // Lyrics - Studio Mode (Paper & Ink)
    lyricsActive = NeutralBlack,
    lyricsInactive = NeutralGray400,
    lyricsPassed = NeutralGray200,
    lyricsHighlight = BrandCyan.copy(alpha = 0.1f),  // Soft watercolor
    
    // Player - Studio Mode
    playerSurface = NeutralWhite,
    progressTrack = NeutralGray200,
    progressFill = BrandCyan
)

private val DarkTokens = VikifyColors(
    physics = VisualPhysics.AURORA,
    atmosphereType = AtmosphereType.AURORA_MESH,
    
    brandPrimary = BrandCyan,
    brandSecondary = BrandPurple,
    accent = DarkColors.Accent,
    
    surfaceBackground = VoidDark,
    surfaceCard = VoidCard,
    surfaceSheet = VoidCardHover,
    surface = DarkColors.Surface,
    surfaceElevated = DarkColors.SurfaceElevated,
    background = DarkColors.Background,
    
    textPrimary = NeutralWhite,
    textSecondary = NeutralGray400,
    textTertiary = NeutralGray600,
    onSurface = DarkColors.TextPrimary,
    onSurfaceVariant = DarkColors.TextSecondary,
    onAccent = Color.White,
    
    glassBackground = VoidBlack.copy(alpha = 0.5f),
    glassBorder = NeutralWhite.copy(alpha = 0.1f),
    
    border = VoidBorder,
    divider = VoidCardHover,
    
    error = StatusError,
    success = StatusSuccess,
    
    glow = BrandPurple,
    shimmerBase = VoidCardHover,
    shimmerHighlight = VoidBorder,
    
    // Lyrics - Aurora Mode (Glow & Neon)
    lyricsActive = NeutralWhite,
    lyricsInactive = NeutralWhite.copy(alpha = 0.4f),
    lyricsPassed = NeutralWhite.copy(alpha = 0.25f),
    lyricsHighlight = BrandPurple.copy(alpha = 0.2f),  // Neon glow
    
    // Player - Aurora Mode
    playerSurface = VoidCard,
    progressTrack = VoidCardHover,
    progressFill = BrandCyan
)

private val CoolTokens = VikifyColors(
    physics = VisualPhysics.AURORA,
    atmosphereType = AtmosphereType.AURORA_MESH,
    
    brandPrimary = BrandCyan,
    brandSecondary = BrandPurple,
    accent = BrandCyan,
    
    surfaceBackground = Color(0xFF0F111A), // Blend
    surfaceCard = Color(0x99181A24),
    surfaceSheet = VoidCard,
    surface = VoidCard,
    surfaceElevated = Color(0xFF1E1E24),
    background = Color(0xFF0F111A),
    
    textPrimary = NeutralWhite,
    textSecondary = NeutralGray400,
    textTertiary = NeutralGray600,
    onSurface = DarkColors.TextPrimary,
    onSurfaceVariant = DarkColors.TextSecondary,
    onAccent = Color.White,
    
    glassBackground = VoidBlack.copy(alpha = 0.5f),
    glassBorder = NeutralWhite.copy(alpha = 0.1f),
    
    border = VoidBorder,
    divider = VoidCardHover,
    
    error = StatusError,
    success = StatusSuccess,
    
    glow = BrandCyan,
    shimmerBase = VoidCardHover,
    shimmerHighlight = VoidBorder,
    
    // Lyrics
    lyricsActive = NeutralWhite,
    lyricsInactive = NeutralWhite.copy(alpha = 0.4f),
    lyricsPassed = NeutralWhite.copy(alpha = 0.25f),
    lyricsHighlight = BrandCyan.copy(alpha = 0.2f),
    
    // Player
    playerSurface = VoidCard,
    progressTrack = VoidCardHover,
    progressFill = BrandCyan
)

/**
 * CompositionLocal for dark theme status
 */
val LocalIsDarkTheme = compositionLocalOf { false }

// --- Main Composable ---

@Composable
fun VikifyTheme(
    themeMode: ThemeMode = VikifyThemeState.currentMode,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SUNLIGHT -> false
        ThemeMode.MOON -> true
        ThemeMode.COOL -> true
    }
    
    val colorScheme = if (darkTheme) VikifyDarkColorScheme else VikifyLightColorScheme
    val vikifyColors = when (themeMode) {
        ThemeMode.SUNLIGHT -> LightTokens
        ThemeMode.MOON -> DarkTokens
        ThemeMode.COOL -> CoolTokens
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalVikifyColors provides vikifyColors,
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// --- Accessor ---
object VikifyTheme {
    val colors: VikifyColors
        @Composable
        get() = LocalVikifyColors.current
        
    val isDark: Boolean
        @Composable
        get() = LocalIsDarkTheme.current
}
