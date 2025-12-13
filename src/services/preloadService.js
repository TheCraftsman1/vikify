/**
 * Preload Service for Vikify
 * Handles preloading upcoming songs in the queue for instant playback
 */
import { streamService } from './streamService';

const PRELOAD_CACHE_TTL = 6 * 60 * 60 * 1000; // 6 hours in milliseconds

class PreloadService {
    constructor() {
        // Track preloaded songs: Map<songId, { timestamp, preloading }>
        this.preloadCache = new Map();
        
        // Track currently preloading songs to avoid duplicates
        this.preloadingSet = new Set();
    }

    /**
     * Preload next N songs from the queue
     * @param {Array} queue - Full queue array
     * @param {number} currentIndex - Index of currently playing song
     * @param {number} count - Number of upcoming songs to preload (default 3)
     */
    async preloadQueue(queue, currentIndex, count = 3) {
        if (!queue || queue.length === 0 || currentIndex < 0) {
            return;
        }

        // Get next N songs
        const songsToPreload = [];
        for (let i = 1; i <= count; i++) {
            const nextIndex = currentIndex + i;
            if (nextIndex < queue.length) {
                songsToPreload.push(queue[nextIndex]);
            }
        }

        if (songsToPreload.length === 0) {
            console.log('[PreloadService] No songs to preload (end of queue)');
            return;
        }

        console.log(`[PreloadService] 🚀 Preloading ${songsToPreload.length} songs...`);

        // Preload each song (fire-and-forget)
        for (const song of songsToPreload) {
            this.preloadSong(song);
        }
    }

    /**
     * Preload a single song
     * @param {Object} song - Song object with id, title, artist
     */
    async preloadSong(song) {
        if (!song || !song.id) {
            return;
        }

        const songId = song.id;

        // Check if already preloaded and not expired
        if (this.isPreloaded(songId)) {
            console.log(`[PreloadService] ✓ ${songId} already preloaded`);
            return;
        }

        // Check if currently preloading (avoid duplicates)
        if (this.preloadingSet.has(songId)) {
            console.log(`[PreloadService] ⏳ ${songId} already preloading`);
            return;
        }

        // Mark as preloading
        this.preloadingSet.add(songId);

        // Fire prefetch request (fire-and-forget, don't await)
        streamService.prefetch(song)
            .then(() => {
                // Mark as preloaded on success
                this.preloadCache.set(songId, {
                    timestamp: Date.now(),
                    preloading: false
                });
                console.log(`[PreloadService] ✅ Preloaded ${songId}`);
            })
            .catch((error) => {
                console.warn(`[PreloadService] Failed to preload ${songId}:`, error.message);
            })
            .finally(() => {
                // Remove from preloading set
                this.preloadingSet.delete(songId);
            });
    }

    /**
     * Check if a song is preloaded and not expired
     * @param {string} songId - Song ID
     * @returns {boolean}
     */
    isPreloaded(songId) {
        const entry = this.preloadCache.get(songId);
        
        if (!entry) {
            return false;
        }

        // Check if expired
        const age = Date.now() - entry.timestamp;
        if (age > PRELOAD_CACHE_TTL) {
            // Remove expired entry
            this.preloadCache.delete(songId);
            return false;
        }

        return true;
    }

    /**
     * Clear preload cache
     */
    clear() {
        this.preloadCache.clear();
        this.preloadingSet.clear();
        console.log('[PreloadService] 🗑️ Cache cleared');
    }

    /**
     * Get cache statistics
     * @returns {Object}
     */
    getStats() {
        // Clean expired entries
        const now = Date.now();
        for (const [songId, entry] of this.preloadCache.entries()) {
            if (now - entry.timestamp > PRELOAD_CACHE_TTL) {
                this.preloadCache.delete(songId);
            }
        }

        return {
            cached: this.preloadCache.size,
            preloading: this.preloadingSet.size,
            ttl: `${PRELOAD_CACHE_TTL / (60 * 60 * 1000)}h`
        };
    }
}

// Export singleton instance
export const preloadService = new PreloadService();
export default preloadService;
