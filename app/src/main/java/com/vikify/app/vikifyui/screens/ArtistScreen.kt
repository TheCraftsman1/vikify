package com.vikify.app.vikifyui.screens

import com.vikify.app.R
import com.vikify.app.data.ArtistDetails
import com.vikify.app.data.YouTubeArtistRepository

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.launch

/**
 * Artist Details Screen (YouTube-Only Data Source)
 * 
 * Fetches real data from YouTube Music via InnerTube:
 * - Artist info, image, description
 * - Top songs
 * - Albums & Singles
 * - Related Artists ("Fans also like")
 */

@Composable
fun ArtistScreen(
    artistName: String,
    artistId: String? = null, // Optional - if null, will search by name
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onShuffleClick: () -> Unit,
    onArtistClick: (String) -> Unit = {}, // Navigate to another artist
    modifier: Modifier = Modifier
) {
    val isDark = VikifyTheme.isDark
    val scope = rememberCoroutineScope()
    
    // State for artist data
    var artistDetails by remember { mutableStateOf<ArtistDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Fetch artist data
    LaunchedEffect(artistName, artistId) {
        isLoading = true
        errorMessage = null
        
        val result = if (artistId != null) {
            YouTubeArtistRepository.getArtist(artistId)
        } else {
            YouTubeArtistRepository.searchArtistByName(artistName)
        }
        
        result.fold(
            onSuccess = { details ->
                artistDetails = details
                isLoading = false
            },
            onFailure = { error ->
                errorMessage = error.message ?: "Failed to load artist"
                isLoading = false
            }
        )
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Theme Backgrounds
        if (isDark) {
            LivingBackground(modifier = Modifier.matchParentSize()) { }
        } else {
            EtherealBackground(modifier = Modifier.matchParentSize()) { }
        }

        when {
            isLoading -> {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = LocalVikifyColors.current.accent)
                        Spacer(modifier = Modifier.height(Spacing.MD))
                        Text(
                            text = "Loading $artistName...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalVikifyColors.current.textSecondary
                        )
                    }
                }
            }
            errorMessage != null -> {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.Error,
                            contentDescription = "Error",
                            tint = LocalVikifyColors.current.textSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.MD))
                        Text(
                            text = errorMessage ?: "Error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalVikifyColors.current.textSecondary
                        )
                        Spacer(modifier = Modifier.height(Spacing.MD))
                        TextButton(onClick = onBackClick) {
                            Text("Go Back")
                        }
                    }
                }
            }
            artistDetails != null -> {
                // Content
                ArtistContent(
                    artistDetails = artistDetails!!,
                    onBackClick = onBackClick,
                    onTrackClick = onTrackClick,
                    onShuffleClick = onShuffleClick,
                    onArtistClick = onArtistClick
                )
            }
        }
    }
}

@Composable
private fun ArtistContent(
    artistDetails: ArtistDetails,
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onShuffleClick: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 180.dp)
    ) {
        // Header with back button
        item {
            ArtistHeader(
                artistName = artistDetails.name,
                onBackClick = onBackClick
            )
        }
        
        // Hero image
        item {
            ArtistHero(imageUrl = artistDetails.thumbnailUrl)
        }
        
        // Artist name + description
        item {
            ArtistInfo(
                artistName = artistDetails.name,
                description = artistDetails.description
            )
        }
        
        // Action buttons
        item {
            ArtistActions(onShuffleClick = onShuffleClick)
        }
        
        // Popular songs
        if (artistDetails.topSongs.isNotEmpty()) {
            item {
                SectionHeader(title = "Popular", showSeeAll = false)
            }
            
            itemsIndexed(artistDetails.topSongs.take(5)) { index, song ->
                PopularSongRow(
                    index = index + 1,
                    song = song,
                    onClick = {
                        // Convert SongItem to Track
                        val track = Track(
                            id = song.id,
                            title = song.title,
                            artist = song.artists.firstOrNull()?.name ?: "Unknown",
                            remoteArtworkUrl = song.thumbnail
                        )
                        onTrackClick(track)
                    }
                )
            }
        }
        
        // Albums
        if (artistDetails.albums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(Spacing.LG))
                SectionHeader(title = "Albums", showSeeAll = false)
                Spacer(modifier = Modifier.height(Spacing.SM))
                AlbumsRow(albums = artistDetails.albums)
            }
        }
        
        // Singles
        if (artistDetails.singles.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(Spacing.LG))
                SectionHeader(title = "Singles & EPs", showSeeAll = false)
                Spacer(modifier = Modifier.height(Spacing.SM))
                AlbumsRow(albums = artistDetails.singles)
            }
        }
        
        // Similar artists
        if (artistDetails.relatedArtists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(Spacing.LG))
                SectionHeader(title = "Fans Also Like", showSeeAll = false)
                Spacer(modifier = Modifier.height(Spacing.SM))
                SimilarArtistsRow(
                    artists = artistDetails.relatedArtists,
                    onArtistClick = onArtistClick
                )
            }
        }
        
        // About section
        if (!artistDetails.description.isNullOrBlank()) {
            item {
                Spacer(modifier = Modifier.height(Spacing.LG))
                AboutSection(description = artistDetails.description)
            }
        }
    }
}

@Composable
private fun ArtistHeader(
    artistName: String,
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.MD, vertical = Spacing.MD)
            .padding(top = Spacing.XL),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .size(Spacing.XL + Spacing.SM)
                .clip(CircleShape)
                .background(LocalVikifyColors.current.surfaceElevated)
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBackIosNew,
                contentDescription = "Back",
                tint = LocalVikifyColors.current.textPrimary,
                modifier = Modifier.size(Sizing.IconMedium)
            )
        }
        
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleMedium,
            color = Color.Transparent // Hidden title (animate on scroll)
        )
        
        IconButton(
            onClick = { },
            modifier = Modifier
                .size(Spacing.XL + Spacing.SM)
                .clip(CircleShape)
                .background(LocalVikifyColors.current.surfaceElevated)
        ) {
            Icon(
                imageVector = Icons.Filled.MoreHoriz,
                contentDescription = "More",
                tint = LocalVikifyColors.current.textPrimary,
                modifier = Modifier.size(Sizing.IconMedium)
            )
        }
    }
}

@Composable
private fun ArtistHero(imageUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.MD)
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
            .background(LocalVikifyColors.current.surfaceElevated)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Artist",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun ArtistInfo(artistName: String, description: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.MD, vertical = Spacing.MD),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = artistName,
            style = MaterialTheme.typography.headlineLarge,
            color = LocalVikifyColors.current.textPrimary
        )
        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.XS))
            Text(
                text = description.take(100) + if (description.length > 100) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalVikifyColors.current.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ArtistActions(onShuffleClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD)
    ) {
        // Follow button
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
                .border(Sizing.BorderWidth, Divider, RoundedCornerShape(Sizing.CardRadiusLarge))
                .background(LocalVikifyColors.current.surfaceElevated)
                .clickable { },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Follow",
                style = MaterialTheme.typography.titleSmall,
                color = LocalVikifyColors.current.textPrimary
            )
        }
        
        // Shuffle Play button
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
                .background(LocalVikifyColors.current.accent)
                .clickable(onClick = onShuffleClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Shuffle",
                tint = Color.White,
                modifier = Modifier.size(Sizing.IconSmall)
            )
            Spacer(modifier = Modifier.width(Spacing.SM))
            Text(
                text = "Shuffle Play",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, showSeeAll: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.MD),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = LocalVikifyColors.current.textPrimary
        )
        if (showSeeAll) {
            Text(
                text = "See All",
                style = MaterialTheme.typography.bodySmall,
                color = LocalVikifyColors.current.accent,
                modifier = Modifier.clickable { }
            )
        }
    }
}

@Composable
private fun PopularSongRow(
    index: Int,
    song: SongItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.MD, vertical = Spacing.SM),
        horizontalArrangement = Arrangement.spacedBy(Spacing.SM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalVikifyColors.current.textSecondary,
            modifier = Modifier.width(Spacing.LG)
        )
        
        Box(
            modifier = Modifier
                .size(Sizing.ArtworkSmall)
                .clip(RoundedCornerShape(Sizing.CardRadius))
                .background(LocalVikifyColors.current.surfaceElevated)
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                color = LocalVikifyColors.current.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = LocalVikifyColors.current.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
                tint = LocalVikifyColors.current.textSecondary,
                modifier = Modifier.size(Sizing.IconSmall)
            )
        }
    }
}

@Composable
private fun AlbumsRow(albums: List<AlbumItem>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.MD),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD)
    ) {
        items(albums) { album ->
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickable { }
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(Sizing.CardRadiusLarge))
                        .background(LocalVikifyColors.current.surfaceElevated)
                ) {
                    AsyncImage(
                        model = album.thumbnail,
                        contentDescription = album.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.SM))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalVikifyColors.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.year?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalVikifyColors.current.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SimilarArtistsRow(
    artists: List<ArtistItem>,
    onArtistClick: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Spacing.MD),
        horizontalArrangement = Arrangement.spacedBy(Spacing.MD)
    ) {
        items(artists) { artist ->
            Column(
                modifier = Modifier
                    .width(80.dp)
                    .clickable { onArtistClick(artist.title) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(Sizing.BorderWidth, LocalVikifyColors.current.border, CircleShape)
                        .background(LocalVikifyColors.current.surfaceElevated)
                ) {
                    AsyncImage(
                        model = artist.thumbnail,
                        contentDescription = artist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.SM))
                Text(
                    text = artist.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalVikifyColors.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AboutSection(description: String?) {
    if (description.isNullOrBlank()) return
    
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.padding(horizontal = Spacing.MD)
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleLarge,
            color = LocalVikifyColors.current.textPrimary
        )
        Spacer(modifier = Modifier.height(Spacing.SM))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalVikifyColors.current.textSecondary,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Spacing.XS))
        Text(
            text = if (expanded) "Show Less" else "Read More",
            style = MaterialTheme.typography.labelMedium,
            color = LocalVikifyColors.current.accent,
            modifier = Modifier.clickable { expanded = !expanded }
        )
    }
}
