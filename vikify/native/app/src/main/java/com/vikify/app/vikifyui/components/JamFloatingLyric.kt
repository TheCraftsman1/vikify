package com.vikify.app.vikifyui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Jam Mode: Shared Focus Layer
 * 
 * Displays the current lyric line as the "Hero" element.
 * Fades in/out smoothly to create a "campfire" shared reading experience.
 */
@Composable
fun JamFloatingLyric(
    lyrics: List<SyncedLyric>?,
    currentTimeMs: Long,
    modifier: Modifier = Modifier
) {
    // Logic to find current active line
    // Using derivedStateOf WITH currentTimeMs in the lambda body (not remember key)
    // ensures it recalculates whenever currentTimeMs changes
    val currentLine by remember(lyrics) {
        derivedStateOf {
            if (lyrics.isNullOrEmpty()) return@derivedStateOf null
            
            // Use 800ms offset (same as LyricsScreen.kt) for consistent sync
            val searchTime = currentTimeMs + 800L
            
            // Find the last line that has started
            lyrics.lastOrNull { it.timestamp <= searchTime }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = currentLine,
            transitionSpec = {
                // Smooth crossfade for calm vibe
                fadeIn(animationSpec = tween(600)) togetherWith 
                fadeOut(animationSpec = tween(400))
            },
            label = "lyricFade"
        ) { line ->
            if (line != null && line.text.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        // Subtle dark backing for readability over turntable
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            ),
                            alpha = 0.8f
                        )
                        .padding(24.dp)
                ) {
                    Text(
                        text = line.text,
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                blurRadius = 12f
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                // Empty state (instrumental break or start)
                // Render nothing or a peaceful placeholder
            }
        }
    }
}
