package com.vikify.app.vikifyui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

/**
 * Neon Glow Icon
 * 
 * An icon that emits a colored shadow bloom, simulating a neon light.
 */
@Composable
fun NeonGlowIcon(
    icon: ImageVector,
    contentDescription: String?,
    glowColor: Color,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 12.dp,
    iconSize: Dp = 24.dp
) {
    Box(modifier = modifier) {
        // The Glow (Drew behind)
        // Note: Real canvas blur is expensive. For icons, duplicate drawing with offset/alpha is cheaper 
        // or using native Paint with setMaskFilter(BlurMaskFilter)
        
        // We'll use a simplified approaching: drawing a colored circle behind for the bloom
        Box(
            modifier = Modifier
                .size(iconSize)
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val paint = Paint()
                        val frameworkPaint = paint.asFrameworkPaint()
                        frameworkPaint.color = glowColor.toArgb()
                        frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                            blurRadius.toPx(),
                            android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                        canvas.drawCircle(
                            center = center,
                            radius = size.width / 1.5f,
                            paint = paint
                        )
                    }
                }
        )
        
        // The Icon itself
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Bioluminescent Card Wrapper
 * 
 * Wraps content (like an ObsidianCard) and makes it "light up" (increase glow/brightness)
 * when touched.
 */
@Composable
fun BioluminescentCard(
    onClick: () -> Unit,
    glowColor: Color = Color(0xFF8B5CF6), // Violet default
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Animate the glow intensity
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "glowAlpha"
    )
    
    Box(
        modifier = modifier
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawIntoCanvas { canvas ->
                        val paint = Paint()
                        val frameworkPaint = paint.asFrameworkPaint()
                        frameworkPaint.color = glowColor.copy(alpha = glowAlpha).toArgb()
                        frameworkPaint.maskFilter = android.graphics.BlurMaskFilter(
                            30.dp.toPx(),
                            android.graphics.BlurMaskFilter.Blur.NORMAL
                        )
                        canvas.drawRect(
                            0f, 0f, size.width, size.height,
                            paint
                        )
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple
                onClick = onClick
            )
    ) {
        content()
    }
}
