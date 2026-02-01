package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Obsidian Glass Card
 * 
 * A premium dark glass container with:
 * - High blur (simulated via semi-transparent dark layer, as real-time backdrop blur is expensive on Android < 12)
 * - Inner glow gradient to mimic light catching the edge
 * - Subtle border
 */
@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color(0xFF000000).copy(alpha = 0.6f),
    borderColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.08f),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    // Inner glow gradient (Top-Left White -> Bottom-Right Transparent)
    val innerGlow = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.1f),
            Color.White.copy(alpha = 0.02f),
            Color.Transparent
        ),
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor) // Base dark layer
            .background(innerGlow)       // Inner lighting effect
            .border(1.dp, borderColor, shape) // Subtle glass edge
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

/**
 * Modifier extension for Obsidian effect if you don't want a wrapper
 */
fun Modifier.obsidianGlass(
    shape: Shape = RoundedCornerShape(24.dp),
    alpha: Float = 0.6f
): Modifier = this
    .clip(shape)
    .background(Color(0xFF000000).copy(alpha = alpha))
    .background(
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.1f),
                Color.Transparent
            )
        )
    )
    .border(1.dp, Color(0xFFFFFFFF).copy(alpha = 0.08f), shape)
