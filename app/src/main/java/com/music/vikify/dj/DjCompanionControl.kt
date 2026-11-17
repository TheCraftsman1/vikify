package com.music.vikify.dj

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.vikify.data.settings.AppSettings
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class DjCardState(val active: Boolean, val loading: Boolean, val speaking: Boolean)

@Composable
fun DjCompanionCard(modifier: Modifier = Modifier, songId: String?, playing: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    // Listening counters tick frequently. They must not recompose the Home card.
    val state by remember {
        DjPlayback.state.map { DjCardState(it.active, it.loading, it.speaking) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = DjCardState(false, false, false))
    val reduced by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val emotion = when {
        state.speaking -> DjEmotion.SPEAKING
        state.loading -> DjEmotion.THINKING
        playing -> DjEmotion.HAPPY
        state.active -> DjEmotion.SLEEPY
        else -> DjEmotion.HAPPY
    }
    val secondary = DjStyle.text.copy(alpha = .68f)
    val fill = if (compact) Brush.horizontalGradient(listOf(Color.White.copy(alpha = .10f), Color.White.copy(alpha = .06f)))
        else Brush.linearGradient(listOf(Color(0xFF0E2458), Color(0xFF07133A)))
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 18.dp else 24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, if (compact) Color.White.copy(alpha = .10f) else DjStyle.glassBorder.copy(alpha = .85f)),
    ) {
        Row(
            Modifier.background(fill).padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Same soft blue halo as the studio, scaled down for the card.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.drawBehind {
                drawCircle(Brush.radialGradient(listOf(DjStyle.glow.copy(alpha = .5f), DjStyle.glow.copy(alpha = .16f), Color.Transparent),
                    center, size.minDimension * .6f), size.minDimension * .6f)
            }) {
                VikifyMascot(size = if (compact) 48.dp else 64.dp, emotion = emotion, newSong = songId, reduceMotion = reduced)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(when { state.loading -> "Finding your next songs…"; state.active -> "DJ AI · On air"; else -> "DJ AI" },
                    style = MaterialTheme.typography.labelMedium, color = if (compact) secondary else DjStyle.cyan,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (compact) "Pick a song or send a request" else "Let me pick a song for you",
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                    color = DjStyle.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = secondary, modifier = Modifier.size(20.dp))
        }
    }
}
