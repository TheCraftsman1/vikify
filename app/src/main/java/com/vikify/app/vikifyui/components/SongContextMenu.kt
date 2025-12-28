package com.vikify.app.vikifyui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextMenu(
    track: Track,
    onDismissRequest: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onShareClick: () -> Unit,
    onViewAlbumClick: () -> Unit,
    isLiked: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ─────────────────────────────────────────────────────────────────
            // HEADER: "Vikify-Style" Now Playing Card
            // ─────────────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                RaataanPurple.copy(alpha = 0.8f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
                    .height(100.dp) // Fixed height for the card
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Now Playing Label + Song Info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    // RIGHT: Album Art + Spinning Vinyl
                    // Similar to NowPlayingVinylRow but compact
                    Box(
                        modifier = Modifier.width(80.dp), // Container for the visual
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        // Vinyl Disc (Spinning)
                        val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
                        val angle by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(6000, easing = LinearEasing)
                            ),
                            label = "angle"
                        )
                        
                        // Vinyl Layer (Behind Art, Protruding)
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .offset(x = 12.dp) // Protrude right
                                .rotate(angle)
                        ) {
                            // Disc base
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(Color(0xFF111111))
                            )
                            // Grooves (simulate with borders/gradients if needed, keeping simple for now)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(0.95f)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(Color.DarkGray, Color.Black)
                                        )
                                    )
                            )
                            // Label
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center)
                                    .clip(CircleShape)
                                    .background(RaataanPink)
                            )
                        }
                        
                        // Album Art (Front)
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            elevation = CardDefaults.cardElevation(4.dp),
                            modifier = Modifier.size(60.dp)
                        ) {
                            VikifyImage(
                                url = track.remoteArtworkUrl,
                                placeholder = track.artwork,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ─────────────────────────────────────────────────────────────────
            // MENU ITEMS
            // ─────────────────────────────────────────────────────────────────
            MenuActionItem(
                icon = if (isLiked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                text = if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                tint = if (isLiked) RaataanPink else LocalVikifyColors.current.textPrimary,
                onClick = {
                    onLikeClick()
                    onDismissRequest()
                }
            )
            
            MenuActionItem(
                icon = Icons.Outlined.PlaylistAdd,
                text = "Add to playlist",
                onClick = {
                    onAddToPlaylistClick()
                    onDismissRequest()
                }
            )
            
            MenuActionItem(
                icon = Icons.Outlined.Album,
                text = "View Album",
                onClick = {
                    onViewAlbumClick()
                    onDismissRequest()
                }
            )
            
            MenuActionItem(
                icon = Icons.Outlined.Share,
                text = "Share",
                onClick = {
                    onShareClick()
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
private fun MenuActionItem(
    icon: ImageVector,
    text: String,
    tint: Color = LocalVikifyColors.current.textPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalVikifyColors.current.textPrimary
        )
    }
}
