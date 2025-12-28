package com.vikify.app.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Spotify Authentication & API Repository
 * 
 * Handles OAuth flow and API calls to Spotify.
 */
class SpotifyRepository(
    private val context: Context
) {
    companion object {
        const val CLIENT_ID = "242fffd1ca15426ab8c7396a6931b780"
        private const val CLIENT_SECRET = "5a479c5370ba48bc860048d89878ee4d"
        private const val REDIRECT_URI = "vikify://spotify-callback"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        
        private const val SCOPES = "user-read-private user-read-email playlist-read-private playlist-read-collaborative user-library-read"
    }
    
    private val prefs = context.getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)
    
    var accessToken: String?
        get() = prefs.getString("access_token", null)
        set(value) = prefs.edit().putString("access_token", value).apply()
    
    var refreshToken: String?
        get() = prefs.getString("refresh_token", null)
        set(value) = prefs.edit().putString("refresh_token", value).apply()
    
    val isLoggedIn: Boolean
        get() = accessToken != null
    
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
                accessToken = json.getString("access_token")
                refreshToken = json.optString("refresh_token", null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        // Spotify returns "items" -> "track" object
        apiGet("$API_BASE/playlists/$playlistId/tracks?limit=100")?.let { json ->
            val items = json.optJSONArray("items")
            if (items != null) {
                for (i in 0 until items.length()) {
                    val itemWrapper = items.getJSONObject(i)
                    val trackParams = itemWrapper.optJSONObject("track")
                    if (trackParams != null) {
                         // Parse artists
                         val artistsJson = trackParams.optJSONArray("artists")
                         val artistName = artistsJson?.optJSONObject(0)?.optString("name") ?: "Unknown"
                         
                         // Parse duration (ms)
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
     * Log out - clear tokens
     */
    fun logout() {
        accessToken = null
        refreshToken = null
    }
    
    private suspend fun apiGet(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            
            if (conn.responseCode == 200) {
                JSONObject(conn.inputStream.bufferedReader().readText())
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
    val duration: Long
)
