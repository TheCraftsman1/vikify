package com.vikify.app.vikifyui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.vikify.app.vikifyui.data.SearchViewModel
import com.vikify.app.vikifyui.data.SearchSource
import com.vikify.app.vikifyui.data.UnifiedSearchResult
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.data.Artist
import com.vikify.app.vikifyui.data.Category
import com.vikify.app.vikifyui.data.MockData
import com.vikify.app.vikifyui.theme.LivingBackground
import com.vikify.app.vikifyui.theme.*
import com.zionhuang.innertube.models.AlbumItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================================
// PREMIUM SEARCH SCREEN - NEXT-GEN DISCOVERY INTERFACE
// ============================================================================

@Composable
fun SearchScreen(
    onCategoryClick: (Category) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlaylistClick: (String, com.vikify.app.spotify.SpotifyPlaylist?) -> Unit = { _, _ -> },
    spotifyRepository: com.vikify.app.spotify.SpotifyRepository? = null,
    initialQuery: String? = null, // Pre-filled search query (e.g., from mood click)
    onInitialQueryConsumed: () -> Unit = {}, // Clear initial query after consuming
    modifier: Modifier = Modifier,
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Voice Search Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { spokenText ->
                searchViewModel.updateQuery(spokenText)
            }
        }
    }
    
    // Theme-aware colors
    val isDark = VikifyTheme.isDark
    val backgroundColor = if (isDark) DarkColors.Background else LightColors.Background
    val surfaceColor = if (isDark) DarkColors.Surface else LightColors.Surface
    val textPrimary = if (isDark) DarkColors.TextPrimary else LightColors.TextPrimary
    val textSecondary = if (isDark) DarkColors.TextSecondary else LightColors.TextSecondary
    val accentColor = if (isDark) DarkColors.Accent else LightColors.Accent
    
    // ViewModel-driven state
    val searchQuery by searchViewModel.query.collectAsState()
    val searchSource by searchViewModel.searchSource.collectAsState()
    val unifiedResults by searchViewModel.unifiedResults.collectAsState()
    val isSearching by searchViewModel.isSearching.collectAsState()
    
    // Initialize Spotify repository
    LaunchedEffect(spotifyRepository) {
        searchViewModel.setSpotifyRepository(spotifyRepository)
    }
    
    // Local query for immediate UI feedback
    var localQuery by remember { mutableStateOf(searchQuery) }
    var isSearchFocused by remember { mutableStateOf(false) }
    
    // Handle initial query from external navigation (e.g., mood click)
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrEmpty() && localQuery != initialQuery) {
            localQuery = initialQuery
            searchViewModel.updateQuery(initialQuery)
            onInitialQueryConsumed()
        }
    }
    
    // FIX: Prevent infinite loop with distinct check
    // Only update VM when local actually changes to something different
    LaunchedEffect(localQuery) {
        if (searchQuery != localQuery) {
            searchViewModel.updateQuery(localQuery)
        }
    }
    
    // Only update local if VM changes externally (e.g., category search)
    // This also has the distinct check to prevent loops
    LaunchedEffect(searchQuery) {
        if (searchQuery != localQuery) {
            localQuery = searchQuery
        }
    }
    
    // Blur micro-interaction: mosaic blurs when search focused (context preserved)
    val blurRadius by animateDpAsState(
        targetValue = if (isSearchFocused && localQuery.isEmpty()) 12.dp else 0.dp,
        animationSpec = tween(300),
        label = "blurEffect"
    )

    // Theme-aware Logic
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (VikifyTheme.isDark) {
            com.vikify.app.vikifyui.theme.LivingBackground(modifier = Modifier.matchParentSize()) { }
        } else {
            com.vikify.app.vikifyui.theme.EtherealBackground(modifier = Modifier.matchParentSize()) { }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // ═══════════════════════════════════════════════════════════════
            // 1. LIQUID SEARCH HEADER
            // ═══════════════════════════════════════════════════════════════
            LiquidSearchHeader(
                query = localQuery,
                onQueryChange = { localQuery = it },
                onClear = { 
                    localQuery = ""
                    try { keyboardController?.hide() } catch (e: Exception) { /* Safe ignore */ }
                    focusManager.clearFocus()
                },
                onVoiceClick = {
                    try {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Sing or say lyrics to find your song...")
                        }
                        voiceLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Voice search not available", Toast.LENGTH_SHORT).show()
                    }
                },
                isFocused = isSearchFocused,
                onFocusChange = { isSearchFocused = it },
                focusRequester = focusRequester,
                isDark = isDark,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                surfaceColor = surfaceColor,
                accentColor = accentColor
            )
            
            // ═══════════════════════════════════════════════════════════════
            // 2. CONTENT AREA
            // ═══════════════════════════════════════════════════════════════
            
            // Filter state for search results
            var selectedFilter by remember { mutableStateOf("All") }
            val filterOptions = listOf("All", "Songs", "Artists", "Playlists")
            
            Crossfade(
                targetState = localQuery.isEmpty(),
                label = "searchContent",
                animationSpec = tween(200)
            ) { isEmptyQuery ->
                if (isEmptyQuery) {
                    // BROWSE STATE: Staggered Mosaic Grid with blur effect
                    MosaicBrowseGrid(
                        onCategoryClick = { category ->
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            searchViewModel.searchByCategory(category.name)
                        },
                        isDark = isDark,
                        textPrimary = textPrimary,
                        blurRadius = blurRadius
                    )
                } else {
                    // SEARCH RESULTS STATE with Filter Chips
                    Column(modifier = Modifier.fillMaxSize()) {
                        // ═══════════════════════════════════════════════════════════════
                        // SPOTIFY-STYLE FILTER CHIPS ROW
                        // ═══════════════════════════════════════════════════════════════
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            items(filterOptions) { filter ->
                                val isSelected = selectedFilter == filter
                                
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { 
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        selectedFilter = filter 
                                    },
                                    label = {
                                        Text(
                                            text = filter,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8E8E8),
                                        labelColor = textSecondary,
                                        selectedContainerColor = accentColor,
                                        selectedLabelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = Color.Transparent,
                                        selectedBorderColor = Color.Transparent,
                                        enabled = true,
                                        selected = isSelected
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                        
                        // Filter results based on selected chip
                        val filteredResults = when (selectedFilter) {
                            "Songs" -> unifiedResults.filter { it.type == "track" || it.type == "song" }
                            "Artists" -> unifiedResults.filter { it.type == "artist" }
                            "Playlists" -> unifiedResults.filter { it.type == "playlist" }
                            else -> unifiedResults // "All"
                        }
                        
                        SpotlightResultsList(
                            results = filteredResults,
                            isSearching = isSearching,
                            query = localQuery,
                            isDark = isDark,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            surfaceColor = surfaceColor,
                            accentColor = accentColor,
                            onResultClick = { result ->
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                handleResultClick(result, onTrackClick, onAlbumClick, onArtistClick, onPlaylistClick)
                            }
                        )
                    }
                }
            }
        }
        
        // Clickable overlay to clear focus when blur is active
        if (blurRadius > 0.dp) {
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

// ============================================================================
// LIQUID SEARCH HEADER
// ============================================================================

@Composable
private fun LiquidSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onVoiceClick: () -> Unit,
    isFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    surfaceColor: Color,
    accentColor: Color
) {
    val view = LocalView.current
    
    // Animation for header transformation
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
        // Large Title (Collapses when typing)
        AnimatedVisibility(
            visible = query.isEmpty() && !isFocused,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp, top = 12.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.vikify.app.R.drawable.vikify_logo),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = "Search",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
        }

        // Glass Pill Search Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Main Search Input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .shadow(
                        elevation = if (isFocused) 12.dp else 6.dp,
                        shape = RoundedCornerShape(26.dp),
                        spotColor = if (isDark) DarkColors.Accent.copy(alpha = 0.2f) else CardShadow
                    )
                    .background(surfaceColor, RoundedCornerShape(26.dp))
                    .border(
                        width = if (isFocused) 1.5.dp else 0.dp,
                        color = if (isFocused) accentColor.copy(alpha = 0.5f) else Color.Transparent,
                        shape = RoundedCornerShape(26.dp)
                    )
                    .clip(RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    
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
                            color = textPrimary,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(accentColor),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    "What's the vibe today?",
                                    color = textSecondary,
                                    fontSize = 17.sp
                                )
                            }
                            innerTextField()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { 
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        })
                    )
                    
                    // Mic & Camera Icons (Idle state)
                    AnimatedVisibility(
                        visible = query.isEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = "Voice Search",
                                tint = textSecondary,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        onVoiceClick()
                                    }
                            )
                        }
                    }
                    
                    // Clear Button (Active state)
                    AnimatedVisibility(
                        visible = query.isNotEmpty(),
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(textSecondary.copy(alpha = 0.2f), CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { 
                                    try {
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        onQueryChange("") 
                                    } catch (e: Exception) {
                                        // Safe ignore
                                        onQueryChange("")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = textPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Cancel Button
            AnimatedVisibility(
                visible = query.isNotEmpty() || isFocused,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it })
            ) {
                Text(
                    text = "Cancel",
                    color = accentColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { 
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onClear() 
                    }
                )
            }
        }
    }
}

// ============================================================================
// DISCOVERY PORTAL: STAGGERED GENRE MOSAIC
// Pinterest-style layout with "louder" cards for priority genres
// ============================================================================

@Composable
private fun MosaicBrowseGrid(
    onCategoryClick: (Category) -> Unit,
    isDark: Boolean,
    textPrimary: Color,
    blurRadius: Dp = 0.dp
) {
    val categories = remember { MockData.browseCategories }
    
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = Modifier
            .fillMaxSize()
            .blur(blurRadius)
    ) {
        // Section Title (Full width span)
        item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                text = "Explore Your Vibe",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        
        // Staggered Category Cards
        items(categories, key = { it.id }) { category ->
            LivingCategoryCard(
                category = category,
                isDark = isDark,
                onClick = { onCategoryClick(category) }
            )
        }
        
        // Bottom spacing for mini player
        item(span = StaggeredGridItemSpan.FullLine) {
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ============================================================================
// LIVING CATEGORY CARD: Animated gradient with priority sizing
// ============================================================================

@Composable
private fun LivingCategoryCard(
    category: Category,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Dynamic height based on priority
    val height = when {
        category.isPriority -> 160.dp  // "Louder" genres get bigger cards
        category.isNew -> 130.dp       // Trending genres are medium
        else -> 100.dp                  // Standard genres
    }
    
    // Category-specific colors
    val categoryColor = Color(category.color.toInt())
    
    // ═══════════════════════════════════════════════════════════════════
    // LIVING GRADIENT: Animated color shift
    // ═══════════════════════════════════════════════════════════════════
    val infiniteTransition = rememberInfiniteTransition(label = "livingGradient")
    val gradientPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientPhase"
    )
    
    // Secondary color for gradient (lighter variation)
    val secondaryColor = categoryColor.copy(
        red = (categoryColor.red + 0.15f).coerceAtMost(1f),
        green = (categoryColor.green + 0.1f).coerceAtMost(1f),
        blue = (categoryColor.blue + 0.2f).coerceAtMost(1f)
    )
    
    val animatedGradient = Brush.linearGradient(
        colors = listOf(categoryColor, secondaryColor, categoryColor),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(
            x = 300f + (100f * gradientPhase),
            y = 300f + (100f * (1 - gradientPhase))
        )
    )
    
    // Category icon emoji
    val categoryIcon = when (category.name.lowercase()) {
        "pop" -> "🎤"
        "hip-hop" -> "🎧"
        "rock" -> "🎸"
        "indie" -> "🌿"
        "r&b" -> "💜"
        "electronic" -> "🎹"
        "podcasts" -> "🎙️"
        "new releases" -> "✨"
        "charts" -> "📊"
        "moods" -> "🌙"
        else -> "🎵"
    }
    
    Box(
        modifier = modifier
            .height(height)
            .fillMaxWidth()
            .shadow(
                elevation = if (category.isPriority) 12.dp else 6.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = categoryColor.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(animatedGradient)
            .clickable(onClick = onClick)
    ) {
        // "NEW" badge for trending genres
        if (category.isNew) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NEW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = categoryColor,
                    letterSpacing = 0.5.sp
                )
            }
        }
        
        // Genre name (Top-Left)
        Text(
            text = category.name,
            fontSize = if (category.isPriority) 20.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
        )
        
        // Rotated icon (Bottom-Right, "tucked" into corner)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(if (category.isPriority) 72.dp else 52.dp)
                .offset(x = 10.dp, y = 10.dp)
                .graphicsLayer {
                    rotationZ = 25f
                    shadowElevation = 8f
                }
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = categoryIcon,
                fontSize = if (category.isPriority) 36.sp else 28.sp
            )
        }
    }
}

// ============================================================================
// SPOTLIGHT RESULTS LIST
// ============================================================================

@Composable
private fun SpotlightResultsList(
    results: List<UnifiedSearchResult>,
    isSearching: Boolean,
    query: String,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    surfaceColor: Color,
    accentColor: Color,
    onResultClick: (UnifiedSearchResult) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Loading State
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }
        // Empty State
        else if (results.isEmpty() && query.isNotEmpty()) {
            item {
                EmptySearchState(
                    query = query,
                    isDark = isDark,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentColor = accentColor
                )
            }
        }
        // Results
        else {
            // TOP RESULT SPOTLIGHT (First item gets special treatment)
            results.firstOrNull()?.let { topResult ->
                item {
                    SpotlightTopResultCard(
                        result = topResult,
                        query = query,
                        isDark = isDark,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        surfaceColor = surfaceColor,
                        accentColor = accentColor,
                        onClick = { onResultClick(topResult) }
                    )
                }
                
                // "Songs" section header
                if (results.size > 1) {
                    item {
                        Text(
                            text = "Results",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                }
            }
            
            // Remaining results
            items(results.drop(1), key = { it.id }) { result ->
                SearchResultRow(
                    result = result,
                    query = query,
                    isDark = isDark,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    surfaceColor = surfaceColor,
                    onClick = { onResultClick(result) }
                )
            }
        }
        
        // Bottom spacing for mini player
        item { Spacer(Modifier.height(100.dp)) }
    }
}

// ============================================================================
// SPOTLIGHT TOP RESULT CARD
// ============================================================================

@Composable
private fun SpotlightTopResultCard(
    result: UnifiedSearchResult,
    query: String,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    surfaceColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    var glowColor by remember { mutableStateOf(accentColor) }
    
    // Extract dominant color from artwork
    LaunchedEffect(result.imageUrl) {
        if (result.imageUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(result.imageUrl)
                        .allowHardware(false)
                        .build()
                    val imageResult = coil3.ImageLoader(context).execute(request)
                    val bitmap = (imageResult.image as? coil3.BitmapImage)?.bitmap
                    if (bitmap != null) {
                        Palette.from(bitmap).generate { palette ->
                            val vibrant = palette?.vibrantSwatch?.rgb
                            val dominant = palette?.dominantSwatch?.rgb
                            glowColor = Color(vibrant ?: dominant ?: accentColor.toArgb())
                        }
                    }
                } catch (e: Exception) { /* Keep default */ }
            }
        }
    }
    
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
                        glowColor.copy(alpha = if (isDark) 0.25f else 0.15f),
                        surfaceColor
                    )
                )
            )
            .border(
                width = 1.dp,
                color = glowColor.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Large Artwork - Circle for Artists, Square for Songs
            val isArtist = result.type == "artist"
            AsyncImage(
                model = result.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(100.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(12.dp))
            )
            
            Spacer(Modifier.width(16.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TOP RESULT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = glowColor,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                // Highlighted title
                Text(
                    text = buildHighlightedText(result.headline, query, glowColor),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                
                // Subheadline + Type Badge Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.subheadline,
                        fontSize = 14.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    
                    // Type Badge (Artist, Song, Playlist)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.1f) 
                                else Color.Black.copy(alpha = 0.08f)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (result.type) {
                                "artist" -> "ARTIST"
                                "track", "song" -> "SONG"
                                "playlist" -> "PLAYLIST"
                                "album" -> "ALBUM"
                                else -> result.type.uppercase()
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            
            // Floating Play FAB
            FloatingActionButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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

// ============================================================================
// SEARCH RESULT ROW
// ============================================================================

@Composable
private fun SearchResultRow(
    result: UnifiedSearchResult,
    query: String,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    surfaceColor: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surfaceColor)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Artwork
        AsyncImage(
            model = result.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(if (result.isCircular) CircleShape else RoundedCornerShape(8.dp))
        )
        
        Spacer(Modifier.width(12.dp))
        
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildHighlightedText(result.headline, query, if (isDark) DarkColors.Accent else LightColors.Accent),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = result.subheadline,
                fontSize = 14.sp,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // More button
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = "More",
            tint = textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ============================================================================
// EMPTY SEARCH STATE
// ============================================================================

@Composable
private fun EmptySearchState(
    query: String,
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Lost astronaut illustration (placeholder icon)
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    if (isDark) DarkColors.SurfaceVariant else LightColors.SurfaceVariant,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.SearchOff,
                contentDescription = null,
                tint = textSecondary,
                modifier = Modifier.size(60.dp)
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "No results for \"$query\"",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Check the spelling or try a different search",
            fontSize = 14.sp,
            color = textSecondary
        )
        
        Spacer(Modifier.height(24.dp))
        
        // Suggestion button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(accentColor)
                .clickable { /* Try suggestion */ }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Try \"The Weeknd\"",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ============================================================================
// HELPER: Highlight matching text
// ============================================================================

@Composable
private fun buildHighlightedText(
    text: String,
    query: String,
    highlightColor: Color
) = buildAnnotatedString {
    // ✅ CRITICAL FIX: Guard against empty query to prevent infinite loop
    // String.indexOf("") always returns startIndex, causing while(true) to never end
    if (query.isBlank()) {
        append(text)
        return@buildAnnotatedString
    }
    
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var startIndex = 0
    
    while (true) {
        val index = lowerText.indexOf(lowerQuery, startIndex)
        if (index < 0) {
            append(text.substring(startIndex))
            break
        }
        
        // Append text before match
        append(text.substring(startIndex, index))
        
        // Append highlighted match
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(text.substring(index, index + query.length))
        }
        
        startIndex = index + query.length
    }
}

// ============================================================================
// RESULT CLICK HANDLER
// ============================================================================

private fun handleResultClick(
    result: UnifiedSearchResult,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (String, com.vikify.app.spotify.SpotifyPlaylist?) -> Unit
) {
    when (result) {
        is UnifiedSearchResult.Song -> {
            result.originalItem?.let { songItem ->
                onTrackClick(Track(
                    id = songItem.id,
                    title = songItem.title,
                    artist = songItem.artists.firstOrNull()?.name ?: "Unknown",
                    remoteArtworkUrl = songItem.thumbnail
                ))
            }
        }
        is UnifiedSearchResult.Album -> {
            result.originalItem?.let { onAlbumClick(it) }
        }
        is UnifiedSearchResult.Artist -> {
            onArtistClick(Artist(id = result.id, name = result.headline, remoteArtworkUrl = result.imageUrl))
        }
        is UnifiedSearchResult.Playlist -> {
            onPlaylistClick(result.id, result.spotifyPlaylist)
        }
    }
}
