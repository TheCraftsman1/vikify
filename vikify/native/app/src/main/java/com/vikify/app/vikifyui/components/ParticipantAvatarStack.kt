/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Participant Avatar Stack - Multi-user display component for Jam Mode
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.JamParticipant

/**
 * Displays a horizontal stack of overlapping participant avatars.
 * 
 * Features:
 * - Shows up to [maxVisible] avatars with overlap
 * - "+N" badge when participants exceed [maxVisible]
 * - Online/offline status indicators
 * - Click to open participants drawer
 * - Animates on participant changes
 */
@Composable
fun ParticipantAvatarStack(
    participants: List<JamParticipant>,
    hostId: String,
    hostName: String,
    hostAvatar: String?,
    maxVisible: Int = 4,
    avatarSize: Dp = 40.dp,
    overlapOffset: Dp = (-12).dp,
    accentColor: Color = Color(0xFF7C4DFF),
    secondaryColor: Color = Color(0xFF00E5FF),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Include host in display (host always first)
    val allUsers = remember(participants, hostId, hostName, hostAvatar) {
        val hostParticipant = JamParticipant(
            id = hostId,
            name = hostName,
            avatar = hostAvatar,
            isOnline = true
        )
        listOf(hostParticipant) + participants.filter { it.id != hostId }
    }
    
    val visibleUsers = allUsers.take(maxVisible)
    val overflowCount = (allUsers.size - maxVisible).coerceAtLeast(0)
    
    Row(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar stack
        Box {
            visibleUsers.forEachIndexed { index, participant ->
                val isHost = participant.id == hostId
                
                ParticipantAvatar(
                    participant = participant,
                    isHost = isHost,
                    size = avatarSize,
                    accentColor = accentColor,
                    secondaryColor = secondaryColor,
                    modifier = Modifier
                        .offset(x = (avatarSize + overlapOffset) * index)
                        .zIndex((visibleUsers.size - index).toFloat())
                )
            }
            
            // Overflow badge
            if (overflowCount > 0) {
                OverflowBadge(
                    count = overflowCount,
                    size = avatarSize,
                    accentColor = accentColor,
                    modifier = Modifier
                        .offset(x = (avatarSize + overlapOffset) * visibleUsers.size)
                        .zIndex(0f)
                )
            }
        }
        
        // Participant count text
        Spacer(Modifier.width(12.dp))
        
        Column {
            Text(
                text = if (allUsers.size == 1) "Just you" else "${allUsers.size} listening",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Tap to see all",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Single participant avatar with online status indicator
 */
@Composable
private fun ParticipantAvatar(
    participant: JamParticipant,
    isHost: Boolean,
    size: Dp,
    accentColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // Avatar container with border
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = if (isHost) {
                        Brush.linearGradient(listOf(accentColor, secondaryColor))
                    } else {
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f)))
                    },
                    shape = CircleShape
                )
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center
        ) {
            if (participant.avatar != null) {
                AsyncImage(
                    model = participant.avatar,
                    contentDescription = participant.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                // Fallback: Initial letter
                Text(
                    text = participant.name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = (size.value / 2.5f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Online status indicator
        Box(
            modifier = Modifier
                .size(size / 3.5f)
                .align(Alignment.BottomEnd)
                .offset(x = (-2).dp, y = (-2).dp)
                .clip(CircleShape)
                .background(Color(0xFF121212))
                .padding(2.dp)
                .clip(CircleShape)
                .background(
                    if (participant.isOnline) Color(0xFF4CAF50) else Color(0xFF757575)
                )
        )
        
        // Host crown badge
        if (isHost) {
            Box(
                modifier = Modifier
                    .size(size / 3f)
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(accentColor, secondaryColor))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "👑",
                    fontSize = (size.value / 4f).sp
                )
            }
        }
    }
}

/**
 * "+N" overflow badge when too many participants to show
 */
@Composable
private fun OverflowBadge(
    count: Int,
    size: Dp,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .background(accentColor.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "+$count",
            color = Color.White,
            fontSize = (size.value / 3f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Compact version for use in headers/toolbars
 */
@Composable
fun CompactParticipantStack(
    participants: List<JamParticipant>,
    hostId: String,
    maxVisible: Int = 3,
    avatarSize: Dp = 28.dp,
    accentColor: Color = Color(0xFF7C4DFF),
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allUsers = listOf(
        JamParticipant(id = hostId, name = "Host", isOnline = true)
    ) + participants.filter { it.id != hostId }
    
    val visibleCount = allUsers.size.coerceAtMost(maxVisible)
    val overflowCount = (allUsers.size - maxVisible).coerceAtLeast(0)
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp) // Overlap handled by zIndex
    ) {
        repeat(visibleCount) { index ->
            val user = allUsers.getOrNull(index)
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .zIndex((visibleCount - index).toFloat())
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFF121212), CircleShape)
                    .background(
                        if (index == 0) accentColor else Color(0xFF2A2A2A)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user?.name?.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        if (overflowCount > 0) {
            Text(
                text = "+$overflowCount",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}
