package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.vikify.app.vikifyui.components.*
import com.vikify.app.viewmodels.YouTubeBrowseViewModel
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.vikify.app.vikifyui.theme.VikifyTheme
import com.vikify.app.vikifyui.theme.DarkColors
import com.vikify.app.vikifyui.theme.LightColors
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun BrowseScreen(
    browseId: String,
    params: String? = null,
    initialTitle: String? = null,
    gradientColor: Int? = null, // Passed as int color
    onBackClick: () -> Unit,
    onTrackClick: (com.vikify.app.vikifyui.data.Track) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: YouTubeBrowseViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val result by viewModel.result.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Theme colors
    val isDark = VikifyTheme.isDark
    val backgroundColor = if (isDark) DarkColors.Background else LightColors.Background
    val surfaceColor = if (isDark) DarkColors.Surface else LightColors.Surface
    val textPrimary = if (isDark) DarkColors.TextPrimary else LightColors.TextPrimary
    val textSecondary = if (isDark) DarkColors.TextSecondary else LightColors.TextSecondary
    
    // Header Color
    val headerColor = remember(gradientColor) {
        if (gradientColor != null) Color(gradientColor) else Color(0xFF6366F1) // Default Indigo
    }

    LaunchedEffect(browseId, params) {
        viewModel.load(browseId, params)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // CONTENT
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp) // Space for player
        ) {
            // HEADER SECTION
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    // Gradient Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        headerColor.copy(alpha = 0.6f),
                                        headerColor.copy(alpha = 0.3f),
                                        backgroundColor
                                    )
                                )
                            )
                    )
                    
                    // Title
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Browse",
                            color = textPrimary.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = result?.title ?: initialTitle ?: "Loading...",
                            color = textPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 48.sp
                        )
                    }
                }
            }

            // SECTIONS
            result?.items?.forEach { section ->
                item {
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                        section.title?.let { title ->
                            Text(
                                text = title,
                                color = textPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }
                        
                        // Check content type for this section
                        val isSongSection = section.items.all { it is SongItem }
                        
                        if (isSongSection) {
                            // Render Vertical List for Songs (or horizontal?)
                            // Usually mixed content is horizontal rows
                            // Let's use Horizontal mostly, unless it explicitly looks like a track list
                            // But usually browse results are shelves.
                            
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(section.items) { item ->
                                    when (item) {
                                        is SongItem -> {
                                            CompactSongCard(
                                                title = item.title,
                                                artist = item.artists.firstOrNull()?.name ?: "",
                                                imageUrl = item.thumbnail,
                                                isPlaying = false,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    // Convert to App Track model
                                                    val track = com.vikify.app.vikifyui.data.Track(
                                                        id = item.id,
                                                        title = item.title,
                                                        artist = item.artists.firstOrNull()?.name ?: "Unknown",
                                                        remoteArtworkUrl = item.thumbnail,
                                                        duration = item.duration?.toLong() ?: 0L,
                                                        originalBackendRef = item
                                                    )
                                                    onTrackClick(track)
                                                }
                                            )
                                        }
                                        // Other items inside song section (rare)
                                        else -> {}
                                    }
                                }
                            }
                        } else {
                            // Albums/Playlists/Artists -> Large Square Cards or Circles
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(section.items) { item ->
                                    when (item) {
                                        is AlbumItem -> {
                                            LargeSquareCard(
                                                title = item.title,
                                                subtitle = item.artists?.firstOrNull()?.name ?: "Album",
                                                imageUrl = item.thumbnail,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    onAlbumClick(item.browseId)
                                                }
                                            )
                                        }
                                        is PlaylistItem -> {
                                            LargeSquareCard(
                                                title = item.title,
                                                subtitle = item.author?.name ?: "Playlist",
                                                imageUrl = item.thumbnail,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    onPlaylistClick(item.id)
                                                }
                                            )
                                        }
                                        is ArtistItem -> {
                                            CircleArtistCard(
                                                name = item.title,
                                                imageUrl = item.thumbnail,
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    onArtistClick(item.id)
                                                }
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // BACK BUTTON
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        
        // LOADING STATE
        if (isLoading && result == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = headerColor)
            }
        }
        
        // EMPTY STATE
        if (!isLoading && result != null && result?.items.isNullOrEmpty()) {
             Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                         imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = textPrimary.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp)
                    )
                    Text(
                        text = "No content found",
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Try a different category",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CompactSongCard(
    title: String,
    artist: String,
    imageUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .background(Color.LightGray)
        ) {
            coil3.compose.AsyncImage(
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
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = artist,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LargeSquareCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    VikifyGlassCard(
        modifier = Modifier
            .width(176.dp)
            .clickable(onClick = onClick),
        contentPadding = 12.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .shadow(8.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(com.vikify.app.vikifyui.theme.VikifyTheme.colors.surface)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = com.vikify.app.vikifyui.theme.VikifyTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = com.vikify.app.vikifyui.theme.VikifyTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CircleArtistCard(
    name: String,
    imageUrl: String?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.LightGray)
        ) {
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
