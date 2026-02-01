package com.vikify.app.vikifyui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VikifyGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    // VIKIFY GLASS CARD
    // Translucent "Frosted Glass" with Holographic Edge
    // Distinct from Spotify's solid colors.
    
    val backgroundColor = Color.White.copy(alpha = 0.05f) // Ultra-thin tint
    
    // Holographic Border: White to Transparent Gradient
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.1f),
            Color.Transparent
        )
    )
    
    Box(
        modifier = modifier
            // 1. Clip to shape
            .clip(shape)
            // 2. Background Blur (removed to prevent content blurring)
            // .then(...)
            // 3. Translucent Tint
            .background(backgroundColor)
            // 4. Holographic Border
            .border(BorderStroke(1.dp, borderBrush), shape)
            .padding(contentPadding), // Internal padding
        content = content
    )
}


@Composable
fun VikifyGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit
) {
    // Slightly more opaque for heavily interactive elements
    VikifyGlassCard(
        modifier = modifier,
        shape = shape,
        content = content
    )
}
