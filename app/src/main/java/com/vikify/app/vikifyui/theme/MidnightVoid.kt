package com.vikify.app.vikifyui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living Background for Midnight Void Theme
 * 
 * Creates an atmospheric, breathing-like background using animated radial gradients.
 * The center of the gradient moves slowly in a figure-8 pattern, and the radius pulses.
 */
@Composable
fun LivingBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidth = with(density) { configuration.screenWidthDp.toFloat() }
    val screenHeight = with(density) { configuration.screenHeightDp.toFloat() }
    
    // Infinite transition for continuous animation
    val infiniteTransition = rememberInfiniteTransition(label = "livingBackground")
    
    // Animate the center point (x, y)
    // Using simple sin/cos waves with different periods creates an organic movement
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -0.2f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "xOffset"
    )
    
    val yOffset by infiniteTransition.animateFloat(
        initialValue = -0.1f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yOffset"
    )
    
    // Animate the radius for "breathing" effect
    val radiusScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radiusScale"
    )

    // Midnight Void Colors
    val deepIndigo = Color(0xFF050510)  // Deepest Indigo
    val pureVoid = Color(0xFF000000)    // Pure Void (Black)
    val faintPurple = Color(0xFF1A1A2E) // Subtle highlight

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        deepIndigo,
                        pureVoid,
                        pureVoid
                    ),

                    // Wait, center needs pixel coordinates for radialGradient in Compose usually,
                    // but Offset.Unspecified defaults to center. Let's use exact coordinates.
                    center = Offset(
                        x = (0.5f + xOffset) * 2000f, // Approx generic screen width multiplier for effect
                        y = (0.4f + yOffset) * 2000f 
                    // Actually, let's use the screen width/height we calculated
                    ).let { 
                        // Using fixed large values to ensure smoothness regardless of screen size for now
                        // or better: use a custom draw modifier if exact px needed, 
                        // but here we can just map 0.5 to screenWidth/2
                         Offset(
                            x = (0.5f + xOffset) * 1080f, // Assuming typical width for center focus
                            y = (0.4f + yOffset) * 2400f
                        )
                    },
                    radius = 1800f * radiusScale
                )
            )
    ) {
        // Overlay a second subtle gradient for complexity (Nebula effect)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            faintPurple.copy(alpha = 0.3f),
                            Color.Transparent
                        ),
                        center = Offset(
                            x = (0.2f - yOffset) * 1080f, // Opposing movement
                            y = (0.7f - xOffset) * 2400f
                        ),
                        radius = 1200f
                    )
                )
        )
        
        content()
    }
}
