package com.vikify.app.vikifyui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*

// Premium Vikify colors
private val VikifyRed = Color(0xFFED5564)
private val VikifyPink = Color(0xFFFF6B9D)
private val VikifyPurple = Color(0xFF8B5CF6)
private val VikifyDark = Color(0xFF0A0A0F)
private val VikifyCard = Color(0xFF12121A)

/**
 * Queue Overlay - Premium Vikify Design
 * 
 * Modal bottom sheet showing the play queue with real tracks
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueOverlay(
    currentTrack: Track?,
    currentTrackIndex: Int = 0,
    queueTracks: List<Track> = emptyList(),
    onDismiss: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onTrackIndexClick: (Int) -> Unit = {},
    onRemoveTrack: (Int) -> Unit = {},  // Remove track at index
    onMoveTrack: (Int, Int) -> Unit = { _, _ -> },  // Move from index to index
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VikifyDark,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Up Next",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "${queueTracks.size} tracks in queue",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Now Playing indicator
            currentTrack?.let { track ->
                Text(
                    text = "NOW PLAYING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = VikifyRed,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Current track card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(VikifyRed.copy(alpha = 0.2f), VikifyPurple.copy(alpha = 0.1f))
                            )
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VikifyCard)
                    ) {
                        VikifyImage(
                            url = track.remoteArtworkUrl,
                            placeholder = track.artwork,
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Playing",
                        tint = VikifyRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Queue list
            if (queueTracks.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Queue is empty",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Play a playlist to see tracks here",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        queueTracks.filter { it.id != currentTrack?.id },
                        key = { index, track -> "${track.id}_$index" }
                    ) { index, track ->
                        // Calculate real index in original queue
                        val realIndex = queueTracks.indexOfFirst { it.id == track.id }
                        QueueTrackRow(
                            index = index + 1,
                            track = track,
                            onClick = { onTrackClick(track) },
                            onRemove = { if (realIndex >= 0) onRemoveTrack(realIndex) }
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

@Composable
private fun QueueTrackRow(
    index: Int,
    track: Track,
    onClick: () -> Unit,
    onRemove: () -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.width(24.dp)
            )
            
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(VikifyCard)
            ) {
                VikifyImage(
                    url = track.remoteArtworkUrl,
                    placeholder = track.artwork,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove from queue",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            
            // Drag handle for reorder
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "Reorder",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

