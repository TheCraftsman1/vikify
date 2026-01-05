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
import kotlin.math.sin
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Pause

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
    onAddToPlaylist: () -> Unit = {}, // NEW: Add to playlist
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
                accentColor = accentColor, // Dynamic from artwork
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
                onAddToPlaylist = onAddToPlaylist, // Pass new callback
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
                    onAddToPlaylist = { 
                        showContextMenu = false // Dismiss menu first
                        onAddToPlaylist() 
                    },
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
// "DYNAMIC ISLAND" MINI PLAYER
// Design: Background IS the progress bar • Swipe gestures • Ultra-minimal
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
    val haptic = LocalHapticFeedback.current
    
    // ─────────────────────────────────────────────────────────────────
    // SWIPE GESTURE STATE
    // ─────────────────────────────────────────────────────────────────
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 100f // Minimum swipe distance to trigger action
    
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
    // ANIMATIONS
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
    
    // Animate swipe offset back to 0
    val animatedSwipeOffset by animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "swipe"
    )
    
    // ─────────────────────────────────────────────────────────────────
    // DYNAMIC ISLAND CONTAINER
    // ─────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Main Card with progress fill background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .offset { androidx.compose.ui.unit.IntOffset(animatedSwipeOffset.toInt(), 0) }
                .clip(RoundedCornerShape(22.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // Trigger action based on swipe direction
                            when {
                                swipeOffset < -swipeThreshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSkipNext()
                                }
                                swipeOffset > swipeThreshold -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSkipPrevious()
                                }
                            }
                            swipeOffset = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            swipeOffset = (swipeOffset + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
                .clickable { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onExpand() 
                }
        ) {
            // ─────────────────────────────────────────────────────────
            // GLASS THEME: Transparent with accent color glow
            // ─────────────────────────────────────────────────────────
            
            // Layer 1: Blurred artwork background (creates color harmony)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius = 60.dp)
                    .alpha(0.4f)
            ) {
                VikifyImage(
                    url = track.remoteArtworkUrl,
                    placeholder = track.artwork,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Layer 2: Dark glass overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
            
            // Layer 3: Progress Fill (accent colored, more visible)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.35f),
                                accentColor.copy(alpha = 0.2f)
                            )
                        )
                    )
            )
            
            // Layer 4: Glass highlight (top edge)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            
            // ─────────────────────────────────────────────────────────
            // CONTENT: [Art] + [Title/Artist] + [Play/Pause]
            // (No skip buttons - use swipe instead)
            // ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ALBUM ART with glow effect
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .graphicsLayer {
                            shadowElevation = 12f
                            shape = RoundedCornerShape(14.dp)
                        }
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
                
                // TEXT (Title + Artist)
                Column(
                    modifier = Modifier.weight(1f),
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
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // PLAY/PAUSE (Hero button - larger, pill-shaped)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(playScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.8f))
                            )
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            
            // ─────────────────────────────────────────────────────────
            // SWIPE HINT INDICATORS (subtle arrows when swiping)
            // ─────────────────────────────────────────────────────────
            if (animatedSwipeOffset < -30f) {
                // Swiping left - show "skip next" hint
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                        .alpha(((-animatedSwipeOffset) / swipeThreshold).coerceIn(0f, 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip Next",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (animatedSwipeOffset > 30f) {
                // Swiping right - show "skip previous" hint
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .alpha((animatedSwipeOffset / swipeThreshold).coerceIn(0f, 0.8f))
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Skip Previous",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PremiumExpandedPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    shuffleEnabled: Boolean,
    repeatMode: Int, // 0=Off, 1=All, 2=One
    isLiked: Boolean,
    isDownloaded: Boolean = false,
    accentColor: Color = Color(0xFFE53935), // Dynamic from artwork
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
    onAddToPlaylist: () -> Unit = {}, // NEW
    onArtistClick: (String) -> Unit = {}, // NEW: Navigate to artist profile
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Adaptive control colors based on background luminance
    var isLightBackground by remember { mutableStateOf(false) }
    
    // For Kinetic UI, we use White for almost everything on the Glass Console
    // But for the upper layer context menu, we might need adaptive colors
    val controlPrimary = Color.White
    val controlSecondary = Color.White.copy(alpha = 0.6f)
    
    // Breathing Artwork Animation
    val infiniteTransition = rememberInfiniteTransition(label = "breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isPlaying) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // 1. BACKGROUND: Aurora Mesh (The "Soul")
        LivingPlayerBackground(
            artworkUrl = track.remoteArtworkUrl,
            onLuminanceCalculated = { isLight -> isLightBackground = isLight },
            modifier = Modifier.fillMaxSize()
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = Color.White)
                }
                
                // Center: Now Playing + Source Context
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.5f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        "From Your Library", // TODO: Dynamic based on source (Search, Album, Playlist)
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Right: Only Menu button
                IconButton(onClick = onMenuClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, "Options", tint = Color.White)
                }
            }
            
            // Artwork Box (Breathing) with Floating Like Button
            // accentColor is now passed as parameter from PlayerContainer
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .scale(breathScale), // BREATHING EFFECT
                contentAlignment = Alignment.Center
            ) {
                // The Card with artwork
                Card(
                    shape = RoundedCornerShape(32.dp),
                    elevation = CardDefaults.cardElevation(24.dp),
                    modifier = Modifier.aspectRatio(1f)
                ) {
                    Box {
                        VikifyImage(
                            url = track.remoteArtworkUrl?.replace("maxresdefault", "maxresdefault"),
                            placeholder = track.artwork,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Floating Like Button (Bottom-Right INSIDE the artwork)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable { onLikeClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) accentColor else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // 3. THE GLASS CONSOLE (Controls + Info)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Dynamic height based on need, or fixed if we want consistent size
                    // Let's use wrapContentHeight but with a minimum for the look
                    .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .background(Color.White.copy(alpha = 0.08f)) // Glass Effect
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .navigationBarsPadding() // FIX: Prevent overlap with home/nav buttons
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp, start = 24.dp, end = 24.dp, bottom = 20.dp), // Even more compact
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title Row: [Lyrics] [Title] [Queue]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lyrics Button (Left)
                        IconButton(onClick = onLyricsClick, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Subtitles, "Lyrics", tint = Color.White.copy(0.7f), modifier = Modifier.size(22.dp))
                        }
                        
                        // Title & Artist (Center)
                        Column(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
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
                        
                        // Queue Button (Right - replaced Like)
                        IconButton(onClick = onQueueClick, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.QueueMusic, "Queue", tint = Color.White.copy(0.7f), modifier = Modifier.size(22.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    // Neon Waveform Scrubber
                    NeonWaveform(
                        progress = progress,
                        isPlaying = isPlaying,
                        onSeek = onSeek,
                        accentColor = accentColor,
                        modifier = Modifier.fillMaxWidth().height(44.dp) // Slightly shorter
                    )
                    
                    // Duration labels
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val duration = if (track.duration > 0) track.duration else 0L
                        Text(
                            text = formatDuration((duration * progress).toLong()),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.4f)
                        )
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(0.4f)
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    // Primary Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        IconButton(onClick = onShuffleClick) {
                            Icon(
                                Icons.Default.Shuffle, 
                                "Shuffle", 
                                tint = if (shuffleEnabled) accentColor else Color.White.copy(0.4f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    
                        // Prev
                        IconButton(
                            onClick = { 
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipPrevious() 
                            }, 
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        
                        // Play/Pause (Massive & Glowing)
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                                .clickable { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPlayPause() 
                                },
                            contentAlignment = Alignment.Center
                        ) {
                             Icon(
                                 imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                 contentDescription = null,
                                 tint = Color.White,
                                 modifier = Modifier.size(40.dp)
                             )
                        }
                        
                        // Next
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSkipNext()
                            }, 
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        
                        // Repeat
                        IconButton(onClick = onRepeatClick) {
                            val icon = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat
                            val tint = if (repeatMode > 0) accentColor else Color.White.copy(0.4f)
                            Icon(icon, "Repeat", tint = tint, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
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

// --- Component 4: Neon Waveform (Kinetic Upgrade) ---
@Composable
fun NeonWaveform(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // Dancing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "phase"
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bars = 35
            val barWidth = size.width / (bars * 1.8f)
            val gap = barWidth * 0.8f
            val startX = (size.width - (bars * (barWidth + gap))) / 2f
            
            for (i in 0 until bars) {
                val barProgress = i.toFloat() / bars
                val isPlayed = barProgress < progress
                
                // Height Calculation (Simulated Audio Data)
                // Base static S-curve + dancing sine wave when playing
                val baseHeight = 0.3f + (sin(i * 0.5f) * 0.2f).toFloat() 
                val dance = if (isPlaying) sin(phase * 6.28f + i).toFloat() * 0.15f else 0f
                val height = (baseHeight + dance).coerceIn(0.1f, 1f) * size.height
                
                // Color Logic
                val barColor = if (isPlayed) accentColor else Color.White.copy(alpha = 0.2f)
                
                // Draw Glow (Only for played bars)
                if (isPlayed) {
                    drawRoundRect(
                        color = accentColor.copy(alpha = 0.5f),
                        topLeft = Offset(startX + i * (barWidth + gap), (size.height - height) / 2f),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(barWidth),
                        // Note: Real blur is expensive in Canvas on older Androids, 
                        // so we use a semi-transparent layer to simulate glow
                    )
                }

                // Draw Core Bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(startX + i * (barWidth + gap), (size.height - height) / 2f),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth)
                )
            }
        }
        
        // Invisible Touch Target for scrubbing
        Box(
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
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
        )
    }
}


