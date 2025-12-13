/**
 * Stream Service for Vikify
 * Handles fetching stream URLs with retry logic and timeout handling
 */
import axios from 'axios';

// Use the backend server URL from config or environment
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:5000';

class StreamService {
    constructor() {
        this.maxRetries = 3;
        this.retryDelays = [1000, 2000, 3000]; // Exponential backoff: 1s, 2s, 3s
        this.requestTimeout = 15000; // 15 seconds per request
    }

    /**
     * Get stream URL for a song with retry logic
     * @param {Object} song - Song object with id, title, artist
     * @param {Function} onProgress - Progress callback (optional)
     * @returns {Promise<Object>} - { url, source, timeTaken, cached }
     */
    async getStreamUrl(song, onProgress = null) {
        const { id, title = '', artist = '' } = song;

        if (!id) {
            throw new Error('Song ID is required');
        }

        // Attempt with retries
        for (let attempt = 1; attempt <= this.maxRetries; attempt++) {
            try {
                // Notify progress: fetching
                if (onProgress) {
                    onProgress({
                        stage: 'fetching',
                        attempt,
                        message: `Fetching stream (attempt ${attempt}/${this.maxRetries})...`
                    });
                }

                const startTime = Date.now();

                // Make request to backend
                const response = await axios.get(
                    `${API_BASE_URL}/stream/${id}`,
                    {
                        params: { title, artist },
                        timeout: this.requestTimeout
                    }
                );

                const timeTaken = (Date.now() - startTime) / 1000;

                if (response.data.success && response.data.url) {
                    const result = {
                        url: response.data.url,
                        source: response.data.source,
                        timeTaken: parseFloat(response.data.time_taken) || timeTaken,
                        cached: response.data.cached || response.data.source === 'cache'
                    };

                    // Notify progress: ready
                    if (onProgress) {
                        onProgress({
                            stage: 'ready',
                            message: `Ready (${result.source}, ${result.timeTaken.toFixed(2)}s)`,
                            ...result
                        });
                    }

                    console.log(`[StreamService] ✅ Got stream URL for ${id} from ${result.source} in ${result.timeTaken.toFixed(2)}s`);
                    return result;
                } else {
                    throw new Error(response.data.error || 'Failed to get stream URL');
                }

            } catch (error) {
                const isLastAttempt = attempt === this.maxRetries;
                const errorMessage = error.response?.data?.error || error.message;

                console.error(`[StreamService] ❌ Attempt ${attempt}/${this.maxRetries} failed:`, errorMessage);

                if (isLastAttempt) {
                    // Notify progress: error
                    if (onProgress) {
                        onProgress({
                            stage: 'error',
                            attempt,
                            message: `Failed to load stream: ${errorMessage}`
                        });
                    }
                    throw new Error(`Failed to get stream after ${this.maxRetries} attempts: ${errorMessage}`);
                }

                // Wait before retrying (exponential backoff)
                const delay = this.retryDelays[attempt - 1];
                console.log(`[StreamService] ⏳ Retrying in ${delay}ms...`);
                await new Promise(resolve => setTimeout(resolve, delay));
            }
        }
    }

    /**
     * Prefetch a song (fire-and-forget preload request)
     * @param {Object} song - Song object with id, title, artist
     */
    async prefetch(song) {
        const { id, title = '', artist = '' } = song;

        if (!id) {
            console.warn('[StreamService] Cannot prefetch: missing song ID');
            return;
        }

        try {
            console.log(`[StreamService] 🔄 Prefetching ${id}...`);

            const response = await axios.post(
                `${API_BASE_URL}/stream/preload`,
                {
                    songId: id,
                    title,
                    artist
                },
                { timeout: 5000 } // Quick timeout for preload
            );

            if (response.data.success) {
                const status = response.data.cached ? 'already cached' : 'preloading';
                console.log(`[StreamService] ✅ Prefetch ${id}: ${status}`);
            }
        } catch (error) {
            // Silently fail - prefetching is optional
            console.warn(`[StreamService] Prefetch failed for ${id}:`, error.message);
        }
    }
}

// Export singleton instance
export const streamService = new StreamService();
export default streamService;
