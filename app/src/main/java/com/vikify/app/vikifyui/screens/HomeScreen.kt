/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import com.vikify.app.R
import coil3.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.vikify.app.vikifyui.theme.LocalVikifyColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.valentinilk.shimmer.shimmer
import com.vikify.app.LocalPlayerConnection
import com.vikify.app.models.toMediaMetadata
import com.vikify.app.playback.queues.ListQueue
import com.vikify.app.viewmodels.HomeViewModel
import com.vikify.app.vikifyui.components.NowPlayingVinylRow
import com.vikify.app.vikifyui.theme.DarkColors
import com.vikify.app.vikifyui.theme.GlowBlue
import com.vikify.app.vikifyui.theme.LightColors
import com.vikify.app.vikifyui.theme.LivingBackground
import com.vikify.app.vikifyui.theme.SoftSurface
import com.vikify.app.vikifyui.theme.TextBlack
import com.vikify.app.vikifyui.theme.TextGray
import com.vikify.app.vikifyui.theme.VikifyTheme
import com.vikify.app.vikifyui.theme.CardShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.vikify.app.vikifyui.components.MeshBackground
import com.vikify.app.vikifyui.components.VikifyGlassCard
import com.vikify.app.vikifyui.components.PremiumVinyl
import com.vikify.app.vikifyui.theme.SonicTheme

// ============================================================================
// SERVER-DRIVEN UI ARCHITECTURE - FeedSection System
// ============================================================================

/**
 * FeedSection - The "Lego blocks" of the Home Feed
 * 
 * Instead of hardcoding UI, we render a List<FeedSection> dynamically.
 * This creates the "Limitless" Spotify feel - the app doesn't know what's next,
 * it just renders whatever comes down the pipe.
 */
sealed class FeedSection {
    // The "Good Morning" 2x3 Grid - Playlists, Liked, Downloaded
    data class QuickResumeGrid(
        val items: List<QuickResumeItem>
    ) : FeedSection()
    
    // Standard horizontal rail with compact cards
    data class HorizontalRail(
        val title: String,
        val subtitle: String? = null,
        val items: List<RailItem>
    ) : FeedSection()
    
    // Large 160dp square cards for visual variety
    data class LargeSquareRail(
        val title: String,
        val subtitle: String? = null,
        val items: List<RailItem>
    ) : FeedSection()
    
    // Circle profiles for artists
    data class CircleArtistRail(
        val title: String,
        val artists: List<ArtistItem>
    ) : FeedSection()
    
    // Full-width hero card for new releases
    data class HeroCard(
        val title: String,
        val subtitle: String,
        val imageUrl: String?,
        val label: String = "NEW RELEASE",
        val onClick: () -> Unit
    ) : FeedSection()
    
    // Vertical track list for "more of this"
    data class VerticalTrackList(
        val title: String,
        val tracks: List<RailItem>
    ) : FeedSection()

    // Now Playing Vinyl Hero (Wide Ticket)
    data class NowPlayingHero(
        val song: com.vikify.app.models.MediaMetadata,
        val isPlaying: Boolean
    ) : FeedSection()
}

// Data models for feed items
data class QuickResumeItem(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val type: QuickResumeType,
    val onClick: () -> Unit
)

enum class QuickResumeType {
    PLAYLIST, LIKED_SONGS, DOWNLOADED, RECENT_SONG
}

data class RailItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val isPlaying: Boolean = false,
    val onClick: () -> Unit
)

data class ArtistItem(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val onClick: () -> Unit
)

// ============================================================================
// PREMIUM HOME SCREEN - LIMITLESS DISCOVERY EDITION
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistClick: (String, com.vikify.app.spotify.SpotifyPlaylist?) -> Unit = { _, _ -> },
    onLikedSongsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    onMoodClick: (String, String?, Int?) -> Unit = { _, _, _ -> },
    onAlbumClick: (String) -> Unit = {}, // New callback for album navigation
    onMoodAndGenresClick: () -> Unit = {}, // Navigate to full Mood & Genres screen
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val view = LocalView.current
    val density = LocalDensity.current
    
    // State Collection
    val homePage by viewModel.homePage.collectAsState()
    val quickPicks by viewModel.quickPicks.collectAsState()
    val forgottenFavorites by viewModel.forgottenFavorites.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val recentActivity by viewModel.recentActivity.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Smart Queue sections
    val jumpBackIn by viewModel.jumpBackIn.collectAsState()
    val dailyMix by viewModel.dailyMix.collectAsState()
    
    // YouTube Music Data
    val explorePage by viewModel.explorePage.collectAsState()
    
    // Infinite Scroll State
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMoreContent = remember(homePage) { homePage?.continuation != null }

    
    // Player State
    val currentMediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: mutableStateOf(null)
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: mutableStateOf(false)

    // Dynamic Glow Color from Album Art
    var dynamicGlowColor by remember { mutableStateOf(GlowBlue) }
    val animatedGlowColor by animateColorAsState(
        targetValue = dynamicGlowColor,
        animationSpec = tween(durationMillis = 500),
        label = "glowColor"
    )

    // Scroll state for collapsing header
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val heroListState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    // Calculate scroll progress for header animation
    val scrollOffset = remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItem = remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    
    // Header collapse progress (0 = expanded, 1 = collapsed)
    val headerProgress by remember {
        derivedStateOf {
            if (firstVisibleItem.value > 0) 1f
            else (scrollOffset.value / 200f).coerceIn(0f, 1f)
        }
    }
    
    // Greeting based on time
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }
    
    val dateText = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }
    


    // ... 

    // Theme-aware colors
    val isDark = VikifyTheme.isDark
    // Use transparent background for Dark mode so LivingBackground shows
    val backgroundColor = if (isDark) Color.Transparent else LightColors.Background
    val surfaceColor = if (isDark) DarkColors.Surface else LightColors.Surface
    
    // ═══════════════════════════════════════════════════════════════
    // GENERATE FEED - The "Server-Driven UI" Engine
    // Creates a dynamic list of FeedSection "Lego blocks"
    // ═══════════════════════════════════════════════════════════════    // Sync Progress from SpotifySyncWorker
    val syncProgress by viewModel.syncProgress.collectAsState()
    
    // Build Feed Sections
    val feedSections = remember(
        homePage, 
        quickPicks, 
        forgottenFavorites, 
        keepListening, 
        currentMediaMetadata, 
        isPlaying,
        jumpBackIn,
        dailyMix,
        // Add dependency on syncProgress
        syncProgress
    ) {
        buildList {
            // 0. SYNC STATUS INDICATOR
            if (syncProgress != null) {
                // We add a special "Hero" type for sync status or just a custom placeholder
                // Since FeedSection is strict, we'll render this directly in the LazyColumn item 
                // outside the FeedSection abstraction if possible, OR we cheat and add a Header
            }
            
            // 1. GOOD MORNING HERO GRID (Quick Resume)
            // Playlists, Liked, Downloaded
            val quickResumeItems = mutableListOf<QuickResumeItem>()
            
            // Add Liked Songs
            quickResumeItems.add(QuickResumeItem(
                id = "liked_songs",
                title = "Liked Songs",
                imageUrl = null, // Will use gradient icon
                type = QuickResumeType.LIKED_SONGS,
                onClick = onLikedSongsClick
            ))
            
            // Add Downloaded
                quickResumeItems.add(QuickResumeItem(
                id = "downloaded",
                title = "Downloads",
                imageUrl = null,
                type = QuickResumeType.DOWNLOADED,
                onClick = onDownloadsClick
            ))
            
            // Add recent songs to fill grid
            quickPicks?.take(4)?.forEach { song ->
                quickResumeItems.add(QuickResumeItem(
                    id = song.song.id,
                    title = song.song.title,
                    imageUrl = song.song.thumbnailUrl,
                    type = QuickResumeType.RECENT_SONG,
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(title = "Quick Resume", items = listOf(song.toMediaMetadata()), startIndex = 0)
                        )
                    }
                ))
            }
            
            if (quickResumeItems.isNotEmpty()) {
                add(FeedSection.QuickResumeGrid(quickResumeItems.take(6)))
            }

            // 1.5 NEW RELEASES FROM YOUTUBE MUSIC - Dynamic Data!
            if (explorePage?.newReleaseAlbums?.isNotEmpty() == true) {
                val newReleaseItems = explorePage!!.newReleaseAlbums.take(10).map { album ->
                    RailItem(
                        id = album.browseId,
                        title = album.title,
                        subtitle = album.artists?.joinToString(", ") { it.name } ?: "New Release",
                        imageUrl = album.thumbnail,
                        onClick = { 
                            // Call parent handler instead of navigating directly
                            onAlbumClick(album.browseId)
                        }
                    )
                }
                add(FeedSection.LargeSquareRail(
                    title = "New Releases",
                    subtitle = "Fresh from YouTube Music",
                    items = newReleaseItems
                ))
            }
            
            // MOOD & GENRES - Discover Section
            if (explorePage?.moodAndGenres?.isNotEmpty() == true) {
                val moodItems = explorePage!!.moodAndGenres.take(8).mapIndexed { index, mood ->
                    RailItem(
                        id = "mood_$index", // Use index-based unique ID to avoid key issues
                        title = mood.title,
                        subtitle = "Explore",
                        imageUrl = null, // Moods use stripeColor for styling
                        onClick = { 
                            // Navigate to Browse Screen
                            onMoodClick(mood.title, mood.endpoint.browseId, mood.stripeColor.toInt())
                        }
                    )
                }
                add(FeedSection.HorizontalRail(
                    title = "Mood & Genres",
                    subtitle = "Explore by vibe",
                    items = moodItems
                ))
            }

            // NOW PLAYING HERO (Visible if song loaded)
            if (currentMediaMetadata != null) {
                add(FeedSection.NowPlayingHero(currentMediaMetadata!!, isPlaying))
            }
            
            // 2. JUMP BACK IN - Your most played recently (Smart Queue feature)
            jumpBackIn?.takeIf { it.isNotEmpty() }?.let { songs ->
                add(FeedSection.HorizontalRail(
                    title = "Jump Back In",
                    subtitle = "Your top songs this week",
                    items = songs.map { song ->
                        RailItem(
                            id = song.song.id,
                            title = song.song.title,
                            subtitle = song.artists.firstOrNull()?.name ?: "",
                            imageUrl = song.song.thumbnailUrl,
                            isPlaying = currentMediaMetadata?.id == song.song.id && isPlaying,
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(title = "Jump Back In", items = listOf(song.toMediaMetadata()), startIndex = 0)
                                )
                            }
                        )
                    }
                ))
            }
            
            // 3. DAILY MIX - Energy-filtered based on time of day (Smart Queue feature)
            dailyMix?.takeIf { it.isNotEmpty() }?.let { songs ->
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val mixTitle = when {
                    hour < 12 -> "Morning Energy"
                    hour < 17 -> "Afternoon Vibes"
                    hour < 21 -> "Evening Chill"
                    else -> "Night Mode"
                }
                add(FeedSection.LargeSquareRail(
                    title = mixTitle,
                    items = songs.map { song ->
                        RailItem(
                            id = song.song.id,
                            title = song.song.title,
                            subtitle = song.artists.firstOrNull()?.name ?: "",
                            imageUrl = song.song.thumbnailUrl,
                            isPlaying = currentMediaMetadata?.id == song.song.id && isPlaying,
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(title = mixTitle, items = listOf(song.toMediaMetadata()), startIndex = 0)
                                )
                            }
                        )
                    }
                ))
            }
            
            // 4. QUICK PICKS - Large Square Rail (160dp)
            quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
                add(FeedSection.LargeSquareRail(
                    title = "Quick Picks",
                    items = picks.map { song ->
                        RailItem(
                            id = song.song.id,
                            title = song.song.title,
                            subtitle = song.artists.firstOrNull()?.name ?: "",
                            imageUrl = song.song.thumbnailUrl,
                            isPlaying = currentMediaMetadata?.id == song.song.id && isPlaying,
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(title = "Quick Picks", items = listOf(song.toMediaMetadata()), startIndex = 0)
                                )
                            }
                        )
                    }
                ))
            }
            
            // 3. FORGOTTEN FAVORITES - Standard Rail
            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { favorites ->
                add(FeedSection.HorizontalRail(
                    title = "Forgotten Favorites",
                    subtitle = "Songs you haven't played in a while",
                    items = favorites.map { song ->
                        RailItem(
                            id = song.song.id,
                            title = song.song.title,
                            subtitle = song.artists.firstOrNull()?.name ?: "",
                            imageUrl = song.song.thumbnailUrl,
                            isPlaying = currentMediaMetadata?.id == song.song.id && isPlaying,
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(title = "Forgotten Favorites", items = listOf(song.toMediaMetadata()), startIndex = 0)
                                )
                            }
                        )
                    }
                ))
            }
            
            // 4. KEEP LISTENING - Standard Rail
            keepListening?.takeIf { it.isNotEmpty() }?.let { keeps ->
                add(FeedSection.HorizontalRail(
                    title = "Keep Listening",
                    subtitle = "Your recent favorites",
                    items = keeps.map { localItem ->
                        RailItem(
                            id = localItem.id,
                            title = localItem.title,
                            subtitle = "",
                            imageUrl = localItem.thumbnailUrl,
                            isPlaying = currentMediaMetadata?.id == localItem.id && isPlaying,
                            onClick = { }
                        )
                    }
                ))
            }
            
            // 5. YOUTUBE SECTIONS - Mix of rail types
            homePage?.sections?.forEachIndexed { index, section ->
                val sectionTitle = section.title ?: return@forEachIndexed
                val sectionItems = section.items.filterIsInstance<com.zionhuang.innertube.models.SongItem>()
                
                if (sectionItems.isNotEmpty()) {
                    // Alternate between rail types for visual variety
                    when (index % 3) {
                        0 -> add(FeedSection.HorizontalRail(
                            title = sectionTitle,
                            items = sectionItems.map { item ->
                                RailItem(
                                    id = item.id,
                                    title = item.title,
                                    subtitle = item.artists.firstOrNull()?.name ?: "",
                                    imageUrl = item.thumbnail,
                                    isPlaying = currentMediaMetadata?.id == item.id && isPlaying,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(title = sectionTitle, items = listOf(item.toMediaMetadata()), startIndex = 0)
                                        )
                                    }
                                )
                            }
                        ))
                        1 -> add(FeedSection.LargeSquareRail(
                            title = sectionTitle,
                            items = sectionItems.map { item ->
                                RailItem(
                                    id = item.id,
                                    title = item.title,
                                    subtitle = item.artists.firstOrNull()?.name ?: "",
                                    imageUrl = item.thumbnail,
                                    isPlaying = currentMediaMetadata?.id == item.id && isPlaying,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(title = sectionTitle, items = listOf(item.toMediaMetadata()), startIndex = 0)
                                        )
                                    }
                                )
                            }
                        ))
                        else -> add(FeedSection.HorizontalRail(
                            title = sectionTitle,
                            items = sectionItems.map { item ->
                                RailItem(
                                    id = item.id,
                                    title = item.title,
                                    subtitle = item.artists.firstOrNull()?.name ?: "",
                                    imageUrl = item.thumbnail,
                                    isPlaying = currentMediaMetadata?.id == item.id && isPlaying,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(title = sectionTitle, items = listOf(item.toMediaMetadata()), startIndex = 0)
                                        )
                                    }
                                )
                            }
                        ))
                    }
                }
            }
        }
    }



    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Living Background Layer (Dark Mode Only)
        // VIKIFY SIGNATURE: The "Lava Lamp" Mesh Gradient
        if (isDark) {
            MeshBackground(modifier = Modifier.matchParentSize())
        } else {
            // Ethereal Day Background
            com.vikify.app.vikifyui.theme.EtherealBackground(modifier = Modifier.matchParentSize()) { }
        }



        // Content Layer
        
        // Premium Loading Transition
        Crossfade(
             targetState = isLoading && feedSections.isEmpty(), 
             label = "LoadingTransition",
             animationSpec = tween(500)
        ) { loading ->
            if (loading) {
                 com.vikify.app.vikifyui.components.SkeletonHomeFeed(
                     modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
            } else {
        LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars), // Fix overlapping status bar
                    contentPadding = PaddingValues(bottom = 180.dp) // Fix mini-player overlap
                ) {
            
            // ═══════════════════════════════════════════════════════════════
            // 1. COLLAPSING HEADER (Dynamic Large Title)
            // ═══════════════════════════════════════════════════════════════
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp) // Adjusted top padding since we have window insets now
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        // === SYNC STATUS PILL ===
                        if (syncProgress != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Surface(
                                    color = DarkColors.Accent.copy(alpha = 0.15f),
                                    contentColor = DarkColors.Accent,
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, DarkColors.Accent.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = DarkColors.Accent
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = syncProgress!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Greeting (fades out on scroll)
                        Text(
                            text = greeting,
                            fontSize = 16.sp,
                            color = LocalVikifyColors.current.textSecondary.copy(alpha = 1f - headerProgress),
                            fontWeight = FontWeight.Medium
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.graphicsLayer {
                                alpha = 1f - (headerProgress * 0.3f)
                            }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.vikify_logo),
                                contentDescription = null,
                                modifier = Modifier.size((38 - (10 * headerProgress)).dp)
                            )
                            Text(
                                text = "Home",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (34 - (10 * headerProgress)).sp,
                                    color = LocalVikifyColors.current.textPrimary
                                )
                            )
                        }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Theme Toggle
                                com.vikify.app.vikifyui.components.ThemeSwitch()
                                
                                // Notifications
                                IconButton(
                                    onClick = { 
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    },
                                    modifier = Modifier
                                        .shadow(4.dp, CircleShape, spotColor = CardShadow)
                                        .background(LocalVikifyColors.current.surfaceElevated, CircleShape)
                                        .size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Notifications, 
                                        contentDescription = "Alerts", 
                                        tint = LocalVikifyColors.current.textPrimary, 
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        
                        // Date subtitle
                        Text(
                            text = dateText,
                            fontSize = 14.sp,
                            color = LocalVikifyColors.current.textSecondary.copy(alpha = 1f - headerProgress),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ═══════════════════════════════════════════════════════════════
            // DYNAMIC FEED RENDERING - Server-Driven UI Architecture
            // Each FeedSection is a "Lego block" rendered in sequence
            // ═══════════════════════════════════════════════════════════════
            feedSections.forEachIndexed { sectionIndex, section ->
                when (section) {
                    // ───────────────────────────────────────────────────────
                    // QUICK RESUME GRID (2x3) - Playlists, Liked, Downloads
                    // ───────────────────────────────────────────────────────
                    is FeedSection.QuickResumeGrid -> {
                        item(key = "quick_resume_grid") {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                        modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                section.items.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowItems.forEach { item ->
                                            QuickResumeCardSDUI(
                                                item = item,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ───────────────────────────────────────────────────────
                    // NOW PLAYING HERO - Wide Ticket with Vinyl Animation
                    // ───────────────────────────────────────────────────────
                    is FeedSection.NowPlayingHero -> {
                        item(key = "now_playing_hero") {
                            Spacer(modifier = Modifier.height(16.dp))
                            NowPlayingVinylRow(
                                song = section.song,
                                isPlaying = section.isPlaying,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                }
            }

                    // ───────────────────────────────────────────────────────
                    // HORIZONTAL RAIL - Standard compact cards
                    // ───────────────────────────────────────────────────────
                    is FeedSection.HorizontalRail -> {
                        item(key = "rail_${sectionIndex}") {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(title = section.title, subtitle = section.subtitle)
                    LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(section.items, key = { it.id }) { item ->
                                    CompactSongCard(
                                        title = item.title,
                                        artist = item.subtitle,
                                        imageUrl = item.imageUrl,
                                        isPlaying = item.isPlaying,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            item.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // ───────────────────────────────────────────────────────
                    // LARGE SQUARE RAIL - 160dp immersive cards
                    // ───────────────────────────────────────────────────────
                    is FeedSection.LargeSquareRail -> {
                        item(key = "large_rail_${sectionIndex}") {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(title = section.title)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                                items(section.items, key = { it.id }) { item ->
                                    LargeSquareCard(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        imageUrl = item.imageUrl,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            item.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // ───────────────────────────────────────────────────────
                    // CIRCLE ARTIST RAIL - Artist profile circles
                    // ───────────────────────────────────────────────────────
                    is FeedSection.CircleArtistRail -> {
                        item(key = "artist_rail_${sectionIndex}") {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(title = section.title)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(section.artists, key = { it.id }) { artist ->
                                    CircleArtistCard(
                                        name = artist.name,
                                        imageUrl = artist.imageUrl,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            artist.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // ───────────────────────────────────────────────────────
                    // HERO CARD - Full-width immersive new release
                    // ───────────────────────────────────────────────────────
                    is FeedSection.HeroCard -> {
                        item(key = "hero_${sectionIndex}") {
                            Spacer(modifier = Modifier.height(20.dp))
                            FullWidthHeroCard(
                                title = section.title,
                                subtitle = section.subtitle,
                                imageUrl = section.imageUrl,
                                label = section.label,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                    section.onClick()
                                }
                            )
                        }
                    }
                    
                    // ───────────────────────────────────────────────────────
                    // VERTICAL TRACK LIST - For "more of this" sections
                    // ───────────────────────────────────────────────────────
                    is FeedSection.VerticalTrackList -> {
                        item(key = "vertical_${sectionIndex}") {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(title = section.title)
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                section.tracks.take(5).forEach { track ->
                                    CompactSongCard(
                                        title = track.title,
                                        artist = track.subtitle,
                                        imageUrl = track.imageUrl,
                                        isPlaying = track.isPlaying,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            track.onClick()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // INFINITE SCROLL TRIGGER - Load more when reaching bottom
            // ═══════════════════════════════════════════════════════════════
            if (hasMoreContent) {
                item(key = "infinite_scroll_trigger") {
                    LaunchedEffect(Unit) {
                        viewModel.loadMore()
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = VikifyTheme.colors.accent
                            )
                        }
                    }
                }
            }
        }
            }
        }
        
        // ═══════════════════════════════════════════════════════════════
        // FAB Removed - Use NowPlayingHero section instead
    }
}


// ============================================================================
// VINYL HERO CARD - The Centerpiece Component
// ============================================================================

@Composable
fun VinylHeroCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isPlaying: Boolean = false,
    glowColor: Color = GlowBlue,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    
    // Animation for vinyl reveal
    val transition = updateTransition(targetState = isPlaying, label = "VinylReveal")
    
    // Vinyl slide animation
    val vinylTranslationX by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.6f, stiffness = 400f) },
        label = "VinylSlide"
    ) { state -> if (state) 50.dp else 0.dp }
            
    // Cover 3D tilt when vinyl shows
    val coverRotationY by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.5f, stiffness = 300f) },
        label = "CoverTilt"
    ) { state -> if (state) -8f else 0f }
    
    // Vinyl spin animation
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpinAngle"
    )
    
    Box(
        modifier = Modifier
            .width(220.dp) // Slightly wider for majesty
            .height(280.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopStart
    ) {
        // === LAYER 1: THE ULTRA-PREMIUM VINYL (Behind) ===
        PremiumVinyl(
            imageUrl = imageUrl,
            vinylSize = 170.dp,
            rotation = if (isPlaying) spinAngle else 0f,
            glowColor = glowColor,
            modifier = Modifier
                .padding(top = 10.dp, start = 12.dp)
                .graphicsLayer {
                    translationX = with(density) { vinylTranslationX.toPx() }
                    // Dynamic depth based on sliding progress
                    val slideProgress = vinylTranslationX.toPx() / 50.dp.toPx()
                    scaleX = 0.95f + (0.05f * slideProgress)
                    scaleY = 0.95f + (0.05f * slideProgress)
                }
        )

        // === LAYER 2: THE COVER ART (Front) ===
        Column(
            modifier = Modifier.width(180.dp)
        ) {
            Box(
                    modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        rotationY = coverRotationY
                        cameraDistance = 15f * density.density
                        // Counter-tilt for realism
                        rotationX = if (isPlaying) 2f else 0f
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(24.dp),
                        spotColor = if (isPlaying) glowColor.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(SoftSurface)
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Playing indicator overlay - Glassmorphism V2
                androidx.compose.animation.AnimatedVisibility(
                    visible = isPlaying,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = "Playing",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
                Spacer(Modifier.height(16.dp))
            
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textSecondary.copy(alpha = 0.7f),
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================================
// GLASS PILL BUTTON (Glassmorphism Quick Actions)
// ============================================================================

@Composable
fun GlassPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    iconTint: Color = VikifyTheme.colors.textPrimary,
    onClick: () -> Unit
) {
    val colors = VikifyTheme.colors
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassBackground)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }
    }
}

// ============================================================================
// COMPACT SONG CARD (For horizontal grids)
// ============================================================================

// CompactSongCard removed (defined in BrowseScreen.kt)

// ============================================================================
// COMPACT ALBUM CARD
// ============================================================================

@Composable
fun CompactAlbumCard(
    title: String,
    artist: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    VikifyGlassCard(
        modifier = Modifier
            .width(156.dp)
            .clickable(onClick = onClick),
        contentPadding = 12.dp
    ) {
        Column {
        Box(
            modifier = Modifier
                .size(140.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = CardShadow)
                .clip(RoundedCornerShape(12.dp))
                .background(SoftSurface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.height(8.dp))
        
                    Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = VikifyTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = artist,
            fontSize = 12.sp,
            color = VikifyTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        }
    }
}

// ============================================================================
// SPINNING VINYL FAB
// ============================================================================

@Composable
fun SpinningVinylFab(
    imageUrl: String?,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FabSpin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )
    
    FloatingActionButton(
        onClick = onClick,
        containerColor = Color(0xFF0A0A0A),
        modifier = Modifier
            .size(56.dp)
            .shadow(12.dp, CircleShape, spotColor = GlowBlue.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotation)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color(0xFF000000),
                            Color(0xFF1A1A1A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Center album art
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
        }
    }
}

// ============================================================================
// SECTION HEADER
// ============================================================================

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = VikifyTheme.colors.textSecondary
            )
        }
    }
        }

// ============================================================================
// NOW PLAYING GLOW CARD
// ============================================================================

@Composable
fun NowPlayingGlowCard(
    title: String,
    artist: String,
    imageUrl: String?,
    glowColor: Color,
    onClick: () -> Unit = {},
    onColorExtracted: (Color) -> Unit
) {
    val context = LocalContext.current
    
    // Extract dominant color from image
    LaunchedEffect(imageUrl) {
        if (imageUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = coil3.ImageLoader(context).execute(request)
                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            val vibrant = palette?.vibrantSwatch?.rgb
                            val dominant = palette?.dominantSwatch?.rgb
                            val color = Color(vibrant ?: dominant ?: GlowBlue.toArgb())
                            onColorExtracted(color)
                        }
                    }
                } catch (e: Exception) {
                    // Keep default color
                }
            }
        }
    }
    
             Box(
                 modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = glowColor.copy(alpha = 0.6f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.3f),
                        SoftSurface
                    )
                )
            )
            .border(1.dp, glowColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Spinning Vinyl + Album Art
            Box(
                modifier = Modifier.size(76.dp),
                contentAlignment = Alignment.Center
            ) {
                // Infinite rotation for vinyl
                val infiniteTransition = rememberInfiniteTransition(label = "vinyl")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 3000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "vinylRotation"
                )
                
                // Vinyl record (peeks out from right side)
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .offset(x = 20.dp) // Offset to peek from right
                        .graphicsLayer { rotationZ = rotation }
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                ) {
                    // Vinyl grooves
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        // Outer ring
                        drawCircle(
                            color = Color(0xFF333333),
                            radius = size.minDimension / 2 - 2.dp.toPx(),
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                        // Middle ring
                        drawCircle(
                            color = Color(0xFF444444),
                            radius = size.minDimension / 3,
                            center = center,
                            style = Stroke(width = 0.5.dp.toPx())
                        )
                        // Center hole
                        drawCircle(
                            color = Color(0xFF666666),
                            radius = 4.dp.toPx(),
                            center = center
                        )
                    }
                }
                
                // Album art (on top, slightly left)
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .offset(x = (-4).dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VikifyTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    fontSize = 14.sp,
                    color = VikifyTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Subtle indicator - tap to expand
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Tap to expand",
                tint = glowColor.copy(alpha = 0.6f),
                modifier = Modifier.size(28.dp)
                 )
             }
        }
    }

// ============================================================================
// SHIMMER SKELETON
// ============================================================================

@Composable
fun ShimmerHeroCard() {
    val ShimmerBase = LocalVikifyColors.current.shimmerBase
    
    Column(
        modifier = Modifier
            .width(160.dp)
            .shimmer()
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ShimmerBase)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ShimmerBase)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ShimmerBase)
        )
    }
}

// ============================================================================
// LIKED SONGS HOME CARD
// ============================================================================

@Composable
fun LikedSongsHomeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0xFFEF4444).copy(alpha = 0.3f))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFEF4444).copy(alpha = 0.15f),
                        LocalVikifyColors.current.surfaceElevated
                    )
                )
            )
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFEC4899))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column {
                Text(
                    "Liked Songs",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = VikifyTheme.colors.textPrimary
                )
                Text(
                    "Your favorite tracks",
                    fontSize = 14.sp,
                    color = VikifyTheme.colors.textSecondary
                )
            }
        }
    }
}

// ============================================================================
// QUICK RESUME CARD (Spotify-style 2x3 Grid Card)
// Dense, compact card for quick resume functionality
// ============================================================================

@Composable
fun QuickResumeCard(
    title: String,
    imageUrl: String?,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPlaying) 
                    VikifyTheme.colors.surfaceElevated.copy(alpha = 0.9f)
                else 
                    VikifyTheme.colors.surface.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art (square, left-aligned)
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
        )
        
        // Title (dense typography - 13sp as per design spec)
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPlaying) GlowBlue else VikifyTheme.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .weight(1f)
        )
        
        // Playing indicator
        if (isPlaying) {
            Icon(
                Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = GlowBlue,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 8.dp)
        )
    }
}
}

// ============================================================================
// LARGE SQUARE CARD (160dp x 160dp for "Your Top Mixes")
// Immersive cards for visual variety
// ============================================================================

@Composable
fun LargeSquareCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VikifyGlassCard(
        modifier = modifier
            .width(176.dp) // +16dp padding space
            .clickable(onClick = onClick),
        contentPadding = 12.dp
    ) {
        Column {
        // Large album art
        Box(
            modifier = Modifier
                .size(160.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(VikifyTheme.colors.surface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Dense typography (13sp title, 11sp subtitle)
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
}

// ============================================================================
// FULL WIDTH HERO CARD (Immersive New Release)
// ============================================================================

@Composable
fun FullWidthHeroCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    label: String = "NEW RELEASE",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VikifyGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        contentPadding = 0.dp // Hero card should be full bleed
    ) {
        // Background image
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
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
        
        // Content
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
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

// ============================================================================
// QUICK RESUME CARD SDUI (For FeedSection.QuickResumeGrid)
// Handles Liked Songs, Downloads with gradient icons
// ============================================================================

@Composable
fun QuickResumeCardSDUI(
    item: QuickResumeItem,
    modifier: Modifier = Modifier
) {
    VikifyGlassCard(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(8.dp),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(), // Row fills the Glass Card
            verticalAlignment = Alignment.CenterVertically
        ) {
        // Left icon/image based on type
        when (item.type) {
            QuickResumeType.LIKED_SONGS -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFEF4444), Color(0xFFEC4899))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            QuickResumeType.DOWNLOADED -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF22C55E), Color(0xFF10B981))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            QuickResumeType.PLAYLIST -> {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(VikifyTheme.colors.surface)
                ) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
    }
}
            QuickResumeType.RECENT_SONG -> {
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

// ============================================================================
// CIRCLE ARTIST CARD (For FeedSection.CircleArtistRail)
// 100dp circular artist profile
// ============================================================================

@Composable
fun CircleArtistCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circle profile image
        Box(
        modifier = Modifier
                .size(100.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(VikifyTheme.colors.surface)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Artist name
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
