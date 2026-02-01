package com.vikify.app.vikifyui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
 * Aurora Day - Premium Light Mode Background
 * Multi-orb warm gradient with floating color accents (Peach, Lavender, Mint)
 * Designed to compete with dark mode's LivingBackground
 */
@Composable
fun EtherealBackground(
    modifier: Modifier = Modifier,
    animated: Boolean = true, // When false, show plain solid color
    content: @Composable () -> Unit
) {
    // If animations disabled, show simple warm cream background
    if (!animated) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFFFFBF5)) // Warm Cream
        ) {
            content()
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "aurora_day")

    // ═══════════════════════════════════════════════════════════════
    // ORB 1: PEACH ROSE (Top Right)
    // ═══════════════════════════════════════════════════════════════
    val orb1X by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1X"
    )
    val orb1Y by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb1Y"
    )

    // ═══════════════════════════════════════════════════════════════
    // ORB 2: LAVENDER MIST (Center)
    // ═══════════════════════════════════════════════════════════════
    val orb2X by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2X"
    )
    val orb2Y by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb2Y"
    )

    // ═══════════════════════════════════════════════════════════════
    // ORB 3: SOFT MINT (Bottom Left)
    // ═══════════════════════════════════════════════════════════════
    val orb3X by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3X"
    )
    val orb3Y by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb3Y"
    )

    // Aurora Day Color Palette (Warm & Inviting)
    val baseColor = Color(0xFFFFFBF5)       // Warm Cream Base
    val peachRose = Color(0xFFFFE5E0)       // Soft Peach/Rose
    val lavenderMist = Color(0xFFE8E0FF)    // Lavender Mist
    val softMint = Color(0xFFE0FFF5)        // Soft Mint
    val warmWhite = Color(0xFFFFF8F3)       // Warm White

    Box(modifier = modifier.fillMaxSize().background(baseColor)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw 3 overlapping gradient orbs
            
            // Orb 1: Peach Rose (top right area)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        peachRose.copy(alpha = 0.6f),
                        peachRose.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * orb1X, size.height * orb1Y),
                    radius = size.width * 0.5f
                )
            )
            
            // Orb 2: Lavender Mist (center area)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lavenderMist.copy(alpha = 0.5f),
                        lavenderMist.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * orb2X, size.height * orb2Y),
                    radius = size.width * 0.6f
                )
            )
            
            // Orb 3: Soft Mint (bottom left area)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        softMint.copy(alpha = 0.5f),
                        softMint.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * orb3X, size.height * orb3Y),
                    radius = size.width * 0.5f
                )
            )
        }
        
        content()
    }
}

