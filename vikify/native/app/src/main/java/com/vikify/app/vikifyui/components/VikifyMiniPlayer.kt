/*
 * Copyright (C) 2025 Vikify
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * VikifyMiniPlayer - Battery-efficient "Dynamic Island" style mini player
 */

package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx. compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose. foundation.interaction.collectIsPressedAsState
import androidx. compose.foundation.layout.*
import androidx. compose.foundation.shape.CircleShape
import androidx.compose. foundation.shape.RoundedCornerShape
import androidx.compose.material. icons.Icons
import androidx.compose.material.icons.filled. Pause
import androidx.compose.material. icons.filled.PlayArrow
import androidx.compose.material. icons.filled. SkipNext
import androidx.compose.material.icons.filled. SkipPrevious
import androidx. compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui. draw.scale
import androidx.compose.ui.graphics. Brush
import androidx. compose.ui.graphics.Color
import androidx.compose.ui. graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx. compose.ui.input.pointer.pointerInput
import androidx. compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx. compose.ui.semantics.semantics
import androidx.compose. ui.text.font.FontWeight
import androidx.compose.ui.text. style.TextOverflow
import androidx.compose.ui.unit. dp
import androidx. compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vikify.app.vikifyui. data.Track

// Vikify Brand Colors
private val VIKIFY_BRAND_PRIMARY = Color(0xFF7C4DFF)
private val VIKIFY_BRAND_GLOW = Color(0xFFB388FF)

// Mini Player specific colors
private val PLAYER_BACKGROUND_TOP = Color(0xFF1A1A1A)
private val PLAYER_BACKGROUND_BOTTOM = Color(0xFF0D0D0D)

/**
 * VikifyMiniPlayer - "Dynamic Island" Style Mini Player
 *
 * Features:
 * - Swipe left/right for skip next/previous
 * - Tap to expand to full player
 * - Background progress indicator
 * - Battery-optimized animations using graphicsLayer
 * - OLED-friendly dark theme
 *
 * @param track Current playing track
 * @param isPlaying Playback state
 * @param progress Playback progress (0f to 1f)
 * @param accentColor Dynamic accent color (extracted from artwork)
 * @param onPlayPause Play/pause toggle callback
 * @param onExpand Expand to full player callback
 * @param onSkipNext Skip to next track callback
 * @param onSkipPrevious Skip to previous track callback
 * @param modifier Standard compose modifier
 */
@Composable
fun VikifyMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    accentColor:  Color = VIKIFY_BRAND_PRIMARY,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSkipPrevious:  () -> Unit = {},
    modifier:  Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // ═══════════════════════════════════════════════════════════════════════
    // SWIPE GESTURE STATE
    // ═══════════════════════════════════════════════════════════════════════
    
    // Raw drag offset - updated during drag
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    
    // Whether we're currently dragging
    var isDragging by remember { mutableStateOf(false) }
    
    // Animated offset - smoothly returns to 0 when drag ends
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetX else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "swipeReturn"
    )
    
    // Use raw offset during drag, animated offset otherwise
    val displayOffset = if (isDragging) dragOffsetX else animatedOffset
    
    val swipeThreshold = 100f
    val maxSwipeDistance = 150f

    // ═══════════════════════════════════════════════════════════════════════
    // PROGRESS ANIMATION (smooth fill)
    // ═══════════════════════════════════════════════════════════════════════
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress. coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progressFill"
    )

    // ═══════════════════════════════════════════════════════════════════════
    // PRE-CALCULATED GRADIENTS (avoid runtime allocation)
    // ═══════════════════════════════════════════════════════════════════════
    
    val glassGradient = remember {
        Brush. verticalGradient(
            colors = listOf(PLAYER_BACKGROUND_TOP, PLAYER_BACKGROUND_BOTTOM)
        )
    }

    val progressGradient = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.5f),
                accentColor.copy(alpha = 0.2f),
                accentColor.copy(alpha = 0.05f)
            )
        )
    }

    val topEdgeGlow = remember(accentColor) {
        Brush. horizontalGradient(
            colors = listOf(
                Color.Transparent,
                accentColor.copy(alpha = 0.6f),
                accentColor.copy(alpha = 0.6f),
                Color.Transparent
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACCESSIBILITY
    // ═══════════════════════════════════════════════════════════════════════
    
    val playerDescription = remember(track. title, track.artist, isPlaying, progress) {
        val playState = if (isPlaying) "Playing" else "Paused"
        val progressPercent = (progress * 100).toInt()
        "$playState:  ${track.title} by ${track.artist}. $progressPercent% complete.  " +
            "Tap to expand, swipe to skip tracks."
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RENDER
    // ════════════════════════════════════════════════════════════════════���══
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = playerDescription }
    ) {
        // ─────────────────────────────────────────────────────────────────────
        // SWIPE HINT INDICATORS (Fixed position, behind the player)
        // ─────────────────────────────────────────────────────────────────────
        
        // Skip Next indicator (appears on left when swiping left)
        SwipeHintIndicator(
            icon = Icons.Default. SkipNext,
            description = "Skip Next",
            alpha = ((-displayOffset) / swipeThreshold).coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        
        // Skip Previous indicator (appears on right when swiping right)
        SwipeHintIndicator(
            icon = Icons.Default. SkipPrevious,
            description = "Skip Previous",
            alpha = (displayOffset / swipeThreshold).coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // ─────────────────────────────────────────────────────────────────────
        // MAIN PLAYER CONTAINER
        // ─────────────────────────────────────────────────────────────────────
        
        Box(
            modifier = Modifier
                . fillMaxWidth()
                .height(68.dp)
                .graphicsLayer {
                    // Battery optimization:  translate without recomposition
                    translationX = displayOffset
                    // Subtle scale feedback during drag
                    val dragProgress = (kotlin.math.abs(displayOffset) / maxSwipeDistance)
                    scaleX = 1f - (dragProgress * 0.02f)
                    scaleY = 1f - (dragProgress * 0.02f)
                }
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                        },
                        onDragEnd = {
                            when {
                                dragOffsetX < -swipeThreshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSkipNext()
                                }
                                dragOffsetX > swipeThreshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSkipPrevious()
                                }
                            }
                            isDragging = false
                            dragOffsetX = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffsetX = (dragOffsetX + dragAmount)
                                .coerceIn(-maxSwipeDistance, maxSwipeDistance)
                            
                            // Haptic feedback at threshold
                            if (kotlin.math.abs(dragOffsetX) >= swipeThreshold &&
                                kotlin.math.abs(dragOffsetX - dragAmount) < swipeThreshold) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null // Custom feedback via graphicsLayer
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExpand()
                }
        ) {
            // Layer 1: Pure black base (OLED optimization)
            Box(
                modifier = Modifier
                    . fillMaxSize()
                    .background(Color.Black)
            )

            // Layer 2: Glass gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glassGradient)
            )

            // Layer 3: Progress fill
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    . fillMaxWidth(animatedProgress)
                    .background(progressGradient)
            )

            // Layer 4: Top edge glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    . height(1.5.dp)
                    .align(Alignment.TopCenter)
                    .background(topEdgeGlow)
            )

            // Layer 5: Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    . padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                MiniPlayerArtwork(
                    url = track.remoteArtworkUrl,
                    fallbackResId = null, // track.artwork is Any? but we need Int? or similar. Safe to pass null for now.
                    contentDescription = "${track.title} album art"
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Track Info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow. Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause Button
                PlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    }
                )
            }
        }
    }
}

/**
 * Swipe hint indicator that appears during swipe gestures
 */
@Composable
private fun SwipeHintIndicator(
    icon: androidx.compose.ui. graphics.vector.ImageVector,
    description: String,
    alpha: Float,
    modifier:  Modifier = Modifier
) {
    if (alpha > 0.05f) {
        Box(
            modifier = modifier
                . padding(horizontal = 24.dp)
                .graphicsLayer { this.alpha = alpha }
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(VIKIFY_BRAND_PRIMARY. copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = VIKIFY_BRAND_GLOW,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Album artwork with loading/error states
 */
@Composable
private fun MiniPlayerArtwork(
    url: String?,
    fallbackResId: Int?,
    contentDescription:  String,
    modifier: Modifier = Modifier
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter. State?>(null) }
    
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale. Crop,
                modifier = Modifier. fillMaxSize(),
                onState = { imageState = it }
            )
        }
        
        // Show placeholder on error or while loading
        if (imageState is AsyncImagePainter.State. Error || 
            imageState is AsyncImagePainter.State.Loading ||
            url == null) {
            // Fallback gradient placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                VIKIFY_BRAND_PRIMARY. copy(alpha = 0.3f),
                                VIKIFY_BRAND_PRIMARY.copy(alpha = 0.1f)
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Animated play/pause button with press feedback
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring. DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "buttonScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) 
            VIKIFY_BRAND_PRIMARY.copy(alpha = 0.8f) 
        else 
            VIKIFY_BRAND_PRIMARY,
        animationSpec = tween(100),
        label = "buttonColor"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}