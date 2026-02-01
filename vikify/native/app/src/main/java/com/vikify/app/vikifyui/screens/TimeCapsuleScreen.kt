/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Time Capsule / Sonic DNA - Your Musical Journey Visualized
 * A premium, immersive experience showcasing listening history
 */
package com.vikify.app.vikifyui.screens

import android.graphics.Color as AndroidColor
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════════════════════

private object CapsuleColors {
    // Cosmic theme
    val DeepSpace = Color(0xFF050510)
    val NebulaPurple = Color(0xFF6B21A8)
    val NebulaBlue = Color(0xFF1E40AF)
    val NebulaPink = Color(0xFFDB2777)
    val StarWhite = Color(0xFFFAFAFA)
    
    // Accent colors for genres
    val SunOrange = Color(0xFFFFA500)
    val SunRed = Color(0xFFFF4500)
    
    // UI
    val GlassSurface = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
    val TextPrimary = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.7f)
    val TextMuted = Color.White.copy(alpha = 0.4f)
    
    // Genre colors
    val genreColors = listOf(
        Color(0xFFEF4444), // Red
        Color(0xFFF97316), // Orange
        Color(0xFFEAB308), // Yellow
        Color(0xFF22C55E), // Green
        Color(0xFF06B6D4), // Cyan
        Color(0xFF3B82F6), // Blue
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════════════════════════════

data class TimeCapsuleState(
    val isLoading: Boolean = true,
    val totalMinutes: Int = 0,
    val totalSongs: Int = 0,
    val topArtist: String = "",
    val topArtistImageUrl: String? = null,
    val topArtistPlayCount: Int = 0,
    val topSong: TopSongData? = null,
    val genres: List<GenreData> = emptyList(),
    val topArtists: List<CapsuleArtistData> = emptyList(),
    val listeningByHour: List<Int> = List(24) { 0 },
    val listeningByDay: List<Int> = List(7) { 0 },
    val moodScore: Float = 0.5f,
    val discoveryScore: Float = 0.5f,
    val streakDays: Int = 0,
    val yearLabel: String = "2025"
)

data class GenreData(
    val name: String,
    val minutes: Int,
    val percentage: Float,
    val colorHex: String,
    val orbitSpeed: Float = 1f
)

data class CapsuleArtistData(
    val name: String,
    val imageUrl: String?,
    val playCount: Int,
    val topSong: String
)

data class TopSongData(
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val playCount: Int
)

// ═══════════════════════════════════════════════════════════════════════════════
// VIEW MODEL (Simplified - connect to your actual ViewModel)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun rememberTimeCapsuleState(): State<TimeCapsuleState> {
    return remember {
        mutableStateOf(
            TimeCapsuleState(
                isLoading = false,
                totalMinutes = 12450,
                totalSongs = 847,
                topArtist = "The Weeknd",
                topArtistImageUrl = "https://i.scdn.co/image/ab6761610000e5eb214f3cf1cbe7139c1e26ffbb",
                topArtistPlayCount = 234,
                topSong = TopSongData(
                    title = "Blinding Lights",
                    artist = "The Weeknd",
                    imageUrl = "https://i.scdn.co/image/ab67616d0000b273c559a84d5a37627db8cde684",
                    playCount = 89
                ),
                genres = listOf(
                    GenreData("Pop", 3200, 0.26f, "#EF4444", 1.0f),
                    GenreData("R&B", 2800, 0.22f, "#8B5CF6", 0.8f),
                    GenreData("Hip-Hop", 2400, 0.19f, "#F97316", 1.2f),
                    GenreData("Electronic", 1800, 0.14f, "#06B6D4", 0.6f),
                    GenreData("Indie", 1200, 0.10f, "#22C55E", 0.9f),
                    GenreData("Rock", 1050, 0.09f, "#3B82F6", 1.1f)
                ),
                topArtists = listOf(
                    CapsuleArtistData("The Weeknd", "https://i.scdn.co/image/ab6761610000e5eb214f3cf1cbe7139c1e26ffbb", 234, "Blinding Lights"),
                    CapsuleArtistData("Drake", "https://i.scdn.co/image/ab6761610000e5eb4293385d324db8558179afd9", 189, "Rich Flex"),
                    CapsuleArtistData("Dua Lipa", "https://i.scdn.co/image/ab6761610000e5eb1f77ec8ad5d2efe8e3e92d5b", 156, "Levitating"),
                    CapsuleArtistData("Post Malone", null, 134, "Circles"),
                    CapsuleArtistData("Billie Eilish", null, 112, "bad guy")
                ),
                listeningByHour = listOf(2,1,0,0,0,1,3,8,12,15,18,22,25,28,30,32,35,40,45,42,38,28,18,8),
                listeningByDay = listOf(180, 220, 195, 240, 280, 320, 290),
                moodScore = 0.72f,
                discoveryScore = 0.45f,
                streakDays = 23,
                yearLabel = "2025"
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeCapsuleScreen(
    onBackClick: () -> Unit,
    onShareClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    // State
    val state by rememberTimeCapsuleState()
    val pagerState = rememberPagerState(pageCount = { 6 })
    
    // Time warp slider value
    var timeWarp by remember { mutableFloatStateOf(1f) }
    
    // Entry animations
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CapsuleColors.DeepSpace)
    ) {
        // Layer 1: Animated starfield background
        CosmicBackground(
            timeWarp = timeWarp,
            modifier = Modifier.fillMaxSize()
        )

        if (state.isLoading) {
            LoadingState()
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CapsuleTopBar(
                    currentPage = pagerState.currentPage,
                    pageCount = pagerState.pageCount,
                    onBackClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBackClick()
                    },
                    onShareClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShareClick()
                    }
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    when (page) {
                        0 -> IntroSlide(state = state, isVisible = pagerState.currentPage == 0)
                        1 -> TopArtistSlide(state = state, isVisible = pagerState.currentPage == 1)
                        2 -> TopSongSlide(state = state, isVisible = pagerState.currentPage == 2)
                        3 -> GenreGalaxySlide(state = state, timeWarp = timeWarp, isVisible = pagerState.currentPage == 3)
                        4 -> ListeningPatternsSlide(state = state, isVisible = pagerState.currentPage == 4)
                        5 -> SummarySlide(state = state, isVisible = pagerState.currentPage == 5)
                    }
                }

                PageIndicator(
                    currentPage = pagerState.currentPage,
                    pageCount = pagerState.pageCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                )
            }

            // Tap zones for navigation
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (pagerState.currentPage > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                        }
                )
                Spacer(Modifier.weight(0.4f))
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (pagerState.currentPage < pagerState.pageCount - 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CapsuleTopBar(
    currentPage: Int,
    pageCount: Int,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            icon = Icons.Rounded.Close,
            contentDescription = "Close",
            onClick = onBackClick
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(pageCount) { index ->
                val progress = when {
                    index < currentPage -> 1f
                    index == currentPage -> 1f
                    else -> 0f
                }
                
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(300),
                    label = "progressBar"
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CapsuleColors.GlassSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(CapsuleColors.TextPrimary)
                    )
                }
            }
        }

        GlassIconButton(
            icon = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = onShareClick
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(CapsuleColors.GlassSurface)
            .border(1.dp, CapsuleColors.GlassBorder, CircleShape)
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
            tint = CapsuleColors.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isSelected = index == currentPage
            
            val width by animateDpAsState(
                targetValue = if (isSelected) 24.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "indicatorWidth"
            )
            
            val alpha by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.4f,
                label = "indicatorAlpha"
            )
            
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CapsuleColors.TextPrimary.copy(alpha = alpha))
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LOADING STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingState() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "loadingRotation"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            CapsuleColors.NebulaPurple,
                            CapsuleColors.NebulaBlue,
                            CapsuleColors.NebulaPink,
                            CapsuleColors.NebulaPurple
                        )
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Analyzing your sonic DNA...",
            style = MaterialTheme.typography.bodyLarge,
            color = CapsuleColors.TextSecondary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// COSMIC BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CosmicBackground(
    timeWarp: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cosmos")

    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(120000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slowRotation"
    )
    
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(80000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mediumRotation"
    )
    
    val rotation3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fastRotation"
    )

    val nebulaPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "nebulaPulse"
    )

    val shootingStarProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shootingStar"
    )

    val stars1 = remember { List(100) { StarData.random(0.3f..0.6f, 1f..2f) } }
    val stars2 = remember { List(70) { StarData.random(0.5f..0.8f, 1.5f..2.5f) } }
    val stars3 = remember { List(40) { StarData.random(0.7f..1f, 2f..3.5f) } }

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        // Nebula clouds
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CapsuleColors.NebulaPurple.copy(alpha = 0.25f * nebulaPulse),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.25f, size.height * 0.2f),
                radius = size.width * 0.6f
            )
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CapsuleColors.NebulaBlue.copy(alpha = 0.2f * nebulaPulse),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.75f, size.height * 0.7f),
                radius = size.width * 0.5f
            )
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CapsuleColors.NebulaPink.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.5f, size.height * 0.5f),
                radius = size.width * 0.7f
            )
        )

        val effectiveSpeed = timeWarp.coerceIn(0.2f, 2f)
        
        // Layer 1: Distant stars
        stars1.forEach { star ->
            val angle = Math.toRadians((rotation1 * 0.3f * effectiveSpeed + star.angle).toDouble())
            val dist = star.distance * size.width * 0.45f
            val x = centerX + (dist * cos(angle)).toFloat()
            val y = centerY + (dist * sin(angle)).toFloat()
            
            drawCircle(
                color = CapsuleColors.StarWhite.copy(alpha = star.brightness * 0.6f),
                radius = star.size,
                center = Offset(x, y)
            )
        }
        
        // Layer 2: Mid-distance stars
        stars2.forEach { star ->
            val angle = Math.toRadians((rotation2 * 0.5f * effectiveSpeed + star.angle).toDouble())
            val dist = star.distance * size.width * 0.5f
            val x = centerX + (dist * cos(angle)).toFloat()
            val y = centerY + (dist * sin(angle)).toFloat()
            
            drawCircle(
                color = CapsuleColors.StarWhite.copy(alpha = star.brightness * 0.8f),
                radius = star.size,
                center = Offset(x, y)
            )
        }
        
        // Layer 3: Close stars
        stars3.forEach { star ->
            val angle = Math.toRadians((rotation3 * 0.7f * effectiveSpeed + star.angle).toDouble())
            val dist = star.distance * size.width * 0.55f
            val x = centerX + (dist * cos(angle)).toFloat()
            val y = centerY + (dist * sin(angle)).toFloat()
            
            if (star.brightness > 0.8f) {
                drawCircle(
                    color = CapsuleColors.StarWhite.copy(alpha = star.brightness * 0.3f),
                    radius = star.size * 3f,
                    center = Offset(x, y)
                )
            }
            
            drawCircle(
                color = CapsuleColors.StarWhite.copy(alpha = star.brightness),
                radius = star.size,
                center = Offset(x, y)
            )
        }

        // Shooting star
        if (shootingStarProgress < 0.25f) {
            val progress = shootingStarProgress / 0.25f
            val startX = size.width * 0.85f
            val startY = size.height * 0.08f
            val endX = size.width * 0.15f
            val endY = size.height * 0.35f

            val currentX = startX + (endX - startX) * progress
            val currentY = startY + (endY - startY) * progress
            val trailLength = 100f

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(CapsuleColors.StarWhite, Color.Transparent),
                    start = Offset(currentX, currentY),
                    end = Offset(currentX + trailLength * 0.7f, currentY - trailLength * 0.3f)
                ),
                start = Offset(currentX, currentY),
                end = Offset(currentX + trailLength, currentY - trailLength * 0.4f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
            
            drawCircle(
                color = CapsuleColors.StarWhite.copy(alpha = 0.8f),
                radius = 4f,
                center = Offset(currentX, currentY)
            )
            drawCircle(
                color = CapsuleColors.StarWhite.copy(alpha = 0.3f),
                radius = 10f,
                center = Offset(currentX, currentY)
            )
        }
    }
}

private data class StarData(
    val angle: Float,
    val distance: Float,
    val brightness: Float,
    val size: Float
) {
    companion object {
        fun random(brightnessRange: ClosedFloatingPointRange<Float>, sizeRange: ClosedFloatingPointRange<Float>): StarData {
            return StarData(
                angle = Random.nextFloat() * 360f,
                distance = Random.nextFloat(),
                brightness = Random.nextFloat() * (brightnessRange.endInclusive - brightnessRange.start) + brightnessRange.start,
                size = Random.nextFloat() * (sizeRange.endInclusive - sizeRange.start) + sizeRange.start
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// STORY SLIDES
// ═══════════════════════════════════════════════════════════════════════════════

// SLIDE 1: INTRO
@Composable
private fun IntroSlide(
    state: TimeCapsuleState,
    isVisible: Boolean
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            animationStarted = true
        } else {
            animationStarted = false
        }
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "titleAlpha"
    )
    
    val minutesScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "minutesScale"
    )

    val animatedMinutes by animateIntAsState(
        targetValue = if (animationStarted) state.totalMinutes else 0,
        animationSpec = tween(2000, easing = FastOutSlowInEasing),
        label = "minutesCounter"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = state.yearLabel,
            style = MaterialTheme.typography.titleMedium.copy(
                letterSpacing = 8.sp
            ),
            color = CapsuleColors.TextMuted,
            modifier = Modifier.alpha(titleAlpha)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your Sonic DNA",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CapsuleColors.TextPrimary,
            modifier = Modifier.alpha(titleAlpha)
        )

        Spacer(Modifier.height(48.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(minutesScale)
        ) {
            Text(
                text = formatNumber(animatedMinutes),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 72.sp
                ),
                color = CapsuleColors.TextPrimary
            )
            Text(
                text = "minutes of music",
                style = MaterialTheme.typography.titleMedium,
                color = CapsuleColors.TextSecondary
            )
        }

        Spacer(Modifier.height(48.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(titleAlpha),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatPill(
                value = "${state.totalSongs}",
                label = "songs",
                icon = Icons.Rounded.MusicNote
            )
            StatPill(
                value = "${state.streakDays}",
                label = "day streak",
                icon = Icons.Rounded.LocalFireDepartment
            )
        }
    }
}

// SLIDE 2: TOP ARTIST
@Composable
private fun TopArtistSlide(
    state: TimeCapsuleState,
    isVisible: Boolean
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            animationStarted = true
        }
    }

    val imageScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "imageScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400),
        label = "contentAlpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "artistGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your #1 Artist",
            style = MaterialTheme.typography.titleMedium,
            color = CapsuleColors.TextMuted,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(200.dp)
                .scale(imageScale),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .blur(40.dp)
                    .alpha(glowAlpha)
                    .background(CapsuleColors.SunOrange, CircleShape)
            )

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .border(
                        width = 4.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                CapsuleColors.SunOrange,
                                CapsuleColors.SunRed,
                                CapsuleColors.NebulaPurple,
                                CapsuleColors.SunOrange
                            )
                        ),
                        shape = CircleShape
                    )
            )

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(CapsuleColors.GlassSurface)
            ) {
                if (state.topArtistImageUrl != null) {
                    AsyncImage(
                        model = state.topArtistImageUrl,
                        contentDescription = state.topArtist,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CapsuleColors.SunOrange, CapsuleColors.SunRed)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.topArtist.take(1),
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = state.topArtist,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CapsuleColors.TextPrimary,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "You played ${state.topArtistPlayCount} times",
            style = MaterialTheme.typography.bodyLarge,
            color = CapsuleColors.TextSecondary,
            modifier = Modifier.alpha(contentAlpha)
        )
    }
}

// SLIDE 3: TOP SONG
@Composable
private fun TopSongSlide(
    state: TimeCapsuleState,
    isVisible: Boolean
) {
    val song = state.topSong ?: return

    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            animationStarted = true
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )

    val albumScale by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "albumScale"
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(600, delayMillis = 400),
        label = "contentAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Your #1 Song",
            style = MaterialTheme.typography.titleMedium,
            color = CapsuleColors.TextMuted,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(albumScale),
            contentAlignment = Alignment.Center
        ) {
            // Vinyl record
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .offset(x = 40.dp)
                    .rotate(vinylRotation)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    for (i in 1..8) {
                        drawCircle(
                            color = Color(0xFF333333),
                            radius = size.width / 2 - (i * 12f),
                            center = center,
                            style = Stroke(width = 1f)
                        )
                    }
                    drawCircle(
                        color = CapsuleColors.SunOrange,
                        radius = 20f,
                        center = center
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                modifier = Modifier.size(180.dp)
            ) {
                if (song.imageUrl != null) {
                    AsyncImage(
                        model = song.imageUrl,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(CapsuleColors.NebulaPurple, CapsuleColors.NebulaBlue)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = song.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CapsuleColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = song.artist,
            style = MaterialTheme.typography.titleMedium,
            color = CapsuleColors.TextSecondary,
            modifier = Modifier.alpha(contentAlpha)
        )

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .alpha(contentAlpha)
                .clip(RoundedCornerShape(20.dp))
                .background(CapsuleColors.GlassSurface)
                .border(1.dp, CapsuleColors.GlassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Played ${song.playCount} times",
                style = MaterialTheme.typography.labelLarge,
                color = CapsuleColors.TextPrimary
            )
        }
    }
}

// SLIDE 4: GENRE GALAXY
@Composable
private fun GenreGalaxySlide(
    state: TimeCapsuleState,
    timeWarp: Float,
    isVisible: Boolean
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            animationStarted = true
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(600),
        label = "contentAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        GenreOrbits(
            genres = state.genres,
            timeWarp = timeWarp,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Genre Galaxy",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = CapsuleColors.TextPrimary
            )

            Spacer(Modifier.weight(1f))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.genres.take(4)) { genre ->
                    GenreLegendItem(genre = genre)
                }
            }
        }
    }
}

@Composable
private fun GenreOrbits(
    genres: List<GenreData>,
    timeWarp: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbits")
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitRotation"
    )

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val baseOrbitRadius = minOf(size.width, size.height) * 0.15f

        genres.forEachIndexed { index, genre ->
            val orbitRadius = baseOrbitRadius + (index * 50f * timeWarp)
            val angle = (baseRotation * genre.orbitSpeed) + (index * 60f)
            val rad = Math.toRadians(angle.toDouble())

            drawCircle(
                color = CapsuleColors.GlassBorder,
                radius = orbitRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1f)
            )

            val planetX = centerX + (orbitRadius * cos(rad)).toFloat()
            val planetY = centerY + (orbitRadius * sin(rad)).toFloat()
            val planetSize = (15f + (genre.percentage * 40f)).coerceIn(15f, 45f)
            
            val planetColor = try {
                Color(AndroidColor.parseColor(genre.colorHex))
            } catch (e: Exception) {
                CapsuleColors.genreColors[index % CapsuleColors.genreColors.size]
            }

            drawCircle(
                color = planetColor.copy(alpha = 0.3f),
                radius = planetSize * 2f,
                center = Offset(planetX, planetY)
            )
            drawCircle(
                color = planetColor.copy(alpha = 0.5f),
                radius = planetSize * 1.4f,
                center = Offset(planetX, planetY)
            )
            
            drawCircle(
                color = planetColor,
                radius = planetSize,
                center = Offset(planetX, planetY)
            )
        }

        val sunSize = 40f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    CapsuleColors.SunOrange,
                    CapsuleColors.SunRed
                ),
                center = Offset(centerX, centerY),
                radius = sunSize
            ),
            radius = sunSize,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = CapsuleColors.SunOrange.copy(alpha = 0.4f),
            radius = sunSize * 1.5f,
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
private fun GenreLegendItem(genre: GenreData) {
    val color = try {
        Color(AndroidColor.parseColor(genre.colorHex))
    } catch (e: Exception) {
        CapsuleColors.NebulaPurple
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CapsuleColors.GlassSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = genre.name,
            style = MaterialTheme.typography.labelMedium,
            color = CapsuleColors.TextPrimary
        )
        Text(
            text = "${(genre.percentage * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = CapsuleColors.TextMuted
        )
    }
}

// SLIDE 5: LISTENING PATTERNS
@Composable
private fun ListeningPatternsSlide(
    state: TimeCapsuleState,
    isVisible: Boolean
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            animationStarted = true
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(600),
        label = "contentAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "When You Listen",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CapsuleColors.TextPrimary
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "By Hour",
            style = MaterialTheme.typography.labelMedium,
            color = CapsuleColors.TextMuted,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(8.dp))

        HourlyChart(
            data = state.listeningByHour,
            isAnimated = animationStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        )

        Spacer(Modifier.height(32.dp))

        Text(
            text = "By Day",
            style = MaterialTheme.typography.labelMedium,
            color = CapsuleColors.TextMuted,
            modifier = Modifier.align(Alignment.Start)
        )

        Spacer(Modifier.height(8.dp))

        WeeklyChart(
            data = state.listeningByDay,
            isAnimated = animationStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        )

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ScoreGauge(
                label = "Energy",
                score = state.moodScore,
                color = CapsuleColors.SunOrange
            )
            ScoreGauge(
                label = "Discovery",
                score = state.discoveryScore,
                color = CapsuleColors.NebulaBlue
            )
        }
    }
}

@Composable
private fun HourlyChart(
    data: List<Int>,
    isAnimated: Boolean,
    modifier: Modifier = Modifier
) {
    val maxValue = data.maxOrNull()?.toFloat() ?: 1f

    val animatedProgress by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "chartProgress"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width / data.size
        val maxHeight = size.height

        data.forEachIndexed { index, value ->
            val height = (value / maxValue) * maxHeight * animatedProgress
            val x = index * barWidth

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CapsuleColors.NebulaPurple,
                        CapsuleColors.NebulaBlue.copy(alpha = 0.5f)
                    ),
                    startY = size.height - height,
                    endY = size.height
                ),
                topLeft = Offset(x + 2, size.height - height),
                size = Size(barWidth - 4, height)
            )
        }
    }
}

@Composable
private fun WeeklyChart(
    data: List<Int>,
    isAnimated: Boolean,
    modifier: Modifier = Modifier
) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val maxValue = data.maxOrNull()?.toFloat() ?: 1f

    val animatedProgress by animateFloatAsState(
        targetValue = if (isAnimated) 1f else 0f,
        animationSpec = tween(1000, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "weeklyProgress"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        data.forEachIndexed { index, value ->
            val height = (value / maxValue) * animatedProgress

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight(height.coerceAtLeast(0.05f))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    CapsuleColors.genreColors[index % CapsuleColors.genreColors.size],
                                    CapsuleColors.genreColors[index % CapsuleColors.genreColors.size].copy(alpha = 0.5f)
                                )
                            )
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = days[index],
                    style = MaterialTheme.typography.labelSmall,
                    color = CapsuleColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun ScoreGauge(
    label: String,
    score: Float,
    color: Color
) {
    val animatedScore by animateFloatAsState(
        targetValue = score,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "scoreAnim"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = CapsuleColors.GlassSurface,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
                
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedScore,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Text(
                text = "${(animatedScore * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = CapsuleColors.TextPrimary
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CapsuleColors.TextSecondary
        )
    }
}

// SLIDE 6: SUMMARY
@Composable
private fun SummarySlide(
    state: TimeCapsuleState,
    isVisible: Boolean
) {
    var animationStarted by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            animationStarted = true
        }
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (animationStarted) 1f else 0f,
        animationSpec = tween(600),
        label = "contentAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "That's Your",
            style = MaterialTheme.typography.titleLarge,
            color = CapsuleColors.TextSecondary
        )

        Text(
            text = "${state.yearLabel} Wrapped",
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Black
            ),
            color = CapsuleColors.TextPrimary
        )

        Spacer(Modifier.height(48.dp))

        Text(
            text = "Your Top Artists",
            style = MaterialTheme.typography.labelMedium,
            color = CapsuleColors.TextMuted
        )

        Spacer(Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.topArtists.take(5)) { artist ->
                TopArtistBubble(artist = artist)
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = { /* Share action */ },
            colors = ButtonDefaults.buttonColors(
                containerColor = CapsuleColors.NebulaPurple
            ),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Share Your Wrapped",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun TopArtistBubble(artist: CapsuleArtistData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CapsuleColors.GlassSurface),
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
                Text(
                    text = artist.name.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    color = CapsuleColors.TextPrimary
                )
            }
        }
        
        Spacer(Modifier.height(6.dp))
        
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelSmall,
            color = CapsuleColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatPill(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CapsuleColors.GlassSurface)
            .border(1.dp, CapsuleColors.GlassBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CapsuleColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = CapsuleColors.TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CapsuleColors.TextMuted
        )
    }
}

private fun formatNumber(number: Int): String {
    return when {
        number >= 1000 -> String.format("%,d", number)
        else -> number.toString()
    }
}
