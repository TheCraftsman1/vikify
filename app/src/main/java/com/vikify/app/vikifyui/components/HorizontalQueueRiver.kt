package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.Track
import kotlin.math.abs

/**
 * Horizontal Queue River 🌊
 * 
 * A cinematic horizontal flow of album art replacing the traditional vertical queue.
 * 
 * Design Philosophy (from Canon):
 * - Current song: centered, 20% larger
 * - Past songs: drift left, desaturated to 60%
 * - Future songs: drift right, full color
 * - Max 7 visible: current + 3 each direction
 * - Snap scrolling to discrete items
 */

@Composable
fun HorizontalQueueRiver(
    currentTrackIndex: Int,
    tracks: List<Track>,
    onTrackSelected: (Int) -> Unit,
    onSwipeToNext: () -> Unit,
    onSwipeToPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = maxOf(0, currentTrackIndex - 3)
    )
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val albumSize = 100.dp
    val albumSpacing = 16.dp
    val centerPadding = (screenWidth - albumSize) / 2
    
    // Detect when user scrolls to a new position and trigger track change
    LaunchedEffect(listState.isScrollInProgress, listState.firstVisibleItemIndex) {
        if (!listState.isScrollInProgress) {
            val centeredIndex = listState.firstVisibleItemIndex + 3
            if (centeredIndex in tracks.indices && centeredIndex != currentTrackIndex) {
                onTrackSelected(centeredIndex)
            }
        }
    }
    
    // Scroll to current track when it changes externally
    LaunchedEffect(currentTrackIndex) {
        if (currentTrackIndex in tracks.indices) {
            listState.animateScrollToItem(maxOf(0, currentTrackIndex - 3))
        }
    }
    
    Box(modifier = modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            flingBehavior = snapBehavior,
            contentPadding = PaddingValues(horizontal = centerPadding),
            horizontalArrangement = Arrangement.spacedBy(albumSpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(tracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                val distanceFromCenter = index - currentTrackIndex
                
                // Scale: current = 1.2, others = 1.0
                val scale by animateFloatAsState(
                    targetValue = if (distanceFromCenter == 0) 1.2f else 1f,
                    animationSpec = tween(300),
                    label = "scale"
                )
                
                // Opacity: current = 1.0, far = 0.4
                val opacity by animateFloatAsState(
                    targetValue = when {
                        distanceFromCenter == 0 -> 1f
                        abs(distanceFromCenter) <= 2 -> 0.7f
                        else -> 0.4f
                    },
                    animationSpec = tween(300),
                    label = "opacity"
                )
                
                // Desaturation for past songs (left side)
                val saturation by animateFloatAsState(
                    targetValue = when {
                        distanceFromCenter < 0 -> 0.4f  // Past songs - desaturated
                        distanceFromCenter == 0 -> 1f   // Current - full color
                        else -> 1f                       // Future - full color
                    },
                    animationSpec = tween(300),
                    label = "saturation"
                )
                
                QueueAlbumItem(
                    track = track,
                    isCurrent = distanceFromCenter == 0,
                    scale = scale,
                    opacity = opacity,
                    saturation = saturation,
                    onClick = { onTrackSelected(index) }
                )
            }
        }
        
        // Title overlay for current track
        tracks.getOrNull(currentTrackIndex)?.let { currentTrack ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(top = 140.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = currentTrack.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 280.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentTrack.artist,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun QueueAlbumItem(
    track: Track,
    isCurrent: Boolean,
    scale: Float,
    opacity: Float,
    saturation: Float,
    onClick: () -> Unit
) {
    val saturationMatrix = ColorMatrix().apply {
        setToSaturation(saturation)
    }
    
    Box(
        modifier = Modifier
            .scale(scale)
            .graphicsLayer { alpha = opacity }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Glow effect for current track
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .background(
                        color = Color(0xFFE53935).copy(alpha = 0.3f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )
        }
        
        AsyncImage(
            model = track.remoteArtworkUrl ?: track.artwork,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            colorFilter = if (saturation < 1f) ColorFilter.colorMatrix(saturationMatrix) else null,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}
