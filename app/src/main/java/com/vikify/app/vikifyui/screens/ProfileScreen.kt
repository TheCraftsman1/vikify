/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Profile Screen - Personal Music Identity
 */
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vikify.app.R
import com.vikify.app.spotify.SpotifyPlaylist
import com.vikify.app.spotify.SpotifyUser
import com.vikify.app.viewmodels.ProfileStats
import com.vikify.app.viewmodels.ProfileViewModel
import com.vikify.app.vikifyui.components.VikifyGlassCard
import com.vikify.app.vikifyui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTE - Theme-aware wrapper
// ═══════════════════════════════════════════════════════════════════════════════

private object ProfileColors {
    // Brand accents (fixed colors)
    val AccentPurple = Color(0xFF7C4DFF)
    val AccentPurpleGlow = Color(0xFFB388FF)
    val AccentCyan = Color(0xFF00D4E8)
    val AccentPink = Color(0xFFFF6B9D)
    val AccentGreen = Color(0xFF10B981)
    val AccentAmber = Color(0xFFF59E0B)
    val SpotifyGreen = Color(0xFF1DB954)
    
    // Theme-aware colors - accessed via composition
    val Background: Color @Composable get() = VikifyTheme.colors.background
    val Surface: Color @Composable get() = VikifyTheme.colors.surface
    val SurfaceElevated: Color @Composable get() = VikifyTheme.colors.surfaceElevated
    val GlassSurface: Color @Composable get() = VikifyTheme.colors.glassBackground
    val GlassBorder: Color @Composable get() = VikifyTheme.colors.glassBorder
    
    // Text
    val TextPrimary: Color @Composable get() = VikifyTheme.colors.textPrimary
    val TextSecondary: Color @Composable get() = VikifyTheme.colors.textSecondary
    val TextMuted: Color @Composable get() = VikifyTheme.colors.textTertiary
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

data class TopArtistData(
    val name: String,
    val imageUrl: String? = null,
    val playCount: Int = 0
)

data class AchievementData(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val isUnlocked: Boolean,
    val progress: Float = 1f
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSpotifyLogin: () -> Unit = {},
    spotifyUser: SpotifyUser? = null,
    spotifyPlaylists: List<SpotifyPlaylist> = emptyList(),
    isSpotifyLoggedIn: Boolean = false,
    onSpotifyLogout: () -> Unit = {},
    likedSongsCount: Int = 0,
    onLikedSongsClick: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
    isDarkTheme: Boolean = true,
    userName: String? = null,
    userEmail: String? = null,
    userPhotoUrl: String? = null,
    onTimeCapsuleClick: () -> Unit = {},
    onSignIn: () -> Unit = {},
    isGuest: Boolean = false,
    onNameChange: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDark = VikifyTheme.isDark
    
    // Collect ViewModel state
    val stats by viewModel.stats.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val gapless by viewModel.gaplessEnabled.collectAsState()
    val normalize by viewModel.normalizeEnabled.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    val cacheSize by viewModel.cacheSize.collectAsState()
    val animatedBackground by viewModel.animatedBackground.collectAsState()
    
    // Local state
    var isRefreshing by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    
    // Header collapse progress
    val headerProgress by remember {
        derivedStateOf {
            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (firstIndex > 0) 1f else (offset / 300f).coerceIn(0f, 1f)
        }
    }
    
    // Refresh on mount
    LaunchedEffect(Unit) {
        viewModel.refreshStats()
        viewModel.calculateCacheSize()
    }
    
    // Edit Profile Dialog
    if (showEditDialog) {
        EditProfileDialog(
            currentName = userName ?: "",
            onDismiss = { showEditDialog = false },
            onSave = { newName ->
                onNameChange(newName)
                showEditDialog = false
            }
        )
    }
    
    // Audio Quality Dialog
    if (showQualityDialog) {
        AudioQualityDialog(
            currentQuality = audioQuality,
            onDismiss = { showQualityDialog = false },
            onSelect = { quality ->
                viewModel.setAudioQuality(quality)
                showQualityDialog = false
            }
        )
    }

    val colors = VikifyTheme.colors
    val currentThemeMode = VikifyThemeState.currentMode

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
    ) {
        // Background - adapt to theme mode
        when (currentThemeMode) {
            ThemeMode.SUNLIGHT -> EtherealBackground(modifier = Modifier.matchParentSize()) {}
            ThemeMode.MOON -> { /* Pure black - no animated background */ }
            ThemeMode.COOL -> LivingBackground(modifier = Modifier.matchParentSize()) {}
        }
        
        // Content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refreshStats()
                    delay(500)
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
                // Hero Identity
                item(key = "hero") {
                    ProfileHeroSection(
                        name = userName ?: "Vikify User",
                        email = userEmail,
                        photoUrl = userPhotoUrl,
                        isGuest = isGuest,
                        memberSince = "2025",
                        headerProgress = headerProgress,
                        onEditClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isGuest) onSignIn() else showEditDialog = true
                        }
                    )
                }
                
                // Listening Stats
                item(key = "stats") {
                    Spacer(Modifier.height(32.dp))
                    ListeningStatsSection(stats = stats)
                }
                
                // Time Capsule / Sonic DNA removed
                /*
                item(key = "capsule") {
                    Spacer(Modifier.height(24.dp))
                    SonicDnaBanner(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTimeCapsuleClick()
                        }
                    )
                }
                */
                
                // Top Artists
                item(key = "artists") {
                    Spacer(Modifier.height(32.dp))
                    TopArtistsSection(
                        artists = topArtists.map { TopArtistData(name = it) }
                    )
                }
                
                // Achievements (Optional gamification)
                item(key = "achievements") {
                    Spacer(Modifier.height(32.dp))
                    AchievementsSection(
                        achievements = generateSampleAchievements(stats)
                    )
                }
                
                // Connected Services
                item(key = "services") {
                    Spacer(Modifier.height(32.dp))
                    ConnectedServicesSection(
                        isSpotifyConnected = isSpotifyLoggedIn,
                        spotifyUser = spotifyUser,
                        playlistCount = spotifyPlaylists.size,
                        onSpotifyConnect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpotifyLogin()
                        },
                        onSpotifyDisconnect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpotifyLogout()
                        }
                    )
                }
                
                // Settings
                item(key = "settings") {
                    Spacer(Modifier.height(32.dp))
                    SettingsSection(
                        isDarkTheme = isDarkTheme,
                        animatedBackground = animatedBackground,
                        gapless = gapless,
                        normalize = normalize,
                        audioQuality = audioQuality,
                        cacheSize = cacheSize,
                        onThemeToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onThemeToggle()
                        },
                        onAnimatedBackgroundToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setAnimatedBackground(it)
                        },
                        onGaplessToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setGapless(it)
                        },
                        onNormalizeToggle = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setNormalize(it)
                        },
                        onQualityClick = { showQualityDialog = true },
                        onClearCache = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearCache()
                        }
                    )
                }
                
                // App info footer
                item(key = "footer") {
                    Spacer(Modifier.height(32.dp))
                    AppInfoFooter()
                }
            }
        }
        
        // Collapsed header
        CollapsedProfileHeader(
            visible = headerProgress > 0.8f,
            name = userName ?: "Vikify User",
            photoUrl = userPhotoUrl,
            onSettingsClick = onSettingsClick
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HERO SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileHeroSection(
    name: String,
    email: String?,
    photoUrl: String?,
    isGuest: Boolean,
    memberSince: String,
    headerProgress: Float,
    onEditClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp)
            .graphicsLayer {
                alpha = 1f - (headerProgress * 0.5f)
                translationY = -headerProgress * 100f
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated Avatar Ring
        ProfileAvatar(
            name = name,
            photoUrl = photoUrl,
            size = 140.dp
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Name
        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = ProfileColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        
        // Email or Guest badge
        if (email != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileColors.AccentPurple
            )
        } else if (isGuest) {
            Spacer(Modifier.height(8.dp))
            GuestBadge()
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Member since
        Text(
            text = "Member since $memberSince",
            style = MaterialTheme.typography.bodySmall,
            color = ProfileColors.TextMuted
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Edit/Sign In button
        ProfileActionButton(
            text = if (isGuest) "Sign In to Sync" else "Edit Profile",
            icon = if (isGuest) Icons.Rounded.Login else Icons.Rounded.Edit,
            onClick = onEditClick
        )
    }
}

@Composable
private fun ProfileAvatar(
    name: String,
    photoUrl: String?,
    size: Dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatarRing")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // Outer glow
        Box(
            modifier = Modifier
                .size(size)
                .scale(pulseScale)
                .blur(20.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ProfileColors.AccentPurple.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        
        // Rotating gradient ring
        Box(
            modifier = Modifier
                .size(size - 4.dp)
                .rotate(rotation)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            ProfileColors.AccentPurple,
                            ProfileColors.AccentCyan,
                            ProfileColors.AccentPink,
                            ProfileColors.AccentPurple
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Avatar content
        Box(
            modifier = Modifier
                .size(size - 16.dp)
                .clip(CircleShape)
                .background(ProfileColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Initials
                Text(
                    text = name.take(2).uppercase(),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = ProfileColors.AccentPurple
                )
            }
        }
    }
}

@Composable
private fun GuestBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ProfileColors.AccentAmber.copy(alpha = 0.15f))
            .border(1.dp, ProfileColors.AccentAmber.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PersonOutline,
                contentDescription = null,
                tint = ProfileColors.AccentAmber,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Guest Mode",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = ProfileColors.AccentAmber
            )
        }
    }
}

@Composable
private fun ProfileActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(ProfileColors.GlassSurface)
            .border(1.dp, ProfileColors.GlassBorder, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = ProfileColors.TextPrimary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = ProfileColors.TextPrimary
            )
        }
    }
}

@Composable
private fun CollapsedProfileHeader(
    visible: Boolean,
    name: String,
    photoUrl: String?,
    onSettingsClick: () -> Unit
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
                            ProfileColors.Background,
                            ProfileColors.Background.copy(alpha = 0.95f),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mini avatar
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ProfileColors.SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = name.take(1).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ProfileColors.AccentPurple
                            )
                        }
                    }
                    
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = ProfileColors.TextPrimary
                    )
                }
                
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = ProfileColors.TextPrimary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STATS SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ListeningStatsSection(stats: ProfileStats) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ProfileSectionHeader(
            title = "Your 2025",
            subtitle = "Listening insights"
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedStatCard(
                icon = Icons.Rounded.Schedule,
                value = formatMinutes(stats.minutesListened),
                label = "Listened",
                color = ProfileColors.AccentCyan,
                modifier = Modifier.weight(1f)
            )
            
            AnimatedStatCard(
                icon = Icons.Rounded.Favorite,
                value = "${stats.likedSongsCount}",
                label = "Liked Songs",
                color = ProfileColors.AccentPink,
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedStatCard(
                icon = Icons.Rounded.GraphicEq,
                value = stats.topGenre.ifEmpty { "Discovering..." },
                label = "Top Genre",
                color = ProfileColors.AccentGreen,
                modifier = Modifier.weight(1f)
            )
            
            AnimatedStatCard(
                icon = Icons.Rounded.Bolt,
                value = "${calculateEnergyScore(stats)}%",
                label = "Energy Score",
                color = ProfileColors.AccentAmber,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnimatedStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "statScale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "statAlpha"
    )
    
    Box(
        modifier = modifier
            .height(110.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(ProfileColors.GlassSurface)
            .border(1.dp, ProfileColors.GlassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon with glow
            Box {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .blur(8.dp)
                        .background(color.copy(alpha = 0.4f), CircleShape)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = ProfileColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ProfileColors.TextMuted
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SONIC DNA BANNER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SonicDnaBanner(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "bannerScale"
    )
    
    // Animated gradient
    val infiniteTransition = rememberInfiniteTransition(label = "banner")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(120.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Animated gradient background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gradient = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1a1a2e),
                    Color(0xFF16213e),
                    Color(0xFF0f3460),
                    Color(0xFF1a1a2e)
                ),
                start = Offset(size.width * shimmerOffset, 0f),
                end = Offset(size.width * (shimmerOffset + 0.5f), size.height)
            )
            drawRect(gradient)
        }
        
        // Floating particles effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..5) {
                val x = size.width * ((shimmerOffset + i * 0.15f) % 1f)
                val y = size.height * (0.3f + sin(shimmerOffset * 6.28f + i) * 0.2f).toFloat()
                drawCircle(
                    color = ProfileColors.AccentAmber.copy(alpha = 0.3f),
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
        
        // Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = ProfileColors.AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Sonic DNA",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Discover your musical identity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Enter Time Capsule →",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ProfileColors.AccentAmber
                )
            }
            
            // DNA helix icon placeholder
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(ProfileColors.AccentAmber.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = null,
                    tint = ProfileColors.AccentAmber,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TOP ARTISTS SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TopArtistsSection(artists: List<TopArtistData>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ProfileSectionHeader(
            title = "Top Artists",
            subtitle = "Your most played"
        )
        
        Spacer(Modifier.height(16.dp))
        
        if (artists.isEmpty()) {
            EmptyArtistsState()
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(artists, key = { it.name }) { artist ->
                    TopArtistCard(artist = artist)
                }
            }
        }
    }
}

@Composable
private fun TopArtistCard(artist: TopArtistData) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "artistScale"
    )
    
    Column(
        modifier = Modifier
            .width(100.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { /* Navigate to artist */ }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Artist image
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ProfileColors.SurfaceElevated)
                .border(2.dp, ProfileColors.AccentPurple.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl != null) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = ProfileColors.TextMuted,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = ProfileColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyArtistsState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ProfileColors.GlassSurface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = ProfileColors.TextMuted,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Keep listening to discover your favorites",
                style = MaterialTheme.typography.bodySmall,
                color = ProfileColors.TextMuted
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACHIEVEMENTS SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AchievementsSection(achievements: List<AchievementData>) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ProfileSectionHeader(
            title = "Achievements",
            subtitle = "Your milestones"
        )
        
        Spacer(Modifier.height(16.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(achievements, key = { it.id }) { achievement ->
                AchievementBadge(achievement = achievement)
            }
        }
    }
}

@Composable
private fun AchievementBadge(achievement: AchievementData) {
    Box(
        modifier = Modifier
            .size(width = 140.dp, height = 100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (achievement.isUnlocked)
                    ProfileColors.GlassSurface
                else
                    ProfileColors.Surface.copy(alpha = 0.5f)
            )
            .border(
                width = 1.dp,
                color = if (achievement.isUnlocked)
                    achievement.color.copy(alpha = 0.3f)
                else
                    ProfileColors.GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (achievement.isUnlocked)
                                achievement.color.copy(alpha = 0.2f)
                            else
                                ProfileColors.Surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = achievement.icon,
                        contentDescription = null,
                        tint = if (achievement.isUnlocked)
                            achievement.color
                        else
                            ProfileColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                if (!achievement.isUnlocked) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Locked",
                        tint = ProfileColors.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = if (achievement.isUnlocked)
                    ProfileColors.TextPrimary
                else
                    ProfileColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (!achievement.isUnlocked && achievement.progress < 1f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { achievement.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = achievement.color,
                    trackColor = ProfileColors.Surface
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CONNECTED SERVICES SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ConnectedServicesSection(
    isSpotifyConnected: Boolean,
    spotifyUser: SpotifyUser?,
    playlistCount: Int,
    onSpotifyConnect: () -> Unit,
    onSpotifyDisconnect: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ProfileSectionHeader(
            title = "Connected Services",
            subtitle = "Sync your music"
        )
        
        Spacer(Modifier.height(16.dp))
        
        ServiceCard(
            name = if (isSpotifyConnected) spotifyUser?.displayName ?: "Spotify Connected" else "Spotify",
            description = if (isSpotifyConnected) "$playlistCount playlists synced" else "Import your playlists",
            iconColor = ProfileColors.SpotifyGreen,
            isConnected = isSpotifyConnected,
            onConnect = onSpotifyConnect,
            onDisconnect = onSpotifyDisconnect
        )
    }
}

@Composable
private fun ServiceCard(
    name: String,
    description: String,
    iconColor: Color,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "serviceScale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(ProfileColors.GlassSurface)
            .border(1.dp, ProfileColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Service icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = ProfileColors.TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = ProfileColors.TextSecondary
            )
        }
        
        if (isConnected) {
            IconButton(onClick = onDisconnect) {
                Icon(
                    Icons.Outlined.Logout,
                    contentDescription = "Disconnect",
                    tint = Color(0xFFEF4444)
                )
            }
        } else {
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Connect", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SettingsSection(
    isDarkTheme: Boolean,
    animatedBackground: Boolean,
    gapless: Boolean,
    normalize: Boolean,
    audioQuality: String,
    cacheSize: String,
    onThemeToggle: () -> Unit,
    onAnimatedBackgroundToggle: (Boolean) -> Unit,
    onGaplessToggle: (Boolean) -> Unit,
    onNormalizeToggle: (Boolean) -> Unit,
    onQualityClick: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ProfileSectionHeader(title = "Settings")
        
        Spacer(Modifier.height(16.dp))
        
        // Appearance - Use new 3-mode theme selector
        com.vikify.app.vikifyui.components.ThemeSwitch(
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        SettingsToggleRow(
            icon = Icons.Outlined.Animation,
            title = "Animated Background",
            subtitle = "Floating color orbs",
            isEnabled = animatedBackground,
            onToggle = onAnimatedBackgroundToggle
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Playback
        SettingsGroupHeader("PLAYBACK")
        
        SettingsToggleRow(
            icon = Icons.Outlined.Speed,
            title = "Gapless Playback",
            subtitle = "Seamless transitions",
            isEnabled = gapless,
            onToggle = onGaplessToggle
        )
        
        SettingsToggleRow(
            icon = Icons.Outlined.VolumeUp,
            title = "Normalize Volume",
            subtitle = "Consistent loudness",
            isEnabled = normalize,
            onToggle = onNormalizeToggle
        )
        
        SettingsNavigationRow(
            icon = Icons.Outlined.HighQuality,
            title = "Audio Quality",
            value = audioQuality,
            onClick = onQualityClick
        )
        
        Spacer(Modifier.height(20.dp))
        
        // Data
        SettingsGroupHeader("DATA & STORAGE")
        
        SettingsActionRow(
            icon = Icons.Outlined.DeleteOutline,
            title = "Clear Cache",
            subtitle = cacheSize,
            actionText = "Clear",
            onClick = onClearCache
        )
    }
}

@Composable
private fun SettingsGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = ProfileColors.TextMuted,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(!isEnabled) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ProfileColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = ProfileColors.TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileColors.TextMuted
                )
            }
        }
        
        Switch(
            checked = isEnabled,
            onCheckedChange = null, // Handled by row click
            colors = SwitchDefaults.colors(
                checkedThumbColor = ProfileColors.AccentPurple,
                checkedTrackColor = ProfileColors.AccentPurple.copy(alpha = 0.3f),
                uncheckedThumbColor = ProfileColors.TextMuted,
                uncheckedTrackColor = ProfileColors.Surface
            )
        )
    }
}

@Composable
private fun SettingsNavigationRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ProfileColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = ProfileColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileColors.AccentPurple
        )
        
        Spacer(Modifier.width(8.dp))
        
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = ProfileColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ProfileColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = ProfileColors.TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ProfileColors.TextMuted
            )
        }
        
        TextButton(onClick = onClick) {
            Text(
                text = actionText,
                color = ProfileColors.AccentPurple,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// APP INFO FOOTER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun AppInfoFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.vikify_logo),
            contentDescription = "Vikify Logo",
            modifier = Modifier.size(40.dp)
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Vikify",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = ProfileColors.TextPrimary
        )
        
        Text(
            text = "Version 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = ProfileColors.TextMuted
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Made with ♥ for music lovers",
            style = MaterialTheme.typography.bodySmall,
            color = ProfileColors.TextMuted
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DIALOGS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun EditProfileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Edit Profile",
                fontWeight = FontWeight.Bold,
                color = ProfileColors.TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Display Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = ProfileColors.TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ProfileColors.AccentPurple,
                        unfocusedBorderColor = ProfileColors.GlassBorder,
                        cursorColor = ProfileColors.AccentPurple
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text("Save", color = ProfileColors.AccentPurple, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ProfileColors.TextSecondary)
            }
        },
        containerColor = ProfileColors.SurfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun AudioQualityDialog(
    currentQuality: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val qualities = listOf(
        "Low" to "Save data",
        "High" to "Better sound",
        "Super" to "Best quality"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Audio Quality",
                fontWeight = FontWeight.Bold,
                color = ProfileColors.TextPrimary
            )
        },
        text = {
            Column {
                qualities.forEach { (quality, description) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(quality) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentQuality == quality,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = ProfileColors.AccentPurple,
                                unselectedColor = ProfileColors.TextMuted
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = quality,
                                style = MaterialTheme.typography.bodyLarge,
                                color = ProfileColors.TextPrimary
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = ProfileColors.TextMuted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ProfileColors.TextSecondary)
            }
        },
        containerColor = ProfileColors.SurfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProfileSectionHeader(
    title: String,
    subtitle: String? = null
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = ProfileColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ProfileColors.TextMuted
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatMinutes(minutes: Long): String {
    return when {
        minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
        else -> "${minutes}m"
    }
}

private fun calculateEnergyScore(stats: ProfileStats): Int {
    // Mock calculation based on listening habits
    return (80 + (stats.minutesListened % 20).toInt()).coerceIn(0, 100)
}

private fun generateSampleAchievements(stats: ProfileStats): List<AchievementData> {
    return listOf(
        AchievementData(
            id = "first_100",
            title = "Century",
            description = "Listen to 100 songs",
            icon = Icons.Rounded.Celebration,
            color = ProfileColors.AccentAmber,
            isUnlocked = stats.minutesListened > 300,
            progress = (stats.minutesListened / 300f).toFloat().coerceIn(0f, 1f)
        ),
        AchievementData(
            id = "night_owl",
            title = "Night Owl",
            description = "Listen past midnight",
            icon = Icons.Rounded.NightsStay,
            color = ProfileColors.AccentPurple,
            isUnlocked = true
        ),
        AchievementData(
            id = "explorer",
            title = "Explorer",
            description = "Discover 10 genres",
            icon = Icons.Rounded.Explore,
            color = ProfileColors.AccentCyan,
            isUnlocked = false,
            progress = 0.6f
        ),
        AchievementData(
            id = "curator",
            title = "Curator",
            description = "Like 50 songs",
            icon = Icons.Rounded.Favorite,
            color = ProfileColors.AccentPink,
            isUnlocked = stats.likedSongsCount >= 50,
            progress = (stats.likedSongsCount / 50f).coerceIn(0f, 1f)
        )
    )
}


