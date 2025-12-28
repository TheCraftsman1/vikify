package com.vikify.app.vikifyui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
 */
@Composable
fun LivingPlayerBackground(
    artworkUrl: String?,
    bpm: Int = 100,
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
                    }
                }
            } catch (e: Exception) {
                // Keep default colors
            }
        }
    }
    
    // BPM-Synced Animation Duration
    // Loop syncs to every 4 beats: (60000ms / bpm) * 4
    val loopDuration = ((60000f / bpm.coerceIn(60, 180)) * 4).toInt()
    
    // Infinite animation transition
    val infiniteTransition = rememberInfiniteTransition(label = "lava_lamp")
    
    // Orb A (Energy): Figure-8 motion
    val orbAPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(loopDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_a_phase"
    )
    
    // Orb B (Deep): Circular motion (slower)
    val orbBAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween((loopDuration * 1.5).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
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
        // Base wash layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(baseColor.copy(alpha = 0.5f))
        )
        
        // Fluid Mesh Canvas with blur
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(120.dp)
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
        
        // Glass Finish Overlay - LIGHTER for better control visibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.1f),   // Lighter top
                            0.2f to Color.Transparent,
                            0.8f to Color.Transparent,                 // More color visible
                            0.95f to Color.Black.copy(alpha = 0.3f)   // Much lighter bottom
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
 * Boost saturation of a color to make it more vibrant
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
    hsv[1] = (hsv[1] * factor).coerceIn(0f, 1f) // Boost saturation
    val saturated = android.graphics.Color.HSVToColor(hsv)
    return Color(saturated)
}
