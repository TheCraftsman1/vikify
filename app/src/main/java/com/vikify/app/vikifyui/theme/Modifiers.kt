package com.vikify.app.vikifyui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/**
 * High-Fidelity UI Modifiers
 * 
 * Premium micro-interactions for "4K" UI quality:
 * - bounceClick(): Spring physics for tactile button feel
 * - scrim(): Gradient overlay for text legibility on images
 * - tactilePress(): Subtle press feedback
 */

// ═══════════════════════════════════════════════════════════════
// BOUNCE CLICK - Physics-Based Touch
// ═══════════════════════════════════════════════════════════════

/**
 * "Tactile" button modifier with spring physics.
 * 
 * Creates the "expensive" feel of premium apps:
 * - Scale: 0.95f when pressed, 1.0f when released
 * - Animation: Spring with MediumBouncy damping
 * - Stiffness: Low for fluid motion
 */
fun Modifier.bounceClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    scaleWhenPressed: Float = 0.95f
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleWhenPressed else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null, // Remove ripple for cleaner look
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Alternative bounce click using gesture detection.
 * More control over press states.
 */
fun Modifier.tactileClick(
    onClick: () -> Unit,
    scaleWhenPressed: Float = 0.96f
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleWhenPressed else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tactileScale"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick() }
            )
        }
}

// ═══════════════════════════════════════════════════════════════
// LEGIBILITY SCRIM - Text Contrast on Images
// ═══════════════════════════════════════════════════════════════

/**
 * The "4K" Legibility Scrim Brush.
 * 
 * Makes text laser-sharp readable on ANY album art.
 * Mathematically perfect gradient: Transparent -> 80% Black.
 */
val ScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
    startY = 0f,
    endY = Float.POSITIVE_INFINITY
)

/**
 * Lighter scrim for cards (less aggressive)
 */
val LightScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
    startY = 0f,
    endY = Float.POSITIVE_INFINITY
)

/**
 * Top scrim (for status bar area)
 */
val TopScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
    startY = 0f,
    endY = Float.POSITIVE_INFINITY
)

/**
 * Apply a scrim overlay to bottom of content.
 * Use on Album Art before drawing text.
 */
fun Modifier.scrim(alpha: Float = 0.8f): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha)),
            startY = size.height * 0.5f, // Start scrim at 50% height
            endY = size.height
        )
    )
}

/**
 * Full height scrim (for backgrounds)
 */
fun Modifier.fullScrim(alpha: Float = 0.6f): Modifier = this.drawWithContent {
    drawContent()
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = alpha)),
            startY = 0f,
            endY = size.height
        )
    )
}

/**
 * Bottom gradient background (for text overlays)
 */
fun Modifier.bottomGradient(
    color: Color = Color.Black,
    alpha: Float = 0.7f
): Modifier = this.background(
    Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            color.copy(alpha = alpha * 0.3f),
            color.copy(alpha = alpha)
        )
    )
)

// ═══════════════════════════════════════════════════════════════
// PREMIUM EFFECTS
// ═══════════════════════════════════════════════════════════════

/**
 * Subtle hover/press elevation effect
 */
fun Modifier.elevateOnPress(): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateFloatAsState(
        targetValue = if (isPressed) 2f else 8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevationAnim"
    )
    
    this.graphicsLayer {
        shadowElevation = elevation
    }
}
