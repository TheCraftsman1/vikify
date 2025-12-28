package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*

// Note: SyncedLyric is defined in LyricsScreen.kt

/**
 * Lyrics Overlay
 * 
 * Shows synchronized lyrics in a modal bottom sheet
 * Now uses REAL lyrics from PlayerViewModel
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOverlay(
    track: Track?,
    currentProgress: Float,
    lyrics: List<SyncedLyric>?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculate current time in ms (assuming track duration is available)
    val trackDuration = track?.duration ?: 240000L // Default 4 min
    val currentTimeMs = (currentProgress * trackDuration).toLong()
    
    // Find current lyric index based on timestamp
    val currentLyricIndex = lyrics?.indexOfLast { it.timestamp <= currentTimeMs } ?: -1
    
    // Auto-scroll to current lyric
    val listState = rememberLazyListState()
    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex > 0 && lyrics != null) {
            listState.animateScrollToItem(
                index = maxOf(0, currentLyricIndex - 2),
                scrollOffset = 0
            )
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Divider)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = track?.title ?: "Lyrics",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    track?.artist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }
            
            // Lyrics content
            if (lyrics.isNullOrEmpty()) {
                // No lyrics available
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🎵",
                            fontSize = 48.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No lyrics available",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lyrics couldn't be found for this song",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    itemsIndexed(lyrics) { index, lyric ->
                        val isActive = index == currentLyricIndex
                        val isPast = index < currentLyricIndex
                        
                        Text(
                            text = lyric.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (isActive) 24.sp else 20.sp
                            ),
                            color = when {
                                isActive -> TextPrimary
                                isPast -> TextSecondary
                                else -> TextTertiary
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
