import axios from 'axios';

const LRCLIB_API = 'https://lrclib.net/api';

/**
 * Fetch lyrics for a song
 * @param {Object} song - { title, artist, album, duration }
 * @returns {Promise<Object|null>} - { syncedLyrics, plainLyrics }
 */
export const getLyrics = async (song) => {
    if (!song || !song.title) return null;

    try {
        // 1. Try exact match
        const params = {
            track_name: song.title,
            artist_name: song.artist,
            album_name: song.album,
            duration: song.duration
        };

        // Remove undefined/null values
        Object.keys(params).forEach(key => params[key] === undefined && delete params[key]);

        try {
            const response = await axios.get(`${LRCLIB_API}/get`, { params });
            if (response.data && (response.data.syncedLyrics || response.data.plainLyrics)) {
                return response.data;
            }
        } catch (e) {
            // 404 expected if not found
        }

        // 2. Try search (fuzzy match)
        const searchRes = await axios.get(`${LRCLIB_API}/search`, {
            params: { q: `${song.title} ${song.artist}` }
        });

        if (searchRes.data && Array.isArray(searchRes.data) && searchRes.data.length > 0) {
            // Pick best match (closest duration if available)
            let bestMatch = searchRes.data[0];

            if (song.duration) {
                const durationMatch = searchRes.data.find(item =>
                    Math.abs(item.duration - song.duration) < 5 // within 5 seconds
                );
                if (durationMatch) bestMatch = durationMatch;
            }

            return bestMatch;
        }

        return null;
    } catch (error) {
        console.error('Lyrics fetch failed:', error);
        return null;
    }
};
