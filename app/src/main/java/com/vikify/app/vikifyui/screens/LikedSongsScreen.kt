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

// Premium Vikify Colors for Liked
private val LikedPink = Color(0xFFFF6B9D)
private val LikedRed = Color(0xFFED5564)
private val LikedPurple = Color(0xFF8B5CF6)
private val VikifyDark = Color(0xFF0A0A0F)
private val VikifyCard = Color(0xFF12121A)

/**
 * Liked Songs Screen - Premium Heart-Themed Design
 * 
 * Features:
 * - Animated heart gradient background
 * - Glassmorphism hero with pulsing heart
 * - Premium song rows with like badges
 * - Smooth animations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    likedSongs: List<Song>,
    likedCount: Int,
    onSongClick: (Song) -> Unit,
    onBackClick: () -> Unit,
    onShufflePlay: () -> Unit,
    currentSongId: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(VikifyDark)) {
        // Animated Heart Background
        LikedSongsBackground(
            artworkUrls = likedSongs.take(4).map { it.song.thumbnailUrl ?: "" }
        )
        
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
            
            // Heart Hero Card
            item {
                LikedHeroCard(
                    songCount = likedCount,
                    onShufflePlay = onShufflePlay
                )
            }
            
            // Section Header with Sort Options
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Loved Songs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = LikedPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$likedCount",
                            fontSize = 14.sp,
                            color = LikedPink
                        )
                    }
                }
            }
            
            // Song List
            if (likedSongs.isEmpty()) {
                item { EmptyLikedState() }
            } else {
                itemsIndexed(
                    items = likedSongs,
                    key = { _, song -> song.song.id }
                ) { index, song ->
                    LikedSongRow(
                        index = index + 1,
                        song = song,
                        isPlaying = song.song.id == currentSongId,
                        onClick = { onSongClick(song) }
                    )
                }
            }
        }
    }
}

/**
 * Animated Heart Background with floating artworks
 */
@Composable
private fun LikedSongsBackground(artworkUrls: List<String>) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Floating artworks
        artworkUrls.forEachIndexed { index, url ->
            val infiniteTransition = rememberInfiniteTransition(label = "float_$index")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = -15f,
                targetValue = 15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500 + index * 400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "offset_$index"
            )
            
            val positions = listOf(
                Pair(0.05f, 0.08f),
                Pair(0.75f, 0.12f),
                Pair(0.15f, 0.22f),
                Pair(0.85f, 0.28f)
            )
            
            if (url.isNotEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(
                            x = (positions.getOrNull(index)?.first ?: 0.5f).let { (it * 350).dp },
                            y = (positions.getOrNull(index)?.second ?: 0.5f).let { (it * 400 + offsetY).dp }
                        )
                        .size((70 + index * 15).dp)
                        .clip(RoundedCornerShape(16.dp))
                        .blur(25.dp)
                )
            }
        }
        
        // Gradient overlay with pink tint
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LikedPink.copy(alpha = 0.08f),
                            VikifyDark.copy(alpha = 0.7f),
                            VikifyDark
                        )
                    )
                )
        )
        
        // Top gradient glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LikedRed.copy(alpha = 0.2f),
                            LikedPink.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * Heart Hero Card with pulsing animation
 */
@Composable
private fun LikedHeroCard(
    songCount: Int,
    onShufflePlay: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
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
            // Animated Pulsing Heart
            val infiniteTransition = rememberInfiniteTransition(label = "heart_pulse")
            val heartScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "heart_scale"
            )
            
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(heartScale),
                contentAlignment = Alignment.Center
            ) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    LikedPink.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Heart container
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(LikedPink, LikedRed)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = "Liked Songs",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "$songCount songs you love",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            
            if (songCount > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Gradient Play Button
                Button(
                    onClick = onShufflePlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(LikedPink, LikedRed)
                                ),
                                shape = RoundedCornerShape(50)
                            )
                            .padding(horizontal = 40.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shuffle Play",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Liked Song Row with heart accent
 */
@Composable
private fun LikedSongRow(
    index: Int,
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) LikedPink.copy(alpha = 0.1f) else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Number
        Text(
            text = index.toString().padStart(2, '0'),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPlaying) LikedPink else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.width(28.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Artwork
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VikifyCard)
        ) {
            VikifyImage(
                url = song.song.thumbnailUrl,
                contentDescription = song.song.title,
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
                        tint = LikedPink,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        // Song Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.song.title,
                fontSize = 15.sp,
                fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isPlaying) LikedPink else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artists.joinToString { it.name },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Heart Badge
        Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = "Liked",
            tint = LikedPink,
            modifier = Modifier.size(18.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
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
 * Empty Liked State
 */
@Composable
private fun EmptyLikedState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated empty heart
        val infiniteTransition = rememberInfiniteTransition(label = "empty_heart")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(LikedPink.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                tint = LikedPink.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No liked songs yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Tap the heart on songs you love",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}
