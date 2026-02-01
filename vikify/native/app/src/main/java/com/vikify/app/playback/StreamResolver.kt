package com.vikify.app.playback

import android.content.Context
import android.net.ConnectivityManager
import com.vikify.app.constants.AudioQuality
import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.SongEntity
import com.vikify.app.utils.YTPlayerUtils
import com.vikify.app.utils.isValidYouTubeId
import com.vikify.app.utils.getMediaIdType
import com.vikify.app.utils.MediaIdType
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
            val startTime = System.currentTimeMillis()
            try {
                // 1. Check Cache (TTL: 5 hours for seamless playback)
                if (!isLinkExpired(song)) {
                    android.util.Log.d("StreamResolver", "Cache HIT for ${song.title}")
                    return@withContext song.streamUrl
                }
                android.util.Log.d("StreamResolver", "Cache MISS for ${song.title}. Resolving...")

                // 2. Find YouTube Video ID using unified MediaIdType detection
                android.util.Log.d("StreamResolver", "=== RESOLVING: ${song.title} ===")
                android.util.Log.d("StreamResolver", "  DB Song ID: ${song.id}")
                
                val idType = song.id.getMediaIdType()
                android.util.Log.d("StreamResolver", "  ID Type: $idType")
                
                val videoId = when (idType) {
                    MediaIdType.YOUTUBE -> {
                        android.util.Log.d("StreamResolver", "  Using ORIGINAL YouTube ID: ${song.id}")
                        song.id
                    }
                    MediaIdType.UNRESOLVED, MediaIdType.SPOTIFY, MediaIdType.UNKNOWN -> {
                        android.util.Log.w("StreamResolver", "  Needs resolution, searching for match...")
                        findBestMatch(song)
                    }
                    MediaIdType.LOCAL -> {
                        android.util.Log.d("StreamResolver", "  Local file, no stream resolution needed")
                        return@withContext null  // Local files don't need stream URL
                    }
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
                    val resolutionTime = System.currentTimeMillis() - startTime
                    android.util.Log.d("StreamResolver", "✓ Resolved ${song.title} in ${resolutionTime}ms")
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

    // ═══════════════════════════════════════════════════════════════════════
    // CACHE EXPIRY - Extended from 45 min to 5 hours for better seamless playback
    // YouTube URLs are valid for ~6 hours, 5hr gives safety margin
    // ═══════════════════════════════════════════════════════════════════════
    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 60 * 1000L  // 5 hours
    }
    
    private fun isLinkExpired(song: SongEntity): Boolean {
        if (song.streamUrl == null || song.lastResolved == null) return true
        
        val now = System.currentTimeMillis()
        val age = now - song.lastResolved
        return age > CACHE_TTL_MS
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
            
            // IMPROVED MATCHING: Use strict title matching to prevent playing wrong songs from same album
            val normalizedTitle = normalizeForMatch(targetTitle)
            val normalizedArtist = normalizeForMatch(targetArtist)
            
            val scoredSongs = items.mapNotNull { songItem ->
                val songTitle = normalizeForMatch(songItem.title)
                val songArtist = normalizeForMatch(songItem.artists.joinToString { it.name })
                
                // CRITICAL: Calculate title similarity with stricter algorithm
                val titleScore = calculateTitleSimilarity(normalizedTitle, songTitle)
                val artistScore = calculateSimilarity(normalizedArtist, songArtist)
                
                // IMPORTANT: Title must match strongly (80% weight) - artist is secondary (20%)
                // This prevents playing "Kadalani" when searching for "Nuvve Nuvve" from same movie
                val totalScore = (titleScore * 0.80) + (artistScore * 0.20)
                
                android.util.Log.d("StreamResolver", 
                    "Song: '${songItem.title}' | Title: ${"%.2f".format(titleScore)} | Artist: ${"%.2f".format(artistScore)} | Total: ${"%.2f".format(totalScore)}")
                
                // RAISED THRESHOLD: Require at least 65% match (up from 40%)
                // Lower threshold only if title is a strong match
                val threshold = if (titleScore >= 0.85) 0.50 else 0.65
                
                if (totalScore >= threshold) {
                    songItem to totalScore
                } else {
                    null
                }
            }
            
            // Return the highest scoring song
            val bestMatch = scoredSongs.maxByOrNull { it.second }?.first
            
            if (bestMatch != null) {
                android.util.Log.d("StreamResolver", "✓ Best match: '${bestMatch.title}' by ${bestMatch.artists.joinToString { it.name }}")
                bestMatch.id
            } else {
                // REMOVED DANGEROUS FALLBACK: Do NOT use first result - return null instead
                android.util.Log.e("StreamResolver", "✗ No reliable match for '$targetTitle'. Refusing to guess.")
                null  // Let UI handle the error gracefully
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
     * IMPROVED: Calculate title similarity using multiple strategies
     * This prevents songs from same album/movie from being confused
     */
    private fun calculateTitleSimilarity(target: String, candidate: String): Double {
        if (target.isEmpty() || candidate.isEmpty()) return 0.0
        if (target == candidate) return 1.0
        
        // Strategy 1: Exact containment (one title fully contains the other)
        if (target == candidate.take(target.length) || candidate == target.take(candidate.length)) {
            return 0.95  // Near-exact prefix match
        }
        
        // Strategy 2: Levenshtein-based similarity for short titles
        if (target.length <= 20 && candidate.length <= 20) {
            val editDistance = levenshteinDistance(target, candidate)
            val maxLen = maxOf(target.length, candidate.length)
            val levenshteinScore = 1.0 - (editDistance.toDouble() / maxLen)
            if (levenshteinScore >= 0.7) return levenshteinScore
        }
        
        // Strategy 3: Word-order-aware matching (prevents "Nuvve Nuvve" matching "Kadalani")
        val targetWords = target.split(" ").filter { it.length > 1 }
        val candidateWords = candidate.split(" ").filter { it.length > 1 }
        
        if (targetWords.isEmpty() || candidateWords.isEmpty()) return 0.0
        
        // Check if first significant word matches (critical for song identification)
        val firstWordMatch = targetWords.firstOrNull()?.let { firstTarget ->
            candidateWords.any { it.startsWith(firstTarget.take(3)) || firstTarget.startsWith(it.take(3)) }
        } ?: false
        
        // Word intersection with position weighting
        var matchScore = 0.0
        var totalWeight = 0.0
        
        targetWords.forEachIndexed { index, word ->
            val weight = 1.0 / (index + 1)  // First words are more important
            totalWeight += weight
            
            if (candidateWords.any { candidateWord ->
                candidateWord == word || 
                candidateWord.startsWith(word) || 
                word.startsWith(candidateWord) ||
                levenshteinDistance(word, candidateWord) <= 1
            }) {
                matchScore += weight
            }
        }
        
        val positionScore = if (totalWeight > 0) matchScore / totalWeight else 0.0
        
        // Boost score if first word matches
        return if (firstWordMatch) {
            minOf(positionScore + 0.15, 1.0)
        } else {
            positionScore * 0.8  // Penalize if first word doesn't match
        }
    }
    
    /**
     * Levenshtein distance for precise string comparison
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        return dp[m][n]
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
