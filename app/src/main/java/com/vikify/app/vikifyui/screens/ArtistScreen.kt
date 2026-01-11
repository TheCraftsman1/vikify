package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose. foundation.interaction.collectIsPressedAsState
import androidx. compose.foundation.layout.*
import androidx. compose.foundation.lazy.LazyColumn
import androidx.compose. foundation.lazy.LazyListState
import androidx. compose.foundation.lazy.LazyRow
import androidx.compose.foundation. lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation. lazy.rememberLazyListState
import androidx.compose. foundation.shape.CircleShape
import androidx.compose. foundation.shape.RoundedCornerShape
import androidx.compose.material. icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material. icons.outlined.*
import androidx.compose.material3.*
import androidx.compose. material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx. compose.ui. Alignment
import androidx. compose.ui. Modifier
import androidx. compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics. Brush
import androidx. compose.ui.graphics.Color
import androidx.compose.ui. graphics.graphicsLayer
import androidx.compose. ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui. semantics.contentDescription
import androidx. compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text. font.FontWeight
import androidx.compose. ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx. compose.ui.unit.dp
import androidx.compose.ui. unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vikify.app.R
import com.vikify.app.data.ArtistDetails
import com.vikify.app.data.YouTubeArtistRepository
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models. ArtistItem
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

private const val HERO_HEIGHT_DP = 320
private const val HEADER_COLLAPSE_THRESHOLD = 200
private const val MAX_POPULAR_SONGS = 5

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN COMPOSABLE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Artist Details Screen
 *
 * Features:
 * - Collapsing header with parallax effect
 * - Pull-to-refresh
 * - Skeleton loading states
 * - Animated content transitions
 * - Full accessibility support
 *
 * @param artistName Display name of the artist
 * @param artistId Optional YouTube Music artist ID
 * @param onBackClick Navigation callback
 * @param onTrackClick Track selection callback
 * @param onShuffleClick Shuffle play callback
 * @param onArtistClick Related artist navigation callback
 * @param onAlbumClick Album navigation callback
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    artistName: String,
    artistId:  String? = null,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onShuffleClick: () -> Unit,
    onArtistClick: (String, String?) -> Unit = { _, _ -> },
    onAlbumClick: (AlbumItem) -> Unit = {},
    modifier:  Modifier = Modifier
) {
    val isDark = VikifyTheme.isDark
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // ─────────────────────────────────────────────────────────────────────────
    // STATE
    // ─────────────────────────────────────────────────────────────────────────
    
    var artistDetails by remember { mutableStateOf<ArtistDetails? >(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFollowing by remember { mutableStateOf(false) }
    
    // Calculate header collapse progress
    val headerCollapseProgress by remember {
        derivedStateOf {
            val scrollOffset = listState.firstVisibleItemIndex * 100 + 
                listState.firstVisibleItemScrollOffset
            (scrollOffset. toFloat() / HEADER_COLLAPSE_THRESHOLD).coerceIn(0f, 1f)
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // DATA FETCHING
    // ─────────────────────────────────────────────────────────────────────────
    
    suspend fun fetchArtist() {
        val result = if (artistId != null) {
            YouTubeArtistRepository.getArtist(artistId)
        } else {
            YouTubeArtistRepository.searchArtistByName(artistName)
        }
        
        result. fold(
            onSuccess = { details ->
                artistDetails = details
                errorMessage = null
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load artist"
            }
        )
    }
    
    // Initial load
    LaunchedEffect(artistName, artistId) {
        isLoading = true
        fetchArtist()
        isLoading = false
    }
    
    // Pull-to-refresh handler
    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            fetchArtist()
            delay(300) // Minimum refresh indicator time
            isRefreshing = false
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // RENDER
    // ─────────────────────────────────────────────────────────────────────────
    
    Box(modifier = modifier. fillMaxSize()) {
        // Theme Background
        if (isDark) {
            LivingBackground(modifier = Modifier.matchParentSize()) { }
        } else {
            EtherealBackground(modifier = Modifier.matchParentSize()) { }
        }

        // Content with animated transitions
        AnimatedContent(
            targetState = Triple(isLoading, errorMessage, artistDetails),
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                    fadeOut(animationSpec = tween(300))
            },
            label = "artistContentTransition"
        ) { (loading, error, details) ->
            when {
                loading -> {
                    ArtistLoadingSkeleton()
                }
                error != null -> {
                    ArtistErrorState(
                        errorMessage = error,
                        onRetry = { 
                            scope.launch {
                                isLoading = true
                                fetchArtist()
                                isLoading = false
                            }
                        },
                        onBack = onBackClick
                    )
                }
                details != null -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = :: onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ArtistContent(
                            artistDetails = details,
                            listState = listState,
                            isFollowing = isFollowing,
                            onFollowClick = { isFollowing = ! isFollowing },
                            onTrackClick = onTrackClick,
                            onShuffleClick = onShuffleClick,
                            onArtistClick = onArtistClick,
                            onAlbumClick = onAlbumClick
                        )
                    }
                }
            }
        }
        
        // Floating collapsed header (appears on scroll)
        CollapsedHeader(
            artistName = artistDetails?.name ?: artistName,
            collapseProgress = headerCollapseProgress,
            onBackClick = onBackClick,
            onMoreClick = { /* TODO: Show options sheet */ }
        )
        
        // Fixed back button (always visible)
        FloatingBackButton(
            onClick = onBackClick,
            collapseProgress = headerCollapseProgress,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = Spacing.MD, top = Spacing. SM)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// LOADING STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArtistLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition. animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode. Reverse
        ),
        label = "shimmerAlpha"
    )
    
    LazyColumn(
        modifier = Modifier. fillMaxSize(),
        userScrollEnabled = false
    ) {
        // Hero skeleton
        item {
            Spacer(modifier = Modifier.height(60.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.MD)
                    . height(HERO_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
                    .background(LocalVikifyColors. current.surfaceElevated. copy(alpha = shimmerAlpha))
            )
        }
        
        // Title skeleton
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.MD),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SkeletonBox(
                    width = 200.dp,
                    height = 32.dp,
                    shimmerAlpha = shimmerAlpha
                )
                Spacer(modifier = Modifier.height(Spacing.SM))
                SkeletonBox(
                    width = 280.dp,
                    height = 16.dp,
                    shimmerAlpha = shimmerAlpha
                )
            }
        }
        
        // Action buttons skeleton
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.MD, vertical = Spacing. SM),
                horizontalArrangement = Arrangement.spacedBy(Spacing.MD)
            ) {
                SkeletonBox(
                    modifier = Modifier. weight(1f),
                    height = 48.dp,
                    shimmerAlpha = shimmerAlpha,
                    cornerRadius = Sizing. CardRadiusLarge
                )
                SkeletonBox(
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    shimmerAlpha = shimmerAlpha,
                    cornerRadius = Sizing. CardRadiusLarge
                )
            }
        }
        
        // Song list skeletons
        item {
            Spacer(modifier = Modifier.height(Spacing.LG))
            SkeletonBox(
                width = 100.dp,
                height = 24.dp,
                shimmerAlpha = shimmerAlpha,
                modifier = Modifier.padding(horizontal = Spacing.MD)
            )
        }
        
        items(5) {
            SongRowSkeleton(shimmerAlpha = shimmerAlpha)
        }
    }
}

@Composable
private fun SkeletonBox(
    shimmerAlpha: Float,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui. unit.Dp?  = null,
    height: androidx.compose.ui.unit. Dp = 16.dp,
    cornerRadius: androidx. compose.ui.unit.Dp = Sizing.CardRadius
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(LocalVikifyColors. current.surfaceElevated. copy(alpha = shimmerAlpha))
    )
}

@Composable
private fun SongRowSkeleton(shimmerAlpha: Float) {
    Row(
        modifier = Modifier
            . fillMaxWidth()
            .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM)
    ) {
        SkeletonBox(width = 24.dp, height = 16.dp, shimmerAlpha = shimmerAlpha)
        SkeletonBox(
            width = Sizing.ArtworkSmall,
            height = Sizing.ArtworkSmall,
            shimmerAlpha = shimmerAlpha,
            cornerRadius = Sizing. CardRadius
        )
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBox(width = 150.dp, height = 16.dp, shimmerAlpha = shimmerAlpha)
            Spacer(modifier = Modifier.height(4.dp))
            SkeletonBox(width = 100.dp, height = 12.dp, shimmerAlpha = shimmerAlpha)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ERROR STATE
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArtistErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack:  () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Error icon with subtle animation
        val infiniteTransition = rememberInfiniteTransition(label = "errorPulse")
        val iconScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "iconScale"
        )
        
        Icon(
            imageVector = Icons. Outlined.CloudOff,
            contentDescription = null,
            tint = LocalVikifyColors.current.textSecondary,
            modifier = Modifier
                .size(72.dp)
                .scale(iconScale)
        )
        
        Spacer(modifier = Modifier.height(Spacing.LG))
        
        Text(
            text = "Couldn't Load Artist",
            style = MaterialTheme. typography.headlineSmall,
            color = LocalVikifyColors.current.textPrimary,
            textAlign = TextAlign. Center
        )
        
        Spacer(modifier = Modifier. height(Spacing. SM))
        
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalVikifyColors.current. textSecondary,
            textAlign = TextAlign. Center
        )
        
        Spacer(modifier = Modifier.height(Spacing.XL))
        
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.MD)) {
            OutlinedButton(
                onClick = onBack,
                colors = ButtonDefaults. outlinedButtonColors(
                    contentColor = LocalVikifyColors.current. textPrimary
                )
            ) {
                Icon(
                    imageVector = Icons. Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.SM))
                Text("Go Back")
            }
            
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalVikifyColors.current. accent
                )
            ) {
                Icon(
                    imageVector = Icons. Filled. Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(Spacing.SM))
                Text("Retry")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN CONTENT
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArtistContent(
    artistDetails: ArtistDetails,
    listState: LazyListState,
    isFollowing:  Boolean,
    onFollowClick: () -> Unit,
    onTrackClick:  (Track) -> Unit,
    onShuffleClick: () -> Unit,
    onArtistClick: (String, String?) -> Unit,
    onAlbumClick: (AlbumItem) -> Unit
) {
    val density = LocalDensity.current
    
    // Calculate parallax for hero image
    val heroParallax by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset * 0.5f
            } else {
                HERO_HEIGHT_DP * 0.5f
            }
        }
    }
    
    LazyColumn(
        state = listState,
        modifier = Modifier. fillMaxSize(),
        contentPadding = PaddingValues(bottom = 180.dp)
    ) {
        // Spacer for status bar + header
        item(key = "header_spacer") {
            Spacer(modifier = Modifier.height(60.dp))
        }
        
        // Hero image with parallax
        item(key = "hero") {
            ArtistHero(
                imageUrl = artistDetails. thumbnailUrl,
                artistName = artistDetails.name,
                parallaxOffset = heroParallax
            )
        }
        
        // Artist name + info
        item(key = "info") {
            ArtistInfo(
                artistName = artistDetails.name,
                subscriberCount = artistDetails. subscriberCount,
                description = artistDetails.description
            )
        }
        
        // Action buttons
        item(key = "actions") {
            ArtistActions(
                isFollowing = isFollowing,
                onFollowClick = onFollowClick,
                onShuffleClick = onShuffleClick
            )
        }
        
        // Popular songs section
        if (artistDetails.topSongs.isNotEmpty()) {
            item(key = "popular_header") {
                Spacer(modifier = Modifier.height(Spacing.LG))
                SectionHeader(
                    title = "Popular",
                    showSeeAll = artistDetails.topSongs.size > MAX_POPULAR_SONGS,
                    onSeeAllClick = { /* TODO: Navigate to all songs */ }
                )
            }
            
            itemsIndexed(
                items = artistDetails.topSongs.take(MAX_POPULAR_SONGS),
                key = { _, song -> "song_${song. id}" }
            ) { index, song ->
                PopularSongRow(
                    index = index + 1,
                    song = song,
                    onClick = {
                        val track = Track(
                            id = song. id,
                            title = song.title,
                            artist = song.artists.firstOrNull()?.name ?: "Unknown",
                            remoteArtworkUrl = song.thumbnail
                        )
                        onTrackClick(track)
                    },
                    onMoreClick = { /* TODO: Show song options */ }
                )
            }
        }
        
        // Albums section
        if (artistDetails.albums. isNotEmpty()) {
            item(key = "albums_section") {
                Spacer(modifier = Modifier.height(Spacing.XL))
                SectionHeader(
                    title = "Albums",
                    showSeeAll = artistDetails.albums.size > 5,
                    onSeeAllClick = { /* TODO:  Navigate to discography */ }
                )
                Spacer(modifier = Modifier.height(Spacing.MD))
                AlbumsRow(
                    albums = artistDetails.albums,
                    onAlbumClick = onAlbumClick
                )
            }
        }
        
        // Singles & EPs section
        if (artistDetails.singles.isNotEmpty()) {
            item(key = "singles_section") {
                Spacer(modifier = Modifier.height(Spacing.XL))
                SectionHeader(
                    title = "Singles & EPs",
                    showSeeAll = artistDetails. singles.size > 5,
                    onSeeAllClick = { /* TODO:  Navigate to discography */ }
                )
                Spacer(modifier = Modifier. height(Spacing. MD))
                AlbumsRow(
                    albums = artistDetails.singles,
                    onAlbumClick = onAlbumClick
                )
            }
        }
        
        // Similar artists section
        if (artistDetails.relatedArtists. isNotEmpty()) {
            item(key = "similar_section") {
                Spacer(modifier = Modifier.height(Spacing.XL))
                SectionHeader(
                    title = "Fans Also Like",
                    showSeeAll = false,
                    onSeeAllClick = { }
                )
                Spacer(modifier = Modifier.height(Spacing.MD))
                SimilarArtistsRow(
                    artists = artistDetails.relatedArtists,
                    onArtistClick = onArtistClick
                )
            }
        }
        
        // About section
        if (! artistDetails.description.isNullOrBlank()) {
            item(key = "about_section") {
                Spacer(modifier = Modifier.height(Spacing.XL))
                AboutSection(
                    description = artistDetails.description,
                    imageUrl = artistDetails. thumbnailUrl
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HEADER COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CollapsedHeader(
    artistName: String,
    collapseProgress: Float,
    onBackClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val showHeader = collapseProgress > 0.8f
    
    androidx.compose.animation.AnimatedVisibility(
        visible = showHeader,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        Box(
            modifier = Modifier
                . fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LocalVikifyColors. current.background,
                            LocalVikifyColors. current.background. copy(alpha = 0.95f),
                            Color.Transparent
                        )
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = Spacing.MD, vertical = Spacing. SM)
        ) {
            Text(
                text = artistName,
                style = MaterialTheme.typography.titleLarge,
                color = LocalVikifyColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    . padding(horizontal = 56.dp) // Space for back/more buttons
            )
            
            // More button on the right
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    . size(40.dp)
            ) {
                Icon(
                    imageVector = Icons. Filled.MoreVert,
                    contentDescription = "More options",
                    tint = LocalVikifyColors. current.textPrimary
                )
            }
        }
    }
}

@Composable
private fun FloatingBackButton(
    onClick: () -> Unit,
    collapseProgress: Float,
    modifier:  Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "backButtonScale"
    )
    
    // Transition background as header collapses
    val backgroundColor by animateColorAsState(
        targetValue = if (collapseProgress > 0.8f)
            Color.Transparent
        else
            LocalVikifyColors.current.surfaceElevated. copy(alpha = 0.9f),
        label = "backButtonBg"
    )
    
    AnimatedVisibility(
        visible = collapseProgress < 0.8f,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .size(44.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(backgroundColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons. Filled.ArrowBackIosNew,
                contentDescription = "Go back",
                tint = LocalVikifyColors.current. textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// HERO & INFO COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArtistHero(
    imageUrl:  String?,
    artistName:  String,
    parallaxOffset: Float
) {
    Box(
        modifier = Modifier
            . fillMaxWidth()
            .padding(horizontal = Spacing.MD)
            .height(HERO_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
    ) {
        // Image with parallax
        AsyncImage(
            model = imageUrl,
            contentDescription = "$artistName artist image",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = parallaxOffset
                    // Slight scale for parallax depth effect
                    scaleX = 1.1f
                    scaleY = 1.1f
                },
            contentScale = ContentScale. Crop
        )
        
        // Gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment. BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            LocalVikifyColors.current. background. copy(alpha = 0.8f)
                        )
                    )
                )
        )
        
        // Subtle vignette for depth
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black. copy(alpha = 0.2f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun ArtistInfo(
    artistName: String,
    subscriberCount: String?,
    description: String? 
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing. MD, vertical = Spacing. MD)
            .semantics { heading() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = artistName,
            style = MaterialTheme.typography.headlineLarge. copy(
                fontWeight = FontWeight.Bold
            ),
            color = LocalVikifyColors.current.textPrimary,
            textAlign = TextAlign. Center,
            modifier = Modifier.semantics {
                contentDescription = "Artist:  $artistName"
            }
        )
        
        if (! subscriberCount.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.XS))
            Text(
                text = subscriberCount,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalVikifyColors.current. textSecondary
            )
        }
        
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.SM))
            Text(
                text = description. take(120) + if (description.length > 120) "…" else "",
                style = MaterialTheme.typography. bodyMedium,
                color = LocalVikifyColors.current. textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ACTION BUTTONS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArtistActions(
    isFollowing:  Boolean,
    onFollowClick: () -> Unit,
    onShuffleClick: () -> Unit
) {
    Row(
        modifier = Modifier
            . fillMaxWidth()
            .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD)
    ) {
        // Follow/Following button with animated state
        FollowButton(
            isFollowing = isFollowing,
            onClick = onFollowClick,
            modifier = Modifier.weight(1f)
        )
        
        // Shuffle Play button
        ShufflePlayButton(
            onClick = onShuffleClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FollowButton(
    isFollowing: Boolean,
    onClick: () -> Unit,
    modifier:  Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "followScale"
    )
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isFollowing)
            LocalVikifyColors. current.accent.copy(alpha = 0.15f)
        else
            LocalVikifyColors.current. surfaceElevated,
        label = "followBg"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (isFollowing)
            LocalVikifyColors.current.accent
        else
            LocalVikifyColors. current.border,
        label = "followBorder"
    )
    
    Row(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
            .border(
                width = if (isFollowing) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(Sizing.CardRadiusLarge)
            )
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = isFollowing,
            transitionSpec = {
                scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut()
            },
            label = "followIcon"
        ) { following ->
            Icon(
                imageVector = if (following) Icons.Filled.Check else Icons.Outlined.PersonAdd,
                contentDescription = null,
                tint = if (following)
                    LocalVikifyColors.current.accent
                else
                    LocalVikifyColors.current.textPrimary,
                modifier = Modifier. size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(Spacing.SM))
        
        Text(
            text = if (isFollowing) "Following" else "Follow",
            style = MaterialTheme.typography. titleSmall,
            color = if (isFollowing)
                LocalVikifyColors.current. accent
            else
                LocalVikifyColors.current.textPrimary
        )
    }
}

@Composable
private fun ShufflePlayButton(
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "shuffleScale"
    )
    
    Row(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
            .background(LocalVikifyColors. current.accent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Shuffle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier. size(20.dp)
        )
        Spacer(modifier = Modifier.width(Spacing.SM))
        Text(
            text = "Shuffle Play",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SECTION COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionHeader(
    title:  String,
    showSeeAll: Boolean,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            . fillMaxWidth()
            .padding(horizontal = Spacing.MD)
            .semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme. typography.titleLarge. copy(
                fontWeight = FontWeight. Bold
            ),
            color = LocalVikifyColors.current.textPrimary
        )
        
        if (showSeeAll) {
            TextButton(
                onClick = onSeeAllClick,
                colors = ButtonDefaults. textButtonColors(
                    contentColor = LocalVikifyColors.current. accent
                )
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography. labelLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons. Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PopularSongRow(
    index: Int,
    song:  SongItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed)
            LocalVikifyColors.current. surfaceElevated. copy(alpha = 0.5f)
        else
            Color.Transparent,
        label = "songRowBg"
    )
    
    Row(
        modifier = Modifier
            . fillMaxWidth()
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = Spacing.MD, vertical = Spacing. SM)
            .semantics {
                contentDescription = "Song $index:  ${song.title} by ${song.artists.firstOrNull()?.name ?: "Unknown"}"
            },
        horizontalArrangement = Arrangement. spacedBy(Spacing.SM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Index number
        Text(
            text = index. toString(),
            style = MaterialTheme. typography.bodyMedium. copy(
                fontWeight = FontWeight. Medium
            ),
            color = LocalVikifyColors.current. textSecondary,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center
        )
        
        // Album art
        Box(
            modifier = Modifier
                . size(Sizing.ArtworkSmall)
                .clip(RoundedCornerShape(Sizing. CardRadius))
                .background(LocalVikifyColors. current.surfaceElevated)
        ) {
            var imageState by remember { mutableStateOf<AsyncImagePainter. State?>(null) }
            
            AsyncImage(
                model = song.thumbnail,
                contentDescription = null,
                modifier = Modifier. fillMaxSize(),
                contentScale = ContentScale. Crop,
                onState = { imageState = it }
            )
            
            // Loading placeholder
            if (imageState is AsyncImagePainter. State.Loading) {
                Box(
                    modifier = Modifier
                        . fillMaxSize()
                        .background(LocalVikifyColors. current.surfaceElevated)
                )
            }
        }
        
        // Song info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song. title,
                style = MaterialTheme. typography.titleSmall,
                color = LocalVikifyColors. current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artists. joinToString(", ") { it.name },
                style = MaterialTheme.typography. bodySmall,
                color = LocalVikifyColors.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // More options button
        IconButton(
            onClick = onMoreClick,
            modifier = Modifier. size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More options for ${song.title}",
                tint = LocalVikifyColors.current.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AlbumsRow(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.MD),
        horizontalArrangement = Arrangement.spacedBy(Spacing. MD)
    ) {
        items(
            items = albums,
            key = { it.id }
        ) { album ->
            AlbumCard(
                album = album,
                onClick = { onAlbumClick(album) }
            )
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "albumScale"
    )
    
    Column(
        modifier = Modifier
            . width(150.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Album:  ${album.title}, ${album.year ?: ""}"
            }
    ) {
        // Album art with shadow
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
                .background(LocalVikifyColors. current.surfaceElevated)
        ) {
            AsyncImage(
                model = album.thumbnail,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Play button overlay on hover/press
            androidx.compose.animation.AnimatedVisibility(
                visible = isPressed,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled. PlayCircleFilled,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(Spacing.SM))
        
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = LocalVikifyColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow. Ellipsis
        )
        
        Text(
            text = buildString {
                album.year?.let { append(it) }
                if (album.year != null && album.artists?. isNotEmpty() == true) append(" • ")
                album.artists?.firstOrNull()?.let { append(it.name) }
            }. ifEmpty { "Album" },
            style = MaterialTheme. typography.bodySmall,
            color = LocalVikifyColors.current.textSecondary,
            maxLines = 1,
            overflow = TextOverflow. Ellipsis
        )
    }
}

@Composable
private fun SimilarArtistsRow(
    artists: List<ArtistItem>,
    onArtistClick: (String, String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.MD),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD)
    ) {
        items(
            items = artists,
            key = { it.id }
        ) { artist ->
            SimilarArtistCard(
                artist = artist,
                onClick = { onArtistClick(artist.title, artist.id) }
            )
        }
    }
}

@Composable
private fun SimilarArtistCard(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "artistScale"
    )
    
    Column(
        modifier = Modifier
            .width(100.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular artist image
        Box(
            modifier = Modifier
                . size(100.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            LocalVikifyColors. current.accent. copy(alpha = 0.5f),
                            LocalVikifyColors.current.accent.copy(alpha = 0.2f)
                        )
                    ),
                    shape = CircleShape
                )
                .background(LocalVikifyColors.current.surfaceElevated)
        ) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(Spacing.SM))
        
        Text(
            text = artist.title,
            style = MaterialTheme.typography.labelLarge,
            color = LocalVikifyColors.current.textPrimary,
            maxLines = 1,
            overflow = TextOverflow. Ellipsis,
            textAlign = TextAlign. Center
        )
    }
}

@Composable
private fun AboutSection(
    description: String?,
    imageUrl:  String?
) {
    if (description. isNullOrBlank()) return
    
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier. padding(horizontal = Spacing.MD)
    ) {
        Text(
            text = "About",
            style = MaterialTheme. typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = LocalVikifyColors.current.textPrimary,
            modifier = Modifier.semantics { heading() }
        )
        
        Spacer(modifier = Modifier.height(Spacing.MD))
        
        // Optional background image for about section
        Box(
            modifier = Modifier
                . fillMaxWidth()
                .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
                .background(LocalVikifyColors. current.surfaceElevated)
        ) {
            // Blurred background image
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        . height(if (expanded) 200.dp else 120.dp)
                        .blur(20. dp)
                        .graphicsLayer { alpha = 0.3f },
                    contentScale = ContentScale.Crop
                )
            }
            
            Column(
                modifier = Modifier.padding(Spacing.MD)
            ) {
                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        expandVertically() + fadeIn() togetherWith
                            shrinkVertically() + fadeOut()
                    },
                    label = "aboutExpand"
                ) { isExpanded ->
                    Text(
                        text = description,
                        style = MaterialTheme. typography.bodyMedium,
                        color = LocalVikifyColors.current. textSecondary,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow. Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(Spacing.SM))
                
                TextButton(
                    onClick = { expanded = !expanded },
                    colors = ButtonDefaults. textButtonColors(
                        contentColor = LocalVikifyColors.current.accent
                    )
                ) {
                    Text(
                        text = if (expanded) "Show Less" else "Read More",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        imageVector = if (expanded)
                            Icons. Filled.KeyboardArrowUp
                        else
                            Icons. Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}