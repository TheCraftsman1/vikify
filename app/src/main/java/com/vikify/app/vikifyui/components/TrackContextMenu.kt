package com.vikify.app.vikifyui.components

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*

/**
 * Track Context Menu
 * 
 * Modal bottom sheet menu shown on long-press of any track.
 * Provides quick actions: Queue, Like, Playlist, Download, Share, etc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextMenu(
    track: Track,
    isLiked: Boolean,
    isDownloaded: Boolean,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    
    // Modal Bottom Sheet
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFF8F8F8),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.LightGray)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Track Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.LightGray)
                ) {
                    AsyncImage(
                        model = track.remoteArtworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = VikifyTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        fontSize = 14.sp,
                        color = VikifyTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Divider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = Color.LightGray.copy(alpha = 0.5f)
            )
            
            // Menu Items
            ContextMenuItem(
                icon = Icons.Rounded.QueuePlayNext,
                label = "Play Next",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onPlayNext()
                    onDismiss()
                }
            )
            
            ContextMenuItem(
                icon = Icons.Rounded.AddToQueue,
                label = "Add to Queue",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onAddToQueue()
                    onDismiss()
                }
            )
            
            ContextMenuItem(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                tint = if (isLiked) Color(0xFFED5564) else VikifyTheme.colors.textPrimary,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onToggleLike()
                    onDismiss()
                }
            )
            
            ContextMenuItem(
                icon = Icons.Rounded.PlaylistAdd,
                label = "Add to Playlist",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onAddToPlaylist()
                    onDismiss()
                }
            )
            
            ContextMenuItem(
                icon = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                label = if (isDownloaded) "Downloaded" else "Download",
                tint = if (isDownloaded) Color(0xFF4CAF50) else VikifyTheme.colors.textPrimary,
                enabled = !isDownloaded,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onDownload()
                    onDismiss()
                }
            )
            
            ContextMenuItem(
                icon = Icons.Rounded.Share,
                label = "Share",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    // Create share intent
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out ${track.title} by ${track.artist}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Song"))
                    onDismiss()
                }
            )
            
            // Optional navigation items
            if (onGoToArtist != null) {
                Divider(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = Color.LightGray.copy(alpha = 0.5f)
                )
                
                ContextMenuItem(
                    icon = Icons.Rounded.Person,
                    label = "Go to Artist",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onGoToArtist()
                        onDismiss()
                    }
                )
            }
            
            if (onGoToAlbum != null) {
                ContextMenuItem(
                    icon = Icons.Rounded.Album,
                    label = "Go to Album",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onGoToAlbum()
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Individual Context Menu Item
 */
@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = VikifyTheme.colors.textPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else VikifyTheme.colors.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = if (enabled) tint else VikifyTheme.colors.textSecondary.copy(alpha = 0.5f)
        )
    }
}

/**
 * Long-press wrapper for any track item
 * 
 * Usage:
 * LongPressTrackWrapper(
 *     track = track,
 *     onPlayNext = { ... },
 *     ...
 * ) {
 *     // Your track card content
 *     TrackRow(...)
 * }
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressTrackWrapper(
    track: Track,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onDownload: () -> Unit = {},
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier.combinedClickable(
            onClick = { }, // No-op, let parent handle tap
            onLongClick = { showMenu = true }
        )
    ) {
        content()
    }
    
    if (showMenu) {
        TrackContextMenu(
            track = track,
            isLiked = isLiked,
            isDownloaded = isDownloaded,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onToggleLike = onToggleLike,
            onAddToPlaylist = onAddToPlaylist,
            onDownload = onDownload,
            onShare = {}, // Share handled inside TrackContextMenu
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
            onDismiss = { showMenu = false }
        )
    }
}
