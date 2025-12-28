/*
 * Copyright (C) 2025 Vikify Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */
package com.vikify.app.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
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
    private const val BACKEND_URL = "https://vikify-production.up.railway.app"
    private const val PREFS_NAME = "vikify_spotify"
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
    
    /**
     * Start Spotify OAuth flow via backend
     * Opens browser to backend auth endpoint
     */
    fun startAuthFlow(context: Context) {
        val authUrl = "$BACKEND_URL/auth/spotify?mobile=true"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    /**
     * Handle OAuth callback from deep link
     * Called when app receives vikify://auth/callback
     */
    fun handleAuthCallback(context: Context, uri: Uri): Boolean {
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
            Log.d(TAG, "Auth callback handled - token saved, expires in ${expiresIn}s")
            return true
        }
        return false
    }
    
    /**
     * Refresh the access token using the stored refresh token
     * Returns true if refresh was successful
     */
    suspend fun refreshAccessToken(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null) 
            ?: return@withContext false
        
        try {
            Log.d(TAG, "Refreshing Spotify access token...")
            
            val requestBody = FormBody.Builder()
                .add("refresh_token", refreshToken)
                .build()
            
            val request = Request.Builder()
                .url("$BACKEND_URL/auth/spotify/refresh")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext false
                val json = JSONObject(body)
                
                if (json.optBoolean("success", false)) {
                    val newAccessToken = json.optString("access_token")
                    val newRefreshToken = json.optString("refresh_token", null)
                    val expiresIn = json.optLong("expires_in", 3600L)
                    
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
                    val error = json.optString("error", "Unknown error")
                    Log.e(TAG, "Token refresh failed: $error")
                }
            } else {
                Log.e(TAG, "Token refresh HTTP error: ${response.code}")
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
     * Fetch user's Spotify playlists
     * Uses auto-refresh token logic
     */
    suspend fun fetchPlaylists(context: Context): List<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context) ?: return@withContext emptyList()
        
        try {
            val request = Request.Builder()
                .url("$BACKEND_URL/spotify/me/playlists")
                .addHeader("Authorization", "Bearer $token")
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                if (json.optBoolean("success", false)) {
                    val playlistsArray = json.optJSONArray("playlists") ?: return@withContext emptyList()
                    val result = mutableListOf<SpotifyPlaylist>()
                    for (i in 0 until playlistsArray.length()) {
                        val playlist = playlistsArray.getJSONObject(i)
                        result.add(SpotifyPlaylist(
                            id = playlist.optString("id"),
                            title = playlist.optString("title"),
                            image = playlist.optString("image", null),
                            tracksCount = playlist.optInt("tracksCount", 0)
                        ))
                    }
                    return@withContext result
                }
            } else if (response.code == 401) {
                // Token was rejected, try refreshing once more
                Log.w(TAG, "401 response, attempting token refresh...")
                if (refreshAccessToken(context)) {
                    // Retry with new token
                    return@withContext fetchPlaylists(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlists", e)
        }
        emptyList()
    }
    
    data class SpotifyPlaylist(
        val id: String,
        val title: String,
        val image: String? = null,
        val tracksCount: Int = 0
    )
}

