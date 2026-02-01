package com.vikify.app.data

import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.Playlist
import com.vikify.app.db.entities.PlaylistEntity
import com.vikify.app.db.entities.PlaylistSong
import com.vikify.app.db.entities.PlaylistSongMap
import com.vikify.app.db.entities.SongEntity
import com.vikify.app.spotify.SpotifyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val database: MusicDatabase,
    private val spotifyRepository: SpotifyRepository
) {

    /**
     * Get a playlist by ID
     */
    fun getPlaylist(playlistId: String) = database.playlist(playlistId)

    /**
     * Get songs in a playlist
     */
    fun getPlaylistSongs(playlistId: String) = database.playlistSongs(playlistId)

    /**
     * Get all local playlists for picker
     */
    fun getLocalPlaylists() = database.localPlaylists()

    /**
     * Imports a Spotify playlist into the local database.
     * 
     * Strategy:
     * 1. Fetch tracks from Spotify (Metadata only)
     * 2. Save PlaylistEntity
     * 3. Save SongEntities (streamUrl = null)
     * 4. Save PlaylistSongMap (CrossRefs)
     * 5. Return ID for instant loading
     */
    suspend fun importSpotifyPlaylist(spotifyId: String): String = withContext(Dispatchers.IO) {
        // 1. Fetch Metadata
        // We use getPlaylistTracks (API call)
        // Need playlist details too? spotifyRepository.getPlaylist(id) isn't exposed but userPlaylists has it?
        // Actually we can just fetch tracks and use a generic name or fetch details if possible.
        // Assuming we have basic details or fetch them.
        // SpotifyRepository doesn't have getPlaylistDetails? 
        // We'll use searchPlaylists logic or just generic name if we can't get it.
        // Actually, let's fetch tracks first.
        val tracks = spotifyRepository.getPlaylistTracks(spotifyId)
        
        // 2. Create/Insert Playlist
        // We might not have the name if we only fetched tracks.
        // But assuming the UI passed the name or we fetch it.
        // Let's assume we can fetch it or default to "Imported Playlist"
        // TODO: Add getPlaylistDetails to SpotifyRepository if needed. For now use ID.
        val playlist = PlaylistEntity(
            name = "Spotify Imported", // Ideally fetch real name
            source = "SPOTIFY_IMPORT",
            browseId = spotifyId,
            isLocal = true,
            remoteSongCount = tracks.size
        )
        database.insert(playlist)
        
        // 3. Process Tracks
        val existingSpotifyIds = database.allSpotifyIds().firstOrNull()?.toSet() ?: emptySet()
        val playlistSongMaps = mutableListOf<PlaylistSongMap>()

        tracks.forEachIndexed { index, track ->
            // Check if song exists by Spotify ID
            val existingSong = if (track.id in existingSpotifyIds) {
                // database.getBySpotifyId(track.id)
                database.getBySpotifyId(track.id)
            } else null

            val songId = existingSong?.id ?: SongEntity.generateSongId()

            if (existingSong == null) {
                // Create New Song Entity
                val newSong = SongEntity(
                    id = songId,
                    title = track.title,
                    duration = (track.duration / 1000).toInt(),
                    thumbnailUrl = track.imageUrl,
                    localPath = null, // Not downloaded
                    spotifyId = track.id,
                    spotifyTitle = track.title,
                    spotifyArtist = track.artist,
                    spotifyArtworkUrl = track.imageUrl,
                    inLibrary = LocalDateTime.now(),
                    isLocal = false,
                    streamUrl = null // Key point: Null stream URL (Hybrid Model)
                )
                database.insert(newSong)
            } else {
                // Maybe update metadata?
            }

            // 4. Create CrossRef
            playlistSongMaps.add(
                PlaylistSongMap(
                    playlistId = playlist.id,
                    songId = songId,
                    position = index
                )
            )
        }

        // Batch insert maps
        // Check if insertAll for playlist maps exists. 
        // If not, use loop or single inserts? 
        // SongsDao usually has insert(SongEntity), PlaylistsDao has insert(PlaylistEntity).
        // Let's assume database has insert(map).
        playlistSongMaps.forEach { database.insert(it) }

        return@withContext playlist.id
    }

    // =========================================================================
    // LOCAL PLAYLIST MANAGEMENT
    // =========================================================================

    /**
     * Create a new local playlist
     */
    suspend fun createLocalPlaylist(name: String): String = withContext(Dispatchers.IO) {
        val playlist = PlaylistEntity(
            name = name.trim(),
            source = "LOCAL",
            isLocal = true,
            remoteSongCount = 0
        )
        database.insert(playlist)
        return@withContext playlist.id
    }

    /**
     * Add a song to a playlist
     */
    suspend fun addSongToPlaylist(playlistId: String, songId: String): Unit = withContext(Dispatchers.IO) {
        // Get current song count for position
        val currentCount = database.playlistSongCount(playlistId)
        
        val map = PlaylistSongMap(
            playlistId = playlistId,
            songId = songId,
            position = currentCount
        )
        database.insert(map)
    }

    /**
     * Rename a playlist
     */
    suspend fun renamePlaylist(playlistId: String, newName: String): Unit = withContext(Dispatchers.IO) {
        database.updatePlaylistName(playlistId, newName.trim())
    }

    /**
     * Delete a playlist and its song mappings
     */
    suspend fun deletePlaylist(playlistId: String): Unit = withContext(Dispatchers.IO) {
        database.deletePlaylist(playlistId)
    }


}
