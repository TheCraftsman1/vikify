/*
 * Copyright (C) 2025 Vikify
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * RealtimeWaveform - FFT-based audio visualizer with draw-phase optimization
 * Reads amplitude State inside onDraw to bypass composition entirely
 */

package com.vikify.app.vikifyui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RealtimeWaveform - FFT-Based Audio Visualizer
 *
 * THE KEY OPTIMIZATION:
 * `amplitudes.value` is read INSIDE the Canvas lambda (draw phase).
 * This means:
 * - No recomposition when FFT data changes
 * - Only the draw phase is invalidated
 * - ~30fps draw updates WITHOUT ~30fps recompositions
 *
 * DESIGN:
 * - Neon bars with glow effect for played section
 * - Height from real FFT data (not fake sine waves)
 * - Scrubbing/seeking via tap and drag
 */
@Composable
fun RealtimeWaveform(
    amplitudes: State<FloatArray>,  // State<T>, not T - read in draw!
    progress: Float,
    accentColor: Color = Color(0xFF00F0FF),
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    height: Dp = 48.dp
) {
    // ─────────────────────────────────────────────────────────────────────────
    // PRE-CALCULATED COLORS (Avoid runtime color creation)
    // ─────────────────────────────────────────────────────────────────────────
    val playedColor = remember(accentColor) { accentColor }
    val unplayedColor = remember { Color.White.copy(alpha = 0.35f) }
    val glowColor = remember(accentColor) { accentColor.copy(alpha = 0.5f) }
    val secondaryGlow = remember { Color(0xFF6200EA).copy(alpha = 0.3f) }

    // ─────────────────────────────────────────────────────────────────────────
    // CANVAS (All drawing happens in draw phase - zero recomposition)
    // ─────────────────────────────────────────────────────────────────────────
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Seek by tap
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(seekProgress)
                }
            }
            // Seek by drag
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val seekProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(seekProgress)
                }
            }
    ) {
        // ═══════════════════════════════════════════════════════════════════
        // READ AMPLITUDES INSIDE onDraw - BYPASSES COMPOSITION!
        // ═══════════════════════════════════════════════════════════════════
        val amps = amplitudes.value
        val effectiveBarCount = barCount.coerceAtMost(amps.size)

        val barWidth = size.width / (effectiveBarCount * 2f)
        val gap = barWidth * 0.6f
        val totalBarSpace = effectiveBarCount * (barWidth + gap)
        val startX = (size.width - totalBarSpace) / 2f

        for (i in 0 until effectiveBarCount) {
            val barProgress = i.toFloat() / effectiveBarCount
            val isPlayed = barProgress <= progress

            // Height from FFT amplitude (real audio data!)
            val amp = amps.getOrElse(i) { 0f }
            // Minimum height + amplitude contribution
            val barHeight = (0.15f + amp * 0.85f).coerceIn(0.1f, 1f) * size.height

            val x = startX + i * (barWidth + gap)
            val y = (size.height - barHeight) / 2

            val color = if (isPlayed) playedColor else unplayedColor

            // ─────────────────────────────────────────────────────────────
            // GLOW LAYERS (Only for played bars)
            // ─────────────────────────────────────────────────────────────
            if (isPlayed) {
                // Primary cyan glow
                drawRoundRect(
                    color = glowColor,
                    topLeft = Offset(x - 2f, y - 2f),
                    size = Size(barWidth + 4f, barHeight + 4f),
                    cornerRadius = CornerRadius(barWidth)
                )

                // Secondary purple glow (offset for depth)
                drawRoundRect(
                    color = secondaryGlow,
                    topLeft = Offset(x - 3f, y - 3f),
                    size = Size(barWidth + 6f, barHeight + 6f),
                    cornerRadius = CornerRadius(barWidth + 2f)
                )
            }

            // ─────────────────────────────────────────────────────────────
            // CORE BAR
            // ─────────────────────────────────────────────────────────────
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }

        // ─────────────────────────────────────────────────────────────────
        // PROGRESS INDICATOR LINE (subtle)
        // ─────────────────────────────────────────────────────────────────
        val progressX = size.width * progress.coerceIn(0f, 1f)
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(progressX, 0f),
            end = Offset(progressX, size.height),
            strokeWidth = 2f
        )
    }
}

/**
 * RealtimeWaveformCompact - Smaller version for compact layouts
 */
@Composable
fun RealtimeWaveformCompact(
    amplitudes: State<FloatArray>,
    progress: Float,
    accentColor: Color = Color(0xFF00F0FF),
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    RealtimeWaveform(
        amplitudes = amplitudes,
        progress = progress,
        accentColor = accentColor,
        onSeek = onSeek,
        modifier = modifier,
        barCount = 24,
        height = 36.dp
    )
}
