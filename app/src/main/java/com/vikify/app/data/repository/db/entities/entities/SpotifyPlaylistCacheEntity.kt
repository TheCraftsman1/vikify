package com.vikify.app.data.repository.db.entities.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Cache entity for Spotify playlists.
 * 
 * This allows offline access to Spotify playlists by storing:
 * - Playlist metadata (name, image, track count)
 * - Last sync time for cache invalidation
 * 
 * Track data is stored in SongEntity with spotifyId field,
 * linked via SpotifyPlaylistTrackMap.
 */
@Immutable
@Entity(
    tableName = "spotify_playlist_cache",
    indices = [
        Index(value = ["lastSynced"])
    ]
)
data class SpotifyPlaylistCacheEntity(
    @PrimaryKey 
    val spotifyId: String,  // Spotify playlist ID
    
    val name: String,
    
    val imageUrl: String? = null,
    
    val trackCount: Int = 0,
    
    val owner: String? = null,
    
    @ColumnInfo(name = "lastSynced")
    val lastSynced: LocalDateTime = LocalDateTime.now()
)

/**
 * Junction table to link Spotify playlists to their tracks.
 * 
 * Note: Songs are stored in the main song table with spotifyId field.
 * This table maintains the playlist-to-track relationship.
 */
@Entity(
    tableName = "spotify_playlist_track_map",
    primaryKeys = ["playlistId", "spotifyTrackId"]
)
data class SpotifyPlaylistTrackMap(
    val playlistId: String,  // Spotify playlist ID (from SpotifyPlaylistCacheEntity)
    val spotifyTrackId: String,  // Spotify track ID
    val position: Int = 0  // Track position in playlist
)
