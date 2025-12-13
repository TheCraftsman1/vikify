/**
 * Playlist Cache Utility
 * Caches fetched Spotify playlists/albums to avoid repeated API calls
 */

const CACHE_PREFIX = 'vikify_playlist_cache_';
const CACHE_TTL = 30 * 60 * 1000; // 30 minutes

/**
 * Get cached playlist data
 * @param {string} id - Playlist or album ID
 * @returns {Object|null} - Cached data or null
 */
export const getCachedPlaylist = (id) => {
    try {
        const stored = localStorage.getItem(CACHE_PREFIX + id);
        if (!stored) return null;

        const { data, timestamp } = JSON.parse(stored);

        // Check if cache is still valid
        if (Date.now() - timestamp > CACHE_TTL) {
            localStorage.removeItem(CACHE_PREFIX + id);
            return null;
        }

        return data;
    } catch {
        return null;
    }
};

/**
 * Cache playlist data
 * @param {string} id - Playlist or album ID
 * @param {Object} data - Playlist data to cache
 */
export const setCachedPlaylist = (id, data) => {
    if (!id || !data) return;

    try {
        const entry = {
            data,
            timestamp: Date.now(),
        };
        localStorage.setItem(CACHE_PREFIX + id, JSON.stringify(entry));
    } catch (e) {
        // Storage full - clear old entries
        prunePlaylistCache();
    }
};

/**
 * Clear all playlist cache
 */
export const clearPlaylistCache = () => {
    const keys = Object.keys(localStorage).filter(k => k.startsWith(CACHE_PREFIX));
    keys.forEach(k => localStorage.removeItem(k));
};

/**
 * Prune old cache entries
 */
export const prunePlaylistCache = () => {
    const keys = Object.keys(localStorage).filter(k => k.startsWith(CACHE_PREFIX));

    // Remove entries older than TTL
    keys.forEach(key => {
        try {
            const stored = localStorage.getItem(key);
            if (stored) {
                const { timestamp } = JSON.parse(stored);
                if (Date.now() - timestamp > CACHE_TTL) {
                    localStorage.removeItem(key);
                }
            }
        } catch {
            localStorage.removeItem(key);
        }
    });
};
