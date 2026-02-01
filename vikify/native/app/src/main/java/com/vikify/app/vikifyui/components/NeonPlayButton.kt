/*
 * Copyright (C) 2025 Vikify
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * NeonPlayButton - Amplitude-driven custom Canvas play/pause button
 * Uses pre-calculated blur paint and draws directly without recomposition
 */

package com.vikify.app.vikifyui.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * NeonPlayButton - Custom Canvas-Drawn Play/Pause Button
 *
 * BATTERY OPTIMIZATIONS:
 * 1. BlurMaskFilter paint cached in remember (expensive operation)
 * 2. Amplitude read directly in draw scope (no recomposition)
 * 3. Icon drawn with primitives, not Material Icon composable
 *
 * DESIGN:
 * - Neon gradient button with glow halo
 * - Pulse radius based on audio amplitude
 * - Vikify brand colors: Cyan → Purple → Pink
 */
@Composable
fun NeonPlayButton(
    isPlaying: Boolean,
    amplitude: Float = 0f,  // 0f-1f from EcoVisualizerViewModel
    onClick: () -> Unit,
    accentColor: Color = Color(0xFF00F0FF),  // Neon Cyan
    modifier: Modifier = Modifier,
    size: Dp = 80.dp
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    // ─────────────────────────────────────────────────────────────────────────
    // PRE-CALCULATED GLOW PAINT (Expensive operation - cache in remember)
    // ─────────────────────────────────────────────────────────────────────────
    val glowPaint = remember(accentColor) {
        Paint().asFrameworkPaint().apply {
            color = accentColor.toArgb()
            maskFilter = BlurMaskFilter(50f, BlurMaskFilter.Blur.NORMAL)
        }
    }

    // Secondary glow for outer halo
    val outerGlowPaint = remember {
        Paint().asFrameworkPaint().apply {
            color = Color(0xFFFF00CC).copy(alpha = 0.3f).toArgb()  // Pink
            maskFilter = BlurMaskFilter(80f, BlurMaskFilter.Blur.OUTER)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GRADIENT BRUSH (Pre-calculated)
    // ─────────────────────────────────────────────────────────────────────────
    val gradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF00F0FF),  // Neon Cyan
                Color(0xFF6200EA),  // Deep Purple
                Color(0xFFFF00CC)   // Neon Pink
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CANVAS (All drawing happens here - bypasses recomposition)
    // ─────────────────────────────────────────────────────────────────────────
    Canvas(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = interactionSource,
                indication = null  // No ripple - we handle feedback ourselves
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
    ) {
        val center = Offset(this.size.width / 2, this.size.height / 2)
        val baseRadius = this.size.minDimension / 2 * 0.85f

        // AMPLITUDE PULSE: Glow radius increases with bass
        // Read amplitude directly in draw - no recomposition!
        val pulseRadius = baseRadius + (amplitude * 15f)

        // ─────────────────────────────────────────────────────────────────
        // LAYER 1: Outer glow halo (subtle pulse)
        // ─────────────────────────────────────────────────────────────────
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y,
                pulseRadius + 20f,
                outerGlowPaint
            )
        }

        // ─────────────────────────────────────────────────────────────────
        // LAYER 2: Primary neon glow
        // ─────────────────────────────────────────────────────────────────
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                center.x,
                center.y,
                pulseRadius,
                glowPaint
            )
        }

        // ─────────────────────────────────────────────────────────────────
        // LAYER 3: Gradient core button
        // ─────────────────────────────────────────────────────────────────
        drawCircle(
            brush = gradientBrush,
            radius = baseRadius,
            center = center
        )

        // ─────────────────────────────────────────────────────────────────
        // LAYER 4: White border (premium feel)
        // ─────────────────────────────────────────────────────────────────
        drawCircle(
            color = Color.White.copy(alpha = 0.3f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = 2f)
        )

        // ─────────────────────────────────────────────────────────────────
        // LAYER 5: Play or Pause icon
        // ─────────────────────────────────────────────────────────────────
        if (isPlaying) {
            // PAUSE: Two vertical bars
            val barWidth = baseRadius * 0.22f
            val barHeight = baseRadius * 0.55f
            val gap = barWidth * 0.7f

            // Left bar
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(
                    center.x - gap - barWidth,
                    center.y - barHeight / 2
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )

            // Right bar
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(
                    center.x + gap,
                    center.y - barHeight / 2
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        } else {
            // PLAY: Triangle icon (slightly offset right for optical center)
            val triangleSize = baseRadius * 0.45f
            val offsetX = baseRadius * 0.08f  // Optical center correction

            val path = Path().apply {
                moveTo(
                    center.x - triangleSize * 0.4f + offsetX,
                    center.y - triangleSize * 0.6f
                )
                lineTo(
                    center.x - triangleSize * 0.4f + offsetX,
                    center.y + triangleSize * 0.6f
                )
                lineTo(
                    center.x + triangleSize * 0.6f + offsetX,
                    center.y
                )
                close()
            }
            drawPath(path, color = Color.White)
        }
    }
}

/**
 * NeonPlayButtonMini - Smaller version for mini player
 */
@Composable
fun NeonPlayButtonMini(
    isPlaying: Boolean,
    amplitude: Float = 0f,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeonPlayButton(
        isPlaying = isPlaying,
        amplitude = amplitude,
        onClick = onClick,
        size = 48.dp,
        modifier = modifier
    )
}
