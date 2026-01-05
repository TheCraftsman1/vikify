package com.vikify.app.playback

import android.content.Context
import android.net.ConnectivityManager
import com.vikify.app.constants.AudioQuality
import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.SongEntity
import com.vikify.app.utils.YTPlayerUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import com.vikify.app.utils.dataStore
import com.vikify.app.utils.get
import com.vikify.app.constants.AudioQualityKey
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamResolver @Inject constructor(
    private val database: MusicDatabase,
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Resolves the audio stream URL for a song.
     * 
     * Strategy:
     * 1. Check local DB for cached, non-expired URL
     * 2. If missing/expired:
     *    a. Search YouTube for Video ID (using title + artist)
     *    b. Fetch Stream URL using YTPlayerUtils (handles PoTokens, clients, formats)
     *    c. Update DB with new URL
     */
    suspend fun resolveAudio(song: SongEntity): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Check Cache (TTL: 55 minutes to be safe/conservative relative to 1h exp)
                if (!isLinkExpired(song)) {
                    android.util.Log.d("StreamResolver", "Cache HIT for ${song.title}")
                    return@withContext song.streamUrl
                }
                android.util.Log.d("StreamResolver", "Cache MISS for ${song.title}. resolving...")

                // 2. Find YouTube Video ID
                android.util.Log.d("StreamResolver", "Resolving ID for ${song.title} (ID: ${song.id})")
                val videoId = if (song.id.length == 11 && !song.id.startsWith("LS") && !song.id.startsWith("UNRESOLVED")) {
                    song.id
                } else {
                    findBestMatch(song)
                } 
                
                if (videoId == null) {
                    android.util.Log.e("StreamResolver", "Video ID NOT FOUND for ${song.title}")
                    return@withContext null
                }
                android.util.Log.d("StreamResolver", "Found Video ID: $videoId")


                // 3. Resolve Stream URL
                val qualityString = context.dataStore[AudioQualityKey] ?: "HIGH"
                val audioQuality = try {
                    AudioQuality.valueOf(qualityString)
                } catch (e: Exception) {
                    AudioQuality.HIGH
                }

                val playbackResult = YTPlayerUtils.playerResponseForPlayback(
                    videoId = videoId,
                    audioQuality = audioQuality, 
                    connectivityManager = connectivityManager
                ).getOrNull()

                val newStreamUrl = playbackResult?.streamUrl
                
                if (newStreamUrl != null) {
                    // 4. Update DB
                    val updatedSong = song.copy(
                        streamUrl = newStreamUrl,
                        lastResolved = System.currentTimeMillis()
                    )
                    database.update(updatedSong) // Direct update via delegated DAO
                    android.util.Log.d("StreamResolver", "Resolved $videoId -> $newStreamUrl")
                    return@withContext newStreamUrl
                }
                
                android.util.Log.e("StreamResolver", "Failed to fetch stream URL for $videoId")
                null
            } catch (e: Exception) {
                e.printStackTrace()
                android.util.Log.e("StreamResolver", "Exception: ${e.message}")
                null
            }
        }
    }

    private fun isLinkExpired(song: SongEntity): Boolean {
        if (song.streamUrl == null || song.lastResolved == null) return true
        
        val now = System.currentTimeMillis()
        val age = now - song.lastResolved
        // YouTube links typically expire in 6 hours, but we cache for 1 hour to be safe
        // Or check `expire` param in URL? No, relying on timestamp is simpler/safer.
        return age > 3600 * 1000 // 1 hour TTL
    }

    private suspend fun findBestMatch(song: SongEntity): String? {
        // Search query: Title + Artist (fallback to Spotify data if available)
        val query = "${song.title} ${song.spotifyArtist ?: ""}"
        android.util.Log.d("StreamResolver", "Searching YouTube for: '$query'")
        
        return try {
            val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val items = searchResult?.items?.filterIsInstance<SongItem>()
            android.util.Log.d("StreamResolver", "Search items found: ${items?.size ?: 0}")
            
            // Find best match logic could be improved, but taking first song is standard
            // Could reuse PlayerViewModel.findBestMatchingSong logic here if moved to utils
            items?.firstOrNull()?.id
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
