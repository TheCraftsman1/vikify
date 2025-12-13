/**
 * Listening History Service
 * Tracks recently played songs with timestamps
 */

const HISTORY_KEY = 'vikify_listening_history';
const MAX_HISTORY = 50;

/**
 * Get listening history sorted by most recent
 * @returns {Array} Array of song objects with playedAt timestamp
 */
export const getListeningHistory = () => {
    try {
        const stored = localStorage.getItem(HISTORY_KEY);
        if (!stored) return [];
        return JSON.parse(stored);
    } catch {
        return [];
    }
};

/**
 * Add a song to listening history
 * @param {Object} song - Song object to add
 */
export const addToHistory = (song) => {
    if (!song || !song.id) return;

    const history = getListeningHistory();

    // Remove if already exists (we'll re-add at top)
    const filtered = history.filter(s => s.id !== song.id);

    // Add to beginning with timestamp
    const entry = {
        ...song,
        playedAt: Date.now(),
    };

    filtered.unshift(entry);

    // Keep only last MAX_HISTORY items
    const trimmed = filtered.slice(0, MAX_HISTORY);

    try {
        localStorage.setItem(HISTORY_KEY, JSON.stringify(trimmed));
    } catch (e) {
        console.warn('[History] Failed to save:', e);
    }
};

/**
 * Get recently played (last 10 songs)
 * @returns {Array}
 */
export const getRecentlyPlayed = () => {
    return getListeningHistory().slice(0, 10);
};

/**
 * Clear all listening history
 */
export const clearHistory = () => {
    localStorage.removeItem(HISTORY_KEY);
};

/**
 * Get unique artists from history
 * @returns {Array}
 */
export const getTopArtists = () => {
    const history = getListeningHistory();
    const artistCounts = {};

    history.forEach(song => {
        if (song.artist) {
            artistCounts[song.artist] = (artistCounts[song.artist] || 0) + 1;
        }
    });

    return Object.entries(artistCounts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([artist, count]) => ({ artist, count }));
};
