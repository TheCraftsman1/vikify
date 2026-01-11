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
                android.util.Log.d("StreamResolver", "=== RESOLVING: ${song.title} ===")
                android.util.Log.d("StreamResolver", "  DB Song ID: ${song.id}")
                android.util.Log.d("StreamResolver", "  ID Length: ${song.id.length}")
                
                // YouTube video IDs are typically 11 characters but can vary slightly
                // Valid YouTube ID: alphanumeric + _ and -
                val isValidYouTubeId = song.id.length in 10..12 && 
                    song.id.all { it.isLetterOrDigit() || it == '_' || it == '-' } &&
                    !song.id.startsWith("LS") && 
                    !song.id.startsWith("UNRESOLVED")
                
                android.util.Log.d("StreamResolver", "  Is valid YouTube ID: $isValidYouTubeId")
                
                val videoId = if (isValidYouTubeId) {
                    android.util.Log.d("StreamResolver", "  Using ORIGINAL ID: ${song.id}")
                    song.id
                } else {
                    android.util.Log.w("StreamResolver", "  ID looks invalid, searching for match...")
                    findBestMatch(song)
                } 
                
                if (videoId == null) {
                    android.util.Log.e("StreamResolver", "Video ID NOT FOUND for ${song.title}")
                    return@withContext null
                }
                android.util.Log.d("StreamResolver", "  FINAL Video ID: $videoId ${if (videoId != song.id) "(CHANGED!)" else ""}")


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
        val targetTitle = song.title
        val targetArtist = song.spotifyArtist ?: ""
        val query = "$targetTitle $targetArtist"
        android.util.Log.d("StreamResolver", "Searching YouTube for: '$query'")
        
        return try {
            val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            val items = searchResult?.items?.filterIsInstance<SongItem>()
            android.util.Log.d("StreamResolver", "Search items found: ${items?.size ?: 0}")
            
            if (items.isNullOrEmpty()) return null
            
            // Use smart matching to find the best song - not just the first one!
            val normalizedTitle = normalizeForMatch(targetTitle)
            val normalizedArtist = normalizeForMatch(targetArtist)
            
            val scoredSongs = items.mapNotNull { songItem ->
                val songTitle = normalizeForMatch(songItem.title)
                val songArtist = normalizeForMatch(songItem.artists.joinToString { it.name })
                
                val titleScore = calculateSimilarity(normalizedTitle, songTitle)
                val artistScore = calculateSimilarity(normalizedArtist, songArtist)
                
                // Combined score: title is more important (70%) than artist (30%)
                val totalScore = (titleScore * 0.7) + (artistScore * 0.3)
                
                android.util.Log.d("StreamResolver", 
                    "Song: '${songItem.title}' | Title Score: ${"%.2f".format(titleScore)} | Artist Score: ${"%.2f".format(artistScore)} | Total: ${"%.2f".format(totalScore)}")
                
                // Require at least 40% match to avoid completely wrong songs
                if (totalScore >= 0.4) {
                    songItem to totalScore
                } else {
                    null
                }
            }
            
            // Return the highest scoring song
            val bestMatch = scoredSongs.maxByOrNull { it.second }?.first
            
            if (bestMatch != null) {
                android.util.Log.d("StreamResolver", "Best match: '${bestMatch.title}' by ${bestMatch.artists.joinToString { it.name }}")
                bestMatch.id
            } else {
                // Fallback to first result if no good match (but log warning)
                android.util.Log.w("StreamResolver", "No good match found for '$targetTitle', using first result as fallback")
                items.firstOrNull()?.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Normalize a string for matching (lowercase, remove special chars)
     */
    private fun normalizeForMatch(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "") // Remove special chars
            .replace(Regex("\\s+"), " ")        // Normalize spaces
            .trim()
    }
    
    /**
     * Calculate similarity between two strings (0.0 to 1.0)
     * Uses containment check + word overlap approach
     */
    private fun calculateSimilarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        
        // Check if one contains the other (common for multilingual matches)
        if (a.contains(b) || b.contains(a)) return 0.9
        
        // Word overlap scoring
        val wordsA = a.split(" ").filter { it.length > 1 }.toSet()
        val wordsB = b.split(" ").filter { it.length > 1 }.toSet()
        
        if (wordsA.isEmpty() || wordsB.isEmpty()) return 0.0
        
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        
        return intersection.toDouble() / union.toDouble()
    }
}
