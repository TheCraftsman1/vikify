/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Jam Reaction Overlay - Floating emoji animations
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.JamReaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Overlay that displays floating emojis when reactions are received.
 */
@Composable
fun JamReactionOverlay(
    reactions: Map<String, JamReaction>,
    modifier: Modifier = Modifier
) {
    // Local state to track currently animating emojis
    val animatingEmojis = remember { mutableStateListOf<AnimatableReaction>() }
    val latestTimestamp = remember { mutableLongStateOf(0L) }
    
    // Observe reactions map for changes
    LaunchedEffect(reactions) {
        val newTimestamp = reactions.values.maxOfOrNull { it.timestamp } ?: 0L
        if (newTimestamp > latestTimestamp.longValue) {
            // Find new reactions
            val newReactions = reactions.values.filter { it.timestamp > latestTimestamp.longValue }
            
            // Add to animation list
            newReactions.forEach { reaction ->
                // Add multiple instances for a "burst" effect
                repeat(Random.nextInt(1, 4)) {
                    animatingEmojis.add(
                        AnimatableReaction(
                            id = reaction.senderId + reaction.timestamp + it,
                            emoji = reaction.emoji,
                            startX = Random.nextFloat() * 0.8f - 0.4f, // Random horizontal position
                            startDelay = it * 100L
                        )
                    )
                }
            }
            latestTimestamp.longValue = newTimestamp
        }
    }
    
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        animatingEmojis.forEach { item ->
            key(item.id) {
                FloatingEmoji(
                    emoji = item.emoji,
                    startX = item.startX,
                    startDelay = item.startDelay,
                    onAnimationFinished = {
                        animatingEmojis.remove(item)
                    }
                )
            }
        }
    }
}

data class AnimatableReaction(
    val id: String,
    val emoji: String,
    val startX: Float,
    val startDelay: Long
)

@Composable
fun FloatingEmoji(
    emoji: String,
    startX: Float,
    startDelay: Long,
    onAnimationFinished: () -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val travelDistance = with(density) { screenHeight.toPx() * 0.6f } // Travel up 60% of screen
    
    val yOffset = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(0.5f) }
    
    LaunchedEffect(Unit) {
        delay(startDelay)
        
        launch {
            scale.animateTo(
                targetValue = 1.5f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = LinearEasing)
            )
        }
        
        launch {
            yOffset.animateTo(
                targetValue = -travelDistance,
                animationSpec = tween(
                    durationMillis = 2000,
                    easing = FastOutSlowInEasing
                )
            )
        }
        
        launch {
            delay(1000)
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(1000)
            )
            onAnimationFinished()
        }
    }
    
    Text(
        text = emoji,
        fontSize = 32.sp,
        modifier = Modifier
            .offset(x = (startX * 300).dp) // Spread horizontally
            .graphicsLayer {
                translationY = yOffset.value
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
    )
}
