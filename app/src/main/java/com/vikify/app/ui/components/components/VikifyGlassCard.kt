package com.vikify.app.ui.components.components

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

import com.vikify.app.ui.theme.VikifyTheme

@Composable
fun VikifyGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    // VIKIFY GLASS CARD
    // Theme-aware translucent "Frosted Glass" with Holographic Edge
    
    val backgroundColor = VikifyTheme.colors.glassBackground
    
    // Holographic Border: Theme-aware gradient
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            VikifyTheme.colors.glassBorder,
            VikifyTheme.colors.glassBorder.copy(alpha = 0.1f),
            Color.Transparent
        )
    )
    
    Box(
        modifier = modifier
            // 1. Clip to shape
            .clip(shape)
            // 2. Translucent Tint
            .background(backgroundColor)
            // 3. Holographic Border
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
