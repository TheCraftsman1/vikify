import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { saveAudioBlob, getAudioBlob, getAllOfflineSongs, deleteAudioBlob, getStorageUsage } from '../utils/offlineDB';
import { downloadSong } from '../utils/download';

const DEFAULT_OFFLINE_CONTEXT = {
    offlineSongIds: new Set(),
    downloadProgress: null,
    isOnline: true,
    storageUsage: { used: 0, songs: 0, usedMB: '0' },
    isDownloading: false,
    loadOfflineSongs: async () => {},
    downloadSingleSong: async () => false,
    downloadPlaylist: async () => ({ success: 0, failed: 0 }),
    getCachedAudioUrl: async () => null,
    isSongOffline: () => false,
    removeFromOffline: async () => {},
};

const OfflineContext = createContext(DEFAULT_OFFLINE_CONTEXT);

export const useOffline = () => useContext(OfflineContext);

export const OfflineProvider = ({ children }) => {
    const [offlineSongIds, setOfflineSongIds] = useState(new Set());
    const [downloadProgress, setDownloadProgress] = useState(null);
    const [isOnline, setIsOnline] = useState(navigator.onLine);
    const [storageUsage, setStorageUsage] = useState({ used: 0, songs: 0, usedMB: '0' });
    const [isDownloading, setIsDownloading] = useState(false);

    // Monitor online/offline status
    useEffect(() => {
        const handleOnline = () => {
            console.log('[Offline] Network: Online');
            setIsOnline(true);
        };
        const handleOffline = () => {
            console.log('[Offline] Network: Offline');
            setIsOnline(false);
        };

        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);

        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, []);

    // Load offline songs on mount
    useEffect(() => {
        loadOfflineSongs();
    }, []);

    const loadOfflineSongs = async () => {
        const songs = await getAllOfflineSongs();
        setOfflineSongIds(new Set(songs.map(s => s.songId)));
        const usage = await getStorageUsage();
        setStorageUsage(usage);
        console.log('[Offline] Loaded', songs.length, 'offline songs');
    };

    /**
     * Download a single song for offline playback
     */
    const downloadSingleSong = useCallback(async (song) => {
        if (isDownloading) {
            console.log('[Offline] Already downloading, please wait');
            return false;
        }

        setIsDownloading(true);
        setDownloadProgress({
            currentSong: song,
            progress: 0,
            total: 1,
            current: 1,
            status: 'downloading'
        });

        try {
            const success = await downloadSong(song, true); // cacheOnly = true

            if (success) {
                setOfflineSongIds(prev => new Set([...prev, song.id]));
                await loadOfflineSongs();
            }

            setDownloadProgress({
                currentSong: song,
                progress: 100,
                total: 1,
                current: 1,
                status: 'complete'
            });

            // Clear after 2 seconds
            setTimeout(() => {
                setDownloadProgress(null);
                setIsDownloading(false);
            }, 2000);

            return success;
        } catch (error) {
            console.error('[Offline] Download failed:', error);
            setDownloadProgress(null);
            setIsDownloading(false);
            return false;
        }
    }, [isDownloading]);

    /**
     * Download multiple songs (playlist) for offline playback
     */
    const downloadPlaylist = useCallback(async (songs, playlistName = 'Playlist') => {
        if (isDownloading) {
            console.log('[Offline] Already downloading, please wait');
            return { success: 0, failed: 0 };
        }

        if (!songs || songs.length === 0) {
            return { success: 0, failed: 0 };
        }

        setIsDownloading(true);
        let success = 0;
        let failed = 0;

        console.log(`[Offline] Starting playlist download: ${playlistName} (${songs.length} songs)`);

        for (let i = 0; i < songs.length; i++) {
            const song = songs[i];

            // Skip if already offline
            if (offlineSongIds.has(song.id)) {
                console.log(`[Offline] Skipping (already cached): ${song.title}`);
                success++;
                continue;
            }

            setDownloadProgress({
                currentSong: song,
                progress: 0,
                total: songs.length,
                current: i + 1,
                status: 'downloading',
                playlistName
            });

            try {
                const result = await downloadSong(song, true);
                if (result) {
                    success++;
                    setOfflineSongIds(prev => new Set([...prev, song.id]));
                } else {
                    failed++;
                }
            } catch (error) {
                console.error(`[Offline] Failed: ${song.title}`, error);
                failed++;
            }

            // Small delay between downloads
            await new Promise(resolve => setTimeout(resolve, 500));
        }

        await loadOfflineSongs();

        setDownloadProgress({
            currentSong: null,
            progress: 100,
            total: songs.length,
            current: songs.length,
            status: 'complete',
            playlistName,
            success,
            failed
        });

        // Clear after 3 seconds
        setTimeout(() => {
            setDownloadProgress(null);
            setIsDownloading(false);
        }, 3000);

        console.log(`[Offline] Playlist complete: ${success} succeeded, ${failed} failed`);
        return { success, failed };
    }, [isDownloading, offlineSongIds]);

    /**
     * Get cached audio URL for a song
     */
    const getCachedAudioUrl = useCallback(async (songId) => {
        const blob = await getAudioBlob(songId);
        if (blob) {
            return URL.createObjectURL(blob);
        }
        return null;
    }, []);

    /**
     * Check if a song is available offline
     */
    const isSongOffline = useCallback((songId) => {
        return offlineSongIds.has(songId);
    }, [offlineSongIds]);

    /**
     * Remove a song from offline cache
     */
    const removeFromOffline = useCallback(async (songId) => {
        await deleteAudioBlob(songId);
        setOfflineSongIds(prev => {
            const newSet = new Set(prev);
            newSet.delete(songId);
            return newSet;
        });
        const usage = await getStorageUsage();
        setStorageUsage(usage);
    }, []);

    return (
        <OfflineContext.Provider value={{
            isOnline,
            offlineSongIds,
            downloadProgress,
            storageUsage,
            isDownloading,
            downloadSingleSong,
            downloadPlaylist,
            getCachedAudioUrl,
            isSongOffline,
            removeFromOffline,
            refreshOfflineSongs: loadOfflineSongs
        }}>
            {children}
        </OfflineContext.Provider>
    );
};
