/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Jam Mode - Social Listening Experience
 * Listen together with friends in real-time
 */
package com.vikify.app.vikifyui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.vikify.app.vikifyui.components.JamChatButton
import com.vikify.app.vikifyui.components.JamChatPanel
import com.vikify.app.vikifyui.components.JamModeTurntable
import com.vikify.app.vikifyui.components.JamUser
import com.vikify.app.vikifyui.components.ParticipantAvatarStack
import com.vikify.app.vikifyui.components.ParticipantsDrawer
import com.vikify.app.vikifyui.components.LeaveConfirmationDialog
import com.vikify.app.vikifyui.data.JamChatMessage
import com.vikify.app.vikifyui.data.JamParticipant
import com.vikify.app.vikifyui.data.JamSession
import com.vikify.app.vikifyui.components.QueueOverlay
import com.vikify.app.vikifyui.components.LyricsOverlay
import com.vikify.app.vikifyui.components.SyncedLyric
import com.vikify.app.vikifyui.components.JamFloatingLyric
import com.vikify.app.vikifyui.components.ParticipantAvatarStack
import com.vikify.app.vikifyui.components.JamSearchSheet
import com.vikify.app.vikifyui.components.JamReactionOverlay
import com.vikify.app.vikifyui.data.JamReaction
import com.vikify.app.vikifyui.data.JamSessionState
import com.vikify.app.vikifyui.data.PlayerUIState
import com.vikify.app.vikifyui.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

// ═══════════════════════════════════════════════════════════════════════════════
// COLOR PALETTE & THEME
// ═══════════════════════════════════════════════════════════════════════════════

enum class JamModeStyle {
    CYBERPUNK,
    SUNSET,
    MIDNIGHT,
    NEON
}

private object JamColors {
    // Cyberpunk
    val CyberPurple = Color(0xFF7C4DFF)
    val CyberCyan = Color(0xFF00E5FF)
    val CyberPink = Color(0xFFFF4081)
    
    // Sunset
    val SunsetOrange = Color(0xFFFF6B35)
    val SunsetPink = Color(0xFFFF0844)
    val SunsetYellow = Color(0xFFFFB347)
    
    // Midnight
    val MidnightBlue = Color(0xFF1E3A5F)
    val MidnightPurple = Color(0xFF4A1A6B)
    val MidnightCyan = Color(0xFF00B4D8)
    
    // Neon
    val NeonGreen = Color(0xFF39FF14)
    val NeonYellow = Color(0xFFFFFF00)
    val NeonPink = Color(0xFFFF1493)
    
    // Common
    val DeepBlack = Color(0xFF050508)
    val GlassSurface = Color.White.copy(alpha = 0.08f)
    val GlassBorder = Color.White.copy(alpha = 0.15f)
    
    // Status
    val Online = Color(0xFF4CAF50)
    val Offline = Color(0xFF757575)
    
    // Quick aliases
    val Cyberpunk = CyberPurple
    val Sunset = SunsetOrange
    
    fun getThemeColors(style: JamModeStyle, accent: Color? = null): JamThemeColors {
        val primary = accent ?: when (style) {
            JamModeStyle.CYBERPUNK -> CyberPurple
            JamModeStyle.SUNSET -> SunsetOrange
            JamModeStyle.MIDNIGHT -> MidnightCyan
            JamModeStyle.NEON -> NeonGreen
        }
        
        val secondary = when (style) {
            JamModeStyle.CYBERPUNK -> CyberCyan
            JamModeStyle.SUNSET -> SunsetPink
            JamModeStyle.MIDNIGHT -> MidnightPurple
            JamModeStyle.NEON -> NeonYellow
        }
        
        val tertiary = when (style) {
            JamModeStyle.CYBERPUNK -> CyberPink
            JamModeStyle.SUNSET -> SunsetYellow
            JamModeStyle.MIDNIGHT -> MidnightBlue
            JamModeStyle.NEON -> NeonPink
        }
        
        return JamThemeColors(primary, secondary, tertiary)
    }
}

data class JamThemeColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color
)

// ═══════════════════════════════════════════════════════════════════════════════
// SCREEN DATA MODELS
// ═══════════════════════════════════════════════════════════════════════════════

data class JamTrack(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val duration: Long,
    val addedBy: String? = null
)

// ═══════════════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun JamModeScreen(
    sessionState: JamSessionState,
    uiState: PlayerUIState,
    currentUser: JamUser,
    chatMessages: List<JamChatMessage> = emptyList(),
    unreadChatCount: Int = 0,

    isChatOpen: Boolean = false,
    lyrics: List<SyncedLyric>? = null,
    style: JamModeStyle = JamModeStyle.CYBERPUNK,
    accentColor: Color? = null,
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onLeaveSession: () -> Unit = {},
    onMinimize: () -> Unit = {}, // Minimize to background (keep session running)
    onCopyCode: () -> Unit = {},
    onShareSession: () -> Unit = {},
    onSendChatMessage: (String) -> Unit = {},
    onToggleChat: () -> Unit = {},
    // New callbacks for Phase 2
    onAddToQueue: (com.vikify.app.vikifyui.data.Track) -> Unit = {},
    onRemoveFromQueue: (String) -> Unit = {},
    onSearchTracks: suspend (String) -> List<com.vikify.app.vikifyui.data.Track> = { emptyList() },
    onSendReaction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Extract track info from uiState with addedBy attribution from queue
    val currentTrack = uiState.currentTrack?.let { track ->
        // Look up who added this track from the session queue
        val queueItem = when (sessionState) {
            is JamSessionState.Active -> sessionState.session.queue.find { it.trackId == track.id }
            is JamSessionState.WaitingForGuest -> sessionState.session.queue.find { it.trackId == track.id }
            else -> null
        }
        JamTrack(
            id = track.id,
            title = track.title,
            artist = track.artist,
            artworkUrl = track.remoteArtworkUrl,
            duration = track.duration,
            addedBy = queueItem?.addedByName?.takeIf { it.isNotBlank() }
        )
    }
    val isPlaying = uiState.isPlaying
    val progress = uiState.progress
    
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    
    val themeColors = remember(style, accentColor) {
        JamColors.getThemeColors(style, accentColor)
    }
    
    val screenWidth = configuration.screenWidthDp.dp
    val vinylSize = (screenWidth * 0.75f).coerceIn(260.dp, 340.dp)
    
    val isHost = when (sessionState) {
        is JamSessionState.Active -> sessionState.isHost
        is JamSessionState.WaitingForGuest -> true
        else -> true
    }
    
    // Multi-participant support - get all participants from session
    val participants = remember(sessionState) {
        when (sessionState) {
            is JamSessionState.Active -> sessionState.session.participantList
            else -> emptyList()
        }
    }
    
    // Session info for UI display
    val sessionInfo = remember(sessionState) {
        when (sessionState) {
            is JamSessionState.Active -> sessionState.session
            is JamSessionState.WaitingForGuest -> sessionState.session
            else -> null
        }
    }
    
    val inviteCode = when (sessionState) {
        is JamSessionState.WaitingForGuest -> sessionState.sessionCode
        is JamSessionState.Active -> sessionState.session.sessionCode
        else -> ""
    }
    
    // UI state for new components
    var showParticipantsDrawer by remember { mutableStateOf(false) }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    var showSearchSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    
    val showInviteCode = sessionState is JamSessionState.WaitingForGuest
    
    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "jam")
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    
    val floatY by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )
    
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinylRotation"
    )
    
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(lastInteractionTime) {
        delay(5000)
        if (System.currentTimeMillis() - lastInteractionTime >= 5000) {
            controlsVisible = false
        }
    }
    
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(300),
        label = "controlsAlpha"
    )
    
    fun onInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        controlsVisible = true
    }
    
    var horizontalDragAccumulator by remember { mutableFloatStateOf(0f) }
    var verticalDragAccumulator by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JamColors.DeepBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onInteraction() },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlayPause()
                    }
                )
            }
            .pointerInput(onMinimize) {
                // Swipe down to minimize Jam Mode (stays in background)
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    verticalDragAccumulator += dragAmount.y
                    if (verticalDragAccumulator > 300f) {
                        verticalDragAccumulator = 0f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMinimize() // Just minimize, don't leave
                    }
                }
            }
    ) {
        // Background layers
        JamModeBackground(
            themeColors = themeColors,
            glowIntensity = glowPulse,
            modifier = Modifier.fillMaxSize()
        )
        
        currentTrack?.artworkUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp)
                    .alpha(0.25f)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            JamColors.DeepBlack.copy(alpha = 0.7f)
                        ),
                        radius = 1000f
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JamColors.DeepBlack.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Transparent,
                            JamColors.DeepBlack.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            JamTopBar(
                currentUser = currentUser,
                participants = participants,
                themeColors = themeColors,
                onLeave = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showLeaveConfirmation = true // Trigger confirmation on button click
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            Spacer(Modifier.weight(0.5f))
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(y = floatY.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onInteraction()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    }
            ) {
                // Turntable (Atmosphere - Demoted)
                JamModeTurntable(
                    artworkUrl = currentTrack?.artworkUrl,
                    isPlaying = isPlaying,
                    isConnected = sessionState is JamSessionState.Active,
                    modifier = Modifier
                        .size(vinylSize + 20.dp) // Scaled down from +60.dp
                        .alpha(0.85f) // Slight transparency for atmosphere
                )
                
                // Floating Lyrics removed per user request
            }
            
            Spacer(Modifier.height(32.dp))
            
            AnimatedContent(
                targetState = currentTrack,
                transitionSpec = {
                    fadeIn(tween(300)) + slideInVertically { it / 2 } togetherWith
                    fadeOut(tween(200)) + slideOutVertically { -it / 2 }
                },
                label = "trackInfo"
            ) { track ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = track?.title ?: "Ready to Jam",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = track?.artist ?: "Pick a song to start the party",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // "Added by" attribution chip
                    AnimatedVisibility(
                        visible = track?.addedBy != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(
                                    themeColors.primary.copy(alpha = 0.2f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = themeColors.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Added by ${track?.addedBy}",
                                color = themeColors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // "Up Next" preview chip
            val upNextTrack = remember(sessionState) {
                when (sessionState) {
                    is JamSessionState.Active -> sessionState.session.queue.getOrNull(1) // Index 1 = next track
                    is JamSessionState.WaitingForGuest -> sessionState.session.queue.getOrNull(1)
                    else -> null
                }
            }
            
            AnimatedVisibility(
                visible = upNextTrack != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                upNextTrack?.let { nextTrack ->
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showQueueSheet = true
                            }
                            .background(
                                JamColors.GlassSurface,
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                JamColors.GlassBorder,
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Up Next: ${nextTrack.title}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                        if (nextTrack.addedByName.isNotBlank()) {
                            Text(
                                text = "• ${nextTrack.addedByName}",
                                color = themeColors.primary.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JamProgressBar(
                    progress = progress,
                    currentTime = (progress * (currentTrack?.duration ?: 0L)).toLong(),
                    totalTime = currentTrack?.duration ?: 0L,
                    themeColors = themeColors,
                    onSeek = { newProgress ->
                        onInteraction()
                        onSeek(newProgress)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(24.dp))
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                ) {
                    AnimatedContent(
                        targetState = Pair(participants.isNotEmpty(), showInviteCode),
                        transitionSpec = {
                            fadeIn(tween(300)) + scaleIn(initialScale = 0.9f) togetherWith
                            fadeOut(tween(200)) + scaleOut(targetScale = 0.9f)
                        },
                        label = "bottomContent"
                    ) { (hasParticipants, showCode) ->
                        when {
                            hasParticipants -> {
                                // Multi-participant avatar stack
                                ParticipantAvatarStack(
                                    participants = participants,
                                    hostId = sessionInfo?.hostId ?: currentUser.id,
                                    hostName = sessionInfo?.hostName ?: currentUser.displayName,
                                    hostAvatar = sessionInfo?.hostAvatar,
                                    accentColor = themeColors.primary,
                                    secondaryColor = themeColors.secondary,
                                    onClick = { showParticipantsDrawer = true },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            showCode -> {
                                JamInviteCard(
                                    sessionCode = inviteCode,
                                    themeColors = themeColors,
                                    onInvite = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onShareSession()
                                    }
                                )
                            }
                            else -> {
                                Spacer(Modifier.height(72.dp))
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Playback controls - visible to ALL participants (guests see controls but host-only actions indicated)
                AnimatedVisibility(
                    visible = controlsVisible,  // Show to all, not just host
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    JamTransportControls(
                        isPlaying = isPlaying,
                        isHost = isHost,
                        themeColors = themeColors,
                        onPlayPause = {
                            onInteraction()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayPause()
                        },
                        onPrevious = {
                            onInteraction()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSkipPrevious()
                        },
                        onNext = {
                            onInteraction()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSkipNext()
                        },
                        modifier = Modifier.alpha(controlsAlpha)
                    )
                }
                
                AnimatedVisibility(
                    visible = !controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = "Tap to show controls", // Removed swipe inst
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Clean Bottom Bar - All buttons in a single row with proper spacing
        // Hide when transport controls are visible to avoid clutter
        AnimatedVisibility(
            visible = (sessionState is JamSessionState.Active || sessionState is JamSessionState.WaitingForGuest) && !isChatOpen && !controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Queue button
                JamGlassIconButton(
                    icon = Icons.Rounded.QueueMusic,
                    contentDescription = "Queue",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showQueueSheet = true 
                    },
                    size = 48.dp
                )
                
                // Right: Action buttons group
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Song
                    JamGlassIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Add Song",
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSearchSheet = true 
                        },
                        size = 44.dp
                    )
                    
                    // Lyrics
                    JamGlassIconButton(
                        icon = Icons.Rounded.Mic,
                        contentDescription = "Lyrics",
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLyricsSheet = true 
                        },
                        size = 44.dp
                    )
                    
                    // Chat
                    JamChatButton(
                        unreadCount = unreadChatCount,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleChat()
                        }
                    )
                }
            }
        }
        
        // Chat panel (slides up from bottom)
        AnimatedVisibility(
            visible = isChatOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            JamChatPanel(
                messages = chatMessages,
                currentUserId = currentUser.id,
                onSendMessage = onSendChatMessage,
                onClose = onToggleChat
            )
        }
        
        // Overlays
        when (sessionState) {
            is JamSessionState.Creating,
            is JamSessionState.Joining -> {
                JamLoadingOverlay()
            }
            is JamSessionState.Error -> {
                JamErrorOverlay(
                    error = sessionState.message,
                    onRetry = { },
                    onDismiss = onLeaveSession
                )
            }
            else -> { }
        }

        // Reaction Overlay (re-enabled for social experience)
        val sessionReactions = remember(sessionState) {
            when (sessionState) {
                is JamSessionState.Active -> sessionState.session.reactions
                else -> emptyMap()
            }
        }
        
        JamReactionOverlay(
            reactions = sessionReactions,
            modifier = Modifier.fillMaxSize()
        )
        
        // Participants Drawer (modal bottom sheet)
        ParticipantsDrawer(
            isVisible = showParticipantsDrawer,
            participants = participants,
            hostId = sessionInfo?.hostId ?: currentUser.id,
            hostName = sessionInfo?.hostName ?: currentUser.displayName,
            hostAvatar = sessionInfo?.hostAvatar,
            currentUserId = currentUser.id,
            isCurrentUserHost = isHost,
            accentColor = themeColors.primary,
            secondaryColor = themeColors.secondary,
            onDismiss = { showParticipantsDrawer = false },
            onRemoveParticipant = { /* TODO: Implement participant removal */ }
        )
        
        // Leave Confirmation Dialog
        LeaveConfirmationDialog(
            isVisible = showLeaveConfirmation,
            participantCount = participants.size,
            isHost = isHost,
            accentColor = themeColors.primary,
            onConfirm = {
                showLeaveConfirmation = false
                onLeaveSession()
            },
            onDismiss = { showLeaveConfirmation = false }
        )

        // Queue Sheet
        // Queue Overlay (Shared Component)
        if (showQueueSheet) {
            val sessionQueue = (sessionState as? JamSessionState.Active)?.session?.queue ?: emptyList()
            
            // Map JamQueueItem to standard Track for QueueOverlay
            val queueTracks = remember(sessionQueue) {
                sessionQueue.map { jamItem ->
                    Track(
                        id = jamItem.trackId,
                        title = jamItem.title,
                        artist = jamItem.artist,
                        remoteArtworkUrl = jamItem.artwork,
                        artwork = null, // Placeholder handled by coil
                        duration = jamItem.duration
                    )
                }
            }
            
            val currentTrackModel = uiState.currentTrack

            // Calculate current index if possible, otherwise 0
            val currentIndex = 0 

            QueueOverlay(
                currentTrack = currentTrackModel,
                currentTrackIndex = currentIndex,
                queueTracks = queueTracks,
                userQueueTracks = emptyList(), // We treat the whole jam queue as the main context queue
                contextTitle = "Jam Session",
                onDismiss = { showQueueSheet = false },
                onTrackClick = { /* Jam queue is generally read-only for playback order usually, but allow play if host? */ },
                onRemoveTrack = { index -> 
                    // Need to map index back to ID if possible or use index
                    if (index in sessionQueue.indices) {
                        onRemoveFromQueue(sessionQueue[index].trackId) 
                    }
                },
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                // Add a "Add Song" header button action? QueueOverlay might need an update for that or we just use floating button
            )
            
            // QueueOverlay doesn't have an "Add" button built-in usually, strictly for viewing/reordering.
            // But we need to allow adding.
            // We can overlay a floating button or modify QueueOverlay. 
            // For now, let's keep the Add button from the main screen accessible, OR handle it separately.
            // Actually, the user asked to use existing screens.
            // We can launch SearchSheet from here if we modified QueueOverlay, but it's a shared component.
            // Let's assume the user uses the main screen FAB or SearchSheet triggers elsewhere.
            // Wait, previous JamQueueSheet had an Add button. QueueOverlay does not seem to export an onAddClick.
            // We might need to rely on the main screen UI for adding songs, or valid user concern.
            // However, the prompt said "use existing screens... delete jam specified queue".
            // So we rely on standard QueueOverlay features.
        }

        // Lyrics Overlay (Shared Component)
        if (showLyricsSheet) {
            LyricsOverlay(
                track = uiState.currentTrack,
                currentProgress = progress,
                lyrics = lyrics,
                onDismiss = { showLyricsSheet = false }
            )
        }

        // Search Sheet
        if (showSearchSheet) {
            JamSearchSheet(
                onDismissRequest = { showSearchSheet = false },
                onSearch = onSearchTracks,
                onTrackSelected = { track ->
                    onAddToQueue(track)
                    showSearchSheet = false
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// BACKGROUND
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamModeBackground(
    themeColors: JamThemeColors,
    glowIntensity: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bgAnim")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgRotation"
    )
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    themeColors.primary.copy(alpha = 0.2f * glowIntensity),
                    Color.Transparent
                ),
                center = Offset(
                    centerX + cos(Math.toRadians(rotation.toDouble())).toFloat() * 100f,
                    centerY * 0.3f
                ),
                radius = size.width * 0.6f
            )
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    themeColors.secondary.copy(alpha = 0.15f * glowIntensity),
                    Color.Transparent
                ),
                center = Offset(
                    centerX - cos(Math.toRadians(rotation.toDouble())).toFloat() * 80f,
                    centerY * 1.5f
                ),
                radius = size.width * 0.5f
            )
        )
        
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    themeColors.tertiary.copy(alpha = 0.1f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = size.width * 0.7f
            )
        )
    }
}

@Composable
private fun JamReactionControls(
    onSendReaction: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        listOf("🔥", "❤️", "🎉").forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onSendReaction(emoji) },
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 20.sp)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TOP BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamTopBar(
    currentUser: JamUser,
    participants: List<JamParticipant>,
    themeColors: JamThemeColors,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Presence: Avatars (Minimal list)
        Row(verticalAlignment = Alignment.CenterVertically) {
           ParticipantAvatarStack(
               participants = participants.ifEmpty { 
                   // Fallback if list empty
                   listOf(JamParticipant(currentUser.id, currentUser.displayName, currentUser.avatarUrl, joinedAt = System.currentTimeMillis(), isOnline = true)) 
               },
               hostId = currentUser.id, // Using current user as host for localized display if needed
               hostName = currentUser.displayName,
               hostAvatar = currentUser.avatarUrl,
               maxVisible = 4,
               avatarSize = 32.dp,
               overlapOffset = (-10).dp
           ) 
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(
                        themeColors.primary.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "liveDot")
                val dotScale by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dotPulse"
                )
                
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(dotScale)
                        .background(themeColors.primary, CircleShape)
                )
                
                Text(
                    text = "JAM",
                    color = themeColors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            // Exit button (top right)
            JamGlassIconButton(
                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                contentDescription = "Leave session",
                onClick = onLeave,
                size = 36.dp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// PROGRESS BAR
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamProgressBar(
    progress: Float,
    currentTime: Long,
    totalTime: Long,
    themeColors: JamThemeColors,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        var isDragging by remember { mutableStateOf(false) }
        var dragProgress by remember { mutableFloatStateOf(progress) }
        
        LaunchedEffect(progress) {
            if (!isDragging) dragProgress = progress
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            onSeek(dragProgress.coerceIn(0f, 1f))
                        },
                        onDragCancel = {
                            isDragging = false
                            dragProgress = progress
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isDragging) dragProgress else progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(themeColors.primary, themeColors.secondary)
                        )
                    )
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isDragging) dragProgress else progress)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isDragging) 16.dp else 12.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .background(Color.White)
                        .then(
                            if (isDragging) {
                                Modifier.border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(themeColors.primary, themeColors.secondary)
                                    ),
                                    shape = CircleShape
                                )
                            } else Modifier
                        )
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(if (isDragging) (dragProgress * totalTime).toLong() else currentTime),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatDuration(totalTime),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TRACK INFO
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamTrackInfo(
    track: JamTrack,
    themeColors: JamThemeColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = track.artist,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        AnimatedVisibility(
            visible = track.addedBy != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .background(
                        themeColors.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = themeColors.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Added by ${track.addedBy}",
                    color = themeColors.primary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// USER CHIP
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamUserChip(
    user: JamUser,
    themeColors: JamThemeColors,
    isYou: Boolean,
    isConnected: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .background(
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(if (compact) 12.dp else 16.dp)
            )
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(if (compact) 28.dp else 36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(themeColors.primary, themeColors.secondary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = user.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White,
                        fontSize = if (compact) 12.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .size(if (compact) 10.dp else 12.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF121212))
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) JamColors.Online else JamColors.Offline)
            )
        }
        
        if (!compact) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = user.displayName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (isYou) {
                        Text(
                            text = "(You)",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
                
                if (user.isHost) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = themeColors.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Host",
                            color = themeColors.primary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// INVITE CARD
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamInviteCard(
    sessionCode: String,
    themeColors: JamThemeColors,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Improved visibility: stronger semi-transparent background
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        themeColors.primary.copy(alpha = 0.15f),
                        themeColors.secondary.copy(alpha = 0.10f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            // Add visible border for better contrast
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        themeColors.primary.copy(alpha = 0.5f),
                        themeColors.secondary.copy(alpha = 0.3f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Invite friends",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Make session code VERY visible
                Text(
                    text = sessionCode,
                    color = Color.White,  // Changed to pure white for maximum contrast
                    fontSize = 22.sp,     // Larger font
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp  // More spacing for readability
                )
                IconButton(
                    onClick = {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Jam Code", sessionCode)
                        clipboardManager.setPrimaryClip(clip)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = themeColors.primary,  // Use theme color for visibility
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        SmallActionButton(
            text = "Share",
            icon = Icons.AutoMirrored.Rounded.Send,
            themeColors = themeColors,
            onClick = onInvite
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SMALL ACTION BUTTON
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmallActionButton(
    text: String,
    icon: ImageVector,
    themeColors: JamThemeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(themeColors.primary, themeColors.secondary)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// TRANSPORT CONTROLS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamTransportControls(
    isPlaying: Boolean,
    isHost: Boolean,
    themeColors: JamThemeColors,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Simplified: Only core playback controls for calm Jam Mode
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous",
            onClick = onPrevious,
            enabled = isHost,
            size = 40.dp
        )
        
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(themeColors.primary, themeColors.secondary)
                    )
                )
                .clickable(enabled = isHost, onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        TransportButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next",
            onClick = onNext,
            enabled = isHost,
            size = 40.dp
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 32.dp,
    tint: Color = Color.White
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size + 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.3f),
            modifier = Modifier.size(size)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// OVERLAYS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "loading")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "loadingRotation"
            )
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotation)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                JamColors.Cyberpunk,
                                JamColors.Sunset,
                                JamColors.Cyberpunk
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            
            Text(
                text = "Connecting to Jam...",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun JamErrorOverlay(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = JamColors.Sunset,
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Connection Error",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = error,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text("Leave")
                }
                
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JamColors.Sunset
                    )
                ) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun JamDisconnectedOverlay(
    onReconnect: () -> Unit,
    onLeave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            
            Text(
                text = "Disconnected",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Lost connection to the Jam session",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onLeave,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                ) {
                    Text("Leave")
                }
                
                Button(
                    onClick = onReconnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JamColors.Cyberpunk
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reconnect")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// GLASS ICON BUTTON
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun JamGlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// UTILITIES
// ═══════════════════════════════════════════════════════════════════════════════

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
