/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Participants Drawer - Full list of Jam session participants
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.JamParticipant

/**
 * Modal bottom sheet showing all participants in a Jam session
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsDrawer(
    isVisible: Boolean,
    participants: List<JamParticipant>,
    hostId: String,
    hostName: String,
    hostAvatar: String?,
    currentUserId: String,
    isCurrentUserHost: Boolean,
    accentColor: Color = Color(0xFF7C4DFF),
    secondaryColor: Color = Color(0xFF00E5FF),
    onDismiss: () -> Unit,
    onRemoveParticipant: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    if (isVisible) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = Color(0xFF121212),
            contentColor = Color.White,
            dragHandle = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }
            },
            modifier = modifier
        ) {
            ParticipantsContent(
                participants = participants,
                hostId = hostId,
                hostName = hostName,
                hostAvatar = hostAvatar,
                currentUserId = currentUserId,
                isCurrentUserHost = isCurrentUserHost,
                accentColor = accentColor,
                secondaryColor = secondaryColor,
                onRemoveParticipant = onRemoveParticipant
            )
        }
    }
}

@Composable
private fun ParticipantsContent(
    participants: List<JamParticipant>,
    hostId: String,
    hostName: String,
    hostAvatar: String?,
    currentUserId: String,
    isCurrentUserHost: Boolean,
    accentColor: Color,
    secondaryColor: Color,
    onRemoveParticipant: (String) -> Unit
) {
    val hostParticipant = JamParticipant(
        id = hostId,
        name = hostName,
        avatar = hostAvatar,
        isOnline = true
    )
    
    val allParticipants = remember(participants, hostParticipant) {
        listOf(hostParticipant) + participants.filter { it.id != hostId }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Participants",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Participant count badge
            Box(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(listOf(accentColor, secondaryColor)),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${allParticipants.size}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Participants list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 400.dp)
        ) {
            items(
                items = allParticipants,
                key = { it.id }
            ) { participant ->
                ParticipantRow(
                    participant = participant,
                    isHost = participant.id == hostId,
                    isCurrentUser = participant.id == currentUserId,
                    canRemove = isCurrentUserHost && participant.id != hostId,
                    accentColor = accentColor,
                    secondaryColor = secondaryColor,
                    onRemove = { onRemoveParticipant(participant.id) }
                )
            }
        }
        
        // Info footer
        Spacer(Modifier.height(16.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color.White.copy(alpha = 0.05f),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Everyone hears the same music in sync",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: JamParticipant,
    isHost: Boolean,
    isCurrentUser: Boolean,
    canRemove: Boolean,
    accentColor: Color,
    secondaryColor: Color,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isHost) {
                            Brush.linearGradient(listOf(accentColor, secondaryColor))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)))
                        }
                    ),
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
                    Text(
                        text = participant.name.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Online indicator
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF121212))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(
                        if (participant.isOnline) Color(0xFF4CAF50) else Color(0xFF757575)
                    )
            )
        }
        
        Spacer(Modifier.width(12.dp))
        
        // Name and role
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = participant.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                if (isCurrentUser) {
                    Text(
                        text = "(You)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isHost) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Host",
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = if (participant.isOnline) "Listening" else "Offline",
                        color = if (participant.isOnline) Color(0xFF4CAF50) else Color(0xFF757575),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // Remove button (host only, for non-host participants)
        if (canRemove) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonRemove,
                    contentDescription = "Remove participant",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
