/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Home Screen Enhancements - Micro-interactions & Delight
 */
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════════════════
// PARALLAX SCROLL MODIFIERS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Adds parallax effect to cards based on scroll position
 * Cards further from center have more tilt and slight scale reduction
 */
@Composable
fun Modifier.parallaxCard(
    lazyListState: LazyListState,
    index: Int,
    maxRotation: Float = 3f,
    maxScale: Float = 0.03f
): Modifier {
    val density = LocalDensity.current
    
    val parallaxOffset by remember(lazyListState) {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == index }
            
            if (visibleItem != null) {
                // Use width for horizontal LazyRows
                val viewportCenter = layoutInfo.viewportSize.width / 2f
                val itemCenter = visibleItem.offset + visibleItem.size / 2f
                val distanceFromCenter = (itemCenter - viewportCenter) / viewportCenter
                distanceFromCenter.coerceIn(-1f, 1f)
            } else {
                0f
            }
        }
    }
    
    return this.graphicsLayer {
        // Subtle rotation based on scroll position - Rotate Y for cover flow effect
        // Invert offset so left items face right (positive rotation)
        rotationY = -parallaxOffset * maxRotation
        
        // Slight scale reduction for items at edges
        val scaleReduction = abs(parallaxOffset) * maxScale
        scaleX = 1f - scaleReduction
        scaleY = 1f - scaleReduction
        
        // Camera distance for 3D effect
        cameraDistance = 12f * density.density
    }
}

/**
 * Adds floating animation to hero cards
 */
@Composable
fun Modifier.floatingCard(
    floatDistance: Float = 8f,
    duration: Int = 3000
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "float")
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -floatDistance,
        targetValue = floatDistance,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    
    return this.graphicsLayer {
        translationY = offsetY
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PRESS INTERACTION MODIFIER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Adds premium press feedback with scale, shadow, and subtle rotation
 */
@Composable
fun Modifier.premiumPressable(
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    scaleOnPress: Float = 0.96f,
    rotationOnPress: Float = -2f
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleOnPress else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pressScale"
    )
    
    val rotation by animateFloatAsState(
        targetValue = if (isPressed) rotationOnPress else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "pressRotation"
    )
    
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = rotation
        }
        .pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    try {
                        awaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = { onClick() },
                onLongPress = { onLongPress?.invoke() }
            )
        }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STAGGERED ENTRANCE ANIMATION
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Adds staggered fade-in and slide-up animation based on index
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelay: Long = 30L,
    slideDistance: Float = 50f
): Modifier {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(index * baseDelay)
        isVisible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "staggerAlpha"
    )
    
    val offsetY by animateFloatAsState(
        targetValue = if (isVisible) 0f else slideDistance,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "staggerOffset"
    )
    
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHIMMER LOADING EFFECT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.shimmerEffect(
    baseColor: Color = Color(0xFF1A1A1A),
    highlightColor: Color = Color(0xFF2D2D2D)
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )
    
    // This would need a custom DrawModifier for true shimmer
    // For now, return the modifier with alpha animation
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    
    return this.graphicsLayer {
        this.alpha = alpha
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLOW EFFECT FOR PLAYING ITEMS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.playingGlow(
    isPlaying: Boolean,
    glowColor: Color,
    intensity: Float = 0.6f
): Modifier {
    if (!isPlaying) return this
    
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = intensity * 0.5f,
        targetValue = intensity,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    // In a real implementation, this would use a custom shadow/glow DrawModifier
    return this.graphicsLayer {
        // Placeholder - actual glow would need custom drawing
        shadowElevation = 8f * glowAlpha
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SPRING BOUNCE ON APPEAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun Modifier.bounceOnAppear(
    initialScale: Float = 0.8f,
    overshoot: Float = 1.1f
): Modifier {
    var hasAppeared by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        hasAppeared = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (hasAppeared) 1f else initialScale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bounceScale"
    )
    
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TIME-AWARE GREETING COMPONENT DATA
// ═══════════════════════════════════════════════════════════════════════════════

data class TimeAwareGreeting(
    val greeting: String,
    val emoji: String,
    val subtitle: String,
    val suggestedMood: String
) {
    companion object {
        fun current(): TimeAwareGreeting {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return when {
                hour in 5..8 -> TimeAwareGreeting(
                    greeting = "Rise & Shine",
                    emoji = "🌅",
                    subtitle = "Start your day with energy",
                    suggestedMood = "Energizing"
                )
                hour in 9..11 -> TimeAwareGreeting(
                    greeting = "Good Morning",
                    emoji = "☀️",
                    subtitle = "What's the vibe today?",
                    suggestedMood = "Focus"
                )
                hour in 12..14 -> TimeAwareGreeting(
                    greeting = "Good Afternoon",
                    emoji = "🎵",
                    subtitle = "Perfect time for some tunes",
                    suggestedMood = "Upbeat"
                )
                hour in 15..17 -> TimeAwareGreeting(
                    greeting = "Hey There",
                    emoji = "🎧",
                    subtitle = "Winding down?",
                    suggestedMood = "Chill"
                )
                hour in 18..20 -> TimeAwareGreeting(
                    greeting = "Good Evening",
                    emoji = "🌆",
                    subtitle = "Time to unwind",
                    suggestedMood = "Relaxing"
                )
                hour in 21..23 -> TimeAwareGreeting(
                    greeting = "Night Owl",
                    emoji = "🌙",
                    subtitle = "Late night session?",
                    suggestedMood = "Mellow"
                )
                else -> TimeAwareGreeting(
                    greeting = "Still Up?",
                    emoji = "✨",
                    subtitle = "The night is young",
                    suggestedMood = "Deep"
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DELIGHT MOMENTS - Random surprises
// ═══════════════════════════════════════════════════════════════════════════════

object DelightMoments {
    private val pullToRefreshMessages = listOf(
        "Mixing fresh beats... 🎧",
        "Scanning the universe for bangers... 🚀",
        "Consulting the music gods... 🎵",
        "Warming up the turntables... 💿",
        "Downloading good vibes... ✨",
        "Asking AI for recommendations... 🤖"
    )
    
    private val emptyStateMessages = listOf(
        "This is where the magic happens ✨",
        "Your journey starts here 🎵",
        "Ready when you are 🎧",
        "Let's discover something new 🔍"
    )
    
    fun getRefreshMessage(): String = pullToRefreshMessages.random()
    fun getEmptyMessage(): String = emptyStateMessages.random()
    
    // Easter egg: specific day greetings
    fun getSpecialGreeting(): String? {
        val calendar = java.util.Calendar.getInstance()
        val month = calendar.get(java.util.Calendar.MONTH)
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        
        return when {
            month == 0 && day == 1 -> "🎉 Happy New Year! New music, new vibes!"
            month == 9 && day == 31 -> "🎃 Spooky season calls for dark beats!"
            month == 11 && day == 25 -> "🎄 'Tis the season for festive jams!"
            month == 1 && day == 14 -> "💕 Love songs loading..."
            else -> null
        }
    }
}
