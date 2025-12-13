import axios from 'axios';
import { BACKEND_URL } from '../config.js';

import { createCache } from './cacheController';
import { withRetry, isLikelyTransientNetworkError, isOffline } from './net';

// Cache strategy:
// - Cache videoId per query for a long time (stable + saves a network roundtrip).
// - Cache direct audioUrl only briefly (it can expire quickly).
const videoIdCache = createCache({
    name: 'yt_videoid',
    persist: true,
    defaultTtlMs: 7 * 24 * 60 * 60 * 1000, // 7 days
    maxEntries: 600,
});

const streamUrlCache = createCache({
    name: 'yt_streamurl',
    persist: true,
    defaultTtlMs: 2 * 60 * 1000, // 2 minutes
    maxEntries: 300,
});

export const pruneYouTubeCaches = () => {
    videoIdCache.prune();
    streamUrlCache.prune();
};

export const clearYouTubeCaches = () => {
    videoIdCache.clear();
    streamUrlCache.clear();
};

const normalizeQueryKey = (query) => (query || '').trim().toLowerCase();

const isValidHttpUrl = (u) => {
    if (!u) return false;
    try {
        const url = new URL(u);
        return url.protocol === 'http:' || url.protocol === 'https:';
    } catch {
        return false;
    }
};

export const prefetchYouTubeVideoId = async (query) => {
    try {
        await getYouTubeVideoId(query);
    } catch {
        // ignore
    }
};

/**
 * Prefetch stream URLs for multiple video IDs (for preloading queue)
 * Uses batch endpoint for efficiency
 */
export const prefetchStreamUrls = async (videoIds) => {
    if (!videoIds?.length || isOffline()) return;
    
    // Filter out already cached IDs
    const uncachedIds = videoIds.filter(id => !streamUrlCache.get(id));
    if (!uncachedIds.length) return;
    
    try {
        const response = await axios.post(`${BACKEND_URL}/api/stream/batch`, {
            ids: uncachedIds.slice(0, 5) // Max 5 at a time
        }, { timeout: 30000 });
        
        if (response.data?.results) {
            for (const [videoId, result] of Object.entries(response.data.results)) {
                if (result.success && result.url && isValidHttpUrl(result.url)) {
                    streamUrlCache.set(videoId, result.url);
                    console.log(`[YouTube] 📦 Preloaded ${videoId} via ${result.source}`);
                }
            }
        }
    } catch (e) {
        console.log('[YouTube] Batch prefetch failed:', e.message);
    }
};

export const getYouTubeVideoId = async (query) => {
    const key = normalizeQueryKey(query);
    if (!key) return null;

    const cached = videoIdCache.get(key);
    if (cached) return cached;

    if (isOffline()) return null;

    const result = await withRetry(
        async () => {
            const searchResponse = await axios.get(`${BACKEND_URL}/search`, {
                params: { q: query },
                timeout: 15000,
            });
            if (searchResponse.data?.success && searchResponse.data?.videoId) {
                return searchResponse.data.videoId;
            }
            return null;
        },
        {
            retries: 2,
            baseDelayMs: 400,
            shouldRetry: (e) => isLikelyTransientNetworkError(e),
        }
    );

    if (result) videoIdCache.set(key, result);
    return result;
};

export const getYouTubeStreamUrl = async (videoId, title = '', artist = '') => {
    if (!videoId) return null;

    const cached = streamUrlCache.get(videoId);
    if (cached && isValidHttpUrl(cached)) return cached;

    if (isOffline()) return null;

    const audioUrl = await withRetry(
        async () => {
            // Try new Cobalt-first endpoint first (much faster)
            try {
                const cobaltResponse = await axios.get(`${BACKEND_URL}/api/stream/${videoId}`, {
                    params: { title, artist },
                    timeout: 15000,
                });
                if (cobaltResponse.data?.success && cobaltResponse.data?.url && isValidHttpUrl(cobaltResponse.data.url)) {
                    console.log(`[YouTube] ⚡ Stream via ${cobaltResponse.data.source} (${cobaltResponse.data.time})`);
                    return cobaltResponse.data.url;
                }
            } catch (cobaltError) {
                console.log('[YouTube] Cobalt-first failed, trying legacy...');
            }
            
            // Fallback to legacy /stream endpoint
            const streamResponse = await axios.get(`${BACKEND_URL}/stream/${videoId}`, {
                timeout: 20000,
            });
            if (streamResponse.data?.success && streamResponse.data?.audioUrl && isValidHttpUrl(streamResponse.data.audioUrl)) {
                return streamResponse.data.audioUrl;
            }
            return null;
        },
        {
            retries: 2,
            baseDelayMs: 500,
            shouldRetry: (e) => isLikelyTransientNetworkError(e),
        }
    );

    if (audioUrl) streamUrlCache.set(videoId, audioUrl);
    return audioUrl;
};

/**
 * Search for a video and get its direct audio stream URL
 * OPTIMIZED: Uses combined endpoint to get videoId + audioUrl in ONE call
 * Returns audio URL directly (not YouTube URL)
 */
export const searchYouTube = async (query) => {
    console.log(`[YouTube] Searching: ${query}`);
    const key = normalizeQueryKey(query);

    try {
        // Check if we already have a cached stream URL for this query's video
        const cachedVideoId = videoIdCache.get(key);
        if (cachedVideoId) {
            const cachedStreamUrl = streamUrlCache.get(cachedVideoId);
            if (cachedStreamUrl && isValidHttpUrl(cachedStreamUrl)) {
                console.log('[YouTube] ✅ Cache hit for stream URL!');
                return cachedStreamUrl;
            }
        }

        if (isOffline()) return null;

        // OPTIMIZED: Single call with include_stream=true
        // Backend returns videoId + audioUrl together, eliminating one roundtrip
        const response = await withRetry(
            async () => {
                const searchResponse = await axios.get(`${BACKEND_URL}/search`, {
                    params: { q: query, include_stream: 'true' },
                    timeout: 25000, // Slightly longer timeout since it does more work
                });
                return searchResponse.data;
            },
            {
                retries: 2,
                baseDelayMs: 400,
                shouldRetry: (e) => isLikelyTransientNetworkError(e),
            }
        );

        if (!response?.success || !response?.videoId) {
            console.log('[YouTube] No results found');
            return null;
        }

        // Cache the videoId
        videoIdCache.set(key, response.videoId);
        console.log(`[YouTube] Found video: ${response.videoId}`);

        // If audioUrl was included (optimization worked!), use it directly
        if (response.audioUrl && isValidHttpUrl(response.audioUrl)) {
            streamUrlCache.set(response.videoId, response.audioUrl);
            console.log('[YouTube] ✅ Got audio stream in single call! (optimized)');
            return response.audioUrl;
        }

        // Fallback: If audioUrl wasn't included, fetch it separately
        // This can happen if stream resolution failed on backend
        console.log('[YouTube] Falling back to separate /stream call...');
        const audioUrl = await getYouTubeStreamUrl(response.videoId);
        if (audioUrl) {
            console.log('[YouTube] ✅ Got direct audio stream (fallback)');
            return audioUrl;
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

    if (isOffline()) return [];

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
