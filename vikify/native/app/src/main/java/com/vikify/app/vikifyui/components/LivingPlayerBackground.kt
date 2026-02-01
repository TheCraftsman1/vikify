package com.vikify.app.vikifyui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living Player Background - Premium "Lava Lamp" Effect
 * 
 * Design Philosophy:
 * - Extracts a TRIAD PALETTE from album art
 * - 3 animated orbs in Figure-8, Circle, and Pulse patterns
 * - BPM-synced animation timing
 * - Heavy blur for glass-morphism effect
 * 
 * @param artworkUrl URL of the album artwork
 * @param bpm Beats per minute - controls animation speed
 * @param onLuminanceCalculated Callback with isLight boolean for adaptive UI colors
 */
@Composable
fun LivingPlayerBackground(
    artworkUrl: String?,
    bpm: Int = 100,
    onLuminanceCalculated: (Boolean) -> Unit = {}, // true = light background, false = dark
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Triad Palette Colors
    var energyColor by remember { mutableStateOf(Color(0xFFFF6B6B)) }   // Vibrant - "Energy" blob
    var deepColor by remember { mutableStateOf(Color(0xFF4A0E4E)) }     // Dark Muted - "Deep" blob  
    var baseColor by remember { mutableStateOf(Color(0xFF1A1A2E)) }     // Dominant - "Base" wash
    
    // Extract colors from artwork
    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrEmpty()) return@LaunchedEffect
        
        withContext(Dispatchers.IO) {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()
                
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val image = result.image
                    val bitmap = when (image) {
                        is coil3.BitmapImage -> image.bitmap
                        else -> null
                    }
                    
                    if (bitmap != null) {
                        val palette = Palette.from(bitmap).generate()
                        
                        // Energy Color: Vibrant swatch (punchy, saturated)
                        energyColor = palette.vibrantSwatch?.rgb?.let { saturateColor(Color(it), 1.3f) }
                            ?: palette.lightVibrantSwatch?.rgb?.let { saturateColor(Color(it), 1.3f) }
                            ?: shiftHue(baseColor, 30f)
                        
                        // Deep Color: Use vibrant colors too for more color (NOT muted)
                        deepColor = palette.lightVibrantSwatch?.rgb?.let { saturateColor(Color(it), 1.2f) }
                            ?: palette.vibrantSwatch?.rgb?.let { saturateColor(Color(it), 1.2f) }
                            ?: palette.mutedSwatch?.rgb?.let { saturateColor(Color(it), 1.4f) }
                            ?: shiftHue(baseColor, -60f)
                        
                        // Base Color: Dominant with boosted saturation
                        baseColor = palette.dominantSwatch?.rgb?.let { saturateColor(Color(it), 1.2f) }
                            ?: palette.vibrantSwatch?.rgb?.let { Color(it) }
                            ?: Color(0xFF2A1A3E)
                        
                        // Calculate luminance for adaptive UI
                        val luminance = (0.299f * baseColor.red) + (0.587f * baseColor.green) + (0.114f * baseColor.blue)
                        onLuminanceCalculated(luminance > 0.5f)
                    }
                }
            } catch (e: Exception) {
                // Keep default colors, assume dark background
                onLuminanceCalculated(false)
            }
        }
    }
    
    // BPM-Synced Animation Duration
    // Loop syncs to every 4 beats: (60000ms / bpm) * 4
    val loopDuration = ((60000f / bpm.coerceIn(60, 180)) * 4).toInt()
    
    // Infinite animation transition
    val infiniteTransition = rememberInfiniteTransition(label = "lava_lamp")
    
    // Orb A (Energy): Figure-8 motion (ORGANIC - drifts back and forth)
    val orbAPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(loopDuration, easing = FastOutSlowInEasing), // Less robotic
            repeatMode = RepeatMode.Reverse // Drift back and forth like lava lamp
        ),
        label = "orb_a_phase"
    )
    
    // Orb B (Deep): Circular motion (slower, organic)
    val orbBAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween((loopDuration * 1.5).toInt(), easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_b_angle"
    )
    
    // Orb C (Highlight): Pulsing scale (synced to beat)
    val orbCScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(loopDuration / 4, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_c_scale"
    )
    
    Box(modifier = modifier.fillMaxSize()) {
        // Base wash layer (solid for better depth)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor) // Solid background, no alpha
        )
        
        // Fluid Mesh Canvas with blur (reduced for performance)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(100.dp) // Reduced from 120dp for better GPU performance
                .alpha(0.85f) // Blend better with base
        ) {
            val width = size.width
            val height = size.height
            
            // === ORB A (Energy) - Figure-8 Pattern ===
            // Figure-8 parametric: x = sin(t), y = sin(2t)
            val t = Math.toRadians(orbAPhase.toDouble())
            val orbAX = (width * 0.5f) + (sin(t).toFloat() * width * 0.3f)
            val orbAY = (height * 0.35f) + (sin(2 * t).toFloat() * height * 0.15f)
            val orbARadius = width * 0.4f
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        energyColor.copy(alpha = 0.7f),
                        energyColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(orbAX.toFloat(), orbAY.toFloat()),
                    radius = orbARadius
                ),
                radius = orbARadius,
                center = Offset(orbAX.toFloat(), orbAY.toFloat()),
                blendMode = BlendMode.Screen
            )
            
            // === ORB B (Deep) - Circular Motion at Bottom ===
            val orbBX = (width * 0.5f) + (cos(Math.toRadians(orbBAngle.toDouble())).toFloat() * width * 0.25f)
            val orbBY = (height * 0.7f) + (sin(Math.toRadians(orbBAngle.toDouble())).toFloat() * height * 0.1f)
            val orbBRadius = width * 0.6f
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        deepColor.copy(alpha = 0.6f),
                        deepColor.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(orbBX, orbBY),
                    radius = orbBRadius
                ),
                radius = orbBRadius,
                center = Offset(orbBX, orbBY),
                blendMode = BlendMode.Screen
            )
            
            // === ORB C (Highlight) - Pulsing Center ===
            val orbCRadius = width * 0.2f * orbCScale
            val orbCX = width * 0.5f
            val orbCY = height * 0.45f
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        energyColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(orbCX, orbCY),
                    radius = orbCRadius
                ),
                radius = orbCRadius,
                center = Offset(orbCX, orbCY),
                blendMode = BlendMode.Screen
            )
        }
        
        // Noise/Grain Overlay - Prevents banding on OLED screens
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f)),
                        radius = 1200f
                    )
                )
        )
        
        // Glass Finish Overlay - LIGHTER for better control visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.05f),  // Very light top
                            0.3f to Color.Transparent,
                            0.55f to Color.Transparent,               // Keep mid clear
                            0.75f to Color.Black.copy(alpha = 0.3f),  // Start control area scrim
                            1.0f to Color.Black.copy(alpha = 0.65f)   // Strong bottom for controls
                        )
                    )
                )
        )
    }
}

/**
 * Shift the hue of a color by degrees
 * Used to create complementary colors when Palette returns null
 */
private fun shiftHue(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ),
        hsv
    )
    hsv[0] = (hsv[0] + degrees) % 360
    if (hsv[0] < 0) hsv[0] += 360
    val shifted = android.graphics.Color.HSVToColor(hsv)
    return Color(shifted)
}

/**
 * Boost saturation and clamp brightness for premium color output
 * Prevents blown-out neon or muddy dark colors
 * @param factor Values > 1.0 increase saturation, < 1.0 decrease
 */
private fun saturateColor(color: Color, factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        ),
        hsv
    )
    hsv[1] = (hsv[1] * factor).coerceIn(0.2f, 1f) // Ensure mostly saturated
    hsv[2] = hsv[2].coerceIn(0.3f, 0.9f)          // Clamp brightness (not too dark/bright)
    val saturated = android.graphics.Color.HSVToColor(hsv)
    return Color(saturated)
}
