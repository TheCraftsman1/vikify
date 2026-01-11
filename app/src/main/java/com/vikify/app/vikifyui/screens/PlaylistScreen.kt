/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Playlist Details Screen
 */
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.R
import com.vikify.app.vikifyui.components.VikifyImage
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════════════════════

private object PlaylistColors {
    val AccentRed = Color(0xFFED5564)
    val AccentPink = Color(0xFFFF6B9D)
    val AccentPurple = Color(0xFF8B5CF6)
    val AccentGreen = Color(0xFF4CAF50)
    
    val Background = Color(0xFF0A0A0F)
    val Surface = Color(0xFF12121A)
    val SurfaceElevated = Color(0xFF1A1A25)
    val Border = Color(0xFF2A2A35)
    
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.7f)
    val TextMuted = Color.White.copy(alpha = 0.5f)
    val TextDisabled = Color.White.copy(alpha = 0.3f)
    
    val GlassSurface = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.15f)
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

private const val HEADER_HEIGHT_DP = 400
private const val SWIPE_QUEUE_THRESHOLD = -100f
private const val MAX_SWIPE_DISTANCE = 150f

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedTrackIds: Set<String> = emptySet(),
    val isPlaylistDownloaded: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Premium Playlist Details Screen
 *
 * Features:
 * - Parallax scrolling header with dynamic glow
 * - Pull-to-refresh
 * - Swipe-to-queue track rows
 * - Download progress with animated states
 * - Haptic feedback throughout
 * - Full accessibility support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String?,
    playlistName: String,
    tracks: List<Track>,
    coverUrl: String?,
    currentTrackId: String?,
    uiState: PlaylistUiState = PlaylistUiState(),
    onTrackClick: (Track) -> Unit,
    onBackClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    onRefresh: suspend () -> Unit = {},
    onMoreOptionsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Calculate playlist metadata
    val totalDurationMs = remember(tracks) {
        tracks.sumOf { it.duration.coerceAtLeast(0L) }
    }
    val formattedDuration = remember(totalDurationMs) {
        formatPlaylistDuration(totalDurationMs)
    }
    
    // Header parallax progress
    val headerProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
        }
    }
    
    // Refresh handler
    var isRefreshing by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PlaylistColors.Background)
    ) {
        // Background gradient (parallax)
        PlaylistBackgroundGradient(
            accentColor = PlaylistColors.AccentPurple,
            progress = headerProgress
        )
        
        when {
            uiState.isLoading && tracks.isEmpty() -> {
                PlaylistLoadingSkeleton()
            }
            
            uiState.error != null && tracks.isEmpty() -> {
                PlaylistErrorState(
                    message = uiState.error,
                    onRetry = { scope.launch { onRefresh() } }
                )
            }
            
            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing || uiState.isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            onRefresh()
                            delay(300)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 180.dp)
                    ) {
                        // Cover & Info
                        item(key = "header") {
                            PlaylistHeader(
                                name = playlistName,
                                coverUrl = coverUrl ?: tracks.firstOrNull()?.remoteArtworkUrl,
                                trackCount = tracks.size,
                                duration = formattedDuration,
                                headerProgress = headerProgress
                            )
                        }
                        
                        // Action buttons
                        item(key = "actions") {
                            PlaylistActions(
                                trackCount = tracks.size,
                                isDownloading = uiState.isDownloading,
                                downloadProgress = uiState.downloadProgress,
                                isPlaylistDownloaded = uiState.isPlaylistDownloaded,
                                onShuffleClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShuffleClick()
                                },
                                onPlayAllClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPlayAllClick()
                                },
                                onDownloadClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDownloadClick()
                                }
                            )
                        }
                        
                        // Section header
                        item(key = "section_header") {
                            TracksSectionHeader(trackCount = tracks.size)
                        }
                        
                        // Track list
                        if (tracks.isEmpty()) {
                            item(key = "empty") {
                                EmptyTracksState()
                            }
                        } else {
                            itemsIndexed(
                                items = tracks,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                SwipeableTrackRow(
                                    index = index + 1,
                                    track = track,
                                    isPlaying = track.id == currentTrackId,
                                    isDownloaded = uiState.downloadedTrackIds.contains(track.id),
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onTrackClick(track)
                                    },
                                    onAddToQueue = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAddToQueue(track)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Floating header (shows on scroll)
        CollapsedPlaylistHeader(
            title = playlistName,
            visible = headerProgress > 0.8f,
            onBackClick = onBackClick,
            onMoreClick = onMoreOptionsClick
        )
        
        // Back button (always visible when header is expanded)
        AnimatedVisibility(
            visible = headerProgress < 0.8f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 12.dp)
        ) {
            FloatingNavigationButtons(
                onBackClick = onBackClick,
                onMoreClick = onMoreOptionsClick
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistBackgroundGradient(
    accentColor: Color,
    progress: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - (progress * 0.5f)
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.2f),
                        accentColor.copy(alpha = 0.08f),
                        PlaylistColors.Background,
                        PlaylistColors.Background
                    ),
                    startY = 0f,
                    endY = 800f
                )
            )
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// HEADERS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistHeader(
    name: String,
    coverUrl: String?,
    trackCount: Int,
    duration: String,
    headerProgress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
            .graphicsLayer {
                alpha = 1f - (headerProgress * 0.3f)
                translationY = -headerProgress * 50f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover with glow
        PlaylistCoverArt(
            coverUrl = coverUrl,
            name = name,
            headerProgress = headerProgress
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Title
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = PlaylistColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .semantics { heading() }
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Metadata pills
        PlaylistMetadataPills(
            author = "Vikify",
            trackCount = trackCount,
            duration = duration
        )
    }
}

@Composable
private fun PlaylistCoverArt(
    coverUrl: String?,
    name: String,
    headerProgress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "coverGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(
        modifier = Modifier
            .size(280.dp)
            .graphicsLayer {
                scaleX = 1f - (headerProgress * 0.3f)
                scaleY = 1f - (headerProgress * 0.3f)
            },
        contentAlignment = Alignment.Center
    ) {
        // Animated glow
        Box(
            modifier = Modifier
                .size(260.dp)
                .blur(50.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PlaylistColors.AccentRed.copy(alpha = 0.5f),
                            PlaylistColors.AccentPurple.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
        )
        
        // Cover image
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
            modifier = Modifier.size(240.dp)
        ) {
            Box {
                VikifyImage(
                    url = coverUrl,
                    placeholder = R.drawable.artwork_placeholder,
                    contentDescription = "$name playlist cover",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Bottom gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    PlaylistColors.Background.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun PlaylistMetadataPills(
    author: String,
    trackCount: Int,
    duration: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        MetadataPill(
            icon = Icons.Filled.Person,
            text = author,
            iconTint = PlaylistColors.AccentRed
        )
        
        MetadataPill(
            icon = Icons.Filled.MusicNote,
            text = "$trackCount tracks",
            iconTint = PlaylistColors.AccentPurple
        )
        
        MetadataPill(
            icon = Icons.Filled.Schedule,
            text = duration,
            iconTint = PlaylistColors.AccentPink
        )
    }
}

@Composable
private fun MetadataPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(PlaylistColors.Surface)
            .border(1.dp, PlaylistColors.Border, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = PlaylistColors.TextSecondary
            )
        }
    }
}

@Composable
private fun CollapsedPlaylistHeader(
    title: String,
    visible: Boolean,
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically { -it },
        exit = fadeOut(tween(200)) + slideOutVertically { -it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PlaylistColors.Background,
                            PlaylistColors.Background.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
                
                IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "More options",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingNavigationButtons(
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GlassIconButton(
            icon = Icons.Rounded.ArrowBackIosNew,
            contentDescription = "Back",
            onClick = onBackClick
        )
        
        GlassIconButton(
            icon = Icons.Rounded.MoreHoriz,
            contentDescription = "More options",
            onClick = onMoreClick,
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "glassButtonScale"
    )
    
    Box(
        modifier = modifier
            .size(42.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(PlaylistColors.GlassSurface)
            .border(1.dp, PlaylistColors.GlassBorder, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACTION BUTTONS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistActions(
    trackCount: Int,
    isDownloading: Boolean,
    downloadProgress: Float,
    isPlaylistDownloaded: Boolean,
    onShuffleClick: () -> Unit,
    onPlayAllClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Main buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Shuffle button
            GlassActionButton(
                text = "Shuffle",
                icon = Icons.Filled.Shuffle,
                onClick = onShuffleClick,
                modifier = Modifier.weight(1f)
            )
            
            // Play All button
            GradientActionButton(
                text = "Play All",
                icon = Icons.Filled.PlayArrow,
                onClick = onPlayAllClick,
                modifier = Modifier.weight(1f)
            )
        }
        
        // Download button
        DownloadButton(
            trackCount = trackCount,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            isDownloaded = isPlaylistDownloaded,
            onClick = onDownloadClick
        )
    }
}

@Composable
private fun GlassActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "glassActionScale"
    )
    
    Box(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(PlaylistColors.GlassSurface)
            .border(1.dp, PlaylistColors.GlassBorder, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}

@Composable
private fun GradientActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "gradientActionScale"
    )
    
    Box(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(PlaylistColors.AccentRed, PlaylistColors.AccentPink)
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Composable
private fun DownloadButton(
    trackCount: Int,
    isDownloading: Boolean,
    downloadProgress: Float,
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isDownloading) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "downloadScale"
    )
    
    val borderBrush = when {
        isDownloading -> Brush.horizontalGradient(
            listOf(PlaylistColors.AccentPurple, PlaylistColors.AccentPink)
        )
        isDownloaded -> Brush.horizontalGradient(
            listOf(PlaylistColors.AccentGreen, PlaylistColors.AccentGreen)
        )
        else -> Brush.horizontalGradient(
            listOf(PlaylistColors.Border, PlaylistColors.Border)
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    isDownloading -> PlaylistColors.AccentPurple.copy(alpha = 0.15f)
                    isDownloaded -> PlaylistColors.AccentGreen.copy(alpha = 0.1f)
                    else -> PlaylistColors.GlassSurface.copy(alpha = 0.5f)
                }
            )
            .border(1.dp, borderBrush, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isDownloading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Progress background
        if (isDownloading && downloadProgress > 0) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(downloadProgress)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                PlaylistColors.AccentPurple.copy(alpha = 0.3f),
                                PlaylistColors.AccentPink.copy(alpha = 0.3f)
                            )
                        )
                    )
                    .align(Alignment.CenterStart)
            )
        }
        
        // Content
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = PlaylistColors.AccentPink,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Downloading... ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = PlaylistColors.AccentPink
                    )
                }
                isDownloaded -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = PlaylistColors.AccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Downloaded • $trackCount tracks",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = PlaylistColors.AccentGreen
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = PlaylistColors.TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Download All • $trackCount tracks",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = PlaylistColors.TextSecondary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TRACK LIST
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TracksSectionHeader(trackCount: Int) {
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
            color = Color.White,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = "$trackCount songs",
            style = MaterialTheme.typography.bodySmall,
            color = PlaylistColors.TextMuted
        )
    }
}

@Composable
private fun SwipeableTrackRow(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // Swipe state
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }
    
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "trackSwipeOffset"
    )
    
    val displayOffset = if (isDragging) dragOffset else animatedOffset
    val showQueueHint = displayOffset < SWIPE_QUEUE_THRESHOLD * 0.5f
    val isAtThreshold = displayOffset <= SWIPE_QUEUE_THRESHOLD
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Background queue indicator
        AnimatedVisibility(
            visible = showQueueHint,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isAtThreshold) 
                            PlaylistColors.AccentPurple 
                        else 
                            PlaylistColors.AccentPurple.copy(alpha = 0.5f)
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
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(if (isAtThreshold) 24.dp else 20.dp)
                    )
                    Text(
                        text = if (isAtThreshold) "Release to Queue" else "Queue",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isAtThreshold) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                }
            }
        }
        
        // Track row content
        TrackRowContent(
            index = index,
            track = track,
            isPlaying = isPlaying,
            isDownloaded = isDownloaded,
            onClick = onClick,
            modifier = Modifier
                .offset { IntOffset(displayOffset.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            hasTriggeredHaptic = false
                        },
                        onDragEnd = {
                            if (dragOffset <= SWIPE_QUEUE_THRESHOLD) {
                                onAddToQueue()
                            }
                            isDragging = false
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            dragOffset = (dragOffset + delta).coerceIn(-MAX_SWIPE_DISTANCE, 0f)
                            
                            // Haptic at threshold
                            if (!hasTriggeredHaptic && dragOffset <= SWIPE_QUEUE_THRESHOLD) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hasTriggeredHaptic = true
                            } else if (hasTriggeredHaptic && dragOffset > SWIPE_QUEUE_THRESHOLD) {
                                hasTriggeredHaptic = false
                            }
                        }
                    )
                }
        )
    }
}

@Composable
private fun TrackRowContent(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> PlaylistColors.SurfaceElevated
            isPlaying -> PlaylistColors.AccentRed.copy(alpha = 0.15f)
            else -> PlaylistColors.Surface
        },
        label = "trackRowBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isPlaying) PlaylistColors.AccentRed.copy(alpha = 0.3f) else Color.Transparent,
        label = "trackRowBorder"
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isPlaying) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics {
                contentDescription = "${track.title} by ${track.artist}"
            },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index or playing indicator
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPlaying) {
                AnimatedEqualizerIcon()
            } else {
                Text(
                    text = index.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = PlaylistColors.TextDisabled
                )
            }
        }
        
        // Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PlaylistColors.Surface)
        ) {
            VikifyImage(
                url = track.remoteArtworkUrl,
                placeholder = R.drawable.artwork_placeholder,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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
                color = if (isPlaying) PlaylistColors.AccentRed else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = if (isPlaying) 
                    PlaylistColors.AccentRed.copy(alpha = 0.7f) 
                else 
                    PlaylistColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Downloaded indicator
        if (isDownloaded) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Downloaded",
                tint = PlaylistColors.AccentGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        
        // Duration
        Text(
            text = formatTrackDuration(track.duration),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = PlaylistColors.TextDisabled
        )
        
        // More options menu
        var showMenu by remember { mutableStateOf(false) }
        val haptic = LocalHapticFeedback.current
        
        Box {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showMenu = true
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = PlaylistColors.TextDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(PlaylistColors.SurfaceElevated)
            ) {
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = PlaylistColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Add to Queue",
                                color = PlaylistColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    onClick = {
                        showMenu = false
                        // Use swipe handler which already exists
                    }
                )
            }
        }
    }
}

@Composable
private fun AnimatedEqualizerIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "equalizerScale"
    )
    
    Icon(
        imageVector = Icons.Filled.GraphicEq,
        contentDescription = "Playing",
        tint = PlaylistColors.AccentRed,
        modifier = Modifier
            .size(20.dp)
            .scale(scale)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// STATES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlaylistLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cover skeleton
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PlaylistColors.Surface.copy(alpha = shimmerAlpha))
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Title skeleton
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PlaylistColors.Surface.copy(alpha = shimmerAlpha))
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Pills skeleton
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(PlaylistColors.Surface.copy(alpha = shimmerAlpha))
                )
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Track skeletons
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PlaylistColors.Surface.copy(alpha = shimmerAlpha))
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlaylistColors.Surface.copy(alpha = shimmerAlpha))
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PlaylistColors.Surface.copy(alpha = shimmerAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = PlaylistColors.TextMuted,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "Couldn't Load Playlist",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = PlaylistColors.TextMuted,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = PlaylistColors.AccentPurple
            )
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyTracksState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicOff,
            contentDescription = null,
            tint = PlaylistColors.TextMuted,
            modifier = Modifier.size(48.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = "No tracks yet",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        
        Text(
            text = "Add some music to this playlist",
            style = MaterialTheme.typography.bodySmall,
            color = PlaylistColors.TextMuted
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatPlaylistDuration(totalMs: Long): String {
    if (totalMs <= 0) return "--"
    val totalMinutes = (totalMs / 1000 / 60).toInt()
    return if (totalMinutes >= 60) {
        "${totalMinutes / 60}h ${totalMinutes % 60}m"
    } else {
        "${totalMinutes}m"
    }
}

private fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = (durationMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}


