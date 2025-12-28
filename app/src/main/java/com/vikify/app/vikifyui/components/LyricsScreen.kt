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
    // Determine active line based on timestamp
    val safeLyrics = lyrics ?: emptyList() 
    
    val activeIndex = safeLyrics.indexOfLast { it.timestamp <= currentTimeMs }.coerceAtLeast(0)
    
    val listState = rememberLazyListState()

    // Auto-scroll logic
    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
            // Dark Overlay for Contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
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
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Close, "Close", modifier = Modifier.size(28.dp))
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = track.title.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                IconButton(
                    onClick = { /* Options */ },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.MoreHoriz, "Options")
                }
            }

            // 3. Lyrics List
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 40.dp, horizontal = 24.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(safeLyrics) { index, line ->
                    val isActive = index == activeIndex
                    
                    // Animate properties for smooth focus effect
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
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
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1
            )
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                maxLines = 1
            )
        }
        
        // Circular Play Button
        FilledIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
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
