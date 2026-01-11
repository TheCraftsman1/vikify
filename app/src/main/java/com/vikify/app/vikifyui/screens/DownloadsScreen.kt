/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Downloads Screen - Premium Glass Edition
 * 
 * Features:
 * - Glassmorphism design language
 * - Swipe-to-delete with undo
 * - Sort & filter options
 * - Multi-select mode
 * - Storage stats
 * - Animated transitions
 */
package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.db.entities.Song
import com.vikify.app.vikifyui.components.MeshBackground
import com.vikify.app.vikifyui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.roundToInt
import kotlin.math.pow

// ═══════════════════════════════════════════════════════════════════════════════
// THEME COLORS
// ═══════════════════════════════════════════════════════════════════════════════

private object DownloadColors {
    val Primary = Color(0xFF10B981)      // Emerald green
    val Secondary = Color(0xFF06B6D4)    // Cyan
    val Accent = Color(0xFF34D399)       // Light green
    val Error = Color(0xFFEF4444)        // Red for delete
    val Warning = Color(0xFFF59E0B)      // Amber
    
    val GradientStart = Color(0xFF10B981)
    val GradientEnd = Color(0xFF059669)
}

// ═══════════════════════════════════════════════════════════════════════════════
// SORT & FILTER
// ═══════════════════════════════════════════════════════════════════════════════

enum class DownloadSortOption(val displayName: String, val icon: ImageVector) {
    RECENT("Recently Added", Icons.Rounded.Schedule),
    TITLE("Title A-Z", Icons.Rounded.SortByAlpha),
    ARTIST("Artist", Icons.Rounded.Person),
    SIZE("File Size", Icons.Rounded.Storage),
    MOST_PLAYED("Most Played", Icons.Rounded.TrendingUp)
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    downloadCount: Int,
    totalSizeBytes: Long = 0L,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit = {},
    onBackClick: () -> Unit,
    onShufflePlay: () -> Unit,
    onPlayAll: () -> Unit = {},
    onDeleteSong: (Song) -> Unit = {},
    onDeleteMultiple: (List<Song>) -> Unit = {},
    currentSongId: String? = null,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDark = VikifyTheme.isDark

    // ─────────────────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────────────────

    var sortOption by remember { mutableStateOf(DownloadSortOption.RECENT) }
    var showSortSheet by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }
    var deletedSong by remember { mutableStateOf<Song?>(null) }
    var showUndoSnackbar by remember { mutableStateOf(false) }

    // Sorted songs
    val sortedSongs = remember(downloadedSongs, sortOption) {
        when (sortOption) {
            DownloadSortOption.RECENT -> downloadedSongs // Assuming already sorted by date
            DownloadSortOption.TITLE -> downloadedSongs.sortedBy { it.song.title.lowercase() }
            DownloadSortOption.ARTIST -> downloadedSongs.sortedBy { 
                it.artists.firstOrNull()?.name?.lowercase() ?: "" 
            }
            DownloadSortOption.SIZE -> downloadedSongs // Would need file size data
            DownloadSortOption.MOST_PLAYED -> downloadedSongs.sortedByDescending { 
                it.playCount?.sumOf { pc -> pc.count } ?: 0
            }
        }
    }

    // Header collapse
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

    val onToggleSelect: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        selectedSongs = if (selectedSongs.contains(song.song.id)) {
            selectedSongs - song.song.id
        } else {
            selectedSongs + song.song.id
        }
    }

    val onEnterMultiSelect: (Song) -> Unit = { song ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        isMultiSelectMode = true
        selectedSongs = setOf(song.song.id)
    }

    val onSelectAll: () -> Unit = {
        selectedSongs = sortedSongs.map { it.song.id }.toSet()
    }

    val onClearSelection: () -> Unit = {
        selectedSongs = emptySet()
        isMultiSelectMode = false
    }

    val onDeleteSelected: () -> Unit = {
        val toDelete = sortedSongs.filter { selectedSongs.contains(it.song.id) }
        onDeleteMultiple(toDelete)
        onClearSelection()
    }

    val onSwipeDelete: (Song) -> Unit = { song ->
        deletedSong = song
        showUndoSnackbar = true
        scope.launch {
            delay(3000)
            if (showUndoSnackbar && deletedSong == song) {
                onDeleteSong(song)
                showUndoSnackbar = false
                deletedSong = null
            }
        }
    }

    val onUndoDelete: () -> Unit = {
        showUndoSnackbar = false
        deletedSong = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI
    // ─────────────────────────────────────────────────────────────────────────

    Box(modifier = modifier.fillMaxSize()) {
        // Background
        if (isDark) {
            MeshBackground(
                modifier = Modifier.matchParentSize(),
                accentColor = DownloadColors.Primary
            )
        } else {
            EtherealBackground(modifier = Modifier.matchParentSize()) {}
        }

        // Floating artwork background
        if (sortedSongs.isNotEmpty()) {
            FloatingArtBackground(
                artworkUrls = sortedSongs.take(4).mapNotNull { it.song.thumbnailUrl },
                accentColor = DownloadColors.Primary,
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
                DownloadsTopBar(
                    isMultiSelectMode = isMultiSelectMode,
                    selectedCount = selectedSongs.size,
                    totalCount = sortedSongs.size,
                    headerProgress = headerProgress,
                    onBackClick = {
                        if (isMultiSelectMode) {
                            onClearSelection()
                        } else {
                            onBackClick()
                        }
                    },
                    onSelectAll = onSelectAll,
                    onDeleteSelected = onDeleteSelected
                )
            }

            // Hero card
            item(key = "hero") {
                AnimatedVisibility(
                    visible = !isMultiSelectMode,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    DownloadsHeroCard(
                        songCount = downloadCount,
                        totalSize = totalSizeBytes,
                        accentColor = DownloadColors.Primary,
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

            // Sort & filter row
            item(key = "sort") {
                AnimatedVisibility(
                    visible = sortedSongs.isNotEmpty() && !isMultiSelectMode,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    SortFilterRow(
                        currentSort = sortOption,
                        onSortClick = { showSortSheet = true }
                    )
                }
            }

            // Songs list
            if (sortedSongs.isEmpty()) {
                item(key = "empty") {
                    DownloadsEmptyState()
                }
            } else {
                itemsIndexed(
                    items = sortedSongs.filterNot { it == deletedSong },
                    key = { _, song -> song.song.id }
                ) { index, song ->
                    val isSelected = selectedSongs.contains(song.song.id)
                    val isPlaying = song.song.id == currentSongId

                    SwipeableDownloadRow(
                        song = song,
                        index = index + 1,
                        isPlaying = isPlaying,
                        isSelected = isSelected,
                        isMultiSelectMode = isMultiSelectMode,
                        accentColor = DownloadColors.Primary,
                        onSwipeDelete = { onSwipeDelete(song) },
                        onClick = {
                            if (isMultiSelectMode) {
                                onToggleSelect(song)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSongClick(song)
                            }
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) {
                                onEnterMultiSelect(song)
                            }
                        }
                    )
                }
            }
        }

        // Collapsed header
        CollapsedDownloadsHeader(
            visible = headerProgress > 0.9f && !isMultiSelectMode,
            title = "Downloads",
            songCount = downloadCount,
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
            UndoSnackbar(
                songTitle = deletedSong?.song?.title ?: "",
                onUndo = onUndoDelete,
                onDismiss = {
                    deletedSong?.let { onDeleteSong(it) }
                    showUndoSnackbar = false
                    deletedSong = null
                }
            )
        }
    }

    // Sort bottom sheet
    if (showSortSheet) {
        SortBottomSheet(
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
private fun DownloadsTopBar(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    headerProgress: Float,
    onBackClick: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        GlassIconButton(
            icon = if (isMultiSelectMode) Icons.Rounded.Close else Icons.Outlined.ArrowBack,
            contentDescription = if (isMultiSelectMode) "Cancel" else "Back",
            onClick = onBackClick
        )

        // Multi-select info
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
                    // Select all
                    GlassIconButton(
                        icon = Icons.Rounded.SelectAll,
                        contentDescription = "Select All",
                        onClick = onSelectAll
                    )
                    
                    // Delete selected
                    GlassIconButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = "Delete",
                        onClick = onDeleteSelected,
                        tint = DownloadColors.Error
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedDownloadsHeader(
    visible: Boolean,
    title: String,
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
                .fillMaxWidth()
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

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VikifyTheme.colors.textPrimary
                )

                Spacer(Modifier.weight(1f))

                // Count badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DownloadColors.Primary.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$songCount",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DownloadColors.Primary
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
private fun DownloadsHeroCard(
    songCount: Int,
    totalSize: Long,
    accentColor: Color,
    onShufflePlay: () -> Unit,
    onPlayAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
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
                .background(DownloadGlass.surface())
                .border(1.dp, DownloadGlass.border(), RoundedCornerShape(28.dp))
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
            // Icon with glow
            Box(contentAlignment = Alignment.Center) {
                // Glow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(iconScale * 1.3f)
                        .blur(30.dp)
                        .background(accentColor.copy(alpha = 0.4f), CircleShape)
                )
                
                // Icon container
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(iconScale)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.7f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DownloadDone,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Title
            Text(
                text = "Downloads",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )

            Spacer(Modifier.height(8.dp))

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(
                    icon = Icons.Rounded.MusicNote,
                    text = "$songCount songs",
                    color = accentColor
                )
                
                if (totalSize > 0) {
                    StatChip(
                        icon = Icons.Rounded.Storage,
                        text = formatFileSize(totalSize),
                        color = DownloadColors.Secondary
                    )
                }
            }

            if (songCount > 0) {
                Spacer(Modifier.height(28.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Play All
                    OutlinedButton(
                        onClick = onPlayAll,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = accentColor
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Play All",
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }

                    // Shuffle
                    Button(
                        onClick = onShufflePlay,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 8.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Shuffle",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SORT & FILTER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SortFilterRow(
    currentSort: DownloadSortOption,
    onSortClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "All Songs",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary
        )

        // Sort button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(DownloadGlass.surface())
                .border(1.dp, DownloadGlass.border(), RoundedCornerShape(20.dp))
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
private fun SortBottomSheet(
    currentSort: DownloadSortOption,
    onSortSelected: (DownloadSortOption) -> Unit,
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
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            DownloadSortOption.entries.forEach { option ->
                val isSelected = option == currentSort
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected(option) }
                        .background(
                            if (isSelected) DownloadColors.Primary.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        tint = if (isSelected) DownloadColors.Primary 
                               else VikifyTheme.colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    
                    Text(
                        text = option.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) DownloadColors.Primary 
                                else VikifyTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = DownloadColors.Primary,
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
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableDownloadRow(
    song: Song,
    index: Int,
    isPlaying: Boolean,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    accentColor: Color,
    onSwipeDelete: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> accentColor.copy(alpha = 0.15f)
            isPlaying -> accentColor.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        label = "rowBg"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        // Delete background (revealed on swipe)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(DownloadColors.Error.copy(alpha = 0.2f))
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = DownloadColors.Error,
                modifier = Modifier.size(28.dp)
            )
        }

        // Main content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(VikifyTheme.colors.background)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    /* TODO: Fix gestures
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -swipeThreshold) {
                                onSwipeDelete()
                            }
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!isMultiSelectMode) {
                                offsetX = (offsetX + dragAmount).coerceIn(-200f, 0f)
                            }
                        }
                    )
                    */
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
                        checkedColor = accentColor,
                        uncheckedColor = VikifyTheme.colors.textSecondary
                    ),
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            // Index
            AnimatedVisibility(
                visible = !isMultiSelectMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isPlaying) accentColor else VikifyTheme.colors.textSecondary,
                    modifier = Modifier.width(28.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Artwork
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DownloadGlass.surface())
                    .border(1.dp, DownloadGlass.border(), RoundedCornerShape(10.dp))
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
                        PlayingEqualizer(color = accentColor)
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
                    color = if (isPlaying) accentColor else VikifyTheme.colors.textPrimary,
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

            // Downloaded badge
            Icon(
                imageVector = Icons.Rounded.OfflinePin,
                contentDescription = "Downloaded",
                tint = DownloadColors.Primary,
                modifier = Modifier.size(18.dp)
            )

            // More options (hidden in multi-select)
            AnimatedVisibility(
                visible = !isMultiSelectMode,
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
private fun PlayingEqualizer(color: Color) {
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
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FloatingArtBackground(
    artworkUrls: List<String>,
    accentColor: Color,
    scrollProgress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")

    Box(modifier = Modifier.fillMaxSize()) {
        // Floating artwork
        artworkUrls.forEachIndexed { index, url ->
            if (url.isNotEmpty()) {
                val offsetY by infiniteTransition.animateFloat(
                    initialValue = -15f,
                    targetValue = 15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(3000 + index * 700, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "float$index"
                )

                val positions = listOf(
                    Offset(0.1f, 0.08f),
                    Offset(0.65f, 0.12f),
                    Offset(0.25f, 0.22f),
                    Offset(0.75f, 0.28f)
                )
                val pos = positions.getOrNull(index) ?: Offset(0.5f, 0.2f)

                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(
                            x = (pos.x * 300).dp,
                            y = (pos.y * 350 + offsetY).dp
                        )
                        .size((70 + index * 15).dp)
                        .graphicsLayer {
                            alpha = (1f - scrollProgress) * 0.6f
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .blur(25.dp)
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
                            VikifyTheme.colors.background.copy(alpha = 0.3f),
                            VikifyTheme.colors.background.copy(alpha = 0.7f),
                            VikifyTheme.colors.background
                        )
                    )
                )
        )

        // Accent glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .graphicsLayer { alpha = 1f - scrollProgress }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.12f),
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
private fun DownloadsEmptyState() {
    val infiniteTransition = rememberInfiniteTransition(label = "empty")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
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
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(DownloadGlass.surface())
                .border(1.dp, DownloadGlass.border(), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = VikifyTheme.colors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "No downloads yet",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Download songs to listen offline\nwithout using data",
            fontSize = 15.sp,
            color = VikifyTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { /* Navigate to browse */ },
            colors = ButtonDefaults.buttonColors(containerColor = DownloadColors.Primary),
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Explore Music",
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UNDO SNACKBAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UndoSnackbar(
    songTitle: String,
    onUndo: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VikifyTheme.colors.surfaceElevated)
            .border(1.dp, DownloadGlass.border(), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Removed from downloads",
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

        TextButton(onClick = onUndo) {
            Text(
                text = "UNDO",
                fontWeight = FontWeight.Bold,
                color = DownloadColors.Primary
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
    size: Dp = 44.dp,
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
            .background(DownloadGlass.surface())
            .border(1.dp, DownloadGlass.border(), CircleShape)
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
private object DownloadGlass {
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

// Format file size
private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val format = DecimalFormat("#,##0.#")
    return "${format.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}


