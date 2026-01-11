/*
 * Copyright (C) 2026 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Jam Status Banners - Offline mode and session expiry warnings
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Banner shown when Jam session falls back to local/offline mode
 */
@Composable
fun OfflineModeBanner(
    isVisible: Boolean,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        JamStatusBanner(
            icon = Icons.Rounded.WifiOff,
            title = "Offline Mode",
            message = "Others cannot join this session",
            backgroundColor = Color(0xFF424242),
            iconColor = Color(0xFFFFB74D),
            onDismiss = onDismiss
        )
    }
}

/**
 * Banner shown when session is about to expire
 */
@Composable
fun SessionExpiryBanner(
    isVisible: Boolean,
    expiresInMinutes: Int,
    onExtend: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        JamStatusBanner(
            icon = Icons.Rounded.Timer,
            title = "Session Expiring",
            message = "Ends in $expiresInMinutes minute${if (expiresInMinutes > 1) "s" else ""}",
            backgroundColor = Color(0xFF5D4037),
            iconColor = Color(0xFFFFCC80),
            actionText = "Extend",
            onAction = onExtend,
            onDismiss = onDismiss
        )
    }
}

/**
 * Banner shown when a participant joins/leaves
 */
@Composable
fun ParticipantEventBanner(
    participantName: String,
    eventType: ParticipantEventType,
    isVisible: Boolean,
    accentColor: Color = Color(0xFF7C4DFF),
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn() + scaleIn(initialScale = 0.9f),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier
    ) {
        val (icon, message, color) = when (eventType) {
            ParticipantEventType.JOINED -> Triple(
                Icons.Rounded.PersonAdd,
                "$participantName joined the Jam",
                Color(0xFF4CAF50)
            )
            ParticipantEventType.LEFT -> Triple(
                Icons.Rounded.PersonRemove,
                "$participantName left",
                Color(0xFF757575)
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF1E1E1E),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

enum class ParticipantEventType {
    JOINED, LEFT
}

/**
 * Reusable status banner component
 */
@Composable
private fun JamStatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    backgroundColor: Color,
    iconColor: Color,
    actionText: String? = null,
    onAction: () -> Unit = {},
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(Modifier.width(12.dp))
        
        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        
        // Action button (optional)
        if (actionText != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionText,
                    color = iconColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Dismiss button
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Auto-dismissing toast-style notification
 */
@Composable
fun JamToast(
    message: String,
    isVisible: Boolean,
    durationMs: Long = 3000L,
    icon: ImageVector? = null,
    accentColor: Color = Color(0xFF7C4DFF),
    onDismiss: () -> Unit
) {
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(durationMs)
            onDismiss()
        }
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    Color(0xFF2A2A2A),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
