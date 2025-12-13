import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { saveAudioBlob, getAudioBlob, getAllOfflineSongs, deleteAudioBlob, getStorageUsage } from '../utils/offlineDB';
import { downloadSong } from '../utils/download';

const DEFAULT_OFFLINE_CONTEXT = {
    offlineSongIds: new Set(),
    downloadProgress: null,
    isOnline: true,
    storageUsage: { used: 0, songs: 0, usedMB: '0' },
    isDownloading: false,
    loadOfflineSongs: async () => { },
    downloadSingleSong: async () => false,
    downloadPlaylist: async () => ({ success: 0, failed: 0 }),
    getCachedAudioUrl: async () => null,
    isSongOffline: () => false,
    removeFromOffline: async () => { },
};

const OfflineContext = createContext(DEFAULT_OFFLINE_CONTEXT);

export const useOffline = () => useContext(OfflineContext);

export const OfflineProvider = ({ children }) => {
    // Core state
    const [offlineSongIds, setOfflineSongIds] = useState(new Set());
    const [isOnline, setIsOnline] = useState(navigator.onLine);
    const [downloadProgress, setDownloadProgress] = useState(null);
    const [storageUsage, setStorageUsage] = useState({ used: 0, songs: 0, usedMB: '0' });
    const [isDownloading, setIsDownloading] = useState(false);

    // Playlist toggle state
    const [toggledPlaylists, setToggledPlaylists] = useState(new Set());
    const [downloadQueue, setDownloadQueue] = useState([]);
    const [currentDownload, setCurrentDownload] = useState(null);

    // Online/offline listener
    useEffect(() => {
        const handleOnline = () => setIsOnline(true);
        const handleOffline = () => setIsOnline(false);
        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);
        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, []);

    // Load offline songs from IndexedDB on mount
    const loadOfflineSongs = useCallback(async () => {
        try {
            const songs = await getAllOfflineSongs();
            setOfflineSongIds(new Set(songs.map(s => s.songId)));
            const usage = await getStorageUsage();
            setStorageUsage(usage);
        } catch (e) {
            console.error('[Offline] Failed to load offline songs:', e);
        }
    }, []);

    useEffect(() => {
        loadOfflineSongs();
    }, [loadOfflineSongs]);

    // Check if a song is offline
    const isSongOffline = useCallback((songId) => {
        return offlineSongIds.has(songId);
    }, [offlineSongIds]);

    // Get cached audio URL for offline playback
    const getCachedAudioUrl = useCallback(async (songId) => {
        try {
            const blob = await getAudioBlob(songId);
            if (blob) {
                return URL.createObjectURL(blob);
            }
        } catch (e) {
            console.error('[Offline] Failed to get cached audio:', e);
        }
        return null;
    }, []);

    // Download a single song
    const downloadSingleSong = useCallback(async (song, cacheOnly = true) => {
        if (!song || offlineSongIds.has(song.id)) return true;

        setDownloadProgress({
            currentSong: song,
            status: 'downloading',
            queueSize: 1
        });

        try {
            const result = await downloadSong(song, cacheOnly);
            if (result?.success) {
                setOfflineSongIds(prev => new Set([...prev, song.id]));
                await loadOfflineSongs();
                setDownloadProgress({ currentSong: song, status: 'complete', queueSize: 0 });
                setTimeout(() => setDownloadProgress(null), 2000);
                return true;
            }
        } catch (e) {
            console.error('[Offline] Download failed:', e);
        }

        setDownloadProgress(null);
        return false;
    }, [offlineSongIds, loadOfflineSongs]);

    // Remove a song from offline storage
    const removeFromOffline = useCallback(async (songId) => {
        try {
            await deleteAudioBlob(songId);
            setOfflineSongIds(prev => {
                const next = new Set(prev);
                next.delete(songId);
                return next;
            });
            await loadOfflineSongs();
            return true;
        } catch (e) {
            console.error('[Offline] Failed to remove song:', e);
            return false;
        }
    }, [loadOfflineSongs]);

    // Load toggled playlists from localStorage
    useEffect(() => {
        try {
            const saved = localStorage.getItem('toggled_playlists');
            if (saved) {
                setToggledPlaylists(new Set(JSON.parse(saved)));
            }
        } catch (e) {
            console.error('Failed to load toggled playlists', e);
        }
    }, []);

    // Save toggled playlists
    useEffect(() => {
        try {
            localStorage.setItem('toggled_playlists', JSON.stringify([...toggledPlaylists]));
        } catch (e) {
            console.error('Failed to save toggled playlists', e);
        }
    }, [toggledPlaylists]);

    // Background Download Queue Processor
    useEffect(() => {
        if (!isOnline || isDownloading || downloadQueue.length === 0) {
            // Clear progress if queue is empty and not downloading
            if (downloadQueue.length === 0 && !isDownloading && downloadProgress) {
                setTimeout(() => setDownloadProgress(null), 2500);
            }
            return;
        }

        const processQueue = async () => {
            setIsDownloading(true);
            const song = downloadQueue[0];
            setCurrentDownload(song);

            setDownloadProgress({
                currentSong: song,
                status: 'downloading',
                queueSize: downloadQueue.length
            });

            try {
                // Check if already downloaded
                if (offlineSongIds.has(song.id)) {
                    console.log(`[Offline] Skipping (already cached): ${song.title}`);
                    const newQueue = downloadQueue.slice(1);
                    setDownloadQueue(newQueue);
                    setIsDownloading(false);
                    // If this was the last song, clear progress
                    if (newQueue.length === 0) {
                        setDownloadProgress({ currentSong: song, status: 'complete', queueSize: 0 });
                        setTimeout(() => setDownloadProgress(null), 2500);
                    }
                    return;
                }

                console.log(`[Offline] Downloading: ${song.title}`);
                const success = await downloadSong(song, true);

                if (success) {
                    setOfflineSongIds(prev => new Set([...prev, song.id]));
                    await loadOfflineSongs();
                }
            } catch (error) {
                console.error(`[Offline] Failed: ${song.title}`, error);
            } finally {
                // Remove from queue and continue
                setDownloadQueue(prev => {
                    const newQueue = prev.slice(1);
                    // If this was the last song, clear download progress after a delay
                    if (newQueue.length === 0) {
                        setDownloadProgress({ currentSong: song, status: 'complete', queueSize: 0 });
                        setTimeout(() => setDownloadProgress(null), 2500);
                    }
                    return newQueue;
                });
                setIsDownloading(false);
                setCurrentDownload(null);
            }
        };

        processQueue();
    }, [isOnline, isDownloading, downloadQueue, offlineSongIds, downloadProgress]);

    /**
     * Toggle download status for a playlist
     */
    const togglePlaylistDownload = useCallback(async (playlistId, songs) => {
        const isToggled = toggledPlaylists.has(playlistId);

        if (isToggled) {
            // TOGGLE OFF: Remove all songs
            console.log(`[Offline] Toggling OFF playlist: ${playlistId}`);

            // 1. Remove from toggled set
            setToggledPlaylists(prev => {
                const next = new Set(prev);
                next.delete(playlistId);
                return next;
            });

            // 2. Clear from queue
            setDownloadQueue(prev => prev.filter(s => !songs.find(ps => ps.id === s.id)));

            // 3. Delete downloaded files
            for (const song of songs) {
                if (offlineSongIds.has(song.id)) {
                    await deleteAudioBlob(song.id);
                }
            }

            // 4. Update state
            await loadOfflineSongs();

        } else {
            // TOGGLE ON: Add to queue
            console.log(`[Offline] Toggling ON playlist: ${playlistId}`);

            setToggledPlaylists(prev => new Set([...prev, playlistId]));

            // Filter songs that are not already in queue and not already downloaded
            const newSongs = songs.filter(s =>
                !offlineSongIds.has(s.id) &&
                !downloadQueue.find(q => q.id === s.id)
            );

            if (newSongs.length > 0) {
                setDownloadQueue(prev => [...prev, ...newSongs]);
                console.log(`[Offline] Added ${newSongs.length} songs to download queue`);
            }
        }
    }, [toggledPlaylists, offlineSongIds, downloadQueue]);

    const isPlaylistDownloaded = useCallback((playlistId) => {
        return toggledPlaylists.has(playlistId);
    }, [toggledPlaylists]);

    return (
        <OfflineContext.Provider value={{
            isOnline,
            offlineSongIds,
            downloadProgress,
            storageUsage,
            isDownloading,
            downloadQueue,
            togglePlaylistDownload,
            isPlaylistDownloaded,
            downloadSingleSong, // Kept for individual song actions
            getCachedAudioUrl,
            isSongOffline,
            removeFromOffline,
            refreshOfflineSongs: loadOfflineSongs
        }}>
            {children}
        </OfflineContext.Provider>
    );
};
