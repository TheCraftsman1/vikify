/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Library Screen - Your Music Collection
 */
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import coil3.compose.AsyncImagePainter
import com.vikify.app.R
import com.vikify.app.db.entities.Playlist
import com.vikify.app.db.entities.Song
import com.vikify.app.spotify.SpotifyPlaylist
import com.vikify.app.viewmodels.LibrarySongsViewModel
import com.vikify.app.vikifyui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════════════════════

private object LibraryColors {
    // Gradients for pinned cards
    val LikedGradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
    val DownloadGradient = listOf(Color(0xFF10B981), Color(0xFF06B6D4))
    val RecentGradient = listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
    
    // Tab colors
    val TabSelected = Color.White
    val TabUnselected = Color.White.copy(alpha = 0.6f)
    
    // Surface
    val GlassSurface = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
    
    // Accents
    val AccentPurple = Color(0xFF8B5CF6)
    val AccentGreen = Color(0xFF10B981)
    val AccentRed = Color(0xFFEF4444)
    val SpotifyGreen = Color(0xFF1DB954)
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════════════════════

enum class LibraryTab(val displayName: String, val icon: ImageVector) {
    Playlists("Playlists", Icons.Rounded.QueueMusic),
    Artists("Artists", Icons.Rounded.Person),
    Albums("Albums", Icons.Rounded.Album),
    Downloaded("Downloads", Icons.Rounded.CloudDownload)
}

enum class SortOption(val displayName: String) {
    RECENT("Recently Added"),
    ALPHABETICAL("A-Z"),
    CREATOR("Creator")
}

enum class ViewMode {
    LIST, GRID
}

data class LibraryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val selectedTab: LibraryTab = LibraryTab.Playlists,
    val sortOption: SortOption = SortOption.RECENT,
    val viewMode: ViewMode = ViewMode.LIST,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onTrackClick: (String) -> Unit,
    onPlaylistClick: (String, SpotifyPlaylist?) -> Unit,
    onLikedSongsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    playlists: List<SpotifyPlaylist> = emptyList(),
    downloadedSongs: List<Song> = emptyList(),
    onDownloadedSongClick: (Song) -> Unit = {},
    onLongPressItem: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: LibrarySongsViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isDark = VikifyTheme.isDark

    // ─────────────────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────────────────
    
    var uiState by remember { mutableStateOf(LibraryUiState()) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Dialog states
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var contextMenuPlaylistId by remember { mutableStateOf<String?>(null) }
    
    // Downloaded tab filter
    var downloadedFilter by remember { mutableIntStateOf(0) } // 0 = Songs, 1 = Albums
    
    // Data from ViewModel
    val playlistRepository = viewModel.playlistRepository
    val localPlaylists by playlistRepository.getLocalPlaylists().collectAsState(initial = emptyList())
    
    // Computed: downloaded albums
    val downloadedAlbums = remember(downloadedSongs) {
        downloadedSongs
            .groupBy { it.album }
            .mapNotNull { (album, songs) ->
                album?.let { Triple(it, songs.size, songs.firstOrNull()?.song?.thumbnailUrl) }
            }
    }
    
    // Header collapse progress
    val headerProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 200f).coerceIn(0f, 1f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIALOGS
    // ─────────────────────────────────────────────────────────────────────────
    
    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                scope.launch {
                    playlistRepository.createLocalPlaylist(name)
                }
                showCreatePlaylistDialog = false
            }
        )
    }
    
    // Rename Dialog
    playlistToRename?.let { playlist ->
        RenamePlaylistDialog(
            currentName = playlist.title,
            onDismiss = { playlistToRename = null },
            onRename = { newName ->
                scope.launch {
                    playlistRepository.renamePlaylist(playlist.id, newName)
                }
                playlistToRename = null
            }
        )
    }
    
    // Delete Confirmation Dialog
    playlistToDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.title,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                scope.launch {
                    playlistRepository.deletePlaylist(playlist.id)
                }
                playlistToDelete = null
            }
        )
    }
    
    // Sort Dialog
    if (showSortDialog) {
        SortOptionsDialog(
            currentSort = uiState.sortOption,
            onDismiss = { showSortDialog = false },
            onSelect = { option ->
                uiState = uiState.copy(sortOption = option)
                showSortDialog = false
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDER
    // ─────────────────────────────────────────────────────────────────────────
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VikifyTheme.colors.background)
    ) {
        // Background
        if (isDark) {
            LivingBackground(modifier = Modifier.matchParentSize()) {}
        } else {
            EtherealBackground(modifier = Modifier.matchParentSize()) {}
        }

        // Content
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    delay(500) // Simulate refresh
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 180.dp)
            ) {
                // Header
                item(key = "header") {
                    LibraryHeader(
                        headerProgress = headerProgress,
                        onSearchClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSearchClick()
                        },
                        onAddClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showCreatePlaylistDialog = true
                        },
                        onSettingsClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSettingsClick()
                        }
                    )
                }

                // Tab selector
                item(key = "tabs") {
                    LibraryTabSelector(
                        selectedTab = uiState.selectedTab,
                        onTabSelect = { tab ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            uiState = uiState.copy(selectedTab = tab)
                        }
                    )
                }

                // Pinned section (only on Playlists tab)
                if (uiState.selectedTab == LibraryTab.Playlists) {
                    item(key = "pinned") {
                        PinnedSection(
                            onLikedClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLikedSongsClick()
                            },
                            onDownloadsClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDownloadsClick()
                            }
                        )
                    }
                }

                // Section header with sort/view toggle
                item(key = "section_header") {
                    LibrarySectionHeader(
                        title = when (uiState.selectedTab) {
                            LibraryTab.Playlists -> "Your Playlists"
                            LibraryTab.Artists -> "Followed Artists"
                            LibraryTab.Albums -> "Saved Albums"
                            LibraryTab.Downloaded -> "Downloaded"
                        },
                        sortOption = uiState.sortOption,
                        viewMode = uiState.viewMode,
                        onSortClick = { showSortDialog = true },
                        onViewModeToggle = {
                            uiState = uiState.copy(
                                viewMode = if (uiState.viewMode == ViewMode.LIST) 
                                    ViewMode.GRID else ViewMode.LIST
                            )
                        }
                    )
                }

                // Content based on selected tab
                when (uiState.selectedTab) {
                    LibraryTab.Playlists -> {
                        // Local playlists
                        if (localPlaylists.isNotEmpty()) {
                            itemsIndexed(
                                items = localPlaylists,
                                key = { _, playlist -> "local_${playlist.id}" }
                            ) { index, playlist ->
                                PlaylistListItem(
                                    title = playlist.title,
                                    subtitle = "${playlist.songCount} songs • Local",
                                    imageUrl = null, // TODO: Collage
                                    isLocal = true,
                                    index = index,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlaylistClick(playlist.id, null)
                                    },
                                    onMenuClick = { contextMenuPlaylistId = playlist.id },
                                    contextMenuExpanded = contextMenuPlaylistId == playlist.id,
                                    onDismissMenu = { contextMenuPlaylistId = null },
                                    onRename = {
                                        playlistToRename = playlist
                                        contextMenuPlaylistId = null
                                    },
                                    onDelete = {
                                        playlistToDelete = playlist
                                        contextMenuPlaylistId = null
                                    }
                                )
                            }
                        }
                        
                        // Spotify playlists
                        if (playlists.isNotEmpty()) {
                            itemsIndexed(
                                items = playlists,
                                key = { _, playlist -> "spotify_${playlist.id}" }
                            ) { index, playlist ->
                                PlaylistListItem(
                                    title = playlist.name,
                                    subtitle = "${playlist.trackCount} songs • Spotify",
                                    imageUrl = playlist.imageUrl,
                                    isLocal = false,
                                    index = localPlaylists.size + index,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlaylistClick(playlist.id, playlist)
                                    }
                                )
                            }
                        }
                        
                        // Empty state
                        if (localPlaylists.isEmpty() && playlists.isEmpty()) {
                            item(key = "empty_playlists") {
                                LibraryEmptyState(
                                    icon = Icons.Rounded.QueueMusic,
                                    title = "No playlists yet",
                                    subtitle = "Create your first playlist or connect Spotify to import",
                                    actionText = "Create Playlist",
                                    onAction = { showCreatePlaylistDialog = true }
                                )
                            }
                        }
                    }
                    
                    LibraryTab.Artists -> {
                        item(key = "empty_artists") {
                            LibraryEmptyState(
                                icon = Icons.Rounded.PersonSearch,
                                title = "No artists followed",
                                subtitle = "Follow your favorite artists to see them here",
                                actionText = "Discover Artists",
                                onAction = onSearchClick
                            )
                        }
                    }
                    
                    LibraryTab.Albums -> {
                        item(key = "empty_albums") {
                            LibraryEmptyState(
                                icon = Icons.Rounded.Album,
                                title = "No saved albums",
                                subtitle = "Save albums to access them quickly",
                                actionText = "Browse Albums",
                                onAction = onSearchClick
                            )
                        }
                    }
                    
                    LibraryTab.Downloaded -> {
                        if (downloadedSongs.isEmpty()) {
                            item(key = "empty_downloads") {
                                LibraryEmptyState(
                                    icon = Icons.Rounded.CloudOff,
                                    title = "No downloads",
                                    subtitle = "Download songs to listen offline",
                                    actionText = "Explore Music",
                                    onAction = onSearchClick
                                )
                            }
                        } else {
                            // Filter chips
                            item(key = "download_filter") {
                                DownloadFilterChips(
                                    selectedFilter = downloadedFilter,
                                    songCount = downloadedSongs.size,
                                    albumCount = downloadedAlbums.size,
                                    onFilterSelect = { downloadedFilter = it }
                                )
                            }
                            
                            if (downloadedFilter == 0) {
                                // Songs
                                itemsIndexed(
                                    items = downloadedSongs,
                                    key = { _, song -> "download_${song.song.id}" }
                                ) { index, song ->
                                    DownloadedSongItem(
                                        title = song.song.title,
                                        artist = song.artists.joinToString(", ") { it.name },
                                        imageUrl = song.song.thumbnailUrl,
                                        index = index,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onDownloadedSongClick(song)
                                        }
                                    )
                                }
                            } else {
                                // Albums
                                if (downloadedAlbums.isEmpty()) {
                                    item(key = "no_albums") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "No complete albums downloaded",
                                                color = VikifyTheme.colors.textSecondary
                                            )
                                        }
                                    }
                                } else {
                                    itemsIndexed(
                                        items = downloadedAlbums,
                                        key = { _, (album, _, _) -> "album_${album.id}" }
                                    ) { index, (album, count, artUrl) ->
                                        DownloadedAlbumItem(
                                            title = album.title,
                                            subtitle = "$count songs • ${album.year ?: ""}",
                                            imageUrl = artUrl,
                                            index = index,
                                            onClick = { /* Navigate to album */ }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Collapsed header
        CollapsedLibraryHeader(
            visible = headerProgress > 0.8f,
            onSearchClick = onSearchClick,
            onAddClick = { showCreatePlaylistDialog = true }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HEADER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LibraryHeader(
    headerProgress: Float,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .graphicsLayer {
                alpha = 1f - (headerProgress * 0.5f)
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title with logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vikify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "Library",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = VikifyTheme.colors.textPrimary,
                    modifier = Modifier.semantics { heading() }
                )
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIconButton(
                    icon = Icons.Rounded.Search,
                    contentDescription = "Search",
                    onClick = onSearchClick
                )
                HeaderIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Create",
                    onClick = onAddClick
                )
                HeaderIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    onClick = onSettingsClick
                )
            }
        }
    }
}

@Composable
private fun CollapsedLibraryHeader(
    visible: Boolean,
    onSearchClick: () -> Unit,
    onAddClick: () -> Unit
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vikify_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Library",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = VikifyTheme.colors.textPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "Search",
                        onClick = onSearchClick,
                        size = 36.dp
                    )
                    HeaderIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Create",
                        onClick = onAddClick,
                        size = 36.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 40.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "headerButtonScale"
    )

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(VikifyTheme.colors.surface)
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
            tint = VikifyTheme.colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TAB SELECTOR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LibraryTabSelector(
    selectedTab: LibraryTab,
    onTabSelect: (LibraryTab) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(bottom = 24.dp)
    ) {
        items(LibraryTab.entries, key = { it.name }) { tab ->
            AnimatedLibraryTab(
                tab = tab,
                isSelected = tab == selectedTab,
                onClick = { onTabSelect(tab) }
            )
        }
    }
}

@Composable
private fun AnimatedLibraryTab(
    tab: LibraryTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "tabScale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) VikifyTheme.colors.textPrimary else VikifyTheme.colors.surface,
        animationSpec = tween(200),
        label = "tabBg"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) VikifyTheme.colors.background else VikifyTheme.colors.textPrimary,
        animationSpec = tween(200),
        label = "tabContent"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = tab.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PINNED SECTION
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PinnedSection(
    onLikedClick: () -> Unit,
    onDownloadsClick: () -> Unit
) {
    Column {
        Text(
            text = "Pinned",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            item(key = "liked") {
                PremiumPinnedCard(
                    title = "Liked Songs",
                    subtitle = "Your favorites",
                    icon = Icons.Rounded.Favorite,
                    gradient = LibraryColors.LikedGradient,
                    onClick = onLikedClick
                )
            }
            item(key = "downloads") {
                PremiumPinnedCard(
                    title = "Downloads",
                    subtitle = "Listen offline",
                    icon = Icons.Rounded.CloudDownload,
                    gradient = LibraryColors.DownloadGradient,
                    onClick = onDownloadsClick
                )
            }
        }
    }
}

@Composable
private fun PremiumPinnedCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "pinnedScale"
    )

    // Animated shimmer
    val infiniteTransition = rememberInfiniteTransition(label = "pinnedShimmer")
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
            .width(160.dp)
            .height(160.dp)
            .scale(scale)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = gradient.first().copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(gradient))
        )

        // Shimmer overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val shimmerWidth = size.width * 0.5f
            val startX = -shimmerWidth + (size.width + shimmerWidth * 2) * shimmerOffset

            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(startX + shimmerWidth, size.height)
                )
            )
        }

        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .padding(16.dp)
                .size(32.dp)
                .align(Alignment.TopStart)
        )

        // Text content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION HEADER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LibrarySectionHeader(
    title: String,
    sortOption: SortOption,
    viewMode: ViewMode,
    onSortClick: () -> Unit,
    onViewModeToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Sort button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(LibraryColors.GlassSurface)
                    .clickable(onClick = onSortClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = "Sort",
                        tint = VikifyTheme.colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = sortOption.displayName,
                        fontSize = 12.sp,
                        color = VikifyTheme.colors.textSecondary
                    )
                }
            }

            // View mode toggle
            IconButton(
                onClick = onViewModeToggle,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (viewMode == ViewMode.LIST)
                        Icons.Rounded.GridView else Icons.Rounded.ViewList,
                    contentDescription = "Toggle view",
                    tint = VikifyTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LIST ITEMS
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistListItem(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isLocal: Boolean,
    index: Int,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    contextMenuExpanded: Boolean = false,
    onDismissMenu: () -> Unit = {},
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    // Staggered entrance animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "itemAlpha"
    )

    val offsetX by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 20.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "itemOffset"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) 
            VikifyTheme.colors.surface.copy(alpha = 0.5f) 
        else 
            Color.Transparent,
        label = "itemBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .offset(x = offsetX)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onMenuClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork
            PlaylistArtwork(
                imageUrl = imageUrl,
                isLocal = isLocal,
                title = title
            )

            Spacer(Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VikifyTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isLocal) {
                        // Spotify indicator dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LibraryColors.SpotifyGreen)
                        )
                    }
                    Text(
                        text = subtitle,
                        fontSize = 14.sp,
                        color = VikifyTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Menu button
            if (onMenuClick != null) {
                Box {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = VikifyTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Dropdown menu
                    DropdownMenu(
                        expanded = contextMenuExpanded,
                        onDismissRequest = onDismissMenu,
                        modifier = Modifier.background(VikifyTheme.colors.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Rename", color = VikifyTheme.colors.textPrimary)
                            },
                            onClick = onRename,
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Edit,
                                    null,
                                    tint = VikifyTheme.colors.textSecondary
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text("Delete", color = LibraryColors.AccentRed)
                            },
                            onClick = onDelete,
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    null,
                                    tint = LibraryColors.AccentRed
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistArtwork(
    imageUrl: String?,
    isLocal: Boolean,
    title: String
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isLocal) Brush.linearGradient(LibraryColors.LikedGradient)
                else Brush.linearGradient(listOf(VikifyTheme.colors.surface, VikifyTheme.colors.surface))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            var imageState by remember { mutableStateOf<AsyncImagePainter.State?>(null) }
            
            AsyncImage(
                model = imageUrl,
                contentDescription = "$title cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { imageState = it }
            )
            
            // Loading state
            if (imageState is AsyncImagePainter.State.Loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(VikifyTheme.colors.surface)
                )
            }
        } else {
            // Placeholder for local playlists
            Icon(
                imageVector = Icons.Rounded.QueueMusic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun DownloadedSongItem(
    title: String,
    artist: String,
    imageUrl: String?,
    index: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 30L)
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "songAlpha"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) 
            LibraryColors.AccentGreen.copy(alpha = 0.1f) 
        else 
            Color.Transparent,
        label = "songBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(VikifyTheme.colors.surface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                fontSize = 13.sp,
                color = VikifyTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Downloaded indicator
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = "Downloaded",
            tint = LibraryColors.AccentGreen,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DownloadedAlbumItem(
    title: String,
    subtitle: String,
    imageUrl: String?,
    index: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 30L)
        isVisible = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "albumAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(VikifyTheme.colors.surface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = VikifyTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = VikifyTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FILTER CHIPS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DownloadFilterChips(
    selectedFilter: Int,
    songCount: Int,
    albumCount: Int,
    onFilterSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChip(
            selected = selectedFilter == 0,
            onClick = { onFilterSelect(0) },
            label = { Text("Songs ($songCount)") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = LibraryColors.AccentGreen.copy(alpha = 0.2f),
                selectedLabelColor = LibraryColors.AccentGreen,
                selectedLeadingIconColor = LibraryColors.AccentGreen
            )
        )
        FilterChip(
            selected = selectedFilter == 1,
            onClick = { onFilterSelect(1) },
            label = { Text("Albums ($albumCount)") },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Album,
                    null,
                    modifier = Modifier.size(16.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = LibraryColors.AccentPurple.copy(alpha = 0.2f),
                selectedLabelColor = LibraryColors.AccentPurple,
                selectedLeadingIconColor = LibraryColors.AccentPurple
            )
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// EMPTY STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LibraryEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyIcon")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(LibraryColors.GlassSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = VikifyTheme.colors.textSecondary.copy(alpha = iconAlpha),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = VikifyTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onAction,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VikifyTheme.colors.accent
            )
        ) {
            Text(
                text = actionText,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DIALOGS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Create Playlist",
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Playlist Name",
                    style = MaterialTheme.typography.labelMedium,
                    color = VikifyTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("My Playlist") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VikifyTheme.colors.accent,
                        unfocusedBorderColor = VikifyTheme.colors.border,
                        cursorColor = VikifyTheme.colors.accent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create", color = VikifyTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VikifyTheme.colors.textSecondary)
            }
        },
        containerColor = VikifyTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Rename Playlist",
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VikifyTheme.colors.accent,
                    unfocusedBorderColor = VikifyTheme.colors.border,
                    cursorColor = VikifyTheme.colors.accent
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename", color = VikifyTheme.colors.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VikifyTheme.colors.textSecondary)
            }
        },
        containerColor = VikifyTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Delete Playlist?",
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
        },
        text = {
            Text(
                "Are you sure you want to delete \"$playlistName\"? This action cannot be undone.",
                color = VikifyTheme.colors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = LibraryColors.AccentRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VikifyTheme.colors.textSecondary)
            }
        },
        containerColor = VikifyTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SortOptionsDialog(
    currentSort: SortOption,
    onDismiss: () -> Unit,
    onSelect: (SortOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Sort By",
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
        },
        text = {
            Column {
                SortOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(option) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSort == option,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = VikifyTheme.colors.accent,
                                unselectedColor = VikifyTheme.colors.textSecondary
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = option.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = VikifyTheme.colors.textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = VikifyTheme.colors.textSecondary)
            }
        },
        containerColor = VikifyTheme.colors.surfaceElevated,
        shape = RoundedCornerShape(24.dp)
    )
}
