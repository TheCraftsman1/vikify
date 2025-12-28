package com.vikify.app.vikifyui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vikify.app.vikifyui.data.Album
import com.vikify.app.vikifyui.data.Track
import com.vikify.app.vikifyui.theme.TextPrimary

/**
 * Section Row
 * 
 * Horizontal scrolling row with section header
 * Used for album/track carousels on Home screen
 */

@Composable
fun SectionRow(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = contentPadding)
        )
        
        content()
    }
}

@Composable
fun AlbumRow(
    title: String,
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp
) {
    SectionRow(title = title, modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album) },
                    size = cardSize
                )
            }
        }
    }
}

@Composable
fun TrackRow(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
    cardSize: Dp = 140.dp
) {
    SectionRow(title = title, modifier = modifier) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackCard(
                    track = track,
                    onClick = { onTrackClick(track) },
                    size = cardSize
                )
            }
        }
    }
}

/**
 * Recent List
 * 
 * Vertical list of recently played tracks
 */
@Composable
fun RecentList(
    title: String,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section header
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        
        // Track list
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tracks.forEach { track ->
                TrackListItem(
                    track = track,
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}

