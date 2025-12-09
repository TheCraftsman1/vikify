import axios from 'axios';

// Backend URL - uses env var in production, localhost in dev
// Set VITE_BACKEND_URL in your .env file or at build time
const BACKEND_URL = import.meta.env.VITE_BACKEND_URL || 'https://vikify-production.up.railway.app';

/**
 * Search for a video and get its direct audio stream URL
 * Returns audio URL directly (not YouTube URL)
 */
export const searchYouTube = async (query) => {
    console.log(`[YouTube] Searching: ${query}`);

    try {
        // Step 1: Search for video
        const searchResponse = await axios.get(`${BACKEND_URL}/search`, {
            params: { q: query },
            timeout: 15000
        });

        console.log('[YouTube] Search response:', searchResponse.data);

        if (searchResponse.data?.success && searchResponse.data?.videoId) {
            const videoId = searchResponse.data.videoId;
            console.log(`[YouTube] Found video: ${videoId}`);

            // Step 2: Get direct audio stream URL
            const streamResponse = await axios.get(`${BACKEND_URL}/stream/${videoId}`, {
                timeout: 20000
            });

            console.log('[YouTube] Stream response:', streamResponse.data);

            if (streamResponse.data?.success && streamResponse.data?.audioUrl) {
                console.log('[YouTube] ✅ Got direct audio stream!');
                return streamResponse.data.audioUrl;
            }
        }
    } catch (e) {
        console.error(`[YouTube] Error:`, e.message);
    }

    return null;
};

/**
 * Get audio stream URL (for downloads) - same as searchYouTube now
 */
export const getAudioStreamUrl = async (title, artist) => {
    const url = await searchYouTube(`${title} ${artist}`);
    return url ? { url } : null;
};

/**
 * Get related songs for autoplay
 */
export const getRelatedSongs = async (currentSong) => {
    if (!currentSong) return [];

    console.log('[YouTube] Getting related songs for:', currentSong.title);

    try {
        // First, search for the song to get its video ID
        const searchResponse = await axios.get(`${BACKEND_URL}/search`, {
            params: { q: `${currentSong.title} ${currentSong.artist}` },
            timeout: 10000
        });

        if (searchResponse.data?.success && searchResponse.data?.videoId) {
            const videoId = searchResponse.data.videoId;

            // Get related songs
            const relatedResponse = await axios.get(`${BACKEND_URL}/related/${videoId}`, {
                timeout: 20000
            });

            if (relatedResponse.data?.success && relatedResponse.data?.related?.length > 0) {
                console.log('[YouTube] ✅ Got related songs:', relatedResponse.data.related.length);
                return relatedResponse.data.related.map(song => ({
                    id: song.videoId,
                    title: song.title,
                    artist: song.artist,
                    image: song.image,
                    duration: song.duration * 1000, // Convert to ms
                    isAutoplay: true
                }));
            }
        }
    } catch (e) {
        console.error('[YouTube] Related songs error:', e.message);
    }

    return [];
};
