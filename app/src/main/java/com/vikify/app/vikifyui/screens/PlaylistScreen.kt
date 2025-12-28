package com.vikify.app.vikifyui.screens

import com.vikify.app.R

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.MockData
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import com.vikify.app.vikifyui.components.VikifyImage
import kotlin.math.roundToInt

import com.vikify.app.vikifyui.theme.VikifyRed
import com.vikify.app.vikifyui.theme.VikifyPink
import com.vikify.app.vikifyui.theme.VikifyPurple
import com.vikify.app.vikifyui.theme.VikifyDark
import com.vikify.app.vikifyui.theme.VikifyCard
import com.vikify.app.vikifyui.theme.VikifyCardHover
import com.vikify.app.vikifyui.theme.VikifyBorder

/**
 * Playlist Details Screen - Premium Vikify Design
 */

@Composable
fun PlaylistScreen(
    playlistId: String? = null,
    tracks: List<Track> = emptyList(),
    playlistName: String = "Late Night Drives",
    coverUrl: String? = null,
    currentTrack: Track?,
    onTrackClick: (Track) -> Unit,
    onBackClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    downloadedTrackIds: Set<String> = emptySet(),
    isPlaylistDownloaded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalVikifyColors.current
    val displayTracks = if (tracks.isNotEmpty()) tracks else MockData.sampleTracks
    val displaySongCount = if (tracks.isNotEmpty()) tracks.size else 34
    
    // Calculate actual playlist duration - wrapped in remember for performance
    val (totalDurationMs, displayDuration) = remember(displayTracks) {
        val totalMs = displayTracks.sumOf { it.duration.coerceAtLeast(0L) }
        val totalMinutes = (totalMs / 1000 / 60).toInt()
        val duration = if (totalMs > 0) {
            if (totalMinutes >= 60) {
                "${totalMinutes / 60}h ${totalMinutes % 60}m"
            } else {
                "${totalMinutes}m"
            }
        } else {
            "--"
        }
        Pair(totalMs, duration)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.accent.copy(alpha = 0.15f),
                        colors.background,
                        colors.background
                    ),
                    startY = 0f,
                    endY = 800f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // Header
            item {
                PlaylistHeader(
                    title = playlistName,
                    onBackClick = onBackClick
                )
            }
            
            // Cover + Info
            item {
                PlaylistCover(
                    name = playlistName,
                    author = "Vikify",
                    songCount = displaySongCount,
                    duration = displayDuration,
                    coverUrl = coverUrl ?: displayTracks.firstOrNull()?.remoteArtworkUrl
                )
            }
            
            // Action buttons
            item {
                PlaylistActions(
                    onShuffleClick = onShuffleClick,
                    onPlayAllClick = onPlayAllClick,
                    onDownloadClick = onDownloadClick,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    trackCount = displaySongCount,
                    isPlaylistDownloaded = isPlaylistDownloaded
                )
            }
            
            // Section header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tracks",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "$displaySongCount songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }
            
            // Track list
            itemsIndexed(displayTracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    index = index + 1,
                    track = track,
                    isPlaying = track.id == currentTrack?.id,
                    isDownloaded = downloadedTrackIds.contains(track.id),
                    onAddToQueue = { onAddToQueue(track) },
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    title: String,
    onBackClick: () -> Unit
) {
    val colors = LocalVikifyColors.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(top = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button with glassmorphism
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.glassBackground)
                .border(1.dp, colors.glassBorder, CircleShape)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = "Back",
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // More options button
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.glassBackground)
                .border(1.dp, colors.glassBorder, CircleShape)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "More",
                tint = colors.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlaylistCover(
    name: String,
    author: String,
    songCount: Int,
    duration: String,
    coverUrl: String? = null
) {
    val colors = LocalVikifyColors.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover with premium glow effect
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VikifyRed.copy(alpha = 0.4f),
                                VikifyPurple.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
                    .blur(40.dp)
                    .scale(1.3f)
            )
            
            // Cover image with gradient overlay
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = VikifyRed.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(24.dp))
            ) {
                VikifyImage(
                    url = coverUrl,
                    placeholder = R.drawable.artwork_placeholder,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay at bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    VikifyDark.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Playlist title
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = colors.textPrimary
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Metadata row with pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Author pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(VikifyCard)
                    .border(1.dp, VikifyBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = VikifyRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurfaceVariant
                    )
                }
            }
            
            // Song count pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(VikifyCard)
                    .border(1.dp, VikifyBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = VikifyPurple,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$songCount tracks",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurfaceVariant
                    )
                }
            }
            
            // Duration pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(VikifyCard)
                    .border(1.dp, VikifyBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = VikifyPink,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistActions(
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Float = 0f,
    trackCount: Int = 0,
    isPlaylistDownloaded: Boolean = false
) {
    val colors = LocalVikifyColors.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Main action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Shuffle button - Glass style
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.glassBackground)
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(16.dp))
                    .clickable(onClick = onShuffleClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurface
                    )
                }
            }
            
            // Play All button - Gradient filled
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(VikifyRed, VikifyPink)
                        )
                    )
                    .clickable(onClick = onPlayAllClick),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play All",
                        tint = colors.onAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Play All",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.onAccent
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Download button with progress
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isDownloading) 
                        colors.accent.copy(alpha = 0.2f) 
                    else 
                        colors.glassBackground
                )
                .border(
                    width = 1.dp,
                    brush = if (isDownloading) 
                        Brush.horizontalGradient(listOf(VikifyPurple, VikifyPink))
                    else 
                        Brush.horizontalGradient(listOf(VikifyBorder, VikifyBorder)),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(enabled = !isDownloading, onClick = onDownloadClick),
            contentAlignment = Alignment.Center
        ) {
            // Progress background when downloading
            if (isDownloading && downloadProgress > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(downloadProgress)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    VikifyPurple.copy(alpha = 0.3f),
                                    VikifyPink.copy(alpha = 0.3f)
                                )
                            )
                        )
                        .align(Alignment.CenterStart)
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDownloading) {
                    // Animated downloading indicator
                    val infiniteTransition = rememberInfiniteTransition(label = "download")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "rotation"
                    )
                    
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = VikifyPink,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = VikifyPink
                    )
                } else if (isPlaylistDownloaded) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Downloaded • $trackCount tracks",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF4CAF50)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = "Download",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download All • $trackCount tracks",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    isDownloaded: Boolean = false,
    onAddToQueue: () -> Unit = {},
    onClick: () -> Unit
) {
    val colors = LocalVikifyColors.current
    
    // Swipe state for add to queue
    var offsetX by remember { mutableFloatStateOf(0f) }
    var showQueueHint by remember { mutableStateOf(false) }
    val swipeThreshold = -120f // pixels to trigger queue add
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Background "Add to Queue" indicator (revealed on swipe)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (showQueueHint) VikifyPurple.copy(alpha = 0.8f) 
                    else VikifyPurple.copy(alpha = 0.3f)
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier.padding(end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.QueueMusic,
                    contentDescription = "Add to Queue",
                    tint = colors.onAccent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onAccent
                )
            }
        }
        
        // Main track content (swipeable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isPlaying) colors.accent.copy(alpha = 0.15f) else colors.surface
                )
                .border(
                    width = if (isPlaying) 1.dp else 0.dp,
                    color = if (isPlaying) colors.accent.copy(alpha = 0.3f) else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        // Only allow left swipe
                        val newOffset = (offsetX + delta).coerceIn(-200f, 0f)
                        offsetX = newOffset
                        showQueueHint = offsetX < swipeThreshold
                    },
                    onDragStopped = {
                        if (offsetX < swipeThreshold) {
                            // Trigger add to queue
                            onAddToQueue()
                        }
                        // Animate back
                        offsetX = 0f
                        showQueueHint = false
                    }
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Track number or playing indicator
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                // Animated equalizer-like indicator
                val infiniteTransition = rememberInfiniteTransition(label = "playing")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "Playing",
                    tint = VikifyRed,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(scale)
                )
            } else {
                Text(
                    text = index.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = colors.textTertiary
                )
            }
        }
        
        // Track thumbnail (small) - uses VikifyImage for URL loading
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VikifyCard)
        ) {
            com.vikify.app.vikifyui.components.VikifyImage(
                url = track.remoteArtworkUrl,
                placeholder = R.drawable.artwork_placeholder,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                VikifyDark.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
        }
        
        // Track info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 15.sp
                ),
                color = if (isPlaying) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = if (isPlaying) colors.accent.copy(alpha = 0.7f) else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Duration
        val durationText = if (track.duration > 0) {
            val totalSeconds = (track.duration / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            "%d:%02d".format(minutes, seconds)
        } else {
            "--:--"
        }
        
        // Download indicator
        if (isDownloaded) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Downloaded",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Text(
            text = durationText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = colors.textTertiary
        )
        
        // More options
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "More",
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
        }
    }
}
