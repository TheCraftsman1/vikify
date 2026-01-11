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
import com.vikify.app.vikifyui.theme.CyberpunkJamColors
import com.vikify.app.vikifyui.theme.JamModeColors
import com.vikify.app.vikifyui.theme.JamModeStyle
import com.vikify.app.vikifyui.theme.JamModeTheme
import com.vikify.app.vikifyui.theme.LofiJamColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * JAM MODE BACKGROUND
 * 
 * Immersive animated background for collaborative listening:
 * - Cosmic nebula gradients (shifting colors based on theme)
 * - Floating particles with slow drift animation
 * - Soft bokeh effects for depth
 * - Optional BPM-synced pulsing
 * 
 * @param style Visual theme (CYBERPUNK or LOFI)
 * @param bpm Optional beats per minute for pulse sync
 * @param accentColor Optional accent color for AUTO mode
 */
@Composable
fun JamModeBackground(
    style: JamModeStyle = JamModeStyle.CYBERPUNK,
    bpm: Int = 100,
    accentColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = remember(style, accentColor) {
        JamModeTheme.getColors(style, accentColor)
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // ANIMATION TRANSITIONS
    // ═══════════════════════════════════════════════════════════════════
    
    val infiniteTransition = rememberInfiniteTransition(label = "jamBackground")
    
    // Slow nebula movement (12-23 second cycles, like MeshBackground)
    val nebula1Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nebula1"
    )
    
    val nebula2Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nebula2"
    )
    
    val nebula3Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(23000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nebula3"
    )
    
    // Gentle glow pulse (synced to ~BPM if provided)
    val pulseDuration = (60000 / bpm.coerceIn(60, 180)) // ms per beat
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    // ═══════════════════════════════════════════════════════════════════
    // PARTICLE SYSTEM (ENHANCED)
    // ═══════════════════════════════════════════════════════════════════
    
    // Remember particle positions (stable across recompositions)
    // Increased count for "Next Level" density
    val particles = remember { generateParticles(80) }
    
    // Animate particles slowly drifting naturally
    val particleDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(25000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleBase"
    )
    
    // Sparkle effect (global time for phase offsets)
    val sparkleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle"
    )
    
    // ═══════════════════════════════════════════════════════════════════
    // RENDER
    // ═══════════════════════════════════════════════════════════════════
    
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundPrimary)
    ) {
        val width = size.width
        val height = size.height
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 1: Base dark gradient & Mesh-like Nebula
        // ─────────────────────────────────────────────────────────────
        
        // Base
        drawRect(brush = Brush.verticalGradient(
            colors = listOf(colors.backgroundPrimary, colors.backgroundSecondary, colors.backgroundPrimary)
        ))
        
        // Nebula 1 - Primary (Top-Right)
        val x1 = width * 0.6f + (width * 0.2f) * sin(nebula1Phase)
        val y1 = height * 0.3f + (height * 0.1f) * cos(nebula1Phase * 0.8f)
        drawRadialBlob(colors.vinylGlow.copy(alpha = 0.2f * glowPulse), Offset(x1, y1), width * 0.9f)
        
        // Nebula 2 - Secondary (Bottom-Left)
        val x2 = width * 0.3f + (width * 0.2f) * cos(nebula2Phase)
        val y2 = height * 0.7f + (height * 0.15f) * sin(nebula2Phase * 1.1f)
        drawRadialBlob(colors.particlePrimary.copy(alpha = 0.15f * glowPulse), Offset(x2, y2), width * 0.8f)
        
        // Nebula 3 - Tertiary (Moving freely)
        val x3 = width * 0.5f + (width * 0.3f) * sin(nebula3Phase * 0.7f)
        val y3 = height * 0.5f + (height * 0.2f) * cos(nebula3Phase)
        drawRadialBlob(colors.userRingGlow.copy(alpha = 0.12f * glowPulse), Offset(x3, y3), width * 1.0f)
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 2: Floating Particles (Depth & Sparkle)
        // ─────────────────────────────────────────────────────────────
        particles.forEach { particle ->
            // Physics: Depth parallax
            // Closer particles (larger) move faster
            val depthScale = particle.size / 4f // 0.25 to 1.0
            
            // Movement logic
            val driftY = (particle.y - (particleDrift * height * particle.speed * depthScale)) % height
            val adjustedY = if (driftY < 0) driftY + height else driftY
            
            // Horizontal sway
            val swayX = particle.x + sin((particleDrift + particle.phase) * 6.28f) * (20f * depthScale)
            
            // Sparkle logic
            val alphaPulse = (sin(sparkleTime + particle.phase * 10f) + 1f) / 2f // 0..1
            val dynamicAlpha = particle.alpha * (0.6f + 0.4f * alphaPulse)
            
            drawCircle(
                color = (if (particle.isPrimary) colors.particlePrimary else colors.particleSecondary)
                    .copy(alpha = dynamicAlpha),
                radius = particle.size * (0.8f + 0.2f * alphaPulse),
                center = Offset(swayX, adjustedY)
            )
        }
        
        // ─────────────────────────────────────────────────────────────
        // LAYER 3: Bokeh Depth
        // ─────────────────────────────────────────────────────────────
        // Top-left bokeh
        drawBokeh(colors.bokehColor, Offset(width * 0.1f, height * 0.15f), width * 0.15f)
        // Bottom-right bokeh
        drawBokeh(colors.bokehColor.copy(alpha = colors.bokehColor.alpha * 0.6f), Offset(width * 0.9f, height * 0.85f), width * 0.1f)
    }
}

/** Draw a soft radial gradient blob */
private fun DrawScope.drawRadialBlob(color: Color, center: Offset, radius: Float) {
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

/** Draw a bokeh circle */
private fun DrawScope.drawBokeh(color: Color, center: Offset, radius: Float) {
    drawCircle(color = color.copy(alpha = color.alpha * 1.2f), radius = radius * 0.3f, center = center)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f), Color.Transparent),
            center = center,
            radius = radius
        ),
        center = center,
        radius = radius
    )
}

private data class Particle(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val speed: Float,
    val phase: Float,
    val isPrimary: Boolean
)

private fun generateParticles(count: Int): List<Particle> {
    return List(count) {
        Particle(
            x = Random.nextFloat() * 1080f,
            y = Random.nextFloat() * 2400f,
            size = Random.nextFloat() * 4f + 1f,     // 1.0 - 5.0 scale
            alpha = Random.nextFloat() * 0.35f + 0.15f, // 0.15 - 0.50 visibility
            speed = Random.nextFloat() * 0.8f + 0.2f, // Varied speeds
            phase = Random.nextFloat(),
            isPrimary = Random.nextFloat() > 0.3f    // 70% primary, 30% secondary
        )
    }
}