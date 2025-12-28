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

// --- Semantic Token Sets ---

private val LightTokens = VikifyColors(
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
    onSurface = LightColors.TextPrimary,
    onSurfaceVariant = LightColors.TextSecondary,
    onAccent = Color.White,
    
    glassBackground = NeutralWhite.copy(alpha = 0.7f),
    glassBorder = NeutralWhite.copy(alpha = 0.5f),
    
    border = NeutralGray200,
    divider = NeutralGray100,
    
    error = StatusError,
    success = StatusSuccess,
    
    glow = BrandCyan,
    shimmerBase = NeutralGray200,
    shimmerHighlight = NeutralOffWhite
)

private val DarkTokens = VikifyColors(
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
    shimmerHighlight = VoidBorder
)

// --- Theme State ---

enum class ThemeMode { LIGHT, DARK, SYSTEM }

object VikifyThemeState {
    var currentMode by mutableStateOf(ThemeMode.SYSTEM)
    
    fun toggleTheme() {
        currentMode = when (currentMode) {
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.SYSTEM -> ThemeMode.DARK
        }
    }
    
    fun setMode(mode: ThemeMode) {
        currentMode = mode
    }
}

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
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colorScheme = if (darkTheme) VikifyDarkColorScheme else VikifyLightColorScheme
    val vikifyColors = if (darkTheme) DarkTokens else LightTokens
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = if (darkTheme) VoidDark.toArgb() else NeutralOffWhite.toArgb()
            
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
