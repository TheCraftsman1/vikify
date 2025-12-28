package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.theme.MistBlue

/**
 * VaporCard - Ethereal Day Component
 * Frosted glass interface with colored ambient light shadows.
 */
@Composable
fun VaporCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color.White.copy(alpha = 0.7f),
    ambientColor: Color = MistBlue, // Variable shadow color based on content
    elevation: Dp = 0.dp, // Unused, we use coloredShadow
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .coloredShadow(
                color = ambientColor,
                alpha = 0.4f,
                borderRadius = 24.dp,
                blurRadius = 30.dp,
                offsetY = 8.dp,
                spread = 0.dp
            )
            .clip(shape)
            .background(backgroundColor)
    ) {
        content()
    }
}

/**
 * Modifier to draw a colored shadow behind the content.
 * Essential for the "Colored Ambient Light" effect.
 */
fun Modifier.coloredShadow(
    color: Color,
    alpha: Float = 0.2f,
    borderRadius: Dp = 0.dp,
    blurRadius: Dp = 20.dp,
    offsetY: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    spread: Dp = 0.dp
) = this.drawBehind {
    val shadowColor = color.copy(alpha = alpha).toArgb()
    val transparent = color.copy(alpha = 0f).toArgb()

    this.drawIntoCanvas {
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.color = transparent
        frameworkPaint.setShadowLayer(
            blurRadius.toPx(),
            offsetX.toPx(),
            offsetY.toPx(),
            shadowColor
        )
        it.drawRoundRect(
            0f - spread.toPx(),
            0f - spread.toPx(),
            this.size.width + spread.toPx(),
            this.size.height + spread.toPx(),
            borderRadius.toPx(),
            borderRadius.toPx(),
            paint
        )
    }
}
