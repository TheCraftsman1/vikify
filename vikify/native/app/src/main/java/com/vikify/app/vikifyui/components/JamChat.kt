/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Jam Chat - Real-time messaging for Jam sessions
 */
package com.vikify.app.vikifyui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.JamChatMessage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Chat theme colors
private object ChatColors {
    val SurfaceGlass = Color.White.copy(alpha = 0.1f)
    val SurfaceDark = Color(0xFF1A1A2E)
    val BorderGlass = Color.White.copy(alpha = 0.15f)
    val AccentPurple = Color(0xFF7C4DFF)
    val AccentCyan = Color(0xFF00E5FF)
    val SystemMessage = Color(0xFF888888)
}

/**
 * Floating chat button with unread badge
 */
@Composable
fun JamChatButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = ChatColors.SurfaceGlass,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = "Open Chat",
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Unread badge
        AnimatedVisibility(
            visible = unreadCount > 0,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(ChatColors.AccentPurple)
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Full chat panel (slide-up sheet style)
 */
@Composable
fun JamChatPanel(
    messages: List<JamChatMessage>,
    currentUserId: String,
    onSendMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var messageText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ChatColors.SurfaceDark.copy(alpha = 0.98f),
                        ChatColors.SurfaceDark
                    )
                )
            )
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                tint = ChatColors.AccentCyan,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Text(
                text = "Jam Chat",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }
        
        HorizontalDivider(
            color = ChatColors.BorderGlass,
            thickness = 1.dp
        )
        
        // Messages list
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "💬",
                                fontSize = 48.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No messages yet",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Say hi to your jam partner!",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    ChatMessageBubble(
                        message = message,
                        isCurrentUser = message.senderId == currentUserId,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Input field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(ChatColors.SurfaceGlass)
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = {
                    Text(
                        text = "Type a message...",
                        color = Color.White.copy(alpha = 0.4f)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = ChatColors.AccentCyan,
                    focusedBorderColor = ChatColors.AccentCyan.copy(alpha = 0.5f),
                    unfocusedBorderColor = ChatColors.BorderGlass,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                            keyboardController?.hide()
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(Modifier.width(8.dp))
            
            // Send button
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendMessage(messageText)
                        messageText = ""
                        keyboardController?.hide()
                    }
                },
                enabled = messageText.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (messageText.isNotBlank()) ChatColors.AccentPurple
                        else ChatColors.SurfaceGlass
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = if (messageText.isNotBlank()) Color.White 
                           else Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

/**
 * Individual chat message bubble
 */
@Composable
private fun ChatMessageBubble(
    message: JamChatMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    
    if (message.isSystem) {
        // System message (centered, italicized)
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = message.message,
                color = ChatColors.SystemMessage,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    } else {
        // User message bubble
        Column(
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
            modifier = modifier
        ) {
            // Sender name (only for other users)
            if (!isCurrentUser) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                ) {
                    // Avatar
                    if (message.senderAvatar != null) {
                        AsyncImage(
                            model = message.senderAvatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    
                    Text(
                        text = message.senderName,
                        color = ChatColors.AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Message bubble
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                            bottomEnd = if (isCurrentUser) 4.dp else 16.dp
                        )
                    )
                    .background(
                        if (isCurrentUser) ChatColors.AccentPurple.copy(alpha = 0.9f)
                        else ChatColors.SurfaceGlass
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.message,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )
            }
            
            // Timestamp
            Text(
                text = timeFormat.format(Date(message.timestamp)),
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 10.sp,
                modifier = Modifier.padding(
                    start = if (isCurrentUser) 0.dp else 8.dp,
                    end = if (isCurrentUser) 8.dp else 0.dp,
                    top = 2.dp
                )
            )
        }
    }
}
