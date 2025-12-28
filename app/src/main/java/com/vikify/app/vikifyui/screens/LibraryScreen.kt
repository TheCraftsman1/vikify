/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.vikify.app.viewmodels.LibrarySongsViewModel
import com.vikify.app.vikifyui.theme.LivingBackground
import com.vikify.app.vikifyui.theme.*

enum class LibraryTab { Playlists, Artists, Albums, Downloaded }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onTrackClick: (String) -> Unit,
    onPlaylistClick: (String, com.vikify.app.spotify.SpotifyPlaylist?) -> Unit,
    onLikedSongsClick: () -> Unit = {},
    onDownloadsClick: () -> Unit = {},
    playlists: List<com.vikify.app.spotify.SpotifyPlaylist> = emptyList(),
    onSettingsClick: () -> Unit,
    onLongPressItem: ((String, String) -> Unit)? = null, // title, subtitle for context menu
    modifier: Modifier = Modifier
) {
    // Mock Data for UI building (Replace with ViewModel data later)
    val mockTracks = remember {
        listOf(
            Triple("Starboy", "The Weeknd", "https://i.scdn.co/image/ab67616d0000b2734718e28d24527d9774635514"),
            Triple("Die For You", "The Weeknd", "https://i.scdn.co/image/ab67616d0000b273a048415db06a5b6fa7ec4e1a"),
            Triple("Creepin'", "Metro Boomin, The Weeknd", "https://i.scdn.co/image/ab67616d0000b27313e54d6687e65678d60466c2"),
            Triple("Save Your Tears", "The Weeknd", "https://i.scdn.co/image/ab67616d0000b2738863bc11d2aa12b54f5aeb36"),
            Triple("Blinding Lights", "The Weeknd", "https://i.scdn.co/image/ab67616d0000b273c559a84d5a37627db8cde684")
        )
    }

    var selectedTab by remember { mutableStateOf(LibraryTab.Playlists) }
    
    // Theme-aware colors
    val isDark = VikifyTheme.isDark
    val backgroundColor = if (isDark) Color.Transparent else LightColors.Background
    val surfaceColor = if (isDark) DarkColors.Surface else LightColors.Surface
    val textPrimary = if (isDark) DarkColors.TextPrimary else LightColors.TextPrimary
    val textSecondary = if (isDark) DarkColors.TextSecondary else LightColors.TextSecondary

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        if (isDark) {
            LivingBackground(modifier = Modifier.matchParentSize()) { }
        } else {
            com.vikify.app.vikifyui.theme.EtherealBackground(modifier = Modifier.matchParentSize()) { }
        }

        // Content
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp) // Space for Floating Player
        ) {
        
        // 1. BIG HEADER with Settings Button
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Library",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    
                    // Header Icons (including Settings)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VikifyTheme.colors.surface)
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search Library",
                                tint = VikifyTheme.colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VikifyTheme.colors.surface)
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "Add",
                                tint = VikifyTheme.colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        // Settings Button (NEW)
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(VikifyTheme.colors.surface)
                        ) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = VikifyTheme.colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. TABS (Soft Pills)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                items(LibraryTab.values()) { tab ->
                    val isSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) VikifyTheme.colors.textPrimary else VikifyTheme.colors.surface)
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = tab.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) VikifyTheme.colors.surface else VikifyTheme.colors.textPrimary
                        )
                    }
                }
            }
        }

        // 3. PINNED / HERO CARDS (Liked Songs, etc.)
        item {
            Text(
                "Pinned",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = VikifyTheme.colors.textPrimary,
                modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)
            )
            
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                item {
                    PinnedCard(
                        title = "Liked Songs",
                        subtitle = "Your favorites",
                        icon = Icons.Rounded.Favorite,
                        gradient = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)),
                        onClick = onLikedSongsClick
                    )
                }
                item {
                    PinnedCard(
                        title = "Downloaded",
                        subtitle = "Offline music",
                        icon = Icons.Rounded.Download,
                        gradient = listOf(Color(0xFF10B981), Color(0xFF3B82F6)),
                        onClick = onDownloadsClick
                    )
                }
            }
        }

        // 4. RECENTLY ADDED LIST
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recently Added",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = VikifyTheme.colors.textPrimary
                )
                Icon(
                    Icons.Rounded.FilterList,
                    contentDescription = "Sort",
                    tint = VikifyTheme.colors.textPrimary
                )
            }
        }

        // Show playlists when Playlists tab is selected
        items(playlists.takeIf { selectedTab == LibraryTab.Playlists } ?: emptyList()) { playlist ->
            LibraryListItem(
                title = playlist.name,
                subtitle = "${playlist.trackCount} songs",
                imageUrl = playlist.imageUrl,
                onClick = { onPlaylistClick(playlist.id, playlist) },
                onLongClick = { onLongPressItem?.invoke(playlist.name, "${playlist.trackCount} songs") }
            )
        }

        // Empty state for Playlists tab
        if (selectedTab == LibraryTab.Playlists && playlists.isEmpty()) {
            item {
                EmptyTabState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "No playlists yet",
                    subtitle = "Create or import playlists to see them here",
                    actionText = "Create Playlist",
                    onAction = { /* Navigate to create */ }
                )
            }
        }
        
        // Empty state for Artists tab
        if (selectedTab == LibraryTab.Artists) {
            item {
                EmptyTabState(
                    icon = Icons.Rounded.Person,
                    title = "No artists yet",
                    subtitle = "Follow artists to see them in your library",
                    actionText = "Explore Artists",
                    onAction = { /* Navigate to explore */ }
                )
            }
        }
        
        // Empty state for Albums tab
        if (selectedTab == LibraryTab.Albums) {
            item {
                EmptyTabState(
                    icon = Icons.Rounded.Album,
                    title = "No albums saved",
                    subtitle = "Save albums to listen to them offline",
                    actionText = "Browse Albums",
                    onAction = { /* Navigate to explore */ }
                )
            }
        }
        
        // Downloaded tab - shows actual downloaded songs or empty state
        if (selectedTab == LibraryTab.Downloaded) {
            // TODO: Replace with actual downloaded songs from ViewModel
            item {
                EmptyTabState(
                    icon = Icons.Rounded.CloudOff,
                    title = "No downloads yet",
                    subtitle = "Download songs to listen offline",
                    actionText = "Explore Music",
                    onAction = { /* Navigate to home */ }
                )
            }
        }
    }
}
}

// --- HELPER COMPONENTS ---

/**
 * Empty state for tabs with no content
 */
@Composable
private fun EmptyTabState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VikifyTheme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = VikifyTheme.colors.textPrimary,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = VikifyTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = VikifyTheme.colors.textPrimary
            )
        ) {
            Text(actionText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun PinnedCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(160.dp)
            .shadow(10.dp, RoundedCornerShape(24.dp), spotColor = Color(0x1A000000))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .padding(16.dp)
                .size(32.dp)
                .align(Alignment.TopStart)
        )
        
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryListItem(
    title: String,
    subtitle: String,
    imageUrl: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp), spotColor = Color(0x1A000000))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Spacer(Modifier.width(16.dp))
        
        // Text
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
        
        // Context Menu Icon
        Icon(
            Icons.Rounded.MoreHoriz,
            contentDescription = "Options",
            tint = VikifyTheme.colors.textSecondary,
            modifier = Modifier.size(24.dp)
        )
    }
}



// --- PREVIEW ---
@Preview(showBackground = true)
@Composable
fun PreviewLibrary() {
    MaterialTheme {
        LibraryScreen(
            onTrackClick = {},
            onPlaylistClick = { _, _ -> },
            onSettingsClick = {}
        )
    }
}