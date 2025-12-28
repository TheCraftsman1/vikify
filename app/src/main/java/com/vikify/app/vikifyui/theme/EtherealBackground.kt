package com.vikify.app.vikifyui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Morning Mist - Ethereal Day Background
 * A subtle, breathing radial gradient (White -> Mist Blue)
 */
@Composable
fun EtherealBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ethereal_pulse")

    // Animate the center point slightly for organic movement
    val centerX by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centerX"
    )
    
    val centerY by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centerY"
    )

    // Animate radius for "breathing" effect
    val radius by infiniteTransition.animateFloat(
        initialValue = 1200f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radius"
    )

    // Ethereal Mist Colors
    val centerColor = Color(0xFFFFFFFF) // Pure Light
    val midColor = Color(0xFFF5F7FA)    // Soft Cloud
    val edgeColor = Color(0xFFF0F4F8)   // Mist Blue

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(centerColor, midColor, edgeColor),
                    center = Offset(
                        x = if (centerX.isNaN()) 500f else centerX * 1000f, // Fallback/Scaling
                        y = if (centerY.isNaN()) 1000f else centerY * 2000f
                    ),
                    radius = radius
                )
            )
    ) {
        content()
    }
}
