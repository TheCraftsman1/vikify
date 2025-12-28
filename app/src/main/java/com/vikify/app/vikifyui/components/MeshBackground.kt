package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.math.cos

@Composable
fun MeshBackground(
    modifier: Modifier = Modifier
) {
    // VIKIFY "LIVING" BACKGROUND
    // A mesh gradients that moves. Three blobs of color (Cyan, Purple, Pink) slowly floating around.
    
    val infiniteTransition = rememberInfiniteTransition(label = "meshPositions")
    
    // Animate Blob 1 (Cyan/Blue)
    val t1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t1"
    )
    
    val t2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t2"
    )

    val t3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "t3"
    )

    Canvas(modifier = modifier.fillMaxSize().background(Color.Black)) {
        val width = size.width
        val height = size.height
        
        // Base dark layer
        drawRect(Color(0xFF050505)) 
        
        // Blob 1: Cyan/Aqua (Vikify Primary)
        // Moves in a figure-8-ish loop
        val x1 = width * 0.5f + (width * 0.3f) * sin(t1)
        val y1 = height * 0.4f + (height * 0.2f) * cos(t1 * 0.7f)
        
        drawRadialGradient(
            color = Color(0xFF00E5FF).copy(alpha = 0.3f), // Cyan
            center = Offset(x1, y1),
            radius = width * 0.8f
        )
        
        // Blob 2: Magenta/Pink (Energy)
        val x2 = width * 0.2f + (width * 0.4f) * cos(t2)
        val y2 = height * 0.7f + (height * 0.3f) * sin(t2 * 1.2f)
        
        drawRadialGradient(
            color = Color(0xFFFF4081).copy(alpha = 0.25f), // Pink
            center = Offset(x2, y2),
            radius = width * 0.9f
        )
        
        // Blob 3: Violet/Deep Purple (Depth)
        val x3 = width * 0.8f + (width * 0.3f) * sin(t3 * 0.5f)
        val y3 = height * 0.8f + (height * 0.3f) * cos(t3)
        
        drawRadialGradient(
            color = Color(0xFF651FFF).copy(alpha = 0.35f), // Deep Purple
            center = Offset(x3, y3),
            radius = width * 1.0f
        )
        
        // Noise Overlay (Simulated with distinct small dots or just a semi-transparent scrim)
        // Since we can't do shader noise easily in Canvas without AGSL (Android 13+), 
        // we'll stick to a blur/overlay if needed, or rely on GlassCards to add the noise texture.
    }
}

private fun DrawScope.drawRadialGradient(
    color: Color,
    center: Offset,
    radius: Float
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        center = center,
        radius = radius
    )
}
