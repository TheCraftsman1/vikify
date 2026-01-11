package com.vikify.app.vikifyui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AtmosphereEngine
 * 
 * Generates dynamic theme atmospheres based on album artwork.
 * Transforms raw palette colors into theme-appropriate gradients.
 * 
 * Studio Mode (Light): Soft pastel tints, watercolor feels
 * Aurora Mode (Dark): Deep saturated tones, neon accents
 */
object AtmosphereEngine {
    
    /**
     * Extract color palette from album artwork
     */
    suspend fun extractPalette(
        context: Context,
        imageUrl: String?
    ): AlbumPalette? = withContext(Dispatchers.IO) {
        if (imageUrl == null) return@withContext null
        
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .build()
            val result = ImageLoader(context).execute(request)
            val bitmap = (result.image as? BitmapImage)?.bitmap ?: return@withContext null
            
            val palette = Palette.from(bitmap)
                .maximumColorCount(24)
                .generate()
            
            AlbumPalette(
                vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) },
                muted = palette.mutedSwatch?.rgb?.let { Color(it) },
                darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) },
                lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) },
                darkMuted = palette.darkMutedSwatch?.rgb?.let { Color(it) },
                lightMuted = palette.lightMutedSwatch?.rgb?.let { Color(it) }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Transform palette for Studio Mode (Light)
     * Creates soft, pastel-tinted backgrounds
     */
    fun createStudioAtmosphere(palette: AlbumPalette?): StudioAtmosphere {
        val baseColor = palette?.vibrant ?: BrandCyan
        
        // Mix with white (90%) for pastel effect
        val pastelTint = blendWithWhite(baseColor, 0.92f)
        
        // Create subtle gradient
        val gradient = Brush.verticalGradient(
            colors = listOf(
                NeutralOffWhite,
                pastelTint,
                NeutralOffWhite
            ),
            startY = 0f,
            endY = 2000f
        )
        
        return StudioAtmosphere(
            backgroundGradient = gradient,
            shadowColor = baseColor.copy(alpha = 0.15f),
            highlightColor = pastelTint,
            accentColor = baseColor
        )
    }
    
    /**
     * Transform palette for Aurora Mode (Dark)
     * Creates deep, saturated neon atmospheres
     */
    fun createAuroraAtmosphere(palette: AlbumPalette?): AuroraAtmosphere {
        val primaryGlow = palette?.vibrant ?: BrandPurple
        val secondaryGlow = palette?.lightVibrant ?: BrandCyan
        
        // Mix with black (20%) for deep tones
        val deepTone = blendWithBlack(primaryGlow, 0.85f)
        
        // Create aurora gradient
        val gradient = Brush.radialGradient(
            colors = listOf(
                deepTone.copy(alpha = 0.3f),
                VoidDark.copy(alpha = 0.95f),
                VoidBlack
            ),
            radius = 1500f
        )
        
        return AuroraAtmosphere(
            backgroundGradient = gradient,
            primaryGlow = primaryGlow,
            secondaryGlow = secondaryGlow,
            ambientColor = deepTone
        )
    }
    
    /**
     * Blend color with white (for pastel effect)
     */
    private fun blendWithWhite(color: Color, ratio: Float): Color {
        val rgb = ColorUtils.blendARGB(
            color.toArgb(),
            android.graphics.Color.WHITE,
            ratio
        )
        return Color(rgb)
    }
    
    /**
     * Blend color with black (for deep tones)
     */
    private fun blendWithBlack(color: Color, ratio: Float): Color {
        val rgb = ColorUtils.blendARGB(
            color.toArgb(),
            android.graphics.Color.BLACK,
            ratio
        )
        return Color(rgb)
    }
}

/**
 * Raw palette extracted from album art
 */
data class AlbumPalette(
    val vibrant: Color?,
    val muted: Color?,
    val darkVibrant: Color?,
    val lightVibrant: Color?,
    val darkMuted: Color?,
    val lightMuted: Color?
)

/**
 * Studio Mode atmosphere (Light)
 */
data class StudioAtmosphere(
    val backgroundGradient: Brush,
    val shadowColor: Color,
    val highlightColor: Color,
    val accentColor: Color
)

/**
 * Aurora Mode atmosphere (Dark)
 */
data class AuroraAtmosphere(
    val backgroundGradient: Brush,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val ambientColor: Color
)
