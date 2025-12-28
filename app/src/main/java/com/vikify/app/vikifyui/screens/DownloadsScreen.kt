/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.vikifyui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.db.entities.Song
import com.vikify.app.vikifyui.components.VikifyImage
import com.vikify.app.vikifyui.theme.*

// Premium Vikify Colors
private val DownloadGreen = Color(0xFF10B981)
private val DownloadTeal = Color(0xFF06B6D4)
private val VikifyDark = Color(0xFF0A0A0F)
private val VikifyCard = Color(0xFF12121A)

/**
 * Downloads Screen - Premium Glassmorphism Design
 * 
 * Features:
 * - Floating album art mosaic background
 * - Glassmorphism hero card
 * - Animated play button
 * - Premium song rows with swipe actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloadedSongs: List<Song>,
    downloadCount: Int,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onShufflePlay: () -> Unit,
    currentSongId: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(VikifyDark)) {
        // Floating Album Art Background (if songs exist)
        if (downloadedSongs.isNotEmpty()) {
            FloatingArtBackground(
                artworkUrls = downloadedSongs.take(4).map { it.song.thumbnailUrl ?: "" },
                accentColor = DownloadGreen
            )
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // Floating Back Button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Glassmorphism Hero Card
            item {
                GlassHeroCard(
                    title = "Downloads",
                    subtitle = "$downloadCount songs available offline",
                    icon = Icons.Rounded.DownloadDone,
                    accentColor = DownloadGreen,
                    onShufflePlay = onShufflePlay,
                    showShuffle = downloadCount > 0
                )
            }
            
            // Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "All Songs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$downloadCount",
                        fontSize = 14.sp,
                        color = DownloadGreen
                    )
                }
            }
            
            // Song List
            if (downloadedSongs.isEmpty()) {
                item { EmptyStateCard(type = "downloads") }
            } else {
                itemsIndexed(
                    items = downloadedSongs,
                    key = { _, song -> song.song.id }
                ) { index, song ->
                    PremiumSongRow(
                        index = index + 1,
                        artworkUrl = song.song.thumbnailUrl,
                        title = song.song.title,
                        artist = song.artists.joinToString { it.name },
                        isPlaying = song.song.id == currentSongId,
                        accentColor = DownloadGreen,
                        showDownloadBadge = true,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

/**
 * Floating Album Art Background - Creates depth effect
 */
@Composable
private fun FloatingArtBackground(
    artworkUrls: List<String>,
    accentColor: Color
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred artworks floating
        artworkUrls.forEachIndexed { index, url ->
            val infiniteTransition = rememberInfiniteTransition(label = "float_$index")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = -20f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000 + index * 500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "offset_$index"
            )
            
            val positions = listOf(
                Pair(0.1f, 0.1f),
                Pair(0.7f, 0.15f),
                Pair(0.2f, 0.25f),
                Pair(0.8f, 0.35f)
            )
            
            if (url.isNotEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(
                            x = (positions.getOrNull(index)?.first ?: 0.5f).let { (it * 300).dp },
                            y = (positions.getOrNull(index)?.second ?: 0.5f).let { (it * 400 + offsetY).dp }
                        )
                        .size((80 + index * 20).dp)
                        .clip(RoundedCornerShape(20.dp))
                        .blur(30.dp)
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
                            VikifyDark.copy(alpha = 0.3f),
                            VikifyDark.copy(alpha = 0.7f),
                            VikifyDark
                        )
                    )
                )
        )
        
        // Accent glow at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Glassmorphism Hero Card
 */
@Composable
private fun GlassHeroCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onShufflePlay: () -> Unit,
    showShuffle: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        // Glass Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Icon
            val infiniteTransition = rememberInfiniteTransition(label = "icon_pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            if (showShuffle) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Shuffle Play Button
                Button(
                    onClick = onShufflePlay,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 40.dp, vertical = 14.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 2.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Shuffle Play",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/**
 * Premium Song Row - Modern design with index, glow, and badges
 */
@Composable
fun PremiumSongRow(
    index: Int,
    artworkUrl: String?,
    title: String,
    artist: String,
    isPlaying: Boolean,
    accentColor: Color,
    showDownloadBadge: Boolean = false,
    showLikeBadge: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) accentColor.copy(alpha = 0.1f) else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Number
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPlaying) accentColor else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.width(28.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Artwork with playing indicator
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VikifyCard)
        ) {
            VikifyImage(
                url = artworkUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize()
            )
            
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPlaying) accentColor else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Badges
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showDownloadBadge) {
                Icon(
                    imageVector = Icons.Rounded.OfflinePin,
                    contentDescription = "Downloaded",
                    tint = DownloadGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (showLikeBadge) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "Liked",
                    tint = Color(0xFFFF6B9D),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        
        // More options
        IconButton(onClick = { /* TODO: Context menu */ }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "More",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Empty State Card - Premium design
 */
@Composable
fun EmptyStateCard(type: String) {
    val (icon, title, subtitle) = when (type) {
        "downloads" -> Triple(
            Icons.Rounded.CloudDownload,
            "No downloads yet",
            "Download songs to listen offline"
        )
        "liked" -> Triple(
            Icons.Rounded.FavoriteBorder,
            "No liked songs",
            "Heart songs you love to see them here"
        )
        else -> Triple(
            Icons.Rounded.MusicNote,
            "No songs",
            "Start exploring to add songs"
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}
