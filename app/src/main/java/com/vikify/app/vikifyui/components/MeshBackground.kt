/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Living Background 2.0 - The Soul of Vikify
 * A breathing, reactive, mood-aware background that makes the app feel alive
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.*
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTES - Time & Mood Aware
// ═══════════════════════════════════════════════════════════════════════════════

private object BackgroundColors {
    // Base
    val DeepSpace = Color(0xFF030308)
    val Void = Color(0xFF050510)
    
    // Morning (6am-12pm) - Warm, energizing
    val MorningPrimary = Color(0xFFFF9500)    // Sunrise orange
    val MorningSecondary = Color(0xFFFFB347)  // Soft gold
    val MorningAccent = Color(0xFFFF6B6B)     // Warm pink
    
    // Afternoon (12pm-5pm) - Vibrant, active
    val AfternoonPrimary = Color(0xFF00D4FF)  // Electric cyan
    val AfternoonSecondary = Color(0xFF7C4DFF) // Vivid purple
    val AfternoonAccent = Color(0xFFFF4081)   // Hot pink
    
    // Evening (5pm-9pm) - Relaxed, warm
    val EveningPrimary = Color(0xFF8B5CF6)    // Soft purple
    val EveningSecondary = Color(0xFFEC4899)  // Rose pink
    val EveningAccent = Color(0xFFF97316)     // Sunset orange
    
    // Night (9pm-6am) - Calm, mysterious
    val NightPrimary = Color(0xFF6366F1)      // Indigo
    val NightSecondary = Color(0xFF06B6D4)    // Teal
    val NightAccent = Color(0xFF8B5CF6)       // Purple
    
    fun getTimeBasedPalette(): Triple<Color, Color, Color> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..11 -> Triple(MorningPrimary, MorningSecondary, MorningAccent)
            hour in 12..16 -> Triple(AfternoonPrimary, AfternoonSecondary, AfternoonAccent)
            hour in 17..20 -> Triple(EveningPrimary, EveningSecondary, EveningAccent)
            else -> Triple(NightPrimary, NightSecondary, NightAccent)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LIVING MESH BACKGROUND 2.0
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MeshBackground(
    modifier: Modifier = Modifier,
    accentColor: Color? = null, // Optional: sync with now-playing album color
    intensity: Float = 1f,       // 0-1, reduce for battery saving
    enableParticles: Boolean = true,
    enableAurora: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "meshAnim")
    
    // Time-based palette
    val (primary, secondary, accent) = remember { BackgroundColors.getTimeBasedPalette() }
    val effectivePrimary = accentColor ?: primary
    
    // ─────────────────────────────────────────────────────────────────────────
    // BLOB ANIMATIONS - Organic Lissajous-like motion
    // ─────────────────────────────────────────────────────────────────────────
    
    // Blob 1: Primary - Slow, majestic orbit
    val blob1Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((18000 / intensity).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob1"
    )
    
    // Blob 2: Secondary - Medium speed, figure-8
    val blob2Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((14000 / intensity).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob2"
    )
    
    // Blob 3: Accent - Faster, erratic
    val blob3Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((10000 / intensity).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob3"
    )
    
    // Blob 4: Subtle background depth
    val blob4Phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween((25000 / intensity).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "blob4"
    )
    
    // ─────────────────────────────────────────────────────────────────────────
    // AURORA WAVE ANIMATION
    // ─────────────────────────────────────────────────────────────────────────
    
    val auroraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora"
    )
    
    // ─────────────────────────────────────────────────────────────────────────
    // GLOBAL BREATHING (subtle pulse that makes everything feel alive)
    // ─────────────────────────────────────────────────────────────────────────
    
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )
    
    // ─────────────────────────────────────────────────────────────────────────
    // PARTICLES (floating dust motes)
    // ─────────────────────────────────────────────────────────────────────────
    
    val particles = remember {
        if (enableParticles) {
            List(30) { ParticleData.random() }
        } else emptyList()
    }
    
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particles"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundColors.DeepSpace)
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2
        
        // ═══════════════════════════════════════════════════════════════════
        // LAYER 1: DEEP BACKGROUND GRADIENT
        // ═══════════════════════════════════════════════════════════════════
        
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    BackgroundColors.Void,
                    BackgroundColors.DeepSpace
                ),
                center = Offset(centerX, centerY * 0.7f),
                radius = maxOf(width, height) * 0.8f
            )
        )
        
        // ═══════════════════════════════════════════════════════════════════
        // LAYER 2: AURORA BOREALIS (if enabled)
        // ═══════════════════════════════════════════════════════════════════
        
        if (enableAurora) {
            drawAuroraLayer(
                phase = auroraPhase,
                primaryColor = effectivePrimary,
                secondaryColor = secondary,
                width = width,
                height = height,
                intensity = intensity * 0.15f
            )
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // LAYER 3: MAIN BLOBS (the "lava lamp" effect)
        // ═══════════════════════════════════════════════════════════════════
        
        // Blob 4: Deepest, largest, most subtle
        val x4 = centerX + (width * 0.4f) * sin(blob4Phase * 0.5f) * cos(blob4Phase * 0.3f)
        val y4 = centerY + (height * 0.3f) * cos(blob4Phase * 0.4f)
        drawOrganicBlob(
            center = Offset(x4, y4),
            baseRadius = width * 1.2f * breathingScale,
            color = secondary.copy(alpha = 0.08f * intensity),
            phase = blob4Phase,
            complexity = 3
        )
        
        // Blob 1: Primary - Upper region
        val x1 = centerX + (width * 0.35f) * sin(blob1Phase) * cos(blob1Phase * 0.7f)
        val y1 = height * 0.3f + (height * 0.2f) * sin(blob1Phase * 0.8f)
        drawOrganicBlob(
            center = Offset(x1, y1),
            baseRadius = width * 0.7f * breathingScale,
            color = effectivePrimary.copy(alpha = 0.25f * intensity),
            phase = blob1Phase,
            complexity = 5
        )
        
        // Blob 2: Secondary - Lower left, figure-8 motion
        val x2 = width * 0.25f + (width * 0.35f) * sin(blob2Phase) * cos(blob2Phase * 0.5f)
        val y2 = height * 0.65f + (height * 0.25f) * sin(blob2Phase * 1.3f)
        drawOrganicBlob(
            center = Offset(x2, y2),
            baseRadius = width * 0.85f * breathingScale,
            color = secondary.copy(alpha = 0.22f * intensity),
            phase = blob2Phase,
            complexity = 4
        )
        
        // Blob 3: Accent - Wanderer, moves more erratically
        val x3 = width * 0.7f + (width * 0.25f) * cos(blob3Phase * 1.5f)
        val y3 = height * 0.5f + (height * 0.35f) * sin(blob3Phase * 0.9f) * cos(blob3Phase * 0.4f)
        drawOrganicBlob(
            center = Offset(x3, y3),
            baseRadius = width * 0.6f * breathingScale,
            color = accent.copy(alpha = 0.2f * intensity),
            phase = blob3Phase,
            complexity = 6
        )
        
        // ═══════════════════════════════════════════════════════════════════
        // LAYER 4: HIGHLIGHT BLOBS (smaller, brighter, faster)
        // ═══════════════════════════════════════════════════════════════════
        
        // Highlight 1
        val hx1 = centerX + (width * 0.2f) * sin(blob1Phase * 1.5f)
        val hy1 = height * 0.4f + (height * 0.15f) * cos(blob1Phase * 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    effectivePrimary.copy(alpha = 0.4f * intensity),
                    Color.Transparent
                ),
                center = Offset(hx1, hy1),
                radius = width * 0.2f
            ),
            center = Offset(hx1, hy1),
            radius = width * 0.2f
        )
        
        // Highlight 2
        val hx2 = width * 0.3f + (width * 0.15f) * cos(blob2Phase * 2f)
        val hy2 = height * 0.7f + (height * 0.1f) * sin(blob2Phase * 1.8f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.35f * intensity),
                    Color.Transparent
                ),
                center = Offset(hx2, hy2),
                radius = width * 0.15f
            ),
            center = Offset(hx2, hy2),
            radius = width * 0.15f
        )
        
        // ═══════════════════════════════════════════════════════════════════
        // LAYER 5: FLOATING PARTICLES
        // ═══════════════════════════════════════════════════════════════════
        
        if (enableParticles) {
            particles.forEach { particle ->
                drawParticle(
                    particle = particle,
                    time = particleTime,
                    width = width,
                    height = height,
                    baseColor = effectivePrimary
                )
            }
        }
        
        // ═══════════════════════════════════════════════════════════════════
        // LAYER 6: SUBTLE VIGNETTE (focuses attention to center)
        // ═══════════════════════════════════════════════════════════════════
        
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    BackgroundColors.DeepSpace.copy(alpha = 0.4f)
                ),
                center = Offset(centerX, centerY),
                radius = maxOf(width, height) * 0.7f
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ORGANIC BLOB DRAWING (metaball-like soft edges)
// ═══════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawOrganicBlob(
    center: Offset,
    baseRadius: Float,
    color: Color,
    phase: Float,
    complexity: Int
) {
    // Create organic shape by combining multiple overlapping circles
    // with slightly offset centers based on phase
    
    val offsets = (0 until complexity).map { i ->
        val angle = (2f * PI.toFloat() * i / complexity) + phase
        val distance = baseRadius * 0.1f * sin(phase * (i + 1) * 0.3f)
        Offset(
            center.x + distance * cos(angle),
            center.y + distance * sin(angle)
        )
    }
    
    // Main blob
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = baseRadius
        ),
        center = center,
        radius = baseRadius
    )
    
    // Sub-blobs for organic feel
    offsets.forEachIndexed { index, offset ->
        val subRadius = baseRadius * (0.4f + 0.2f * sin(phase + index))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = color.alpha * 0.5f), Color.Transparent),
                center = offset,
                radius = subRadius
            ),
            center = offset,
            radius = subRadius
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// AURORA LAYER
// ═══════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawAuroraLayer(
    phase: Float,
    primaryColor: Color,
    secondaryColor: Color,
    width: Float,
    height: Float,
    intensity: Float
) {
    // Draw wavy aurora bands at the top
    val bands = 3
    
    repeat(bands) { bandIndex ->
        val yBase = height * 0.1f * (bandIndex + 1)
        val waveHeight = height * 0.05f
        val color = if (bandIndex % 2 == 0) primaryColor else secondaryColor
        
        // Create path points for wave
        val points = (0..20).map { i ->
            val x = (width * i / 20f)
            val waveOffset = sin(phase + (i * 0.5f) + (bandIndex * 0.3f)) * waveHeight
            Offset(x, yBase + waveOffset)
        }
        
        // Draw as overlapping circles for soft aurora effect
        points.forEach { point ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = intensity * 0.8f),
                        color.copy(alpha = intensity * 0.3f),
                        Color.Transparent
                    ),
                    center = point,
                    radius = height * 0.15f
                ),
                center = point,
                radius = height * 0.15f
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PARTICLE SYSTEM
// ═══════════════════════════════════════════════════════════════════════════════

private data class ParticleData(
    val startX: Float,    // 0-1 normalized
    val startY: Float,    // 0-1 normalized
    val size: Float,      // 1-4 dp
    val speed: Float,     // 0.5-2 multiplier
    val brightness: Float, // 0.3-1
    val phaseOffset: Float // 0-2PI
) {
    companion object {
        fun random() = ParticleData(
            startX = Random.nextFloat(),
            startY = Random.nextFloat(),
            size = Random.nextFloat() * 3f + 1f,
            speed = Random.nextFloat() * 1.5f + 0.5f,
            brightness = Random.nextFloat() * 0.7f + 0.3f,
            phaseOffset = Random.nextFloat() * 2f * PI.toFloat()
        )
    }
}

private fun DrawScope.drawParticle(
    particle: ParticleData,
    time: Float,
    width: Float,
    height: Float,
    baseColor: Color
) {
    // Particles float upward and drift side to side
    val adjustedTime = (time * particle.speed + particle.phaseOffset) % 1f
    
    val x = particle.startX * width + sin(adjustedTime * 2f * PI.toFloat() + particle.phaseOffset) * 30f
    val y = height - (adjustedTime * height * 1.2f) + (particle.startY * height * 0.3f)
    
    // Fade in at bottom, fade out at top
    val alpha = when {
        adjustedTime < 0.1f -> adjustedTime * 10f
        adjustedTime > 0.8f -> (1f - adjustedTime) * 5f
        else -> 1f
    } * particle.brightness
    
    if (y > -50 && y < height + 50) {
        // Glow
        drawCircle(
            color = baseColor.copy(alpha = alpha * 0.3f),
            radius = particle.size * 4f,
            center = Offset(x, y)
        )
        // Core
        drawCircle(
            color = Color.White.copy(alpha = alpha * 0.8f),
            radius = particle.size,
            center = Offset(x, y)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SIMPLIFIED VERSION (for performance on lower-end devices)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun MeshBackgroundLite(
    modifier: Modifier = Modifier,
    accentColor: Color? = null
) {
    MeshBackground(
        modifier = modifier,
        accentColor = accentColor,
        intensity = 0.7f,
        enableParticles = false,
        enableAurora = false
    )
}
