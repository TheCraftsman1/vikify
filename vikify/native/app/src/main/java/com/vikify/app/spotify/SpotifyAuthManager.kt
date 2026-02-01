/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.spotify

import com.vikify.app.BuildConfig

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Vikify Spotify Integration Manager
 * Handles OAuth flow, token refresh, and playlist fetching via Vikify backend
 */
object SpotifyAuthManager {
    
    private const val TAG = "SpotifyAuthManager"
    private const val PREFS_NAME = "vikify_spotify"
    private const val SPOTIFY_API_BASE = "https://api.spotify.com/v1"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_IMAGE = "user_image"
    
    // Buffer time before expiry to refresh (5 minutes)
    private const val EXPIRY_BUFFER_MS = 5 * 60 * 1000L
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    /**
     * Check if user is connected to Spotify
     * Returns true if we have a valid token OR a refresh token (can get new token)
     */
    fun isConnected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        
        // Connected if we have a valid token OR we have a refresh token to get a new one
        return token != null && (System.currentTimeMillis() < expiry || refreshToken != null)
    }
    
    /**
     * Check if current access token is expired
     */
    private fun isTokenExpired(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiry = prefs.getLong(KEY_TOKEN_EXPIRY, 0)
        // Add buffer to refresh before actual expiry
        return System.currentTimeMillis() >= (expiry - EXPIRY_BUFFER_MS)
    }
    
    /**
     * Get stored access token (raw, without refresh check)
     */
    fun getAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    /**
     * Get a valid access token, refreshing if necessary
     * This should be used for API calls
     */
    suspend fun getValidAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        
        if (token == null) return@withContext null
        
        // If token is expired and we have a refresh token, try to refresh
        if (isTokenExpired(context) && refreshToken != null) {
            Log.d(TAG, "Access token expired, attempting refresh...")
            val success = refreshAccessToken(context)
            if (success) {
                Log.d(TAG, "Token refresh successful")
                return@withContext prefs.getString(KEY_ACCESS_TOKEN, null)
            } else {
                Log.w(TAG, "Token refresh failed")
                return@withContext null
            }
        }
        
        return@withContext token
    }
    
    /**
     * Get stored refresh token
     */
    fun getRefreshToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }
    
    /**
     * Get stored user name
     */
    fun getUserName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, null)
    }
    
    /**
     * Get stored user image URL
     */
    fun getUserImage(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_IMAGE, null)
    }
    
    // PKCE OAuth constants
    private const val SPOTIFY_AUTH_URL = "https://accounts.spotify.com/authorize"
    private const val REDIRECT_URI = "vikify://spotify/callback"
    private const val KEY_CODE_VERIFIER = "code_verifier"
    
    // Scopes needed for playlist access
    private val SCOPES = listOf(
        "user-read-private",
        "user-read-email",
        "playlist-read-private",
        "playlist-read-collaborative",
        "user-library-read"
    ).joinToString(" ")
    
    /**
     * Generate a secure code verifier for PKCE
     */
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }
    
    /**
     * Generate code challenge from verifier (S256 method)
     */
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
    }
    
    /**
     * Start Spotify OAuth flow using PKCE (no backend needed)
     * Opens browser directly to Spotify's authorization endpoint
     */
    fun startAuthFlow(context: Context) {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        
        // Generate PKCE code verifier and challenge
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        
        // Store code verifier for later use in token exchange
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()
        
        // Build authorization URL
        val authUrl = Uri.parse(SPOTIFY_AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", codeChallenge)
            .build()
        
        Log.d(TAG, "Starting PKCE auth flow with redirect: $REDIRECT_URI")
        val intent = Intent(Intent.ACTION_VIEW, authUrl)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * Handle OAuth callback from deep link
     * Called when app receives vikify://spotify/callback?code=...
     * Exchanges authorization code for access token using PKCE
     */
    fun handleAuthCallback(context: Context, uri: Uri): Boolean {
        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")
        
        if (error != null) {
            Log.e(TAG, "OAuth error: $error")
            return false
        }
        
        if (code != null) {
            Log.d(TAG, "Received auth code, exchanging for token...")
            // Exchange code for token in background
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                exchangeCodeForToken(context, code)
            }
            return true
        }
        
        // Fallback: check for direct token (legacy flow)
        val accessToken = uri.getQueryParameter("access_token")
        val refreshToken = uri.getQueryParameter("refresh_token")
        val expiresIn = uri.getQueryParameter("expires_in")?.toLongOrNull() ?: 3600L
        
        if (accessToken != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_ACCESS_TOKEN, accessToken)
                refreshToken?.let { putString(KEY_REFRESH_TOKEN, it) }
                putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000))
                apply()
            }
            Log.d(TAG, "Auth callback handled (legacy) - token saved")
            return true
        }
        return false
    }
    
    /**
     * Exchange authorization code for access token using PKCE
     */
    private suspend fun exchangeCodeForToken(context: Context, code: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val codeVerifier = prefs.getString(KEY_CODE_VERIFIER, null) ?: return@withContext false
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        
        try {
            val requestBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("client_id", clientId)
                .add("code_verifier", codeVerifier)
                .build()
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext false
                val json = JSONObject(body)
                
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                
                if (accessToken.isNotEmpty()) {
                    prefs.edit().apply {
                        putString(KEY_ACCESS_TOKEN, accessToken)
                        putString(KEY_REFRESH_TOKEN, refreshToken)
                        putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000))
                        remove(KEY_CODE_VERIFIER) // Clean up
                        apply()
                    }
                    Log.d(TAG, "PKCE token exchange successful!")
                    return@withContext true
                }
            } else {
                Log.e(TAG, "Token exchange failed: ${response.code} - ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
        }
        return@withContext false
    }
    
    /**
     * Refresh the access token using the stored refresh token
     * Returns true if refresh was successful
     */
    // Security: Credentials loaded from BuildConfig (set via local.properties)
    private val CLIENT_ID = BuildConfig.SPOTIFY_CLIENT_ID
    private val CLIENT_SECRET = BuildConfig.SPOTIFY_CLIENT_SECRET
    private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    
    /**
     * Refresh the access token using the stored refresh token
     * Returns true if refresh was successful
     */
    suspend fun refreshAccessToken(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) 
            ?: return@withContext false
        
        try {
            Log.d(TAG, "Refreshing Spotify access token (Direct)...")
            
            val requestBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
            
            val authString = "$CLIENT_ID:$CLIENT_SECRET"
            val authHeader = "Basic " + android.util.Base64.encodeToString(
                authString.toByteArray(), 
                android.util.Base64.NO_WRAP
            )
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(requestBody)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext false
                val json = JSONObject(body)
                
                // Spotify API returns explicit fields, not "success" wrapper
                val newAccessToken = json.optString("access_token")
                val newRefreshToken = json.optString("refresh_token", null) // Might not always return a new one
                val expiresIn = json.optLong("expires_in", 3600L)
                val scope = json.optString("scope", "")
                
                if (newAccessToken.isNotEmpty()) {
                    prefs.edit().apply {
                        putString(KEY_ACCESS_TOKEN, newAccessToken)
                        // Only update refresh token if a new one was provided
                        if (!newRefreshToken.isNullOrEmpty()) {
                            putString(KEY_REFRESH_TOKEN, newRefreshToken)
                        }
                        putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresIn * 1000))
                        apply()
                    }
                    Log.d(TAG, "Token refreshed successfully, new expiry in ${expiresIn}s")
                    return@withContext true
                }
            } else {
                Log.e(TAG, "Token refresh HTTP error: ${response.code} ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh exception", e)
        }
        
        return@withContext false
    }
    
    /**
     * Save user profile data
     */
    fun saveUserProfile(context: Context, name: String, imageUrl: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_USER_NAME, name)
            imageUrl?.let { putString(KEY_USER_IMAGE, it) }
            apply()
        }
    }
    
    /**
     * Disconnect from Spotify (clear tokens)
     */
    fun disconnect(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Spotify disconnected - all tokens cleared")
    }
    
    /**
     * Fetch user's Spotify playlists with pagination (calls Spotify API directly)
     * Uses auto-refresh token logic
     */
    suspend fun fetchPlaylists(context: Context): List<SpotifyPlaylistInfo> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext emptyList()
        val result = mutableListOf<SpotifyPlaylistInfo>()
        var nextUrl: String? = "$SPOTIFY_API_BASE/me/playlists?limit=50"
        
        try {
            while (nextUrl != null) {
                val request = Request.Builder()
                    .url(nextUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: break
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: break
                    
                    for (i in 0 until items.length()) {
                        val playlist = items.getJSONObject(i)
                        result.add(SpotifyPlaylistInfo(
                            id = playlist.optString("id"),
                            name = playlist.optString("name"),
                            imageUrl = playlist.optJSONArray("images")?.optJSONObject(0)?.optString("url"),
                            trackCount = playlist.optJSONObject("tracks")?.optInt("total", 0) ?: 0,
                            owner = playlist.optJSONObject("owner")?.optString("display_name", "") ?: ""
                        ))
                    }
                    
                    // Get next page URL
                    nextUrl = if (json.isNull("next")) null else json.optString("next", null)
                    Log.d(TAG, "Fetched ${result.size} playlists so far")
                    
                } else if (response.code == 401) {
                    Log.w(TAG, "401 response, attempting token refresh...")
                    if (refreshAccessToken(context)) {
                        // Retry from start with new token
                        return@withContext fetchPlaylists(context)
                    }
                    break
                } else {
                    Log.e(TAG, "API error: ${response.code}")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlists", e)
        }
        
        Log.d(TAG, "Total playlists fetched: ${result.size}")
        result
    }
    
    /**
     * Playlist info from SpotifyAuthManager (consistent with SpotifyRepository.SpotifyPlaylist)
     */
    data class SpotifyPlaylistInfo(
        val id: String,
        val name: String,
        val imageUrl: String? = null,
        val trackCount: Int = 0,
        val owner: String = ""
    )
}

