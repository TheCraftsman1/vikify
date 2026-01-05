package com.vikify.app.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.vikify.app.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spotify Authentication & API Repository
 * 
 * Handles OAuth flow and API calls to Spotify.
 * Uses SpotifyAuthManager as single source of truth for token storage.
 */
class SpotifyRepository(
    private val context: Context
) {
    companion object {
        private const val TAG = "SpotifyRepository"
        const val CLIENT_ID = "242fffd1ca15426ab8c7396a6931b780"
        private const val CLIENT_SECRET = "5a479c5370ba48bc860048d89878ee4d"
        private const val REDIRECT_URI = "vikify://spotify-callback"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        
        private const val SCOPES = "user-read-private user-read-email playlist-read-private playlist-read-collaborative user-library-read"
    }
    
    /**
     * Check if user is logged in (has valid token or refresh token)
     */
    val isLoggedIn: Boolean
        get() = SpotifyAuthManager.isConnected(context)
    
    /**
     * Get the authorization URL to start OAuth flow
     */
    fun getAuthorizationUrl(): String {
        return "$AUTH_URL?" +
            "client_id=$CLIENT_ID" +
            "&response_type=code" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&scope=${Uri.encode(SCOPES)}" +
            "&show_dialog=true"
    }
    
    /**
     * Open browser for Spotify login
     */
    fun startLogin(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(getAuthorizationUrl())).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    
    /**
     * Exchange authorization code for access token
     * Stores tokens via SpotifyAuthManager for persistence
     */
    suspend fun exchangeCodeForToken(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(TOKEN_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            
            val credentials = android.util.Base64.encodeToString(
                "$CLIENT_ID:$CLIENT_SECRET".toByteArray(),
                android.util.Base64.NO_WRAP
            )
            conn.setRequestProperty("Authorization", "Basic $credentials")
            
            val postData = "grant_type=authorization_code" +
                "&code=$code" +
                "&redirect_uri=${Uri.encode(REDIRECT_URI)}"
            
            conn.outputStream.use { it.write(postData.toByteArray()) }
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val accessToken = json.getString("access_token")
                val refreshToken = json.optString("refresh_token", null)
                val expiresIn = json.optLong("expires_in", 3600L)
                
                // Store tokens via SpotifyAuthManager for unified persistence
                val prefs = context.getSharedPreferences("vikify_spotify", Context.MODE_PRIVATE)
                prefs.edit().apply {
                    putString("access_token", accessToken)
                    refreshToken?.let { putString("refresh_token", it) }
                    putLong("token_expiry", System.currentTimeMillis() + (expiresIn * 1000))
                    apply()
                }
                
                Log.d(TAG, "Token exchange successful, expires in ${expiresIn}s")
                true
            } else {
                Log.e(TAG, "Token exchange failed: ${conn.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
            false
        }
    }
    
    /**
     * Get current user profile
     */
    suspend fun getCurrentUser(): SpotifyUser? = withContext(Dispatchers.IO) {
        apiGet("$API_BASE/me")?.let { json ->
            SpotifyUser(
                id = json.getString("id"),
                displayName = json.optString("display_name", "User"),
                email = json.optString("email", ""),
                imageUrl = json.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                followers = json.optJSONObject("followers")?.optInt("total", 0) ?: 0
            )
        }
    }
    
    /**
     * Get user's playlists
     */
    suspend fun getUserPlaylists(): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyPlaylist>()
        apiGet("$API_BASE/me/playlists?limit=50")?.let { json ->
            val items = json.getJSONArray("items")
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                result.add(SpotifyPlaylist(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                    trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                    owner = item.optJSONObject("owner")?.optString("display_name", "") ?: ""
                ))
            }
        }
        result
    }
    
    /**
     * Search for playlists
     */
    suspend fun searchPlaylists(query: String): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyPlaylist>()
        apiGet("$API_BASE/search?q=${Uri.encode(query)}&type=playlist&limit=20")?.let { json ->
            val items = json.optJSONObject("playlists")?.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    result.add(SpotifyPlaylist(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        imageUrl = item.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                        trackCount = item.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                        owner = item.optJSONObject("owner")?.optString("display_name", "") ?: ""
                    ))
                }
            }
        }
        result
    }

    /**
     * Get tracks for a playlist
     */
    suspend fun getPlaylistTracks(playlistId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SpotifyTrack>()
        apiGet("$API_BASE/playlists/$playlistId/tracks?limit=100")?.let { json ->
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val itemWrapper = items.getJSONObject(i)
                    val trackParams = itemWrapper.optJSONObject("track")
                    if (trackParams != null) {
                         val artistsJson = trackParams.optJSONArray("artists")
                         val artistName = artistsJson?.optJSONObject(0)?.optString("name") ?: "Unknown"
                         val duration = trackParams.optLong("duration_ms", 0L)

                        result.add(SpotifyTrack(
                            id = trackParams.getString("id"),
                            title = trackParams.getString("name"),
                            artist = artistName,
                            imageUrl = trackParams.optJSONObject("album")?.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                            duration = duration
                        ))
                    }
                }
            }
        }
        result
    }
    
    /**
     * Batch resolve YouTube IDs for Spotify tracks
     * Uses rate-limited YouTube search to pre-resolve all tracks at once
     * Called when loading a playlist to avoid per-song API calls during playback
     */
    suspend fun resolveYouTubeIds(tracks: List<SpotifyTrack>): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val resolvedTracks = tracks.toMutableList()
        var lastApiCall = 0L
        val apiDelayMs = 300L // 300ms between calls
        
        // OPTIMIZATION: Only resolve the first 3 tracks immediately
        // This ensures the playlist opens fast and playback starts instantly.
        // Remaining tracks will be resolved on-demand when played (via PlayerViewModel cache).
        val limit = 3.coerceAtMost(tracks.size)
        
        Log.d(TAG, "Resolving first $limit tracks for instant playback...")
        
        for (i in 0 until limit) {
            val track = resolvedTracks[i]
            // Skip if already has YouTube ID
            if (track.youtubeId != null) continue
            
            try {
                // Rate limiting
                val now = System.currentTimeMillis()
                val timeSinceLastCall = now - lastApiCall
                if (timeSinceLastCall < apiDelayMs && i > 0) { // No delay for very first call
                    kotlinx.coroutines.delay(apiDelayMs - timeSinceLastCall)
                }
                lastApiCall = System.currentTimeMillis()
                
                val searchQuery = "${track.title} ${track.artist}"
                val searchResult = com.zionhuang.innertube.YouTube.search(
                    searchQuery, 
                    com.zionhuang.innertube.YouTube.SearchFilter.FILTER_SONG
                ).getOrNull()
                
                val song = searchResult?.items
                    ?.filterIsInstance<com.zionhuang.innertube.models.SongItem>()
                    ?.firstOrNull()
                
                if (song != null) {
                    resolvedTracks[i] = track.copy(youtubeId = song.id)
                    Log.d(TAG, "Resolved: ${track.title} -> ${song.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve: ${track.title}: ${e.message}")
            }
        }
        
        Log.d(TAG, "Initial resolution complete")
        resolvedTracks
    }
    
    /**
     * Get tracks for a playlist with pre-resolved YouTube IDs
     * This is the recommended method for loading playlists for playback
     */
    suspend fun getPlaylistTracksWithYouTubeIds(playlistId: String): List<SpotifyTrack> {
        val tracks = getPlaylistTracks(playlistId)
        return resolveYouTubeIds(tracks)
    }

    /**
     * Load playlist tracks and ensure they exist in the DB (Hybrid Model)
     * Maps Spotify tracks to their local DB IDs (either existing YT IDs or UNRESOLVED_xxx)
     * Inserts any missing tracks as UNRESOLVED skeltons.
     */
    suspend fun loadPlaylistAndSync(playlistId: String, database: com.vikify.app.db.MusicDatabase): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val tracks = getPlaylistTracks(playlistId)
        val existingSpotifyIds = database.allSpotifyIds().first().toSet()
        // We need a map of SpotifyID -> DB ID for existing songs
        // database.allSpotifyIds just returns strings.
        // We should add a query to get map or iterate (slow).
        // Optimization: Fetch all songs with non-null spotifyId? No, too big.
        // Just fetch the ones we need? SELECT id, spotifyId FROM songs where spotifyId IN (...)
        // Room doesn't support strict "IN list" easily if list is huge, but 100 items is fine.
        
        // For now, simpler approach given existing tools:
        // We assume "existing" means fully resolved or imported.
        // But `allSpotifyIds` only tells us it exists, not the ID.
        // We need the ID.
        
        // Let's rely on importPlaylistSkeleton-style logic BUT map the result.
        // We will query the DB for each track? No, N+1 problem.
        
        // Better:
        // 1. Fetch metadata (Done)
        // 2. Import "Skeleton" for missing ones (Done via loop below)
        // 3. For the return list, we construct IDs:
        //    - If newly inserted: "UNRESOLVED_<spotifyId>"
        //    - If existing: We MUST lookup the real ID.
        
        // To avoid N+1, let's fetch ALL songs that have a spotify ID.
        // If library is huge (10k songs), this is heavy. 
        // But `allSpotifyIds` is already doing `SELECT spotifyId FROM songs`.
        // Let's add `database.getIdsBySpotifyIds(ids)`?
        // Or just `database.getSongBySpotifyId(id)` inside the loop (since we do it for import anyway).
        
        val resultTracks = mutableListOf<SpotifyTrack>()
        var imported = 0
        
        for (track in tracks) {
             // 1. Check if exists
             val existing = database.songBySpotifyId(track.id).firstOrNull()
             
             if (existing != null) {
                 // Existing: Use DB ID (e.g. YT ID or UNRESOLVED)
                 resultTracks.add(track.copy(id = existing.id, youtubeId = existing.streamUrl))
             } else {
                 // New: Insert UNRESOLVED
                 val newId = "UNRESOLVED_${track.id}"
                 val songEntity = SongEntity(
                    id = newId,
                    title = track.title,
                    duration = (track.duration / 1000).toInt(),
                    thumbnailUrl = track.imageUrl,
                    localPath = null,
                    spotifyId = track.id,
                    spotifyTitle = track.title,
                    spotifyArtist = track.artist,
                    spotifyArtworkUrl = track.imageUrl,
                    inLibrary = java.time.LocalDateTime.now(),
                    isLocal = false
                 )
                 try {
                     database.insert(songEntity)
                     imported++
                     resultTracks.add(track.copy(id = newId, youtubeId = null))
                 } catch (e: Exception) {
                     Log.e(TAG, "Failed to insert skeleton ${track.title}", e)
                 }
             }
        }
        
        if (imported > 0) {
            SpotifySyncWorker.enqueue(context)
        }
        
        resultTracks
    }
    
    /**
     * SKELETON IMPORT - Import Spotify tracks for instant UI, resolve in background
     * 
     * Part of the "Silent Migration" protocol:
     * 1. Insert tracks with UNRESOLVED_ prefix (instant UI)
     * 2. Trigger SpotifySyncWorker for background resolution
     * 3. Existing non-Spotify songs are NOT affected
     * 
     * @param playlistId Spotify playlist ID
     * @param database MusicDatabase instance for insertion
     * @return Number of tracks imported
     */
    suspend fun importPlaylistSkeleton(
        playlistId: String, 
        database: com.vikify.app.db.MusicDatabase
    ): Int = withContext(Dispatchers.IO) {
        val tracks = getPlaylistTracks(playlistId)
        val existingSpotifyIds = database.allSpotifyIds().first().toSet()
        
        var imported = 0
        for (track in tracks) {
            // Skip if already imported
            if (track.id in existingSpotifyIds) {
                Log.d(TAG, "Skipping already imported: ${track.title}")
                continue
            }
            
            // Insert with UNRESOLVED_ prefix - will be resolved by SpotifySyncWorker
            val songEntity = SongEntity(
                id = "UNRESOLVED_${track.id}",  // Placeholder until resolved
                title = track.title,
                duration = (track.duration / 1000).toInt(),
                thumbnailUrl = track.imageUrl,
                localPath = null,
                spotifyId = track.id,
                spotifyTitle = track.title,
                spotifyArtist = track.artist,
                spotifyArtworkUrl = track.imageUrl,
                inLibrary = java.time.LocalDateTime.now()
            )
            
            try {
                database.insert(songEntity)
                imported++
                Log.d(TAG, "Imported skeleton: ${track.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import ${track.title}: ${e.message}")
            }
        }
        
        // Trigger background resolution
        if (imported > 0) {
            SpotifySyncWorker.enqueue(context)
            Log.i(TAG, "Skeleton import complete: $imported tracks. Background sync started.")
        }
        
        imported
    }
    
    /**
     * Log out - clear tokens via SpotifyAuthManager
     */
    fun logout() {
        SpotifyAuthManager.disconnect(context)
    }
    
    /**
     * Make authenticated API call with automatic token refresh
     */
    private suspend fun apiGet(url: String, retryOnUnauthorized: Boolean = true): JSONObject? = withContext(Dispatchers.IO) {
        try {
            // Get valid token (with automatic refresh if expired)
            val token = SpotifyAuthManager.getValidAccessToken(context)
            if (token == null) {
                Log.w(TAG, "No valid access token available")
                return@withContext null
            }
            
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $token")
            
            when (conn.responseCode) {
                200 -> JSONObject(conn.inputStream.bufferedReader().readText())
                401 -> {
                    // Token rejected, try to refresh and retry once
                    if (retryOnUnauthorized) {
                        Log.d(TAG, "401 response, attempting token refresh...")
                        val refreshed = SpotifyAuthManager.refreshAccessToken(context)
                        if (refreshed) {
                            return@withContext apiGet(url, retryOnUnauthorized = false)
                        }
                    }
                    Log.e(TAG, "Unauthorized even after refresh")
                    null
                }
                else -> {
                    Log.e(TAG, "API error: ${conn.responseCode}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "API call exception", e)
            null
        }
    }
}

data class SpotifyUser(
    val id: String,
    val displayName: String,
    val email: String,
    val imageUrl: String?,
    val followers: Int
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val trackCount: Int,
    val owner: String
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val imageUrl: String?,
    val duration: Long,
    var youtubeId: String? = null  // Pre-resolved YouTube ID (filled by batch resolver)
)
