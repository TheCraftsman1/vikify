package com.vikify.app.playback

import com.vikify.app.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * Context Type - What kind of container the songs came from
 */
enum class ContextType {
    NONE,
    PLAYLIST,
    ALBUM,
    ARTIST,
    SEARCH_RESULTS,
    AUTOPLAY_RADIO,
    LIKED_SONGS,
    HISTORY
}

/**
 * Context Queue State - Spotify-style queue architecture
 * 
 * The key insight is separation of concerns:
 * - Context Queue: The playlist/album being played (source of truth)
 * - User Queue: Songs explicitly added via "Add to Queue" (priority stack)
 * - Shuffle Mapping: Shadow indices that never reorder the actual list
 * 
 * When "Next" is pressed:
 * 1. First check User Queue (priority)
 * 2. If empty, advance in Context Queue
 * 
 * When Shuffle is toggled:
 * - Generate new shuffle indices
 * - Original list is NEVER reordered
 */
data class ContextQueueState(
    // ═══════════════════════════════════════════════════════════════════
    // 1. THE CONTEXT (Immutable during playback session)
    // ═══════════════════════════════════════════════════════════════════
    val contextId: String? = null,           // "playlist_123" or "album_456"
    val contextTitle: String = "",           // "Liked Songs" or "Starboy"
    val contextType: ContextType = ContextType.NONE,
    
    // ═══════════════════════════════════════════════════════════════════
    // 2. ORIGINAL LIST (Never reordered - source of truth)
    // ═══════════════════════════════════════════════════════════════════
    val originalList: List<MediaMetadata> = emptyList(),
    
    // ═══════════════════════════════════════════════════════════════════
    // 3. SHUFFLE MAPPING (Shadow Index)
    // ═══════════════════════════════════════════════════════════════════
    val isShuffle: Boolean = false,
    val shuffleIndices: List<Int> = emptyList(),  // e.g., [2, 0, 4, 1, 3]
    
    // ═══════════════════════════════════════════════════════════════════
    // 4. PLAYBACK POINTER
    // ═══════════════════════════════════════════════════════════════════
    val currentIndex: Int = 0,  // Points to shuffleIndices if shuffled, else originalList
) {
    /**
     * Get the currently playing song from the context
     */
    fun getCurrentSong(): MediaMetadata? {
        if (originalList.isEmpty()) return null
        val realIndex = if (isShuffle && shuffleIndices.isNotEmpty()) {
            shuffleIndices.getOrNull(currentIndex) ?: currentIndex
        } else {
            currentIndex
        }
        return originalList.getOrNull(realIndex)
    }
    
    /**
     * Get the next song from the context (not user queue)
     */
    fun getNextContextSong(): MediaMetadata? {
        val nextIndex = currentIndex + 1
        if (nextIndex >= originalList.size) return null
        
        val realIndex = if (isShuffle && shuffleIndices.isNotEmpty()) {
            shuffleIndices.getOrNull(nextIndex) ?: nextIndex
        } else {
            nextIndex
        }
        return originalList.getOrNull(realIndex)
    }
    
    /**
     * Get remaining songs in the context (for UI display)
     */
    fun getRemainingContextSongs(): List<MediaMetadata> {
        if (currentIndex >= originalList.size - 1) return emptyList()
        
        return if (isShuffle && shuffleIndices.isNotEmpty()) {
            // Get remaining shuffle positions and map to original songs
            (currentIndex + 1 until shuffleIndices.size).mapNotNull { pos ->
                shuffleIndices.getOrNull(pos)?.let { originalList.getOrNull(it) }
            }
        } else {
            originalList.drop(currentIndex + 1)
        }
    }
    
    /**
     * Check if there are more songs in the context
     */
    fun hasNextInContext(): Boolean {
        return currentIndex < originalList.size - 1
    }
    
    /**
     * Check if there are previous songs in the context
     */
    fun hasPreviousInContext(): Boolean {
        return currentIndex > 0
    }
}

/**
 * User Queue Manager - Priority stack for explicitly queued songs
 * 
 * Songs added via "Add to Queue" go here and are played BEFORE
 * the context queue advances.
 */
class UserQueueManager {
    private val _userQueue = MutableStateFlow<List<MediaMetadata>>(emptyList())
    val userQueue: StateFlow<List<MediaMetadata>> = _userQueue.asStateFlow()
    
    private val queue = ArrayDeque<MediaMetadata>()
    
    /**
     * Add a song to the end of the user queue
     */
    fun addToQueue(song: MediaMetadata) {
        queue.addLast(song)
        _userQueue.value = queue.toList()
    }
    
    /**
     * Add a song to play next (front of user queue)
     */
    fun addToPlayNext(song: MediaMetadata) {
        queue.addFirst(song)
        _userQueue.value = queue.toList()
    }
    
    /**
     * Pop the next song from user queue
     * Returns null if queue is empty
     */
    fun popNext(): MediaMetadata? {
        return if (queue.isNotEmpty()) {
            val song = queue.removeFirst()
            _userQueue.value = queue.toList()
            song
        } else null
    }
    
    /**
     * Remove a specific song from the queue
     */
    fun remove(song: MediaMetadata) {
        queue.remove(song)
        _userQueue.value = queue.toList()
    }
    
    /**
     * Remove song at specific index
     */
    fun removeAt(index: Int) {
        if (index in queue.indices) {
            val list = queue.toMutableList()
            list.removeAt(index)
            queue.clear()
            queue.addAll(list)
            _userQueue.value = queue.toList()
        }
    }
    
    /**
     * Move a song within the user queue
     */
    fun move(fromIndex: Int, toIndex: Int) {
        if (fromIndex in queue.indices && toIndex in queue.indices) {
            val list = queue.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            queue.clear()
            queue.addAll(list)
            _userQueue.value = queue.toList()
        }
    }
    
    /**
     * Clear the entire user queue
     */
    fun clear() {
        queue.clear()
        _userQueue.value = emptyList()
    }
    
    /**
     * Check if user queue has songs
     */
    fun isNotEmpty(): Boolean = queue.isNotEmpty()
    
    /**
     * Get queue size
     */
    fun size(): Int = queue.size
    
    /**
     * Peek at the next song without removing it
     */
    fun peekNext(): MediaMetadata? = queue.peekFirst()
}
