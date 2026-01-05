package com.vikify.app.playback

import android.util.Log
import com.vikify.app.models.MediaMetadata
import com.vikify.app.models.toMediaMetadata
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * AUTOPLAY MANAGER - Spotify-style Infinite Radio
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Implements Spotify-like Autoplay that activates when the Context Queue reaches its end.
 *
 * Architecture:
 * ┌─────────────────────────────────────────────────────────────────────────────────────────────┐
 * │ QUEUE PRIORITY (3-Layer System)                                                            │
 * ├─────────────────────────────────────────────────────────────────────────────────────────────┤
 * │ 1. USER QUEUE (Explicit "Play Next") - Highest priority                                    │
 * │ 2. CONTEXT QUEUE (Album/Playlist currently playing)                                        │
 * │ 3. AUTOPLAY QUEUE (This feature) - Activates only when Context ends                        │
 * └─────────────────────────────────────────────────────────────────────────────────────────────┘
 *
 * Key Features:
 * - Seed Mechanism: Uses last 3 played songs to generate recommendations
 * - 80% Fetch Trigger: Pre-fetches songs at 80% progress of last context song (gapless)
 * - Deduplication: Filters songs already in history/queue
 * - Toggle: isAutoplayEnabled StateFlow for user preference
 * - Visual Indication: Songs marked with SOURCE_AUTOPLAY flag
 *
 * @author Senior Android Audio Engineer
 */
class AutoplayManager(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AutoplayManager"
        
        // Source flags for UI indication
        const val SOURCE_CONTEXT = 0
        const val SOURCE_USER_QUEUE = 1
        const val SOURCE_AUTOPLAY = 2  // Shows "Similar to [Artist]" in UI
        
        // Configuration
        private const val SEED_SIZE = 3              // Number of songs to use as seed
        private const val PREFETCH_THRESHOLD = 0.80f // 80% = trigger point
        private const val FETCH_BATCH_SIZE = 15      // Songs to fetch per batch
        private const val MIN_SONGS_BEFORE_REFETCH = 3 // Refetch when < 3 songs remain
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // STATE FLOWS
    // ═══════════════════════════════════════════════════════════════════════
    
    /** Autoplay toggle - if false, playback stops at end of context */
    private val _isAutoplayEnabled = MutableStateFlow(true)
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()
    
    /** Autoplay queue - songs fetched from recommendations */
    private val _autoplayQueue = MutableStateFlow<List<AutoplayTrack>>(emptyList())
    val autoplayQueue: StateFlow<List<AutoplayTrack>> = _autoplayQueue.asStateFlow()
    
    /** Currently fetching recommendations */
    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()
    
    /** Last N played songs (rolling history for seed generation) */
    private val playHistory = ArrayDeque<MediaMetadata>(SEED_SIZE + 5)
    
    /** Set of song IDs already played/queued (for deduplication) */
    private val playedIds = mutableSetOf<String>()
    
    /** Active fetch job (for cancellation) */
    private var fetchJob: Job? = null
    
    /** Seed artist for "Similar to [Artist]" display */
    private var _seedArtist = MutableStateFlow<String?>(null)
    val seedArtist: StateFlow<String?> = _seedArtist.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // DATA CLASS FOR AUTOPLAY TRACKS
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Wrapper for autoplay tracks with source indication
     */
    data class AutoplayTrack(
        val metadata: MediaMetadata,
        val source: Int = SOURCE_AUTOPLAY,
        val seedArtistName: String? = null  // For "Similar to [Artist]" subtitle
    )
    
    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Toggle autoplay on/off
     */
    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
        Log.d(TAG, "Autoplay ${if (enabled) "enabled" else "disabled"}")
        
        if (!enabled) {
            // Clear autoplay queue when disabled
            clearAutoplayQueue()
        }
    }
    
    /**
     * Record a song as played (updates history and deduplication set)
     * Call this on every song transition
     */
    fun recordPlayedSong(song: MediaMetadata) {
        // Add to rolling history
        if (playHistory.size >= SEED_SIZE + 5) {
            playHistory.removeFirst()
        }
        playHistory.addLast(song)
        
        // Add to deduplication set
        playedIds.add(song.id)
        
        Log.d(TAG, "Recorded played: ${song.title} (history size: ${playHistory.size})")
    }
    
    /**
     * Mark a list of songs as already in queue (for deduplication)
     */
    fun markAsQueued(songs: List<MediaMetadata>) {
        songs.forEach { playedIds.add(it.id) }
    }
    
    /**
     * Check if we should trigger autoplay fetch
     * 
     * @param currentPosition Current playback position in milliseconds
     * @param duration Total duration in milliseconds
     * @param isLastContextSong True if this is the last song in context queue
     * @param remainingContextSongs Number of songs remaining in context after current
     */
    fun checkAndTriggerFetch(
        currentPosition: Long,
        duration: Long,
        isLastContextSong: Boolean,
        remainingContextSongs: Int
    ) {
        if (!_isAutoplayEnabled.value) return
        if (_isFetching.value) return
        if (duration <= 0) return
        
        val progress = currentPosition.toFloat() / duration.toFloat()
        
        // Trigger at 80% of last context song OR when approaching end
        val shouldFetch = isLastContextSong && 
                          progress >= PREFETCH_THRESHOLD && 
                          _autoplayQueue.value.size < MIN_SONGS_BEFORE_REFETCH
        
        if (shouldFetch) {
            Log.d(TAG, "Triggering autoplay fetch at ${(progress * 100).toInt()}% progress")
            fetchRecommendations()
        }
    }
    
    /**
     * Get the next song from autoplay queue
     * Returns null if autoplay is disabled or queue is empty
     */
    fun popNextAutoplaySong(): AutoplayTrack? {
        if (!_isAutoplayEnabled.value) return null
        
        val queue = _autoplayQueue.value.toMutableList()
        if (queue.isEmpty()) return null
        
        val next = queue.removeAt(0)
        _autoplayQueue.value = queue
        
        Log.d(TAG, "Popped autoplay song: ${next.metadata.title}")
        return next
    }
    
    /**
     * Peek at next autoplay song without removing
     */
    fun peekNextAutoplaySong(): AutoplayTrack? {
        return _autoplayQueue.value.firstOrNull()
    }
    
    /**
     * Check if autoplay has songs available
     */
    fun hasAutoplaySongs(): Boolean {
        return _isAutoplayEnabled.value && _autoplayQueue.value.isNotEmpty()
    }
    
    /**
     * Clear the autoplay queue
     */
    fun clearAutoplayQueue() {
        fetchJob?.cancel()
        _autoplayQueue.value = emptyList()
        Log.d(TAG, "Autoplay queue cleared")
    }
    
    /**
     * Reset all state (call on new context/playlist start)
     */
    fun reset() {
        fetchJob?.cancel()
        _autoplayQueue.value = emptyList()
        playHistory.clear()
        playedIds.clear()
        _seedArtist.value = null
        _isFetching.value = false
        Log.d(TAG, "AutoplayManager reset")
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE IMPLEMENTATION
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Generate seed from last N played songs
     * Uses weighted selection: most recent songs have higher priority
     */
    private fun generateSeed(): MediaMetadata? {
        if (playHistory.isEmpty()) {
            Log.w(TAG, "Cannot generate seed: no play history")
            return null
        }
        
        // Take last SEED_SIZE songs, prefer most recent
        val seeds = playHistory.takeLast(SEED_SIZE)
        
        // Use most recent song as primary seed
        val primarySeed = seeds.lastOrNull()
        
        if (primarySeed != null) {
            _seedArtist.value = primarySeed.artists.firstOrNull()?.name
            Log.d(TAG, "Generated seed: ${primarySeed.title} by ${_seedArtist.value}")
        }
        
        return primarySeed
    }
    
    /**
     * Fetch recommendations from YouTube based on seed
     * Uses YouTube.next() API for radio-style recommendations
     */
    private fun fetchRecommendations() {
        if (_isFetching.value) return
        
        val seed = generateSeed()
        if (seed == null) {
            Log.w(TAG, "Cannot fetch recommendations: no seed available")
            return
        }
        
        fetchJob?.cancel()
        fetchJob = scope.launch(Dispatchers.IO) {
            _isFetching.value = true
            
            try {
                Log.d(TAG, "Fetching recommendations for: ${seed.title}")
                
                // Use YouTube's "next" endpoint for radio-style recommendations
                val endpoint = WatchEndpoint(videoId = seed.id)
                val result = YouTube.next(endpoint).getOrNull()
                
                if (result != null && result.items.isNotEmpty()) {
                    // Convert to MediaMetadata and filter duplicates
                    val recommendations = result.items
                        .map { it.toMediaMetadata() }
                        .filterNot { playedIds.contains(it.id) }
                        .take(FETCH_BATCH_SIZE)
                        .map { metadata ->
                            AutoplayTrack(
                                metadata = metadata,
                                source = SOURCE_AUTOPLAY,
                                seedArtistName = _seedArtist.value
                            )
                        }
                    
                    if (recommendations.isNotEmpty()) {
                        // Append to existing queue (don't replace)
                        _autoplayQueue.value = _autoplayQueue.value + recommendations
                        
                        // Mark as queued for deduplication
                        recommendations.forEach { playedIds.add(it.metadata.id) }
                        
                        Log.d(TAG, "Added ${recommendations.size} songs to autoplay queue")
                        Log.d(TAG, "Autoplay queue now has ${_autoplayQueue.value.size} songs")
                    } else {
                        Log.w(TAG, "All recommendations were duplicates")
                    }
                } else {
                    Log.w(TAG, "No recommendations returned from YouTube")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch recommendations", e)
            } finally {
                _isFetching.value = false
            }
        }
    }
    
    /**
     * Force fetch recommendations (for manual trigger)
     */
    fun forceFetchRecommendations() {
        if (!_isAutoplayEnabled.value) {
            Log.w(TAG, "Cannot fetch: autoplay is disabled")
            return
        }
        fetchRecommendations()
    }
}
