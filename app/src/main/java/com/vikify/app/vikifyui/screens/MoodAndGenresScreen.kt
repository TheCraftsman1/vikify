/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.vikifyui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vikify.app.viewmodels.MoodAndGenresViewModel
import com.vikify.app.vikifyui.theme.DarkColors
import com.vikify.app.vikifyui.theme.LightColors
import com.vikify.app.vikifyui.theme.VikifyTheme
import com.zionhuang.innertube.pages.MoodAndGenres
import com.vikify.app.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

// ============================================================================
// MOOD IMAGE MAPPING (DISABLED - Gradient design is more premium)
// Stock photos look cheap; solid colors/gradients are what Spotify uses
// ============================================================================
private fun getMoodImageUrl(title: String): String? = null // Always use gradient fallback

// Premium Mood Colors - Spotify-inspired vibrant palette
private val moodColors = listOf(
    Color(0xFF1DB954), // Spotify Green
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4), // Cyan
    Color(0xFFF59E0B), // Amber
    Color(0xFFEF4444), // Red
    Color(0xFF10B981), // Emerald
    Color(0xFF3B82F6), // Blue
    Color(0xFFA855F7), // Violet
    Color(0xFF14B8A6), // Teal
    Color(0xFFF97316), // Orange
    Color(0xFF6366F1), // Indigo
)

@Composable
fun MoodAndGenresScreen(
    onBackClick: () -> Unit,
    onMoodClick: (browseId: String, params: String?, title: String, color: Int) -> Unit,
    viewModel: MoodAndGenresViewModel = hiltViewModel()
) {
    val view = LocalView.current
    val moodAndGenres by viewModel.moodAndGenres.collectAsState()
    
    // Theme colors
    val isDark = VikifyTheme.isDark
    val backgroundColor = if (isDark) DarkColors.Background else LightColors.Background
    val surfaceColor = if (isDark) DarkColors.Surface else LightColors.Surface
    val textPrimary = if (isDark) DarkColors.TextPrimary else LightColors.TextPrimary
    val textSecondary = if (isDark) DarkColors.TextSecondary else LightColors.TextSecondary
    
    // Accent header gradient
    val headerGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF8B5CF6).copy(alpha = 0.6f),
            Color(0xFFEC4899).copy(alpha = 0.3f),
            backgroundColor
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp) // Space for mini-player
        ) {
            // ═══════════════════════════════════════════════════════════════
            // PREMIUM HEADER
            // ═══════════════════════════════════════════════════════════════
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    // Gradient Background
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(headerGradient)
                    )
                    
                    // Header Content
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.vikify_logo),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "Mood & Genres",
                                color = textPrimary,
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Discover music that matches your vibe",
                            color = textSecondary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════════
            // MOOD CATEGORIES
            // ═══════════════════════════════════════════════════════════════
            moodAndGenres?.forEachIndexed { categoryIndex, category ->
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (categoryIndex == 0) 8.dp else 24.dp)
                        ) {
                            // Category Title
                            Text(
                                text = category.title,
                                color = textPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                            
                            // Grid of Mood Cards (2 columns)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                category.items.chunked(2).forEach { rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEachIndexed { itemIndex, mood ->
                                            val colorIndex = (categoryIndex * 3 + itemIndex) % moodColors.size
                                            val moodColor = moodColors[colorIndex]
                                            
                                            MoodCard(
                                                title = mood.title,
                                                color = moodColor,
                                                imageUrl = getMoodImageUrl(mood.title), 
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                                    onMoodClick(
                                                        mood.endpoint.browseId,
                                                        mood.endpoint.params,
                                                        mood.title,
                                                        moodColor.hashCode()
                                                    )
                                                }
                                            )
                                        }
                                        // Fill empty space if odd number of items
                                        if (rowItems.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // ═══════════════════════════════════════════════════════════════
        // BACK BUTTON
        // ═══════════════════════════════════════════════════════════════
        IconButton(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onBackClick()
            },
            modifier = Modifier
                .padding(top = 48.dp, start = 16.dp)
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(50))
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        
        // ═══════════════════════════════════════════════════════════════
        // LOADING STATE
        // ═══════════════════════════════════════════════════════════════
        if (moodAndGenres == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF8B5CF6),
                        strokeWidth = 3.dp
                    )
                    Text(
                        text = "Loading vibes...",
                        color = textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// ============================================================================
// PREMIUM MOOD CARD
// ============================================================================
@Composable
private fun MoodCard(
    title: String,
    color: Color,
    imageUrl: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDark = VikifyTheme.isDark
    
    Box(
        modifier = modifier
            .height(100.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = color.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                // Fallback gradient if no image
                Brush.linearGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = 0.7f)
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomStart
    ) {
        // Image Background (if available)
        if (imageUrl != null) {
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Gradient Overlay for Text Readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY)
                        )
                    )
            )
        } else {
             // Subtle pattern overlay for depth if no image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        
        
        // Title
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        )
        
        // Decorative icon in corner
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(32.dp)
        )
    }
}
