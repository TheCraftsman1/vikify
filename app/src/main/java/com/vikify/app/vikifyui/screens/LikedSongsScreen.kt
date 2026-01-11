/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Liked Songs Screen - Premium Glass Edition
 * 
 * Features:
 * - Glassmorphism design language
 * - Pulsing heart animations
 * - Sort & filter options
 * - Multi-select with bulk unlike
 * - Swipe to unlike with undo
 * - Collapsing header
 */
package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.db.entities.Song
import com.vikify.app.vikifyui.components.MeshBackground
import com.vikify.app.vikifyui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
// THEME COLORS
// ═══════════════════════════════════════════════════════════════════════════════

private object LikedColors {
    val Primary = Color(0xFFFF6B9D)      // Pink
    val Secondary = Color(0xFFED5564)    // Red
    val Accent = Color(0xFFFF8FAB)       // Light pink
    val Gradient1 = Color(0xFFFF6B9D)
    val Gradient2 = Color(0xFFED5564)
    val Gradient3 = Color(0xFF8B5CF6)    // Purple accent
    val Unlike = Color(0xFF6B7280)       // Gray for unlike action
}

// ═══════════════════════════════════════════════════════════════════════════════
// SORT OPTIONS
// ═══════════════════════════════════════════════════════════════════════════════

enum class LikedSortOption(val displayName: String, val icon: ImageVector) {
    RECENTLY_LIKED("Recently Liked", Icons.Rounded.Schedule),
    TITLE("Title A-Z", Icons.Rounded.SortByAlpha),
    ARTIST("Artist", Icons.Rounded.Person),
    MOST_PLAYED("Most Played", Icons.Rounded.TrendingUp),
    RECENTLY_PLAYED("Recently Played", Icons.Rounded.History)
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api:: class, ExperimentalFoundationApi::class)
@Composable
fun LikedSongsScreen(
    likedSongs: List<Song>,
    likedCount: Int,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onShufflePlay: () -> Unit,
    onPlayAll: () -> Unit = {},
    onUnlikeSong: (Song) -> Unit = {},
    onUnlikeMultiple: (List<Song>) -> Unit = {},
    currentSongId: String? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDark = VikifyTheme.isDark

    // ─────────────────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────────────────

    var sortOption by remember { mutableStateOf(LikedSortOption.RECENTLY_LIKED) }
    var showSortSheet by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var unlikedSong by remember { mutableStateOf<Song? >(null) }
    var showUndoSnackbar by remember { mutableStateOf(false) }

    // Sorted songs
    val sortedSongs = remember(likedSongs, sortOption) {
        when (sortOption) {
            LikedSortOption.RECENTLY_LIKED -> likedSongs
            LikedSortOption.TITLE -> likedSongs.sortedBy { it.song.title.lowercase() }
            LikedSortOption.ARTIST -> likedSongs.sortedBy { 
                it.artists.firstOrNull()?.name?. lowercase() ?: "" 
            }
            LikedSortOption.MOST_PLAYED -> likedSongs.sortedByDescending { 
                it.playCount?.sumOf { pc -> pc.count } ?: 0 
            }
            LikedSortOption.RECENTLY_PLAYED -> likedSongs // Would need last played timestamp
        }
    }

    // Header collapse progress
    val headerProgress by remember {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            val index = listState.firstVisibleItemIndex
            if (index > 0) 1f else (offset / 300f).coerceIn(0f, 1f)
        }
    }

    // Exit multi-select when empty
    LaunchedEffect(selectedSongs) {
        if (selectedSongs.isEmpty() && isMultiSelectMode) {
            isMultiSelectMode = false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CALLBACKS
    // ─────────────────────────────────────────────────────────────────────────

    val onToggleSelect:  (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedSongs = if (selectedSongs.contains(song.song.id)) {
            selectedSongs - song.song.id
        } else {
            selectedSongs + song.song.id
        }
    }

    val onEnterMultiSelect:  (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isMultiSelectMode = true
        selectedSongs = setOf(song.song.id)
    }

    val onSelectAll: () -> Unit = {
        selectedSongs = sortedSongs.map { it.song.id }. toSet()
    }

    val onClearSelection: () -> Unit = {
        selectedSongs = emptySet()
        isMultiSelectMode = false
    }

    val onUnlikeSelected: () -> Unit = {
        val toUnlike = sortedSongs.filter { selectedSongs.contains(it.song.id) }
        onUnlikeMultiple(toUnlike)
        onClearSelection()
    }

    val onSwipeUnlike: (Song) -> Unit = { song ->
        unlikedSong = song
        showUndoSnackbar = true
        scope.launch {
            delay(3000)
            if (showUndoSnackbar && unlikedSong == song) {
                onUnlikeSong(song)
                showUndoSnackbar = false
                unlikedSong = null
            }
        }
    }

    val onUndoUnlike: () -> Unit = {
        showUndoSnackbar = false
        unlikedSong = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    Box(modifier = modifier.fillMaxSize()) {
        // Background
        if (isDark) {
            MeshBackground(
                modifier = Modifier.matchParentSize(),
                accentColor = LikedColors.Primary
            )
        } else {
            EtherealBackground(modifier = Modifier.matchParentSize()) {}
        }

        // Floating artwork background
        if (sortedSongs.isNotEmpty()) {
            LikedSongsBackground(
                artworkUrls = sortedSongs.take(4).mapNotNull { it.song.thumbnailUrl },
                scrollProgress = headerProgress
            )
        }

        // Content
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // Top bar
            item(key = "topbar") {
                LikedTopBar(
                    isMultiSelectMode = isMultiSelectMode,
                    selectedCount = selectedSongs.size,
                    headerProgress = headerProgress,
                    onBackClick = {
                        if (isMultiSelectMode) {
                            onClearSelection()
                        } else {
                            onBackClick()
                        }
                    },
                    onSelectAll = onSelectAll,
                    onUnlikeSelected = onUnlikeSelected
                )
            }

            // Hero card
            item(key = "hero") {
                AnimatedVisibility(
                    visible = ! isMultiSelectMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LikedHeroCard(
                        songCount = likedCount,
                        onShufflePlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShufflePlay()
                        },
                        onPlayAll = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayAll()
                        },
                        modifier = Modifier.graphicsLayer {
                            alpha = 1f - headerProgress
                            translationY = -headerProgress * 100f
                        }
                    )
                }
            }

            // Sort row
            item(key = "sort") {
                AnimatedVisibility(
                    visible = sortedSongs.isNotEmpty() && !isMultiSelectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LikedSortRow(
                        currentSort = sortOption,
                        songCount = likedCount,
                        onSortClick = { showSortSheet = true }
                    )
                }
            }

            // Songs list
            if (sortedSongs.isEmpty()) {
                item(key = "empty") {
                    LikedEmptyState()
                }
            } else {
                itemsIndexed(
                    items = sortedSongs.filterNot { it == unlikedSong },
                    key = { _, song -> song.song.id }
                ) { index, song ->
                    val isSelected = selectedSongs.contains(song.song.id)
                    val isPlaying = song.song.id == currentSongId

                    SwipeableLikedRow(
                        song = song,
                        index = index + 1,
                        isPlaying = isPlaying,
                        isSelected = isSelected,
                        isMultiSelectMode = isMultiSelectMode,
                        onSwipeUnlike = { onSwipeUnlike(song) },
                        onClick = {
                            if (isMultiSelectMode) {
                                onToggleSelect(song)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSongClick(song)
                            }
                        },
                        onLongClick = {
                            if (! isMultiSelectMode) {
                                onEnterMultiSelect(song)
                            }
                        }
                    )
                }
            }
        }

        // Collapsed header
        CollapsedLikedHeader(
            visible = headerProgress > 0.9f && !isMultiSelectMode,
            songCount = likedCount,
            onBackClick = onBackClick
        )

        // Undo snackbar
        AnimatedVisibility(
            visible = showUndoSnackbar,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            LikedUndoSnackbar(
                songTitle = unlikedSong?.song?.title ?: "",
                onUndo = onUndoUnlike,
                onDismiss = {
                    unlikedSong?. let { onUnlikeSong(it) }
                    showUndoSnackbar = false
                    unlikedSong = null
                }
            )
        }
    }

    // Sort bottom sheet
    if (showSortSheet) {
        LikedSortBottomSheet(
            currentSort = sortOption,
            onSortSelected = {
                sortOption = it
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LikedTopBar(
    isMultiSelectMode:  Boolean,
    selectedCount: Int,
    headerProgress: Float,
    onBackClick: () -> Unit,
    onSelectAll: () -> Unit,
    onUnlikeSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            . fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back/Close button
        GlassIconButton(
            icon = if (isMultiSelectMode) Icons.Rounded.Close else Icons.Outlined.ArrowBack,
            contentDescription = if (isMultiSelectMode) "Cancel" else "Back",
            onClick = onBackClick
        )

        // Selection count
        AnimatedVisibility(
            visible = isMultiSelectMode,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VikifyTheme.colors.textPrimary
            )
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = isMultiSelectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(
                        icon = Icons.Rounded.SelectAll,
                        contentDescription = "Select All",
                        onClick = onSelectAll
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.HeartBroken,
                        contentDescription = "Unlike",
                        onClick = onUnlikeSelected,
                        tint = LikedColors.Unlike
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedLikedHeader(
    visible: Boolean,
    songCount: Int,
    onBackClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically { -it },
        exit = fadeOut(tween(200)) + slideOutVertically { -it }
    ) {
        Box(
            modifier = Modifier
                . fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VikifyTheme.colors.background,
                            VikifyTheme.colors.background.copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassIconButton(
                    icon = Icons.Outlined.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBackClick,
                    size = 36.dp
                )

                // Heart icon
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = LikedColors.Primary,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VikifyTheme.colors.textPrimary
                )

                Spacer(Modifier.weight(1f))

                // Count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(LikedColors.Primary.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$songCount",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LikedColors.Primary
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HERO CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LikedHeroCard(
    songCount: Int,
    onShufflePlay: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    
    // Pulsing heart animation
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    // Glow animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(LikedGlass.surface())
                .border(1.dp, LikedGlass.border(), RoundedCornerShape(28.dp))
                .drawWithContent {
                    drawContent()
                    // Top shine
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = size.height * 0.3f
                        )
                    )
                }
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated heart with glow
            Box(contentAlignment = Alignment.Center) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        . size(110.dp)
                        .scale(heartScale * 1.2f)
                        .blur(35.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LikedColors.Primary.copy(alpha = glowAlpha),
                                    LikedColors.Secondary.copy(alpha = glowAlpha * 0.5f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                // Inner glow
                Box(
                    modifier = Modifier
                        .size(95.dp)
                        .scale(heartScale)
                        .blur(20.dp)
                        .background(LikedColors.Primary.copy(alpha = 0.4f), CircleShape)
                )

                // Heart container
                Box(
                    modifier = Modifier
                        . size(80.dp)
                        .scale(heartScale)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    LikedColors.Gradient1,
                                    LikedColors.Gradient2
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Title with gradient
            Text(
                text = "Liked Songs",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            // Subtitle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = LikedColors.Primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$songCount songs you love",
                    fontSize = 15.sp,
                    color = VikifyTheme.colors.textSecondary
                )
            }

            if (songCount > 0) {
                Spacer(Modifier.height(28.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play All (Outlined)
                    OutlinedButton(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(LikedColors.Primary, LikedColors.Secondary)
                            )
                        ),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = LikedColors.Primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Play All",
                            fontWeight = FontWeight.SemiBold,
                            color = LikedColors.Primary
                        )
                    }

                    // Shuffle (Gradient filled)
                    Button(
                        onClick = onShufflePlay,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(LikedColors.Gradient1, LikedColors.Gradient2)
                                    ),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 28.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Shuffle",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════��═══════════════
// SORT ROW & SHEET
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LikedSortRow(
    currentSort: LikedSortOption,
    songCount: Int,
    onSortClick:  () -> Unit
) {
    Row(
        modifier = Modifier
            . fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Loved Songs",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
            
            // Heart count
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(LikedColors.Primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = LikedColors.Primary,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "$songCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LikedColors.Primary
                )
            }
        }

        // Sort button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(LikedGlass.surface())
                .border(1.dp, LikedGlass.border(), RoundedCornerShape(20.dp))
                .clickable(onClick = onSortClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = currentSort.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = VikifyTheme.colors.textSecondary
            )
            Text(
                text = currentSort.displayName,
                fontSize = 13.sp,
                color = VikifyTheme.colors.textSecondary
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = VikifyTheme.colors.textSecondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LikedSortBottomSheet(
    currentSort:  LikedSortOption,
    onSortSelected: (LikedSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VikifyTheme.colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VikifyTheme.colors.textSecondary.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                . fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            LikedSortOption.entries.forEach { option ->
                val isSelected = option == currentSort

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        . clickable { onSortSelected(option) }
                        .background(
                            if (isSelected) LikedColors.Primary.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = if (isSelected) LikedColors.Primary
                               else VikifyTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) LikedColors.Primary
                                else VikifyTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = LikedColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SONG ROW WITH SWIPE
// ═══��═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableLikedRow(
    song:  Song,
    index: Int,
    isPlaying: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onSwipeUnlike: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> LikedColors.Primary.copy(alpha = 0.15f)
            isPlaying -> LikedColors.Primary.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        label = "rowBg"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // Unlike background (revealed on swipe)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(LikedColors.Unlike.copy(alpha = 0.2f))
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.HeartBroken,
                    contentDescription = "Unlike",
                    tint = LikedColors.Unlike,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Unlike",
                    fontSize = 11.sp,
                    color = LikedColors.Unlike
                )
            }
        }

        // Main content
        Row(
            modifier = Modifier
                . fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.toInt(), 0) }
                . background(VikifyTheme.colors.background)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -swipeThreshold) {
                                onSwipeUnlike()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (! isMultiSelectMode) {
                                offsetX = (offsetX + dragAmount).coerceIn(-200f, 0f)
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Multi-select checkbox
            AnimatedVisibility(
                visible = isMultiSelectMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = LikedColors.Primary,
                        uncheckedColor = VikifyTheme.colors.textSecondary
                    ),
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            // Index
            AnimatedVisibility(
                visible = ! isMultiSelectMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlaying) LikedColors.Primary else VikifyTheme.colors.textSecondary,
                    modifier = Modifier.width(28.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Artwork
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(LikedGlass.surface())
                    .border(1.dp, LikedGlass.border(), RoundedCornerShape(10.dp))
            ) {
                AsyncImage(
                    model = song.song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Playing indicator
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        LikedPlayingEqualizer()
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.song.title,
                    fontSize = 15.sp,
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isPlaying) LikedColors.Primary else VikifyTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artists.joinToString { it.name },
                    fontSize = 13.sp,
                    color = VikifyTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Heart badge (always visible)
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Liked",
                tint = LikedColors.Primary,
                modifier = Modifier.size(18.dp)
            )

            // More options (hidden in multi-select)
            AnimatedVisibility(
                visible = ! isMultiSelectMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(
                    onClick = { /* Context menu */ },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = VikifyTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LikedPlayingEqualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")

    val heights = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + i * 100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(16.dp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(height.value)
                    . clip(RoundedCornerShape(1.5.dp))
                    .background(LikedColors.Primary)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LikedSongsBackground(
    artworkUrls: List<String>,
    scrollProgress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")

    Box(modifier = Modifier.fillMaxSize()) {
        // Floating artwork
        artworkUrls.forEachIndexed { index, url ->
            if (url.isNotEmpty()) {
                val offsetY by infiniteTransition.animateFloat(
                    initialValue = -12f,
                    targetValue = 12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500 + index * 500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "float$index"
                )

                val positions = listOf(
                    androidx.compose.ui.geometry.Offset(0.05f, 0.06f),
                    androidx.compose.ui.geometry.Offset(0.7f, 0.1f),
                    androidx.compose.ui.geometry.Offset(0.15f, 0.2f),
                    androidx.compose.ui.geometry.Offset(0.8f, 0.26f)
                )
                val pos = positions.getOrNull(index) ?: androidx.compose.ui.geometry.Offset(0.5f, 0.2f)

                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(
                            x = (pos.x * 320).dp,
                            y = (pos.y * 380 + offsetY).dp
                        )
                        .size((65 + index * 12).dp)
                        .graphicsLayer {
                            alpha = (1f - scrollProgress) * 0.5f
                        }
                        .clip(RoundedCornerShape(14.dp))
                        .blur(22.dp)
                )
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LikedColors.Primary.copy(alpha = 0.06f),
                            VikifyTheme.colors.background.copy(alpha = 0.7f),
                            VikifyTheme.colors.background
                        )
                    )
                )
        )

        // Top pink glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .graphicsLayer { alpha = 1f - scrollProgress }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LikedColors.Gradient2.copy(alpha = 0.15f),
                            LikedColors.Gradient1.copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LikedEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated heart
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(heartScale),
            contentAlignment = Alignment.Center
        ) {
            // Glow
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .blur(30.dp)
                    .background(LikedColors.Primary.copy(alpha = 0.2f), CircleShape)
            )
            
            // Container
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(LikedGlass.surface())
                    .border(1.dp, LikedGlass.border(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = LikedColors.Primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "No liked songs yet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Tap the ❤️ on songs you love\nto see them here",
            fontSize = 15.sp,
            color = VikifyTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        // Explore button
        Button(
            onClick = { /* Navigate to browse */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues()
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(LikedColors.Gradient1, LikedColors.Gradient2)
                        ),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Discover Music",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UNDO SNACKBAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LikedUndoSnackbar(
    songTitle: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            . padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VikifyTheme.colors.surfaceElevated)
            .border(1.dp, LikedGlass.border(), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.HeartBroken,
                contentDescription = null,
                tint = LikedColors.Unlike,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = "Removed from Liked Songs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VikifyTheme.colors.textPrimary
                )
                Text(
                    text = songTitle,
                    fontSize = 12.sp,
                    color = VikifyTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        TextButton(onClick = onUndo) {
            Text(
                text = "UNDO",
                fontWeight = FontWeight.Bold,
                color = LikedColors.Primary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size:  Dp = 44.dp,
    tint: Color = VikifyTheme.colors.textPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(LikedGlass.surface())
            .border(1.dp, LikedGlass.border(), CircleShape)
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
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

// Glass theme helper
private object LikedGlass {
    @Composable
    fun surface(): Color = if (VikifyTheme.isDark)
        Color.White.copy(alpha = 0.06f)
    else
        Color.Black.copy(alpha = 0.04f)

    @Composable
    fun border(): Color = if (VikifyTheme.isDark)
        Color.White.copy(alpha = 0.12f)
    else
        Color.Black.copy(alpha = 0.08f)
}