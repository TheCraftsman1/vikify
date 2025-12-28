package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.PlayerUIState
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import coil3.request.allowHardware
import kotlin.random.Random

// Helper function to format duration
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// --- Main Container ---
@Composable
fun PlayerContainer(
    uiState: PlayerUIState,
    lyrics: List<SyncedLyric>?,
    isDownloaded: Boolean = false,
    accentColor: Color = Color(0xFFE53935),  // Dynamic from artwork
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
    onArtistClick: (String) -> Unit = {}, // NEW: Navigate to artist profile
    onColorExtracted: (Color) -> Unit = {},  // Callback for ViewModel
    modifier: Modifier = Modifier
) {
    val track = uiState.currentTrack ?: return
    val isExpanded = uiState.density > 0.5f

    // State for local lyrics overlay
    var showLyrics by remember { mutableStateOf(false) }
    
    // State for Context Menu
    var showContextMenu by remember { mutableStateOf(false) }

    // Use a Box to manage transitions if you want to animate between them later
    Box(modifier = modifier) {
        if (isExpanded) {
            PremiumExpandedPlayer(
                track = track,
                isPlaying = uiState.isPlaying,
                progress = uiState.progress,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                isLiked = uiState.isLiked,
                isDownloaded = isDownloaded,
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
                onArtistClick = onArtistClick
            )
            
            // Lyrics Overlay
            if (showLyrics) {
                val trackDurationMs = if (track.duration > 0) track.duration else 240000L // fallback 4 min
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
            if (showContextMenu) {
                SongContextMenu(
                    track = track,
                    isLiked = uiState.isLiked,
                    onDismissRequest = { showContextMenu = false },
                    onLikeClick = onLikeClick,
                    onAddToPlaylistClick = { /* TODO */ },
                    onShareClick = { /* TODO */ },
                    onViewAlbumClick = { /* TODO */ }
                )
            }
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
}

// ═══════════════════════════════════════════════════════════════════════════
// ULTRA-MINIMAL FLOATING MINI PLAYER
// Design: Apple Music × Nothing OS × Spotify mini-player
// Philosophy: Minimal • Focused • Premium • Calm
// ═══════════════════════════════════════════════════════════════════════════

@Composable
fun PremiumMiniPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color = Color(0xFFE53935),
    onPlayPause: () -> Unit,
    onExpand: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onColorExtracted: (Color) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // ─────────────────────────────────────────────────────────────────
    // ARTWORK COLOR EXTRACTION
    // ─────────────────────────────────────────────────────────────────
    LaunchedEffect(track.remoteArtworkUrl) {
        if (track.remoteArtworkUrl != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val request = coil3.request.ImageRequest.Builder(context)
                        .data(track.remoteArtworkUrl)
                        .allowHardware(false)
                        .build()
                    val result = coil3.ImageLoader(context).execute(request)
                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                    if (bitmap != null) {
                        androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                            val vibrant = palette?.vibrantSwatch?.rgb
                            val dominant = palette?.dominantSwatch?.rgb
                            val muted = palette?.mutedSwatch?.rgb
                            val extractedColor = Color(vibrant ?: dominant ?: muted ?: 0xFFE53935.toInt())
                            onColorExtracted(extractedColor)
                        }
                    }
                } catch (e: Exception) { /* Keep default */ }
            }
        }
    }
    
    // ─────────────────────────────────────────────────────────────────
    // ANIMATIONS (Subtle, Premium)
    // ─────────────────────────────────────────────────────────────────
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(400, easing = LinearOutSlowInEasing),
        label = "progress"
    )
    
    var playPressed by remember { mutableStateOf(false) }
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playScale"
    )
    
    // ─────────────────────────────────────────────────────────────────
    // FLOATING PILL CONTAINER
    // ─────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Glassmorphism Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A).copy(alpha = 0.88f))
        ) {
            // Subtle artwork blur behind (very soft)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 40.dp)
                    .alpha(0.15f)
            ) {
                VikifyImage(
                    url = track.remoteArtworkUrl,
                    placeholder = track.artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            // ─────────────────────────────────────────────────────────
            // CONTENT: [Art] Title/Artist | ⏮ ⏯ ⏭
            // ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // HERO ALBUM ART (Larger, with soft shadow) - Tappable to expand
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            shadowElevation = 8f
                            shape = RoundedCornerShape(14.dp)
                        }
                        .clickable(onClick = onExpand)
                ) {
                    VikifyImage(
                        url = track.remoteArtworkUrl,
                        placeholder = track.artwork,
                        contentDescription = track.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                // TEXT (Clean Typography) - Tappable to expand
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onExpand),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // ─────────────────────────────────────────────────────
                // MINIMAL CONTROLS: Only ⏮ ⏯ ⏭
                // ─────────────────────────────────────────────────────
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous (Thin, subtle)
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    
                    // Play/Pause (Slightly larger, hero)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(playScale)
                            .clip(CircleShape)
                            .background(accentColor)
                            .clickable {
                                playPressed = true
                                onPlayPause()
                                playPressed = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Next (Thin, subtle)
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
            // ─────────────────────────────────────────────────────────
            // PROGRESS BAR (Ultra-thin, 2dp, bottom)
            // ─────────────────────────────────────────────────────────
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.06f)
            )
        }
    }
}

// --- Component 2: Premium Expanded Player ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumExpandedPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    isLiked: Boolean,
    isDownloaded: Boolean = false,
    onPlayPause: () -> Unit,
    onCollapse: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit = {},
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}, // NEW: Navigate to artist profile
    modifier: Modifier = Modifier
) {
    val config = LocalConfiguration.current
    val screenHeight = config.screenHeightDp.dp
    
    // Dynamic background brush (fallback for when Living Background is loading)
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            RaataanPurple.copy(alpha = 0.15f), // Top tint
            BackgroundBase,                    // Middle
            BackgroundBase                     // Bottom
        )
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Living Background Layer - Color extraction from album art
        LivingPlayerBackground(
            artworkUrl = track.remoteArtworkUrl,
            modifier = Modifier.fillMaxSize()
        )
        
        // Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
                // Swipe down to dismiss logic
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount > 20) { onCollapse() }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 1. Header (Pull Indicator + Title)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = TextPrimary)
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM PLAYLIST",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
                Text(
                    text = "Vikify Favorites",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Rounded.MoreHoriz, "Options", tint = TextPrimary)
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // 2. Glowing Artwork
        GlowingArtwork(
            track = track,
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxWidth(0.9f).aspectRatio(1f)
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // 3. Track Info (Marquee)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary,
                    maxLines = 1,
                    // Marquee effect for long titles
                    modifier = Modifier.basicMarquee() 
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalVikifyColors.current.accent, // Accent color to indicate clickable
                    maxLines = 1,
                    modifier = Modifier
                        .basicMarquee()
                        .clickable { onArtistClick(track.artist) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action Pills Row (Lyrics + Queue)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Lyrics Pill Button
                    Surface(
                        color = RaataanPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable(onClick = onLyricsClick)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "LYRICS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = RaataanPurple,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    
                    // Queue Pill Button
                    Surface(
                        color = Accent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable(onClick = onQueueClick)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = "QUEUE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Accent,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            
            // Download Button
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = if (isDownloaded) Color(0xFF10B981) else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Like Button
            IconButton(onClick = onLikeClick) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) RaataanPink else TextSecondary,
                    modifier = Modifier.size(28.dp).graphicsLayer {
                        // Subtle pop animation when liked could go here
                        scaleX = if(isLiked) 1.1f else 1f
                        scaleY = if(isLiked) 1.1f else 1f
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Live Waveform Scrubber
        LiveWaveform(
            progress = progress,
            isPlaying = isPlaying,
            onSeek = onSeek,
            modifier = Modifier.fillMaxWidth().height(60.dp)
        )
        
        // Time Labels
        val durationMs = if (track.duration > 0) track.duration else 240000L
        val currentMs = (progress * durationMs).toLong()
        val remainingMs = durationMs - currentMs
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration(currentMs), style = MaterialTheme.typography.labelSmall, color = TextSecondary) 
            Text("-${formatDuration(remainingMs)}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // 5. Controls - FIXED: SpaceEvenly for ergonomic distribution
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle (32.dp)
            IconButton(
                onClick = onShuffleClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Shuffle, 
                    null, 
                    tint = if(shuffleEnabled) Accent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Previous (40.dp)
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.SkipPrevious, null, tint = TextPrimary, modifier = Modifier.size(36.dp))
            }

            // === REACTIVE PLAY BUTTON with Pulse ===
            Box(contentAlignment = Alignment.Center) {
                // Pulse Ring (behind, animates when playing)
                val infiniteTransition = rememberInfiniteTransition(label = "play_pulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1.0f,
                    targetValue = if (isPlaying) 1.25f else 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = if (isPlaying) 0.0f else 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )
                
                // Pulse Ring (only visible when playing)
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = pulseAlpha))
                    )
                }
                
                // Main Play Button with Gradient
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Accent,
                                    Accent.copy(alpha = 0.8f)
                                )
                            )
                        )
                        .clickable { onPlayPause() }
                ) {
                   Icon(
                       imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                       contentDescription = "Play",
                       tint = Color.White,
                       modifier = Modifier.size(36.dp)
                   )
                }
            }

            // Next (40.dp)
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.SkipNext, null, tint = TextPrimary, modifier = Modifier.size(36.dp))
            }

            // Repeat (32.dp)
            IconButton(
                onClick = onRepeatClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if(repeatMode == 1) Icons.Default.RepeatOne else Icons.Default.Repeat, 
                    null, 
                    tint = if(repeatMode > 0) Accent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Increased bottom padding for home button clearance
        Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

// --- Component 3: Glowing Artwork ---
@Composable
fun GlowingArtwork(
    track: Track,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // 1. The Glow (Blurred image behind)
        VikifyImage(
            url = track.remoteArtworkUrl,
            placeholder = track.artwork,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(0.9f) // Slightly smaller
                .offset(y = 12.dp) // Push down
                .blur(radius = 32.dp) // Heavy blur
                .alpha(0.6f) // Semi-transparent
        )
        
        // 2. Main Image
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            VikifyImage(
                url = track.remoteArtworkUrl?.replace("maxresdefault", "maxresdefault"), // Keep your replace logic
                placeholder = track.artwork,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// --- Component 4: Unique Live Waveform (Audio Fingerprint) ---
/**
 * LiveWaveform with Unique Song Fingerprint
 * 
 * Each song has a unique wave shape based on its trackId hash.
 * When playing, the bars dance using sin(time) while maintaining their unique shape.
 * 
 * @param trackId Unique ID of the song - used to seed the wave pattern
 * @param progress Playback progress (0.0 to 1.0)
 * @param isPlaying Whether the song is currently playing
 * @param onSeek Callback when user seeks
 */
@Composable
fun LiveWaveform(
    trackId: String = "",
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    // Generate unique bar heights based on trackId
    // Same trackId always produces the same wave pattern
    val uniqueHeights = remember(trackId) {
        val seed = trackId.hashCode().toLong()
        val random = java.util.Random(seed)
        List(40) { 0.3f + random.nextFloat() * 0.5f } // Heights between 0.3 and 0.8
    }
    
    // Infinite animation for the "Playing" state - bars dance
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase"
    )

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(seekProgress)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val seekProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(seekProgress)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = 40
            val barWidth = size.width / (barCount * 1.5f)
            val spacing = barWidth * 0.5f
            val centerY = size.height / 2
            
            for (i in 0 until barCount) {
                val barProgressLocation = i.toFloat() / barCount
                val isPlayed = barProgressLocation < progress
                
                // Color Logic
                val color = if (isPlayed) Accent else RaataanTextGray.copy(alpha = 0.3f)

                // Unique base height from song fingerprint
                val uniqueHeight = uniqueHeights.getOrElse(i) { 0.5f }
                
                // Dancing animation when playing
                val animatedMultiplier = if (isPlaying) {
                    val waveOffset = (i.toFloat() / barCount) * 4 * Math.PI
                    val wave = kotlin.math.sin(waveOffset + (phase * 2 * Math.PI)).toFloat()
                    0.9f + (wave * 0.15f) // Subtle dance: 0.75 to 1.05 multiplier
                } else {
                    1.0f // Static when paused
                }
                
                val finalHeight = (size.height * uniqueHeight * animatedMultiplier)
                    .coerceAtMost(size.height * 0.9f)
                    .coerceAtLeast(size.height * 0.15f)
                
                // Draw rounded bar
                drawRoundRect(
                    color = color,
                    topLeft = Offset(
                        x = i * (barWidth + spacing),
                        y = centerY - (finalHeight / 2)
                    ),
                    size = Size(barWidth, finalHeight),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }
            
            // Glowing Scrubber Knob
            val scrubberX = size.width * progress
            // Outer glow
            drawCircle(
                color = Accent.copy(alpha = 0.3f),
                radius = 10.dp.toPx(),
                center = Offset(scrubberX, centerY)
            )
            // Inner knob
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(scrubberX, centerY)
            )
        }
    }
}

// Backward compatible overload without trackId
@Composable
fun LiveWaveform(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    LiveWaveform(
        trackId = "",
        progress = progress,
        isPlaying = isPlaying,
        onSeek = onSeek,
        modifier = modifier
    )
}
