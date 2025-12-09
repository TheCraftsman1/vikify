import axios from 'axios';

const BACKEND_URL = 'https://vikify-production.up.railway.app';

/**
 * Search for tracks using the Python SpotAPI backend
 */
export const searchSpotify = async (query) => {
    try {
        const response = await axios.get(`${BACKEND_URL}/api/search`, {
            params: { q: query }
        });
        return response.data.tracks || [];
    } catch (error) {
        console.error('[SpotAPI] Search error:', error);
        return [];
    }
};

/**
 * Get stream URL for a track using the Python SpotAPI backend
 */
export const getStreamUrl = async (trackId) => {
    try {
        const response = await axios.get(`${BACKEND_URL}/api/stream`, {
            params: { id: trackId }
        });
        return response.data.url;
    } catch (error) {
        console.error('[SpotAPI] Stream error:', error);
        return null;
    }
};
