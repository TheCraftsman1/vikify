/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Premium Search Screen - Discovery Interface
 */
package com.vikify.app.vikifyui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vikify.app.R
import com.vikify.app.spotify.SpotifyPlaylist
import com.vikify.app.spotify.SpotifyRepository
import com.vikify.app.vikifyui.data.SearchViewModel
import com.vikify.app.vikifyui.data.UnifiedSearchResult
import com.vikify.app.vikifyui.data.SearchSource
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTE
// ═══════════════════════════════════════════════════════════════════════════════

private object SearchColors {
    val AccentPurple = Color(0xFF7C4DFF)
    val AccentCyan = Color(0xFF00E5FF)
    val AccentPink = Color(0xFFFF4081)
    
    val GlassSurface = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.12f)
    
    // Genre colors
    val genreColors = mapOf(
        "pop" to Color(0xFFFF6B9D),
        "hip-hop" to Color(0xFF7C4DFF),
        "rock" to Color(0xFFEF4444),
        "indie" to Color(0xFF10B981),
        "r&b" to Color(0xFF8B5CF6),
        "electronic" to Color(0xFF06B6D4),
        "jazz" to Color(0xFFF59E0B),
        "classical" to Color(0xFF6366F1),
        "podcasts" to Color(0xFFEC4899),
        "charts" to Color(0xFF14B8A6),
        "new releases" to Color(0xFFF97316),
        "moods" to Color(0xFF8B5CF6)
    )
    
    fun getGenreColor(genre: String): Color {
        return genreColors[genre.lowercase()] ?: AccentPurple
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DATA MODELS
// ═══════════════════════════════════════════════════════════════════════════════

data class SearchCategory(
    val id: String,
    val name: String,
    val icon: String,
    val color: Color,
    val isPriority: Boolean = false,
    val isNew: Boolean = false
)

// Note: UnifiedSearchResult is imported from com.vikify.app.vikifyui.data

data class RecentSearch(
    val id: String,
    val query: String,
    val timestamp: Long,
    val resultType: String? = null,
    val imageUrl: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
// SAMPLE DATA
// ═══════════════════════════════════════════════════════════════════════════════

private val sampleCategories = listOf(
    SearchCategory("1", "Pop", "🎤", SearchColors.genreColors["pop"]!!, isPriority = true),
    SearchCategory("2", "Hip-Hop", "🎧", SearchColors.genreColors["hip-hop"]!!, isPriority = true),
    SearchCategory("3", "Rock", "🎸", SearchColors.genreColors["rock"]!!),
    SearchCategory("4", "Indie", "🌿", SearchColors.genreColors["indie"]!!),
    SearchCategory("5", "R&B", "💜", SearchColors.genreColors["r&b"]!!, isNew = true),
    SearchCategory("6", "Electronic", "🎹", SearchColors.genreColors["electronic"]!!),
    SearchCategory("7", "Jazz", "🎷", SearchColors.genreColors["jazz"]!!),
    SearchCategory("8", "Classical", "🎻", SearchColors.genreColors["classical"]!!),
    SearchCategory("9", "Podcasts", "🎙️", SearchColors.genreColors["podcasts"]!!),
    SearchCategory("10", "Charts", "📊", SearchColors.genreColors["charts"]!!, isPriority = true),
    SearchCategory("11", "New Releases", "✨", SearchColors.genreColors["new releases"]!!, isNew = true),
    SearchCategory("12", "Moods", "🌙", SearchColors.genreColors["moods"]!!)
)

private val trendingSearches = listOf(
    "The Weeknd",
    "Taylor Swift",
    "Bad Bunny",
    "Drake",
    "Dua Lipa"
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SearchScreen(
    onCategoryClick: (SearchCategory) -> Unit = {},
    onArtistClick: (UnifiedSearchResult.Artist) -> Unit = {},
    onAlbumClick: (UnifiedSearchResult.Album) -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onPlaylistClick: (String, SpotifyPlaylist?) -> Unit = { _, _ -> },
    onVoiceSearchResult: (String) -> Unit = {},
    recentSearches: List<RecentSearch> = emptyList(),
    onClearRecentSearches: () -> Unit = {},
    onRemoveRecentSearch: (String) -> Unit = {},
    onRecentSearchClick: (RecentSearch) -> Unit = {},
    spotifyRepository: Any? = null,
    initialQuery: String? = null,
    onInitialQueryConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ViewModel for real search functionality
    val searchViewModel: SearchViewModel = hiltViewModel()
    
    // Observe ViewModel state
    val searchResults by searchViewModel.unifiedResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    val searchSource by searchViewModel.searchSource.collectAsState()
    
    // Set SpotifyRepository for Spotify search capability
    LaunchedEffect(spotifyRepository) {
        if (spotifyRepository is SpotifyRepository) {
            searchViewModel.setSpotifyRepository(spotifyRepository)
        }
    }
    
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isDark = VikifyTheme.isDark
    
    // Local state
    var localQuery by remember { mutableStateOf(initialQuery ?: "") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    
    // Sync with external query and consume it
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrEmpty() && localQuery != initialQuery) {
            localQuery = initialQuery
            searchViewModel.updateQuery(initialQuery) // Trigger search
            onInitialQueryConsumed()
        }
    }
    
    // Update ViewModel query when local query changes (debounced via ViewModel)
    LaunchedEffect(localQuery) {
        searchViewModel.updateQuery(localQuery)
    }
    
    // Voice search launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { spokenText ->
                localQuery = spokenText
                onVoiceSearchResult(spokenText)
            }
        }
    }
    
    // Blur effect when focused with empty query
    val backgroundBlur by animateDpAsState(
        targetValue = if (isSearchFocused && localQuery.isEmpty()) 8.dp else 0.dp,
        animationSpec = tween(300),
        label = "backgroundBlur"
    )

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // Search Header
            SearchHeader(
                query = localQuery,
                onQueryChange = { localQuery = it },
                onClear = {
                    localQuery = ""
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                onVoiceClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search for songs, artists, or lyrics...")
                        }
                        voiceLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Voice search not available", Toast.LENGTH_SHORT).show()
                    }
                },
                isFocused = isSearchFocused,
                onFocusChange = { isSearchFocused = it },
                focusRequester = focusRequester
            )

            // Content
            Crossfade(
                targetState = localQuery.isEmpty(),
                animationSpec = tween(200),
                label = "searchContent"
            ) { isEmpty ->
                if (isEmpty) {
                    // Browse State
                    BrowseContent(
                        recentSearches = recentSearches,
                        onClearRecent = onClearRecentSearches,
                        onRemoveRecent = onRemoveRecentSearch,
                        onRecentClick = { recent ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            localQuery = recent.query
                            onRecentSearchClick(recent)
                        },
                        onCategoryClick = { category ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // Search for category content
                            localQuery = category.name
                            searchViewModel.searchByCategory(category.name)
                            onCategoryClick(category)
                        },
                        onTrendingClick = { query ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            localQuery = query
                        },
                        blurRadius = backgroundBlur,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Results State
                    SearchResultsContent(
                        results = searchResults,
                        isSearching = isSearching,
                        query = localQuery,
                        selectedFilter = selectedFilter,
                        onFilterSelect = { selectedFilter = it },
                        onResultClick = { result ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (result) {
                                is UnifiedSearchResult.Song -> {
                                    // Convert SongItem to Track for playback
                                    val songItem = result.originalItem
                                    val track = Track(
                                        id = result.id,
                                        title = songItem?.title ?: result.headline,
                                        artist = songItem?.artists?.firstOrNull()?.name ?: result.subheadline.removePrefix("Song • "),
                                        remoteArtworkUrl = songItem?.thumbnail ?: result.imageUrl,
                                        duration = songItem?.duration?.times(1000L) ?: -1L,
                                        originalBackendRef = songItem,
                                        youtubeId = result.id
                                    )
                                    onTrackClick(track)
                                }
                                is UnifiedSearchResult.Artist -> onArtistClick(result)
                                is UnifiedSearchResult.Album -> onAlbumClick(result)
                                is UnifiedSearchResult.Playlist -> onPlaylistClick(result.id, result.spotifyPlaylist)
                                is UnifiedSearchResult.Video -> {
                                    val track = Track(
                                        id = result.id,
                                        title = result.headline,
                                        artist = result.subheadline,
                                        remoteArtworkUrl = result.imageUrl,
                                        duration = result.duration ?: -1L,
                                        youtubeId = result.id
                                    )
                                    onTrackClick(track)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Tap to dismiss focus
        if (backgroundBlur > 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEARCH HEADER
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onVoiceClick: () -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester
) {
    val haptic = LocalHapticFeedback.current
    
    val headerPadding by animateDpAsState(
        targetValue = if (query.isEmpty() && !isFocused) 20.dp else 16.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerPadding"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = headerPadding, vertical = 12.dp)
    ) {
        // Large title (collapses when searching)
        AnimatedVisibility(
            visible = query.isEmpty() && !isFocused,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.vikify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Search",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = VikifyTheme.colors.textPrimary
                )
            }
        }

        // Search bar row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main search input
            SearchInputField(
                query = query,
                onQueryChange = onQueryChange,
                onVoiceClick = onVoiceClick,
                isFocused = isFocused,
                onFocusChange = onFocusChange,
                focusRequester = focusRequester,
                modifier = Modifier.weight(1f)
            )

            // Cancel button
            AnimatedVisibility(
                visible = query.isNotEmpty() || isFocused,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it }
            ) {
                Text(
                    text = "Cancel",
                    color = VikifyTheme.colors.accent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClear()
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceClick: () -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) 
            VikifyTheme.colors.accent.copy(alpha = 0.5f) 
        else 
            Color.Transparent,
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 6.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "elevation"
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(26.dp),
                spotColor = VikifyTheme.colors.accent.copy(alpha = 0.2f)
            )
            .background(VikifyTheme.colors.surface, RoundedCornerShape(26.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Search icon
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = VikifyTheme.colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            
            Spacer(Modifier.width(12.dp))

            // Text field
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 17.sp,
                    color = VikifyTheme.colors.textPrimary,
                    fontWeight = FontWeight.Normal
                ),
                cursorBrush = SolidColor(VikifyTheme.colors.accent),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Songs, artists, or podcasts",
                                color = VikifyTheme.colors.textSecondary,
                                fontSize = 17.sp
                            )
                        }
                        innerTextField()
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                })
            )

            // Voice/Clear buttons
            AnimatedContent(
                targetState = query.isEmpty(),
                transitionSpec = {
                    fadeIn(tween(150)) + scaleIn(initialScale = 0.8f) togetherWith
                    fadeOut(tween(100)) + scaleOut(targetScale = 0.8f)
                },
                label = "actionButton"
            ) { isEmpty ->
                if (isEmpty) {
                    // Voice search
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = "Voice Search",
                        tint = VikifyTheme.colors.textSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onVoiceClick()
                            }
                    )
                } else {
                    // Clear button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                VikifyTheme.colors.textSecondary.copy(alpha = 0.2f),
                                CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onQueryChange("")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = VikifyTheme.colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BROWSE CONTENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun BrowseContent(
    recentSearches: List<RecentSearch>,
    onClearRecent: () -> Unit,
    onRemoveRecent: (String) -> Unit,
    onRecentClick: (RecentSearch) -> Unit,
    onCategoryClick: (SearchCategory) -> Unit,
    onTrendingClick: (String) -> Unit,
    blurRadius: Dp,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = modifier.blur(blurRadius)
    ) {
        // Recent searches section
        if (recentSearches.isNotEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                RecentSearchesSection(
                    searches = recentSearches,
                    onClear = onClearRecent,
                    onRemove = onRemoveRecent,
                    onSearchClick = onRecentClick
                )
            }
        }

        // Trending searches
        item(span = StaggeredGridItemSpan.FullLine) {
            TrendingSearchesSection(onTrendingClick = onTrendingClick)
        }

        // Browse categories header
        item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                text = "Browse All",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        // Category cards
        itemsIndexed(
            items = sampleCategories,
            key = { _, category -> category.id }
        ) { index, category ->
            CategoryCard(
                category = category,
                index = index,
                onClick = { onCategoryClick(category) }
            )
        }

        // Bottom spacing
        item(span = StaggeredGridItemSpan.FullLine) {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun RecentSearchesSection(
    searches: List<RecentSearch>,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
    onSearchClick: (RecentSearch) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Searches",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
            
            Text(
                text = "Clear all",
                fontSize = 14.sp,
                color = VikifyTheme.colors.accent,
                modifier = Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClear()
                }
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(searches.take(10), key = { it.id }) { search ->
                RecentSearchChip(
                    search = search,
                    onClick = { onSearchClick(search) },
                    onRemove = { onRemove(search.id) }
                )
            }
        }
    }
}

@Composable
private fun RecentSearchChip(
    search: RecentSearch,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VikifyTheme.colors.surface)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        // Image or icon
        if (search.imageUrl != null) {
            AsyncImage(
                model = search.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
            )
        } else {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = VikifyTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(Modifier.width(8.dp))
        
        Text(
            text = search.query,
            fontSize = 14.sp,
            color = VikifyTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRemove()
            },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Remove",
                tint = VikifyTheme.colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun TrendingSearchesSection(onTrendingClick: (String) -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Rounded.TrendingUp,
                contentDescription = null,
                tint = VikifyTheme.colors.accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Trending",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(trendingSearches) { index, query ->
                TrendingSearchChip(
                    query = query,
                    rank = index + 1,
                    onClick = { onTrendingClick(query) }
                )
            }
        }
    }
}

@Composable
private fun TrendingSearchChip(
    query: String,
    rank: Int,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        VikifyTheme.colors.accent.copy(alpha = 0.15f),
                        VikifyTheme.colors.surface
                    )
                )
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "#$rank",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.accent
        )
        
        Spacer(Modifier.width(8.dp))
        
        Text(
            text = query,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = VikifyTheme.colors.textPrimary
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// CATEGORY CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CategoryCard(
    category: SearchCategory,
    index: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "cardScale"
    )
    
    // Staggered entrance animation
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        isVisible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "cardAlpha"
    )
    
    // Dynamic height based on priority
    val height = when {
        category.isPriority -> 150.dp
        category.isNew -> 130.dp
        else -> 110.dp
    }
    
    // Animated gradient
    val infiniteTransition = rememberInfiniteTransition(label = "categoryGradient")
    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientPhase"
    )
    
    val secondaryColor = category.color.copy(
        red = (category.color.red + 0.15f).coerceAtMost(1f),
        green = (category.color.green + 0.1f).coerceAtMost(1f),
        blue = (category.color.blue + 0.2f).coerceAtMost(1f)
    )
    
    val gradient = Brush.linearGradient(
        colors = listOf(category.color, secondaryColor, category.color),
        start = Offset(0f, 0f),
        end = Offset(
            x = 300f + (100f * gradientPhase),
            y = 300f + (100f * (1f - gradientPhase))
        )
    )

    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .alpha(alpha)
            .scale(scale)
            .shadow(
                elevation = if (category.isPriority) 12.dp else 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = category.color.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // "NEW" badge
        if (category.isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "NEW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = category.color,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Category name
        Text(
            text = category.name,
            fontSize = if (category.isPriority) 20.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )

        // Rotated emoji icon
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(if (category.isPriority) 70.dp else 55.dp)
                .offset(x = 12.dp, y = 12.dp)
                .graphicsLayer {
                    rotationZ = 20f
                    shadowElevation = 8f
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.icon,
                fontSize = if (category.isPriority) 34.sp else 28.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEARCH RESULTS CONTENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchResultsContent(
    results: List<UnifiedSearchResult>,
    isSearching: Boolean,
    query: String,
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    onResultClick: (UnifiedSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val filterOptions = listOf("All", "Songs", "Artists", "Albums", "Playlists")
    
    // Filter results
    val filteredResults = remember(results, selectedFilter) {
        when (selectedFilter) {
            "Songs" -> results.filterIsInstance<UnifiedSearchResult.Song>()
            "Artists" -> results.filterIsInstance<UnifiedSearchResult.Artist>()
            "Albums" -> results.filterIsInstance<UnifiedSearchResult.Album>()
            "Playlists" -> results.filterIsInstance<UnifiedSearchResult.Playlist>()
            else -> results
        }
    }

    Column(modifier = modifier) {
        // Filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items(filterOptions) { filter ->
                SearchFilterChip(
                    label = filter,
                    isSelected = selectedFilter == filter,
                    onClick = { onFilterSelect(filter) }
                )
            }
        }

        // Results list
        when {
            isSearching -> {
                SearchLoadingSkeleton()
            }
            filteredResults.isEmpty() && query.isNotEmpty() -> {
                EmptySearchState(query = query)
            }
            else -> {
                SearchResultsList(
                    results = filteredResults,
                    query = query,
                    onResultClick = onResultClick
                )
            }
        }
    }
}

@Composable
private fun SearchFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) 
            VikifyTheme.colors.accent 
        else 
            VikifyTheme.colors.surface,
        animationSpec = tween(200),
        label = "chipBg"
    )
    
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) 
            Color.White 
        else 
            VikifyTheme.colors.textSecondary,
        animationSpec = tween(200),
        label = "chipContent"
    )

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(backgroundColor)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<UnifiedSearchResult>,
    query: String,
    onResultClick: (UnifiedSearchResult) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Top result spotlight
        results.firstOrNull()?.let { topResult ->
            item(key = "top_${topResult.id}") {
                TopResultCard(
                    result = topResult,
                    query = query,
                    onClick = { onResultClick(topResult) }
                )
            }
            
            if (results.size > 1) {
                item(key = "header") {
                    Text(
                        text = "More Results",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = VikifyTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }

        // Remaining results with staggered animation
        itemsIndexed(
            items = results.drop(1),
            key = { _, result -> result.id }
        ) { index, result ->
            SearchResultRow(
                result = result,
                query = query,
                index = index,
                onClick = { onResultClick(result) }
            )
        }

        // Bottom spacing
        item(key = "spacer") {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun TopResultCard(
    result: UnifiedSearchResult,
    query: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val defaultAccent = VikifyTheme.colors.accent
    
    var glowColor by remember { mutableStateOf(defaultAccent) }
    
    // Extract dominant color
    LaunchedEffect(result.imageUrl) {
        result.imageUrl?.let { url ->
            scope.launch {
                extractDominantColor(context, url)?.let { color ->
                    glowColor = color
                }
            }
        }
    }
    
    val isArtist = result is UnifiedSearchResult.Artist

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = glowColor.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.2f),
                        VikifyTheme.colors.surface
                    )
                )
            )
            .border(1.dp, glowColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Artwork
            AsyncImage(
                model = result.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP RESULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = glowColor,
                    letterSpacing = 1.sp
                )
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = buildHighlightedText(result.headline, query, glowColor),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VikifyTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.subheadline,
                        fontSize = 14.sp,
                        color = VikifyTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Type badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VikifyTheme.colors.surface)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = result.type.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = VikifyTheme.colors.textSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            // Play button
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                containerColor = glowColor,
                contentColor = Color.White,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: UnifiedSearchResult,
    query: String,
    index: Int,
    onClick: () -> Unit
) {
    // Staggered entrance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 30L)
        isVisible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(200),
        label = "rowAlpha"
    )
    
    val offsetX by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 20.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "rowOffset"
    )
    
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) 
            VikifyTheme.colors.surface.copy(alpha = 0.8f) 
        else 
            Color.Transparent,
        label = "rowBg"
    )
    
    val isArtist = result is UnifiedSearchResult.Artist

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .offset(x = offsetX)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        // Artwork
        AsyncImage(
            model = result.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
        )
        
        Spacer(Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildHighlightedText(result.headline, query, VikifyTheme.colors.accent),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(Modifier.height(2.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Type indicator icon
                val typeIcon = when (result) {
                    is UnifiedSearchResult.Song -> Icons.Rounded.MusicNote
                    is UnifiedSearchResult.Artist -> Icons.Rounded.Person
                    is UnifiedSearchResult.Album -> Icons.Rounded.Album
                    is UnifiedSearchResult.Playlist -> Icons.AutoMirrored.Rounded.QueueMusic
                    is UnifiedSearchResult.Video -> Icons.Rounded.PlayArrow
                }
                
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = VikifyTheme.colors.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                
                Text(
                    text = result.subheadline,
                    fontSize = 14.sp,
                    color = VikifyTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = "More options",
            tint = VikifyTheme.colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LOADING & EMPTY STATES
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SearchLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top result skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(VikifyTheme.colors.surface.copy(alpha = shimmerAlpha))
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Row skeletons
        repeat(6) {
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VikifyTheme.colors.surface.copy(alpha = shimmerAlpha))
                )
                
                Spacer(Modifier.width(14.dp))
                
                Column {
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(VikifyTheme.colors.surface.copy(alpha = shimmerAlpha))
                    )
                    
                    Spacer(Modifier.height(6.dp))
                    
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(VikifyTheme.colors.surface.copy(alpha = shimmerAlpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyIcon")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(iconScale)
                .clip(CircleShape)
                .background(VikifyTheme.colors.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = VikifyTheme.colors.textSecondary,
                modifier = Modifier.size(56.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "No results for \"$query\"",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = VikifyTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Check the spelling or try different keywords",
            fontSize = 14.sp,
            color = VikifyTheme.colors.textSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Suggestion chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("The Weeknd", "Taylor Swift", "Drake")) { suggestion ->
                SuggestionChip(
                    suggestion = suggestion,
                    onClick = { /* Apply suggestion */ }
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    suggestion: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VikifyTheme.colors.accent)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = "Try \"$suggestion\"",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITY FUNCTIONS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
): AnnotatedString = buildAnnotatedString {
    // Guard against empty query
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var startIndex = 0
    
    while (startIndex < text.length) {
        val matchIndex = lowerText.indexOf(lowerQuery, startIndex)
        
        if (matchIndex < 0) {
            append(text.substring(startIndex))
            break
        }
        
        // Text before match
        if (matchIndex > startIndex) {
            append(text.substring(startIndex, matchIndex))
        }
        
        // Highlighted match
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(text.substring(matchIndex, matchIndex + query.length))
        }
        
        startIndex = matchIndex + query.length
    }
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
                ?: return@let null
            Color(rgb)
        }
    } catch (e: Exception) {
        null
    }
}
