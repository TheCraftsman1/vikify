package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * ULTRA-PREMIUM VINYL COMPONENT (V2)
 * 
 * Features:
 * - Iridescent "Rainbow" Sheen (SweepGradient Moire Simulation)
 * - Deep Obsidian multi-layered finish (0xFF020202)
 * - Anti-aliased high-precision grooves
 * - Fixed Studio Glare (Doesn't rotate with disk)
 */
@Composable
fun PremiumVinyl(
    imageUrl: String?,
    vinylSize: Dp,
    rotation: Float,
    glowColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Iridescence")
    
    // Subtle shifting for the iridescence to feel "alive"
    val iridescenceShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Shift"
    )

    Box(
        modifier = modifier
            .size(vinylSize)
            .shadow(
                elevation = (vinylSize.value / 8).dp,
                shape = CircleShape,
                spotColor = glowColor.copy(alpha = 0.6f)
            )
            .clip(CircleShape)
            // The "Deep Obsidian" base
            .background(Color(0xFF020202)),
        contentAlignment = Alignment.Center
    ) {
        // ═══════════════════════════════════════════════════════════════
        // LAYER 1: THE CORE DISK (Rotates)
        // ═══════════════════════════════════════════════════════════════
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // 1. Base Disk Texture
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0F0F0F),
                        Color(0xFF020202)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 2. High-Precision Concentric Grooves
            val grooveDensity = 60
            for (i in 0 until grooveDensity) {
                val grooveRadius = radius * (0.32f + (i.toFloat() / grooveDensity) * 0.66f)
                // Variable opacity creates a "rippled" effect
                val alpha = if (i % 8 == 0) 0.12f else if (i % 2 == 0) 0.04f else 0.02f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = grooveRadius,
                    center = center,
                    style = Stroke(width = 0.4.dp.toPx())
                )
            }

            // 3. IRIDESCENT SHEEN (Rainbow effect)
            // This simulates light diffracting off the micro-grooves
            val rainbowBrush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x15FF0000), // Red tint 
                    Color(0x1500FF00), // Green tint
                    Color(0x150000FF), // Blue tint
                    Color.Transparent,
                    Color(0x20FFFFFF), // Pure white highlight
                    Color.Transparent,
                    Color(0x15FF00FF), // Purple tint
                    Color.Transparent
                )
            )
            
            drawCircle(
                brush = rainbowBrush,
                radius = radius,
                center = center
            )
        }

        // ═══════════════════════════════════════════════════════════════
        // LAYER 2: STUDIO LIGHTING (Static - Does NOT rotate)
        // ═══════════════════════════════════════════════════════════════
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // Major "X" shaped sheen
            val studioGlow = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.08f),
                    Color.Transparent,
                    Color.Transparent,
                    Color.White.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
            
            drawCircle(
                brush = studioGlow,
                radius = radius,
                center = center
            )
            
            // Top-left "softbox" reflection
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = 210f,
                sweepAngle = 40f,
                useCenter = true,
                size = Size(size.width * 0.9f, size.height * 0.9f),
                topLeft = Offset(size.width * 0.05f, size.height * 0.05f)
            )
        }

        // ═══════════════════════════════════════════════════════════════
        // LAYER 3: CENTER LABEL (Album Art)
        // ═══════════════════════════════════════════════════════════════
        val labelSize = vinylSize * 0.33f
        Box(
            modifier = Modifier
                .size(labelSize)
                .rotate(rotation)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Color(0xFF111111))
                .border(2.dp, Color.Black.copy(alpha = 0.8f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Vikify Watermark / Record Label Branding
            val logoAlpha = if (imageUrl != null) 0.6f else 1.0f
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.vikify.app.R.drawable.vikify_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(labelSize * 0.4f)
                    .graphicsLayer { alpha = logoAlpha }
            )
            
            // The "Spindle Hole" with depth
            Box(
                modifier = Modifier
                    .size(labelSize * 0.12f)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            )
        }
        
        // Final rim highlight (Edge of the record)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
        )
    }
}
