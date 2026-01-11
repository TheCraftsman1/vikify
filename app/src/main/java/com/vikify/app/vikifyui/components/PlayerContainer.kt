/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Player Components - Kinetic Glass Design System
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vikify.app.vikifyui.data.PlayerUIState
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS & BRAND COLORS
// ═══════════════════════════════════════════════════════════════════════════════

object PlayerColors {
    // Deep Space Theme - Purple to Cyan gradient tones
    val BrandPurple = Color(0xFF7C3AED)  // Vibrant deep purple
    val BrandPurpleGlow = Color(0xFF06B6D4)  // Cyan glow variant
    val DeepPurple = Color(0xFF5B21B6)  // Darker purple
    val NeonCyan = Color(0xFF00F0FF)
    val GlassSurface = Color(0xFF0A0A0F)
    val GlassOverlay = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
}

private const val WAVEFORM_BAR_COUNT = 40
private const val MINI_PLAYER_HEIGHT_DP = 72
private const val SWIPE_THRESHOLD = 100f
private const val COLLAPSE_THRESHOLD = 200f

// Legacy constant for backward compatibility
val VIKIFY_BRAND_COLOR = PlayerColors.BrandPurple

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN PLAYER CONTAINER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Premium Player Container
 *
 * Features:
 * - Smooth animated transition between mini and expanded states
 * - Dynamic accent color extraction from artwork
 * - Gesture-based navigation (swipe to skip, swipe down to collapse)
 * - Context menu and lyrics overlay support
 * - Battery-efficient visualizer integration
 */
@Composable
fun PlayerContainer(
    uiState: PlayerUIState,
    lyrics: List<SyncedLyric>?,
    isDownloaded: Boolean = false,
    accentColor: Color = PlayerColors.BrandPurple,
    amplitudes: State<FloatArray> = remember { mutableStateOf(FloatArray(32) { 0f }) },
    peakAmplitude: State<Float> = remember { mutableStateOf(0f) },
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onLikeClick: () -> Unit = {},
    onDownloadClick: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onJamModeClick: () -> Unit = {},
    onSleepTimerSelect: (SleepTimerDuration) -> Unit = {},
    onColorExtracted: (Color) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val track = uiState.currentTrack ?: return
    val isExpanded = uiState.density > 0.5f

    // Overlay states
    var showLyrics by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Animated transition between mini and expanded
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                val enter = fadeIn(tween(300)) + 
                    slideInVertically(tween(400, easing = FastOutSlowInEasing)) { it / 2 }
                val exit = fadeOut(tween(200)) + 
                    slideOutVertically(tween(300)) { it / 2 }
                enter togetherWith exit
            },
            label = "playerContent"
        ) { expanded ->
            if (expanded) {
                ExpandedPlayerScreen(
                    track = track,
                    uiState = uiState,
                    isDownloaded = isDownloaded,
                    accentColor = accentColor,
                    amplitudes = amplitudes,
                    peakAmplitude = peakAmplitude,
                    sleepTimerState = sleepTimerState,
                    onPlayPause = onPlayPause,
                    onCollapse = onCollapse,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onSeek = onSeek,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onLikeClick = onLikeClick,
                    onDownloadClick = onDownloadClick,
                    onLyricsClick = { showLyrics = true },
                    onQueueClick = onQueueClick,
                    onMenuClick = { showContextMenu = true },
                    onAddToPlaylist = onAddToPlaylist,
                    onArtistClick = onArtistClick,
                    onJamModeClick = onJamModeClick,
                    onSleepTimerClick = { showSleepTimer = true }
                )
            } else {
                PremiumMiniPlayer(
                    track = track,
                    isPlaying = uiState.isPlaying,
                    progress = uiState.progress,
                    accentColor = accentColor,
                    onPlayPause = onPlayPause,
                    onExpand = onExpand,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onColorExtracted = onColorExtracted
                )
            }
        }

        // Lyrics Overlay
        if (showLyrics && isExpanded) {
            val trackDurationMs = track.duration.takeIf { it > 0 } ?: 240000L
            LyricsScreen(
                track = track,
                isPlaying = uiState.isPlaying,
                currentTimeMs = (uiState.progress * trackDurationMs).toLong(),
                totalDurationMs = trackDurationMs,
                lyrics = lyrics,
                onClose = { showLyrics = false },
                onPlayPause = onPlayPause,
                onSeek = onSeek
            )
        }

        // Context Menu Overlay
        if (showContextMenu && isExpanded) {
            SongContextMenu(
                track = track,
                isLiked = uiState.isLiked,
                isDownloaded = isDownloaded,
                onDismissRequest = { showContextMenu = false },
                onLikeClick = onLikeClick,
                onDownloadClick = {
                    showContextMenu = false
                    onDownloadClick()
                },
                onAddToPlaylistClick = {
                    showContextMenu = false
                    onAddToPlaylist()
                },
                onShareClick = { /* TODO */ },
                onViewAlbumClick = { /* TODO */ }
            )
        }
        
        // Sleep Timer Dialog
        if (showSleepTimer) {
            SleepTimerDialog(
                currentState = sleepTimerState,
                onSelectDuration = { duration ->
                    onSleepTimerSelect(duration)
                    showSleepTimer = false
                },
                onDismiss = { showSleepTimer = false }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MINI PLAYER BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun PremiumMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onColorExtracted: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Swipe gesture state
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "swipeOffset"
    )

    val displayOffset = if (isDragging) dragOffset else animatedOffset

    // Progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = LinearEasing),
        label = "progress"
    )

    // Color extraction (cached)
    LaunchedEffect(track.remoteArtworkUrl) {
        track.remoteArtworkUrl?.let { url ->
            extractDominantColor(context, url)?.let(onColorExtracted)
        }
    }

    // Pre-calculated gradients
    val progressGradient = remember(accentColor) {
        Brush.horizontalGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.5f),
                accentColor.copy(alpha = 0.2f)
            )
        )
    }

    val glassGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A1A1A),
                Color(0xFF0D0D0D)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "Now playing: ${track.title} by ${track.artist}"
            }
    ) {
        // Swipe hint indicators (fixed position)
        SwipeHintIndicator(
            icon = Icons.Rounded.SkipNext,
            alpha = ((-displayOffset) / SWIPE_THRESHOLD).coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        SwipeHintIndicator(
            icon = Icons.Rounded.SkipPrevious,
            alpha = (displayOffset / SWIPE_THRESHOLD).coerceIn(0f, 1f),
            modifier = Modifier.align(Alignment.CenterStart)
        )

        // Main player card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MINI_PLAYER_HEIGHT_DP.dp)
                .graphicsLayer {
                    translationX = displayOffset
                    val dragProgress = (abs(displayOffset) / 150f)
                    scaleX = 1f - (dragProgress * 0.02f)
                    scaleY = 1f - (dragProgress * 0.02f)
                }
                .clip(RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            when {
                                dragOffset < -SWIPE_THRESHOLD -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSkipNext()
                                }
                                dragOffset > SWIPE_THRESHOLD -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSkipPrevious()
                                }
                            }
                            isDragging = false
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            isDragging = false
                            dragOffset = 0f
                        },
                        onHorizontalDrag = { _, delta ->
                            dragOffset = (dragOffset + delta).coerceIn(-150f, 150f)
                            // Haptic at threshold
                            if (abs(dragOffset) >= SWIPE_THRESHOLD &&
                                abs(dragOffset - delta) < SWIPE_THRESHOLD
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExpand()
                }
        ) {
            // Layer 1: Black base
            Box(Modifier.fillMaxSize().background(Color.Black))

            // Layer 2: Glass gradient
            Box(Modifier.fillMaxSize().background(glassGradient))

            // Layer 3: Progress fill
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(progressGradient)
            )

            // Layer 4: Top edge glow
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.6f),
                                accentColor.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork
                MiniPlayerArtwork(
                    url = track.remoteArtworkUrl,
                    title = track.title
                )

                Spacer(Modifier.width(12.dp))

                // Track info
                Column(Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Play/Pause button
                MiniPlayPauseButton(
                    isPlaying = isPlaying,
                    accentColor = accentColor,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    }
                )
            }
        }
    }
}

@Composable
private fun SwipeHintIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    if (alpha > 0.05f) {
        Box(
            modifier = modifier
                .padding(horizontal = 24.dp)
                .graphicsLayer { this.alpha = alpha }
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PlayerColors.BrandPurple.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PlayerColors.BrandPurpleGlow,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerArtwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    var imageState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2A2A2A)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = "$title album art",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onState = { imageState = it }
        )

        // Loading/error fallback
        if (imageState is AsyncImagePainter.State.Loading ||
            imageState is AsyncImagePainter.State.Error ||
            url == null
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                PlayerColors.BrandPurple.copy(alpha = 0.3f),
                                PlayerColors.BrandPurple.copy(alpha = 0.1f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun MiniPlayPauseButton(
    isPlaying: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "playButtonScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed)
            accentColor.copy(alpha = 0.8f)
        else
            accentColor,
        animationSpec = tween(100),
        label = "playButtonColor"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EXPANDED PLAYER SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandedPlayerScreen(
    track: Track,
    uiState: PlayerUIState,
    isDownloaded: Boolean,
    accentColor: Color,
    amplitudes: State<FloatArray>,
    peakAmplitude: State<Float>,
    sleepTimerState: SleepTimerState,
    onPlayPause: () -> Unit,
    onCollapse: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onMenuClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onArtistClick: (String) -> Unit,
    onJamModeClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Swipe down to collapse
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    val animatedVerticalOffset by animateFloatAsState(
        targetValue = verticalOffset,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "collapseOffset"
    )

    // Breathing animation for artwork - strict battery optimization
    // Breathing animation for artwork - strict battery optimization
    val breathAnimatable = remember { Animatable(1f) }
    
    LaunchedEffect(uiState.isPlaying) {
        if (uiState.isPlaying) {
            breathAnimatable.animateTo(
                targetValue = 1.02f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            breathAnimatable.snapTo(1f)
        }
    }
    
    val breathScale = breathAnimatable.value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                translationY = animatedVerticalOffset
                alpha = 1f - (animatedVerticalOffset / 500f).coerceIn(0f, 0.3f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (verticalOffset > COLLAPSE_THRESHOLD) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCollapse()
                        }
                        verticalOffset = 0f
                    },
                    onDragCancel = { verticalOffset = 0f },
                    onVerticalDrag = { _, delta ->
                        if (delta > 0) { // Only allow downward drag
                            verticalOffset = (verticalOffset + delta).coerceIn(0f, 400f)
                        }
                    }
                )
            }
    ) {
        // Background
        LivingPlayerBackground(
            artworkUrl = track.remoteArtworkUrl,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            ExpandedPlayerHeader(
                sleepTimerActive = sleepTimerState.isActive,
                onCollapse = onCollapse,
                onMenuClick = onMenuClick,
                onJamModeClick = onJamModeClick,
                onSleepTimerClick = onSleepTimerClick
            )

            // Artwork with breathing effect
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                BreathingArtwork(
                    track = track,
                    scale = breathScale,
                    isLiked = uiState.isLiked,
                    accentColor = accentColor,
                    onLikeClick = onLikeClick
                )
            }

            Spacer(Modifier.height(24.dp))

            // Glass Console
            GlassControlConsole(
                track = track,
                uiState = uiState,
                accentColor = accentColor,
                onPlayPause = onPlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeek = onSeek,
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick,
                onLyricsClick = onLyricsClick,
                onQueueClick = onQueueClick,
                onArtistClick = onArtistClick
            )
        }

        // Drag indicator
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun ExpandedPlayerHeader(
    sleepTimerActive: Boolean,
    onCollapse: () -> Unit,
    onMenuClick: () -> Unit,
    onJamModeClick: () -> Unit,
    onSleepTimerClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Collapse",
                tint = Color.White
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 1.sp
            )
            Text(
                "From Your Library",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium
            )
        }

        Row {
            // Sleep Timer Button
            IconButton(onClick = onSleepTimerClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepTimerActive) PlayerColors.BrandPurple else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onJamModeClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = "Jam Mode",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Rounded.MoreHoriz,
                    contentDescription = "Options",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun BreathingArtwork(
    track: Track,
    scale: Float,
    isLiked: Boolean,
    accentColor: Color,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp),
        modifier = modifier
            .aspectRatio(1f)
            .scale(scale)
    ) {
        Box {
            VikifyImage(
                url = track.remoteArtworkUrl,
                placeholder = track.artwork,
                contentDescription = "${track.title} album art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Floating like button
            FloatingLikeButton(
                isLiked = isLiked,
                accentColor = accentColor,
                onClick = onLikeClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun FloatingLikeButton(
    isLiked: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            isLiked -> 1.1f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "likeScale"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isLiked) "Unlike" else "Like",
            tint = if (isLiked) accentColor else Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassControlConsole(
    track: Track,
    uiState: PlayerUIState,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
            .background(PlayerColors.GlassOverlay)
            .border(
                1.dp,
                PlayerColors.GlassBorder,
                RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)
            )
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title row with actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onLyricsClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = "Lyrics",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        modifier = Modifier
                            .basicMarquee()
                            .clickable { onArtistClick(track.artist) }
                    )
                }

                IconButton(onClick = onQueueClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Waveform scrubber
            PremiumWaveformScrubber(
                progress = uiState.progress,
                isPlaying = uiState.isPlaying,
                onSeek = onSeek,
                accentColor = PlayerColors.BrandPurple,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )

            // Duration labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val duration = track.duration.takeIf { it > 0 } ?: 0L
                Text(
                    text = formatDuration((duration * uiState.progress).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Transport controls
            TransportControls(
                isPlaying = uiState.isPlaying,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                accentColor = accentColor,
                onPlayPause = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayPause()
                },
                onSkipNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSkipNext()
                },
                onSkipPrevious = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSkipPrevious()
                },
                onShuffleClick = onShuffleClick,
                onRepeatClick = onRepeatClick
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        IconButton(onClick = onShuffleClick) {
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) accentColor else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Previous
        TransportButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            onClick = onSkipPrevious,
            size = 56.dp,
            iconSize = 36.dp
        )

        // Play/Pause (Hero button)
        HeroPlayButton(
            isPlaying = isPlaying,
            onClick = onPlayPause
        )

        // Next
        TransportButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            onClick = onSkipNext,
            size = 56.dp,
            iconSize = 36.dp
        )

        // Repeat
        IconButton(onClick = onRepeatClick) {
            val icon = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
            val tint = if (repeatMode > 0) accentColor else Color.White.copy(alpha = 0.4f)
            Icon(icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun TransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    iconSize: Dp = 28.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "transportScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
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
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun HeroPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "heroPlayScale"
    )
    
    // Pulsing glow when playing - strict battery optimization
    val glowAnimatable = remember { Animatable(0.3f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            glowAnimatable.animateTo(
                targetValue = 0.7f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
        } else {
            glowAnimatable.snapTo(0.3f)
        }
    }
    
    val glowAlpha = glowAnimatable.value

    Box(
        modifier = modifier.size(88.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale * 1.1f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PlayerColors.BrandPurple.copy(alpha = glowAlpha * 0.5f),
                            PlayerColors.BrandPurpleGlow.copy(alpha = glowAlpha * 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Main button with gradient
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            PlayerColors.BrandPurple,
                            PlayerColors.DeepPurple,
                            PlayerColors.BrandPurpleGlow.copy(alpha = 0.8f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(150f, 150f)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            PlayerColors.BrandPurpleGlow.copy(alpha = 0.6f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// PREMIUM WAVEFORM SCRUBBER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Pre-calculated waveform bar heights for performance
 * Uses deterministic pseudo-random based on index for consistency
 */
private val precomputedBarHeights = FloatArray(WAVEFORM_BAR_COUNT) { index ->
    val base = 0.3f + (sin(index * 0.5f) * 0.2f).toFloat()
    val variation = (sin(index * 1.3f + 0.5f) * 0.15f).toFloat()
    (base + variation).coerceIn(0.15f, 0.95f)
}

@Composable
private fun PremiumWaveformScrubber(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Animation for dancing effect - strict battery optimization
    // Animation for dancing effect - strict battery optimization
    val phaseAnimatable = remember { Animatable(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            phaseAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            phaseAnimatable.snapTo(0f)
        }
    }
    
    val phase = phaseAnimatable.value

    // Animated progress for smoother scrubbing visual
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(100),
        label = "waveProgress"
    )

    // Pre-calculate colors - gradient from purple to cyan
    val purpleColor = PlayerColors.BrandPurple
    val cyanColor = PlayerColors.BrandPurpleGlow
    val unplayedColor = remember { Color.White.copy(alpha = 0.25f) }
    
    // Pre-calculate gradient colors for all bars to avoid object allocation in draw loop
    val barColors = remember(purpleColor, cyanColor) {
        List(WAVEFORM_BAR_COUNT) { index ->
            val t = index.toFloat() / WAVEFORM_BAR_COUNT
            Color(
                red = purpleColor.red * (1 - t) + cyanColor.red * t,
                green = purpleColor.green * (1 - t) + cyanColor.green * t,
                blue = purpleColor.blue * (1 - t) + cyanColor.blue * t,
                alpha = 1f
            )
        }
    }
    
    // Pre-calculate glow colors
    val barGlowColors = remember(barColors) {
        barColors.map { it.copy(alpha = 0.5f) }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            val barWidth = size.width / (WAVEFORM_BAR_COUNT * 1.8f)
            val gap = barWidth * 0.8f
            val totalWidth = WAVEFORM_BAR_COUNT * (barWidth + gap) - gap
            val startX = (size.width - totalWidth) / 2f

            for (i in 0 until WAVEFORM_BAR_COUNT) {
                val barProgress = i.toFloat() / WAVEFORM_BAR_COUNT
                val isPlayed = barProgress < animatedProgress

                // Height with optional dancing
                val baseHeight = precomputedBarHeights[i]
                val dance = if (isPlaying && isPlayed) {
                    sin(phase * 6.28f + i * 0.3f).toFloat() * 0.12f
                } else 0f
                val height = ((baseHeight + dance) * size.height).coerceIn(
                    size.height * 0.1f,
                    size.height * 0.95f
                )

                val x = startX + i * (barWidth + gap)
                val y = (size.height - height) / 2f
                
                // Get pre-calculated colors
                val barColor = barColors.getOrElse(i) { purpleColor }
                val glowColor = barGlowColors.getOrElse(i) { purpleColor.copy(alpha = 0.5f) }

                // Draw glow for played bars
                if (isPlayed) {
                    drawRoundRect(
                        color = glowColor,
                        topLeft = Offset(x - 2f, y - 2f),
                        size = Size(barWidth + 4f, height + 4f),
                        cornerRadius = CornerRadius(barWidth)
                    )
                }

                // Draw bar with gradient color
                drawRoundRect(
                    color = if (isPlayed) barColor else unplayedColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth)
                )
            }

            // Progress indicator dot with gradient
            val indicatorX = startX + (animatedProgress * totalWidth)
            val indicatorIndex = (animatedProgress * WAVEFORM_BAR_COUNT).toInt().coerceIn(0, WAVEFORM_BAR_COUNT - 1)
            val indicatorColor = barColors.getOrElse(indicatorIndex) { purpleColor }
            drawCircle(
                color = indicatorColor.copy(alpha = 0.6f),
                radius = barWidth * 2f,
                center = Offset(indicatorX, size.height / 2f)
            )
            drawCircle(
                color = indicatorColor,
                radius = barWidth * 1.2f,
                center = Offset(indicatorX, size.height / 2f)
            )
            drawCircle(
                color = Color.White,
                radius = barWidth * 0.5f,
                center = Offset(indicatorX, size.height / 2f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LEGACY COMPONENTS (For backward compatibility)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun GlowingArtwork(
    track: Track,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // The Glow (Blurred image behind)
        VikifyImage(
            url = track.remoteArtworkUrl,
            placeholder = track.artwork,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .offset(y = 12.dp)
                .blur(radius = 32.dp)
                .alpha(0.6f)
        )

        // Main Image
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            VikifyImage(
                url = track.remoteArtworkUrl,
                placeholder = track.artwork,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// Legacy NeonWaveform for backward compatibility
@Composable
fun NeonWaveform(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    PremiumWaveformScrubber(
        progress = progress,
        isPlaying = isPlaying,
        onSeek = onSeek,
        accentColor = accentColor,
        modifier = modifier
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private suspend fun extractDominantColor(
    context: android.content.Context,
    imageUrl: String
): Color? = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false)
            .build()
        val result = ImageLoader(context).execute(request)
        val bitmap = (result.image as? BitmapImage)?.bitmap ?: return@withContext null

        Palette.from(bitmap).generate()?.let { palette ->
            val rgb = palette.vibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
                ?: palette.mutedSwatch?.rgb
                ?: return@let null
            Color(rgb)
        }
    } catch (e: Exception) {
        null
    }
}


