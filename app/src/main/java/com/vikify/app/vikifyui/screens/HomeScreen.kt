/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Home Screen - Server-Driven UI Architecture
 * Optimized for performance with stable keys and minimal recomposition
 */
package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vikify.app.LocalPlayerConnection
import com.vikify.app.R
import com.vikify.app.models.MediaMetadata
import com.vikify.app.models.toMediaMetadata
import com.vikify.app.models.FeedSection
import com.vikify.app.models.RailItem
import com.vikify.app.models.QuickResumeItem
import com.vikify.app.models.QuickResumeType
import com.vikify.app.playback.queues.ListQueue
import com.vikify.app.viewmodels.HomeViewModel
import com.vikify.app.vikifyui.components.MeshBackground
import com.vikify.app.vikifyui.theme.EtherealBackground
import com.vikify.app.vikifyui.components.NowPlayingVinylRow
import com.vikify.app.vikifyui.components.SkeletonHomeFeed
import com.vikify.app.vikifyui.components.ThemeSwitch
import com.vikify.app.vikifyui.components.VikifyGlassCard
import com.vikify.app.vikifyui.theme.*
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.ArtistItem
import com.vikify.app.models.RailItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

private const val QUICK_RESUME_MAX_ITEMS = 6
private const val RAIL_ITEM_WIDTH_DP = 140
private const val LARGE_CARD_SIZE_DP = 160
private const val ARTIST_CIRCLE_SIZE_DP = 100

// Skip these section titles (video content)
private val VIDEO_SECTION_KEYWORDS = listOf(
    "video", "live performance", "music video", "concert", "live session"
)

// ═══════════════════════════════════════════════════════════════════════════════
// FEED SECTION SEALED CLASS - Server-Driven UI Building Blocks
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════════════════════════════

// Predefined genres for fallback
data class GenreItem(
    val id: String,
    val name: String,
    val searchQuery: String,
    val color: Int
)

val PREDEFINED_GENRES = listOf(
    GenreItem("pop", "Pop", "pop music hits", 0xFFE91E63.toInt()),
    GenreItem("rock", "Rock", "rock music hits", 0xFFD32F2F.toInt()),
    GenreItem("hip_hop", "Hip-Hop", "hip hop rap music", 0xFF7C4DFF.toInt()),
    GenreItem("rb", "R&B", "rnb soul music", 0xFF3F51B5.toInt()),
    GenreItem("edm", "EDM", "edm electronic dance music", 0xFF00BCD4.toInt()),
    GenreItem("indie", "Indie", "indie alternative music", 0xFF4CAF50.toInt()),
    GenreItem("jazz", "Jazz", "jazz music classics", 0xFFFFC107.toInt()),
    GenreItem("classical", "Classical", "classical music", 0xFF9C27B0.toInt()),
    GenreItem("chill", "Chill", "chill lofi relaxing music", 0xFF607D8B.toInt()),
    GenreItem("party", "Party", "party dance upbeat music", 0xFFFF5722.toInt()),
    GenreItem("workout", "Workout", "workout gym motivation music", 0xFFFF1744.toInt()),
    GenreItem("focus", "Focus", "focus study concentration music", 0xFF2196F3.toInt())
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN HOME SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistClick: (String, com.vikify.app.spotify.SpotifyPlaylist?) -> Unit = { _, _ -> },
    onLikedSongsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onMoodClick: (String, String?, Int?) -> Unit = { _, _, _ -> },
    onGenreSearch: (String, String, Int) -> Unit = { _, _, _ -> },
    onAlbumClick: (String) -> Unit = {},
    onMoodAndGenresClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val playerConnection = LocalPlayerConnection.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // ─────────────────────────────────────────────────────────────────────────
    // STATE COLLECTION
    // ─────────────────────────────────────────────────────────────────────────

    val homePage by viewModel.homePage.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    // val errorMessage by viewModel.errorMessage.collectAsState() // Removed as it doesn't exist
    val jumpBackIn by viewModel.jumpBackIn.collectAsState()
    val dailyMix by viewModel.dailyMix.collectAsState()
    val explorePage by viewModel.explorePage.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val homeSections by viewModel.homeSections.collectAsState()

    // Player state
    val currentMediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState()
        ?: remember { mutableStateOf(false) }

    // UI State
    val lazyListState = rememberLazyListState()
    val isDark = VikifyTheme.isDark

    // Header collapse progress
    val headerProgress by remember {
        derivedStateOf {
            val firstIndex = lazyListState.firstVisibleItemIndex
            val offset = lazyListState.firstVisibleItemScrollOffset
            if (firstIndex > 0) 1f else (offset / 250f).coerceIn(0f, 1f)
        }
    }

    // Time-based greeting (computed once)
    val greeting = remember { TimeAwareGreeting.current() }
    val dateText = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }

    val hasMoreContent = remember(homePage) { homePage?.continuation != null }

    // ─────────────────────────────────────────────────────────────────────────
    // EVENT HANDLERS (Stable references)
    // ─────────────────────────────────────────────────────────────────────────

    val onItemClick = remember(playerConnection, homeSections, homePage, quickPicks, forgottenFavorites, jumpBackIn, dailyMix, onAlbumClick, onPlaylistClick) {
        { item: RailItem, queueTitle: String ->
            // Check item type first - playlists/albums should open, songs should play
            when (item.itemType) {
                RailItemType.ALBUM -> {
                    // Navigate to album screen
                    onAlbumClick(item.id)
                }
                RailItemType.PLAYLIST -> {
                    // Navigate to playlist screen
                    onPlaylistClick(item.id, null)
                }
                RailItemType.SONG -> {
                    // Play the song
                    // PRIORITY 1: Check local database songs first (most reliable)
                    val localSong = quickPicks?.find { it.song.id == item.id }
                        ?: forgottenFavorites?.find { it.song.id == item.id }
                        ?: jumpBackIn?.find { it.song.id == item.id }
                        ?: dailyMix?.find { it.song.id == item.id }
                    
                    if (localSong != null) {
                        // Play from local database - full metadata available
                        playerConnection?.playQueue(
                            ListQueue(
                                title = queueTitle,
                                items = listOf(localSong.toMediaMetadata()),
                                startIndex = 0
                            )
                        )
                    } else {
                        // PRIORITY 2: Check YouTube homePage for SongItems with full metadata
                        val ytSongItem = homePage?.sections
                            ?.flatMap { it.items }
                            ?.filterIsInstance<SongItem>()
                            ?.find { it.id == item.id }
                        
                        if (ytSongItem != null) {
                            // Found in YouTube sections - use SongItem.toMediaMetadata() for proper playback
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = queueTitle,
                                    items = listOf(ytSongItem.toMediaMetadata()),
                                    startIndex = 0
                                )
                            )
                        } else {
                            // PRIORITY 3: Fallback - create MediaMetadata with ID (player will resolve stream)
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = queueTitle,
                                    items = listOf(MediaMetadata(
                                        id = item.id,
                                        title = item.title,
                                        artists = listOf(MediaMetadata.Artist(null, item.subtitle)),
                                        thumbnailUrl = item.imageUrl,
                                        duration = -1,
                                        genre = null
                                    )),
                                    startIndex = 0
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    val onQuickResumeClick = remember(playerConnection, onLikedSongsClick, onDownloadsClick, onPlaylistClick, quickPicks) {
        { item: QuickResumeItem ->
            when (item.type) {
                QuickResumeType.LIKED_SONGS -> onLikedSongsClick()
                QuickResumeType.DOWNLOADED -> onDownloadsClick()
                QuickResumeType.PLAYLIST -> {
                    // Navigate to playlist with the item's ID
                    onPlaylistClick(item.id, null)
                }
                QuickResumeType.RECENT_SONG -> {
                    // Find the actual song from quickPicks for full metadata
                    val recentSong = quickPicks?.find { it.song.id == item.id }
                    
                    if (recentSong != null) {
                        // Play with full metadata from database
                        playerConnection?.playQueue(
                            ListQueue(
                                title = "Quick Resume",
                                items = listOf(recentSong.toMediaMetadata()),
                                startIndex = 0
                            )
                        )
                    } else {
                        // Fallback: create MediaMetadata with ID for player to resolve
                        playerConnection?.playQueue(
                            ListQueue(
                                title = "Quick Resume",
                                items = listOf(MediaMetadata(
                                    id = item.id,
                                    title = item.title,
                                    artists = emptyList(),
                                    thumbnailUrl = item.imageUrl,
                                    duration = -1, // Player will resolve
                                    genre = null
                                )),
                                startIndex = 0
                            )
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RENDER
    // ─────────────────────────────────────────────────────────────────────────

    val currentThemeMode = VikifyThemeState.currentMode
    val colors = VikifyTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(colors.surfaceBackground)) {
        // Background Layer - adapt to theme mode
        when (currentThemeMode) {
            ThemeMode.SUNLIGHT -> EtherealBackground(modifier = Modifier.matchParentSize()) {}
            ThemeMode.MOON -> { /* Pure black - no animated background */ }
            ThemeMode.COOL -> MeshBackground(modifier = Modifier.matchParentSize())
        }

        // Content with loading/error states
        when {
            isLoading && homeSections.isEmpty() -> {
                SkeletonHomeFeed(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }

            else -> {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars),
                        contentPadding = PaddingValues(bottom = 180.dp)
                    ) {
                        // Header
                        item(key = "header") {
                            HomeHeader(
                                greeting = greeting,
                                dateText = dateText,
                                syncProgress = syncProgress,
                                headerProgress = headerProgress,
                                onNotificationsClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }
                            )
                        }

                        // Dynamic Feed Sections
                        var vinylInserted = false
                        homeSections.forEachIndexed { index, section ->
                            item(key = section.id) {
                                Box(modifier = Modifier.staggeredEntrance(index)) {
                                    FeedSectionRenderer(
                                        section = section,
                                        onItemClick = { item, title ->
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onItemClick(item, title)
                                        },
                                        onQuickResumeClick = { item ->
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onQuickResumeClick(item)
                                        },
                                        onGenreClick = { name, query, color ->
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onGenreSearch(name, query, color)
                                        },
                                        onAlbumClick = { id ->
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onAlbumClick(id)
                                        },
                                        onPlaylistClick = { id ->
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onPlaylistClick(id, null)
                                        }
                                    )
                                }
                            }
                            // Insert NowPlayingVinylRow right after QuickResumeGrid
                            if (!vinylInserted && section is com.vikify.app.models.FeedSection.QuickResumeGrid && currentMediaMetadata != null) {
                                item(key = "now_playing_vinyl") {
                                    Spacer(Modifier.height(16.dp))
                                    NowPlayingVinylRow(
                                        song = currentMediaMetadata!!,
                                        isPlaying = isPlaying,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                                vinylInserted = true
                            }
                        }

                        // Infinite scroll trigger
                        if (hasMoreContent) {
                            item(key = "load_more") {
                                InfiniteScrollTrigger(
                                    isLoading = isLoadingMore,
                                    onLoadMore = { viewModel.loadMore() }
                                )
                            }
                        }

                        // Empty state
                        if (homeSections.isEmpty() && !isLoading) {
                            item(key = "empty") {
                                HomeEmptyState()
                            }
                        }
                    }
                }
            }
        }

        // Collapsed header overlay
        CollapsedHomeHeader(
            visible = headerProgress > 0.9f,
            onNotificationsClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            }
        )
    }
}

// NOTE: Feed sections are built in HomeViewModel.homeSections
// This ensures proper state management and avoids recomposition overhead

// ═══════════════════════════════════════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

private fun getGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

private fun getDailyMixTitle(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Morning Energy"
        hour < 17 -> "Afternoon Vibes"
        hour < 21 -> "Evening Chill"
        else -> "Night Mode"
    }
}

private fun buildQuickResumeItems(
    quickPicks: List<com.vikify.app.db.entities.Song>?
): List<QuickResumeItem> = buildList {
    add(QuickResumeItem(
        id = "liked_songs",
        title = "Liked Songs",
        imageUrl = null,
        type = QuickResumeType.LIKED_SONGS
    ))

    add(QuickResumeItem(
        id = "downloaded",
        title = "Downloads",
        imageUrl = null,
        type = QuickResumeType.DOWNLOADED
    ))

    quickPicks?.take(4)?.forEach { song ->
        add(QuickResumeItem(
            id = song.song.id,
            title = song.song.title,
            imageUrl = song.song.thumbnailUrl,
            type = QuickResumeType.RECENT_SONG
        ))
    }
}

private fun List<com.vikify.app.db.entities.Song>.toRailItems(
    currentSongId: String?,
    isPlaying: Boolean
): List<RailItem> = map { song ->
    RailItem(
        id = song.song.id,
        title = song.song.title,
        subtitle = song.artists.firstOrNull()?.name ?: "",
        imageUrl = song.song.thumbnailUrl,
        isPlaying = currentSongId == song.song.id && isPlaying
    )
}

private fun findSongsForQueue(
    homePage: com.zionhuang.innertube.pages.HomePage?,
    quickPicks: List<com.vikify.app.db.entities.Song>?,
    forgottenFavorites: List<com.vikify.app.db.entities.Song>?,
    keepListening: List<com.vikify.app.db.entities.LocalItem>?,
    jumpBackIn: List<com.vikify.app.db.entities.Song>?,
    dailyMix: List<com.vikify.app.db.entities.Song>?,
    queueTitle: String
): List<com.vikify.app.db.entities.Song> {
    return when {
        queueTitle == "Quick Picks" -> quickPicks ?: emptyList()
        queueTitle == "Jump Back In" -> jumpBackIn ?: emptyList()
        queueTitle == "Forgotten Favorites" -> forgottenFavorites ?: emptyList()
        queueTitle.contains("Mix") || queueTitle.contains("Energy") ||
            queueTitle.contains("Vibes") || queueTitle.contains("Chill") ||
            queueTitle.contains("Night") -> dailyMix ?: emptyList()
        else -> quickPicks ?: emptyList()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// FEED SECTION RENDERER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FeedSectionRenderer(
    section: FeedSection,
    onItemClick: (RailItem, String) -> Unit,
    onQuickResumeClick: (QuickResumeItem) -> Unit,
    onGenreClick: (String, String, Int) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    when (section) {
        is FeedSection.QuickResumeGrid -> {
            QuickResumeGridSection(
                items = section.items,
                onItemClick = onQuickResumeClick
            )
        }

        is FeedSection.NowPlayingHero -> {
            Spacer(Modifier.height(16.dp))
            NowPlayingVinylRow(
                song = section.song,
                isPlaying = section.isPlaying,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        is FeedSection.HorizontalRail -> {
            HorizontalRailSection(
                title = section.title,
                subtitle = section.subtitle,
                items = section.items,
                onItemClick = { item ->
                    // Route based on item type
                    when (item.itemType) {
                        RailItemType.ALBUM -> onAlbumClick(item.id)
                        RailItemType.PLAYLIST -> onPlaylistClick(item.id)
                        RailItemType.SONG -> {
                            // Check if it's a genre/mood item (stripeColor = genre card)
                            if (item.stripeColor != null) {
                                val genre = PREDEFINED_GENRES.find { it.name == item.title }
                                onGenreClick(
                                    item.title,
                                    genre?.searchQuery ?: "${item.title} music songs",
                                    item.stripeColor
                                )
                            } else {
                                onItemClick(item, section.title)
                            }
                        }
                    }
                }
            )
        }

        is FeedSection.LargeSquareRail -> {
            LargeSquareRailSection(
                title = section.title,
                subtitle = section.subtitle,
                items = section.items,
                onItemClick = { item ->
                    // Route based on item type
                    when (item.itemType) {
                        RailItemType.ALBUM -> onAlbumClick(item.id)
                        RailItemType.PLAYLIST -> onPlaylistClick(item.id)
                        RailItemType.SONG -> onItemClick(item, section.title)
                    }
                }
            )
        }

        is FeedSection.CircleArtistRail -> {
            CircleArtistRailSection(
                title = section.title,
                artists = section.artists,
                onArtistClick = { /* Navigate to artist */ }
            )
        }

        is FeedSection.HeroCard -> {
            HeroCardSection(
                title = section.title,
                subtitle = section.subtitle,
                imageUrl = section.imageUrl,
                label = section.label,
                onClick = { /* Handle based on actionId */ }
            )
        }

        is FeedSection.VerticalTrackList -> {
            VerticalTrackListSection(
                title = section.title,
                tracks = section.tracks,
                onTrackClick = { item -> onItemClick(item, section.title) }
            )
        }

        is FeedSection.MoodChipRow -> {
            Spacer(Modifier.height(16.dp))
            SectionHeader(title = section.title)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(section.moods) { mood ->
                    HomeMoodCard(
                        title = mood.title,
                        color = VikifyTheme.colors.surface,
                        onClick = { onGenreClick(mood.title, mood.endpoint.params ?: "", 0) }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HEADER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HomeHeader(
    greeting: TimeAwareGreeting,
    dateText: String,
    syncProgress: String?,
    headerProgress: Float,
    onNotificationsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Sync status pill
            AnimatedVisibility(
                visible = syncProgress != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SyncStatusPill(
                    progress = syncProgress ?: "",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Greeting (fades on scroll)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${greeting.greeting} ${greeting.emoji}",
                    fontSize = 16.sp,
                    color = LocalVikifyColors.current.textSecondary.copy(alpha = 1f - headerProgress),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Logo + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - (headerProgress * 0.3f)
                    }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vikify_logo),
                        contentDescription = "Vikify Logo",
                        modifier = Modifier.size((38 - (10 * headerProgress)).dp)
                    )
                    Text(
                        text = "Home",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = (34 - (10 * headerProgress)).sp,
                            color = LocalVikifyColors.current.textPrimary
                        ),
                        modifier = Modifier.semantics { heading() }
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThemeSwitch()
                }
            }

            // Subtitle/Date (fades on scroll)
            Column(modifier = Modifier.graphicsLayer { alpha = 1f - headerProgress }) {
                Text(
                    text = greeting.subtitle,
                    fontSize = 14.sp,
                    color = LocalVikifyColors.current.textPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    color = LocalVikifyColors.current.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CollapsedHomeHeader(
    visible: Boolean,
    onNotificationsClick: () -> Unit
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
                            LocalVikifyColors.current.background,
                            LocalVikifyColors.current.background.copy(alpha = 0.95f),
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
                        text = "Home",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = LocalVikifyColors.current.textPrimary
                    )
                }

                ThemeSwitch()
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

    VikifyGlassCard(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = CircleShape,
        contentPadding = 0.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = LocalVikifyColors.current.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SyncStatusPill(
    progress: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = DarkColors.Accent.copy(alpha = 0.15f),
        contentColor = DarkColors.Accent,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, DarkColors.Accent.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 2.dp,
                color = DarkColors.Accent
            )
            Text(
                text = progress,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeMoodCard(
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    VikifyGlassCard(
        modifier = Modifier
            .width(100.dp)
            .height(60.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        contentPadding = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = LocalVikifyColors.current.textPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalVikifyColors.current.textSecondary
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickResumeGridSection(
    items: List<QuickResumeItem>,
    onItemClick: (QuickResumeItem) -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    HomeQuickResumeCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HorizontalRailSection(
    title: String,
    subtitle: String?,
    items: List<RailItem>,
    onItemClick: (RailItem) -> Unit
) {
    Column {
    Spacer(Modifier.height(16.dp))
    SectionHeader(title = title, subtitle = subtitle)
    Spacer(Modifier.height(8.dp))

    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            HomeCompactSongCard(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                isPlaying = item.isPlaying,
                stripeColor = item.stripeColor,
                modifier = Modifier,
                onClick = { onItemClick(item) }
            )
        }
    }
    }
}

@Composable
private fun LargeSquareRailSection(
    title: String,
    subtitle: String?,
    items: List<RailItem>,
    onItemClick: (RailItem) -> Unit
) {
    Column {
    Spacer(Modifier.height(16.dp))
    SectionHeader(title = title, subtitle = subtitle)
    Spacer(Modifier.height(8.dp))

    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            HomeLargeSquareCard(
                title = item.title,
                subtitle = item.subtitle,
                imageUrl = item.imageUrl,
                modifier = Modifier,
                onClick = { onItemClick(item) }
            )
        }
    }
    }
}

@Composable
private fun CircleArtistRailSection(
    title: String,
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit
) {
    Column {
    Spacer(Modifier.height(16.dp))
    SectionHeader(title = title)
    Spacer(Modifier.height(8.dp))

    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(artists, key = { _, it -> it.id }) { index, artist ->
            HomeCircleArtistCard(
                name = artist.title,
                imageUrl = artist.thumbnail,
                modifier = Modifier,
                onClick = { onArtistClick(artist) }
            )
        }
    }
    }
}

@Composable
private fun HeroCardSection(
    title: String,
    subtitle: String,
    imageUrl: String?,
    label: String,
    onClick: () -> Unit
) {
    Spacer(Modifier.height(20.dp))
    FullWidthHeroCard(
        title = title,
        subtitle = subtitle,
        imageUrl = imageUrl,
        label = label,
        onClick = onClick
    )
}

@Composable
private fun VerticalTrackListSection(
    title: String,
    tracks: List<RailItem>,
    onTrackClick: (RailItem) -> Unit
) {
    Spacer(Modifier.height(24.dp))
    SectionHeader(title = title)

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tracks.take(5).forEach { track ->
            CompactTrackRow(
                title = track.title,
                subtitle = track.subtitle,
                imageUrl = track.imageUrl,
                isPlaying = track.isPlaying,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CARD COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun HomeQuickResumeCard(
    item: QuickResumeItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "quickResumeScale"
    )

    VikifyGlassCard(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon/Image based on type
            QuickResumeIcon(item = item)

            // Title
            Text(
                text = item.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = VikifyTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun QuickResumeIcon(item: QuickResumeItem) {
    when (item.type) {
        QuickResumeType.LIKED_SONGS -> {
            GradientIconBox(
                icon = Icons.Rounded.Favorite,
                colors = listOf(Color(0xFFEF4444), Color(0xFFEC4899))
            )
        }
        QuickResumeType.DOWNLOADED -> {
            GradientIconBox(
                icon = Icons.Rounded.Download,
                colors = listOf(Color(0xFF22C55E), Color(0xFF10B981))
            )
        }
        QuickResumeType.PLAYLIST, QuickResumeType.RECENT_SONG -> {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            )
        }
    }
}

@Composable
private fun GradientIconBox(
    icon: ImageVector,
    colors: List<Color>
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun HomeCompactSongCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isPlaying: Boolean,
    stripeColor: Int? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Premium pressable with scale
    Column(
        modifier = modifier
            .width(RAIL_ITEM_WIDTH_DP.dp)
            .premiumPressable(onClick = onClick)
    ) {
        // Artwork or gradient
        Box(
            modifier = Modifier
                .size(RAIL_ITEM_WIDTH_DP.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (stripeColor != null) {
                        Brush.linearGradient(
                            colors = listOf(
                                Color(stripeColor),
                                Color(stripeColor).copy(alpha = 0.6f)
                            )
                        )
                    } else {
                        Brush.linearGradient(listOf(VikifyTheme.colors.surface, VikifyTheme.colors.surface))
                    }
                )
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (stripeColor != null) {
                // Genre/Mood card with title overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Playing indicator
            if (isPlaying) {
                PlayingIndicatorOverlay()
            }
        }

        if (imageUrl != null || stripeColor == null) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isPlaying) GlowBlue else VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = VikifyTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HomeLargeSquareCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Premium pressable with bounce on appear
    Column(
        modifier = modifier
            .width((LARGE_CARD_SIZE_DP + 16).dp)
            .bounceOnAppear()
            .premiumPressable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(LARGE_CARD_SIZE_DP.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(VikifyTheme.colors.surface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(4.dp))


        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = VikifyTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeCircleArtistCard(
    name: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .width(ARTIST_CIRCLE_SIZE_DP.dp)
            .premiumPressable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ARTIST_CIRCLE_SIZE_DP.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(VikifyTheme.colors.surface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = VikifyTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompactTrackRow(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> VikifyTheme.colors.surfaceElevated.copy(alpha = 0.8f)
            isPlaying -> GlowBlue.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        label = "trackRowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .premiumPressable(onClick = onClick)
            .padding(8.dp),
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

            if (isPlaying) {
                PlayingIndicatorOverlay(size = 48.dp)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPlaying) GlowBlue else VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = VikifyTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isPlaying) {
            AnimatedEqualizer(
                color = GlowBlue,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun FullWidthHeroCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            // Label chip
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(GlowBlue)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayingIndicatorOverlay(size: Dp = RAIL_ITEM_WIDTH_DP.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        AnimatedEqualizer(color = GlowBlue)
    }
}

@Composable
private fun AnimatedEqualizer(
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val heights = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + index * 100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_\$index"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .background(color, CircleShape)
                    .graphicsLayer {
                        scaleY = height.value
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
        }
    }
}

@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.WifiOff,
            contentDescription = null,
            tint = VikifyTheme.colors.textSecondary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = VikifyTheme.colors.textPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun HomeEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = DelightMoments.getEmptyMessage(),
            style = MaterialTheme.typography.bodyLarge,
            color = VikifyTheme.colors.textSecondary
        )
    }
}

@Composable
private fun InfiniteScrollTrigger(isLoading: Boolean, onLoadMore: () -> Unit) {
    LaunchedEffect(Unit) {
        onLoadMore()
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = VikifyTheme.colors.accent
            )
        }
    }
}
