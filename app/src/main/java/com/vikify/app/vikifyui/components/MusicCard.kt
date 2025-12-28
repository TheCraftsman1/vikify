package com.vikify.app.vikifyui.components
 
import com.vikify.app.vikifyui.components.VikifyImage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.data.Album
import com.vikify.app.vikifyui.data.Track
import androidx.compose.ui.graphics.Color
import com.vikify.app.vikifyui.theme.VikifyTheme
import com.vikify.app.vikifyui.components.ObsidianCard
import com.vikify.app.vikifyui.components.VaporCard

/**
 * Music Card
 * 
 * Reusable card for albums, tracks, playlists
 * Clean, minimal design with artwork + text
 */

@Composable
fun MusicCard(
    title: String,
    subtitle: String,
    artworkRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    artworkUrl: String? = null
) {
    val isDark = VikifyTheme.isDark
    val colors = com.vikify.app.vikifyui.theme.LocalVikifyColors.current
    
    // Determine colors based on theme
    val surfaceColor = colors.surface
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary

    Column(
        modifier = modifier
            .width(size)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Artwork
        Box(modifier = Modifier.size(size)) {
            if (isDark) {
                // VIKIFY GLASS CARD
                // "Holographic & Deep" - Replaces ObsidianCard
                VikifyGlassCard(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    VikifyImage(
                        url = artworkUrl,
                        placeholder = artworkRes,
                        contentDescription = title,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp) // Subtle padding inside glass
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            } else {
                // VaporCard for Ethereal Day
                VaporCard(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = Color.White.copy(alpha = 0.7f),
                    content = {
                        VikifyImage(
                            url = artworkUrl,
                            placeholder = artworkRes,
                            contentDescription = title,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                )
            }
        }
        
        // Text info
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    MusicCard(
        title = album.title,
        subtitle = album.artist,
        artworkRes = album.artwork,
        onClick = onClick,
        modifier = modifier,
        size = size,
        artworkUrl = album.remoteArtworkUrl
    )
}

@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    MusicCard(
        title = track.title,
        subtitle = track.artist,
        artworkRes = track.artwork,
        onClick = onClick,
        modifier = modifier,
        size = size,
        artworkUrl = track.remoteArtworkUrl
    )
}

/**
 * Track List Item
 * 
 * Horizontal layout for lists (recently played, queue)
 */
@Composable
fun TrackListItem(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    val colors = com.vikify.app.vikifyui.theme.LocalVikifyColors.current
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork
        VikifyImage(
            url = track.remoteArtworkUrl,
            placeholder = track.artwork,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surface)
        )
        
        // Track info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary, // Theme aware
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary, // Theme aware
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Optional trailing content
        trailing?.invoke()
    }
}

