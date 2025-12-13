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

/**
 * Get listening stats for a time period
 * @param {number} days - Number of days to look back
 * @returns {Object} Stats object with counts and details
 */
export const getListeningStats = (days = 7) => {
    const history = getListeningHistory();
    const cutoff = Date.now() - (days * 24 * 60 * 60 * 1000);
    
    const recentHistory = history.filter(s => s.playedAt >= cutoff);
    
    // Count unique songs and artists
    const uniqueSongs = new Set(recentHistory.map(s => s.id));
    const artistPlays = {};
    const genrePlays = {};
    
    recentHistory.forEach(song => {
        if (song.artist) {
            // Count artist plays
            const primaryArtist = song.artist.split(',')[0].trim();
            artistPlays[primaryArtist] = (artistPlays[primaryArtist] || 0) + 1;
        }
    });
    
    // Get top artists
    const topArtists = Object.entries(artistPlays)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([name, count]) => ({ name, count }));
    
    // Calculate total listening time
    const totalSeconds = recentHistory.reduce((acc, s) => acc + (s.duration || 0), 0);
    const totalMinutes = Math.floor(totalSeconds / 60);
    
    return {
        period: days,
        totalPlays: recentHistory.length,
        uniqueSongs: uniqueSongs.size,
        totalMinutes,
        topArtists,
        mostPlayed: recentHistory.length > 0 ? getMostPlayedSongs(5) : []
    };
};

/**
 * Get most frequently played songs
 * @param {number} limit - Max songs to return
 * @returns {Array}
 */
export const getMostPlayedSongs = (limit = 5) => {
    const history = getListeningHistory();
    const playCounts = {};
    const songMap = {};
    
    history.forEach(song => {
        playCounts[song.id] = (playCounts[song.id] || 0) + 1;
        if (!songMap[song.id]) {
            songMap[song.id] = song;
        }
    });
    
    return Object.entries(playCounts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, limit)
        .map(([id, count]) => ({
            ...songMap[id],
            playCount: count
        }));
};

/**
 * Get recommendations based on listening history
 * Simple algorithm that finds songs from similar artists
 * @param {Array} availableSongs - Pool of songs to recommend from
 * @param {number} limit - Max recommendations
 * @returns {Array}
 */
export const getRecommendations = (availableSongs = [], limit = 10) => {
    const history = getListeningHistory();
    if (history.length === 0 || availableSongs.length === 0) {
        // Return random songs if no history
        return shuffleArray(availableSongs).slice(0, limit);
    }
    
    // Get top artists from history
    const topArtists = getTopArtists().map(a => a.artist.toLowerCase());
    const playedIds = new Set(history.map(s => s.id));
    
    // Score songs based on artist match
    const scored = availableSongs
        .filter(song => !playedIds.has(song.id)) // Don't recommend already played
        .map(song => {
            const artist = (song.artist || '').toLowerCase();
            let score = 0;
            
            // Check if artist matches any top artists
            topArtists.forEach((topArtist, index) => {
                if (artist.includes(topArtist) || topArtist.includes(artist)) {
                    score += (5 - index); // Higher score for top artists
                }
            });
            
            // Add some randomness
            score += Math.random() * 2;
            
            return { ...song, score };
        })
        .sort((a, b) => b.score - a.score);
    
    return scored.slice(0, limit).map(({ score, ...song }) => song);
};

// Helper to shuffle array
const shuffleArray = (arr) => {
    const shuffled = [...arr];
    for (let i = shuffled.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
    }
    return shuffled;
};
