package com.vikify.app.vikifyui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*

// Lyric line data class
data class SyncedLyric(val timestamp: Long, val text: String)

@Composable
fun LyricsScreen(
    track: Track,
    isPlaying: Boolean,
    currentTimeMs: Long,
    totalDurationMs: Long = 240000L,
    lyrics: List<SyncedLyric>?,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // Theme colors for Dual Identity support
    val colors = VikifyTheme.colors
    val isDark = VikifyTheme.isDark
    
    // Determine active line based on timestamp
    // Add 800ms offset to compensate for UI update delay and make lyrics feel "ahead"
    val safeLyrics = lyrics ?: emptyList() 
    val adjustedTimeMs = currentTimeMs + 800L // Offset to sync lyrics better
    
    val activeIndex = safeLyrics.indexOfLast { it.timestamp <= adjustedTimeMs }.coerceAtLeast(0)
    
    val listState = rememberLazyListState()

    // Auto-scroll logic
    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
    ) {
        // 1. Atmospheric Background
        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred Album Art
            VikifyImage(
                url = track.remoteArtworkUrl,
                placeholder = track.artwork,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(60.dp),
                contentScale = ContentScale.Crop
            )
            // Overlay for Contrast (Studio: lighter, Aurora: darker)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isDark) Color.Black.copy(alpha = 0.6f)
                        else colors.surfaceBackground.copy(alpha = 0.75f)
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 2. Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = colors.textPrimary
                    )
                ) {
                    Icon(Icons.Rounded.Close, "Close", modifier = Modifier.size(28.dp))
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = track.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                IconButton(
                    onClick = { /* Options */ },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = colors.textPrimary
                    )
                ) {
                    Icon(Icons.Rounded.MoreHoriz, "Options")
                }
            }

            // 3. Lyrics List or Empty State
            if (safeLyrics.isEmpty()) {
                // Empty state - No lyrics available
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🎵",
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "No lyrics available",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We couldn't find lyrics for this track",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 40.dp, horizontal = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(safeLyrics) { index, line ->
                    val isActive = index == activeIndex
                    
                    // Animate properties for smooth focus effect
                    // Studio Mode: Charcoal to light gray
                    // Aurora Mode: White to dimmed white
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) colors.lyricsActive 
                                     else colors.lyricsInactive,
                        animationSpec = tween(300), 
                        label = "color"
                    )
                    val textScale by animateFloatAsState(
                        targetValue = if (isActive) 1.0f else 0.95f,
                        animationSpec = tween(300),
                        label = "scale"
                    )
                    
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 42.sp
                        ),
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                // Calculate progress based on timestamp using actual duration
                                val progress = (line.timestamp.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                                onSeek(progress)
                            }
                            .graphicsLayer {
                                scaleX = textScale
                                scaleY = textScale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                    )
                }
            }
            } // End of else block

            // 4. Bottom Mini-Player Control
            LyricsFooter(
                track = track,
                isPlaying = isPlaying,
                onPlayPause = onPlayPause
            )
        }
    }
}

@Composable
fun LyricsFooter(
    track: Track,
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    val colors = VikifyTheme.colors
    val isDark = VikifyTheme.isDark
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VikifyImage(
            url = track.remoteArtworkUrl,
            placeholder = track.artwork,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1
            )
            Text(
                text = track.artist,
                color = colors.textSecondary,
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        
        // Circular Play Button
        // Studio Mode: Charcoal button with white icon
        // Aurora Mode: White button with black icon
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isDark) Color.White else colors.textPrimary,
                contentColor = if (isDark) Color.Black else Color.White
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
