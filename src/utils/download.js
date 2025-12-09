import { saveAudioBlob } from './offlineDB';

const BACKEND_URL = 'https://vikify-production.up.railway.app';

/**
 * Get video ID from YouTube search
 */
const searchYouTubeForDownload = async (title, artist) => {
    try {
        const query = `${title} ${artist}`;
        const response = await fetch(`${BACKEND_URL}/search?q=${encodeURIComponent(query)}`);
        const data = await response.json();

        if (data.success && data.videoId) {
            return data.videoId;
        }
    } catch (error) {
        console.error('[Download] Search failed:', error);
    }
    return null;
};

/**
 * Download a song to the user's device and cache for offline playback
 * @param {Object} song - Song object with title, artist, etc.
 * @param {boolean} cacheOnly - If true, only cache without triggering file download
 * @returns {Promise<boolean>} Success status
 */
export const downloadSong = async (song, cacheOnly = false) => {
    try {
        console.log('[Download] Starting download for:', song.title);

        let blob = null;
        let filename = `${song.artist} - ${song.title}.mp3`;
        let isLocalFile = false;

        // Priority 1: If song has a local URL, use that
        if (song.url && !song.url.startsWith('http')) {
            isLocalFile = true;
            console.log('[Download] Using local file');

            try {
                const response = await fetch(song.url);
                blob = await response.blob();
            } catch (e) {
                console.error('[Download] Local file fetch failed');
            }
        }
        // Priority 2: Use backend proxy for YouTube (bypasses CORS)
        else {
            console.log('[Download] Searching YouTube...');
            const videoId = await searchYouTubeForDownload(song.title, song.artist);

            if (videoId) {
                console.log('[Download] Got videoId:', videoId, '- Using backend proxy');
                filename = `${song.artist} - ${song.title}.webm`;

                try {
                    // Use the backend proxy endpoint that bypasses CORS
                    const proxyUrl = `${BACKEND_URL}/download/${videoId}`;
                    console.log('[Download] Fetching via proxy...');

                    const response = await fetch(proxyUrl);
                    if (response.ok) {
                        blob = await response.blob();
                        console.log('[Download] Audio fetched via proxy, size:', (blob.size / 1024 / 1024).toFixed(2), 'MB');
                    }
                } catch (fetchError) {
                    console.error('[Download] Proxy fetch failed:', fetchError);
                }
            }
        }

        // Fallback: Use iTunes preview (smaller but works)
        if (!blob && song.previewUrl) {
            console.log('[Download] Falling back to iTunes preview');
            filename = `${song.artist} - ${song.title} (Preview).m4a`;

            try {
                const response = await fetch(song.previewUrl);
                blob = await response.blob();
            } catch (e) {
                console.error('[Download] Preview fetch failed');
            }
        }

        if (!blob) {
            console.error('[Download] No audio source available');
            if (!cacheOnly) alert('Unable to download this song');
            return false;
        }

        // Cache to IndexedDB for offline playback
        const cached = await saveAudioBlob(song.id, blob, {
            title: song.title,
            artist: song.artist,
            image: song.image,
            album: song.album,
            duration: song.duration,
            url: song.url
        });

        if (cached) {
            console.log('[Download] ✅ Cached for offline playback');
        }

        // Trigger browser download (unless cacheOnly)
        if (!cacheOnly) {
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
            console.log('[Download] Browser download triggered');
        }

        // Save to download history in localStorage
        const downloads = JSON.parse(localStorage.getItem('downloads') || '[]');
        const downloadEntry = {
            ...song,
            downloadedAt: new Date().toISOString(),
            isFullSong: !isLocalFile && blob.size > 500000, // > 500KB likely full song
            isOffline: true
        };

        // Remove duplicates and add to beginning
        const filtered = downloads.filter(d => d.id !== song.id);
        filtered.unshift(downloadEntry);

        // Keep only last 100 downloads
        if (filtered.length > 100) filtered.pop();

        localStorage.setItem('downloads', JSON.stringify(filtered));

        return true;
    } catch (error) {
        console.error('[Download] Error:', error);
        if (!cacheOnly) alert('Failed to download song. Please try again.');
        return false;
    }
};

/**
 * Download multiple songs (e.g., entire playlist)
 * @param {Array} songs - Array of song objects
 * @param {Function} onProgress - Progress callback (current, total)
 */
export const downloadPlaylist = async (songs, onProgress) => {
    let success = 0;
    let failed = 0;

    for (let i = 0; i < songs.length; i++) {
        if (onProgress) onProgress(i + 1, songs.length);

        const result = await downloadSong(songs[i], true); // cacheOnly = true for bulk
        if (result) {
            success++;
        } else {
            failed++;
        }

        // Small delay to not overwhelm the backend
        await new Promise(resolve => setTimeout(resolve, 1000));
    }

    console.log(`[Download] Playlist complete: ${success} succeeded, ${failed} failed`);
    return { success, failed };
};
