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

export const getYouTubeStreamUrl = async (videoId) => {
    if (!videoId) return null;

    const cached = streamUrlCache.get(videoId);
    if (cached && isValidHttpUrl(cached)) return cached;

    if (isOffline()) return null;

    const audioUrl = await withRetry(
        async () => {
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
 * Returns audio URL directly (not YouTube URL)
 */
export const searchYouTube = async (query) => {
    console.log(`[YouTube] Searching: ${query}`);

    try {
        // Step 1: Resolve (and cache) the videoId for this query
        const videoId = await getYouTubeVideoId(query);
        if (!videoId) return null;
        console.log(`[YouTube] Found video: ${videoId}`);

        // Step 2: Resolve a fresh direct audio stream URL (short cache)
        const audioUrl = await getYouTubeStreamUrl(videoId);
        if (audioUrl) {
            console.log('[YouTube] ✅ Got direct audio stream!');
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
