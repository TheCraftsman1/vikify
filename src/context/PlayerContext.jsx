import React, { createContext, useContext, useState, useRef, useEffect, useCallback } from 'react';
import { searchYouTube, getRelatedSongs, prefetchYouTubeVideoId } from '../utils/youtube';
import { getAudioBlob } from '../utils/offlineDB';
import { useAuth } from './AuthContext';
import { getRecommendations as getSpotifyRecommendations } from '../services/spotify';
import { hapticMedium, hapticLight } from '../utils/haptics';
import { updateNowPlaying, clearNowPlaying, onNowPlayingAction, isNative as isNativePlatform } from '../utils/nowPlaying';

const PlayerContext = createContext();

// Audio quality and volume constants
const VOLUME_MIN = 0;
const VOLUME_MAX = 1;
const VOLUME_DEFAULT = 0.8; // Start at 80% for ear safety
const VOLUME_STEP = 0.05; // 5% increments for keyboard shortcuts

export const usePlayer = () => useContext(PlayerContext);

export const PlayerProvider = ({ children }) => {
    const [currentSong, setCurrentSong] = useState(null);
    const [isPlaying, setIsPlaying] = useState(false);
    const [queue, setQueue] = useState([]); // Current playlist queue
    const [originalQueue, setOriginalQueue] = useState([]); // Store original order for unshuffle
    const [queueIndex, setQueueIndex] = useState(-1); // Current index in queue
    const [progress, setProgress] = useState(0);
    const [duration, setDuration] = useState(0);
    const [youtubeUrl, setYoutubeUrl] = useState(null);
    const [isLoading, setIsLoading] = useState(false);
    const [autoplay, setAutoplay] = useState(true);
    const [shuffle, setShuffle] = useState(false); // Shuffle mode
    const [upNextQueue, setUpNextQueue] = useState([]); // Suggested songs (used only when no playlist queue)
    const [isOfflinePlayback, setIsOfflinePlayback] = useState(false);
    const [volume, setVolume] = useState(VOLUME_DEFAULT); // Start at safe volume
    const [isMuted, setIsMuted] = useState(false);
    const [sleepTimer, setSleepTimer] = useState(null); // Minutes left, null = off
    const [isFullScreen, setIsFullScreen] = useState(false);
    const [audioQuality, setAudioQuality] = useState(null); // {bitrate, codec, level}
    const playerRef = useRef(null);
    const blobUrlRef = useRef(null);
    const sleepTimerRef = useRef(null);
    const progressRafRef = useRef(null);
    const pendingProgressRef = useRef(null);

    // Refs for Media Session handlers (to avoid stale closures)
    const playNextRef = useRef(null);
    const playPreviousRef = useRef(null);

    // Add useAuth hook inside component
    const { spotifyToken, isSpotifyAuthenticated } = useAuth();

    // Control audio playback
    useEffect(() => {
        const audio = playerRef.current;
        if (!audio) return;

        if (isPlaying) {
            audio.play().catch(e => console.warn('[Player] Play failed:', e));
        } else {
            audio.pause();
        }
    }, [isPlaying]);

    // Position State Update Function
    const updatePositionState = useCallback((specificPosition = null) => {
        if ('mediaSession' in navigator && duration > 0 && playerRef.current) {
            try {
                navigator.mediaSession.setPositionState({
                    duration: duration,
                    playbackRate: 1.0,
                    position: specificPosition !== null ? specificPosition : (playerRef.current.currentTime || 0)
                });
            } catch (error) {
                console.warn('Media Session position update failed:', error);
            }
        }
    }, [duration]);

    // Media Session API (Lock Screen Controls + Notification)
    useEffect(() => {
        if (!('mediaSession' in navigator) || !currentSong) return;

        // Set metadata with artwork
        navigator.mediaSession.metadata = new MediaMetadata({
            title: currentSong.title || 'Unknown Title',
            artist: currentSong.artist || 'Unknown Artist',
            album: currentSong.album || 'Vikify',
            artwork: [
                { src: currentSong.image || '/icon.png', sizes: '96x96', type: 'image/png' },
                { src: currentSong.image || '/icon.png', sizes: '128x128', type: 'image/png' },
                { src: currentSong.image || '/icon.png', sizes: '256x256', type: 'image/png' },
                { src: currentSong.image || '/icon.png', sizes: '512x512', type: 'image/png' }
            ]
        });

        // Play handler
        navigator.mediaSession.setActionHandler('play', () => {
            hapticLight();
            if (playerRef.current?.paused) playerRef.current.play();
            setIsPlaying(true);
        });

        // Pause handler
        navigator.mediaSession.setActionHandler('pause', () => {
            hapticLight();
            setIsPlaying(false);
        });

        // Previous track handler (uses ref to avoid stale closure)
        navigator.mediaSession.setActionHandler('previoustrack', () => {
            hapticMedium();
            playPreviousRef.current?.();
        });

        // Next track handler (uses ref to avoid stale closure)
        navigator.mediaSession.setActionHandler('nexttrack', () => {
            hapticMedium();
            playNextRef.current?.();
        });

        // Seek to handler (for notification seek bar)
        navigator.mediaSession.setActionHandler('seekto', (details) => {
            if (details.seekTime !== undefined && playerRef.current) {
                playerRef.current.currentTime = details.seekTime;
                setProgress(details.seekTime);
                updatePositionState(details.seekTime);
            }
        });

        // Seek backward handler (skip back 10 seconds)
        navigator.mediaSession.setActionHandler('seekbackward', (details) => {
            const skipTime = details.seekOffset || 10;
            if (playerRef.current) {
                const newTime = Math.max(0, playerRef.current.currentTime - skipTime);
                playerRef.current.currentTime = newTime;
                setProgress(newTime);
                updatePositionState(newTime);
            }
        });

        // Seek forward handler (skip forward 10 seconds)
        navigator.mediaSession.setActionHandler('seekforward', (details) => {
            const skipTime = details.seekOffset || 10;
            if (playerRef.current && duration > 0) {
                const newTime = Math.min(duration, playerRef.current.currentTime + skipTime);
                playerRef.current.currentTime = newTime;
                setProgress(newTime);
                updatePositionState(newTime);
            }
        });

        // Stop handler
        navigator.mediaSession.setActionHandler('stop', () => {
            setIsPlaying(false);
            if (playerRef.current) {
                playerRef.current.currentTime = 0;
                setProgress(0);
            }
        });

    }, [currentSong, duration, updatePositionState]);

    // Native Android notification shade controls (fallback when MediaSession notification isn't shown by WebView)
    useEffect(() => {
        if (!isNativePlatform()) return;

        if (!currentSong) {
            clearNowPlaying();
            return;
        }

        updateNowPlaying({
            title: currentSong.title || 'Vikify',
            artist: currentSong.artist || '',
            isPlaying: !!isPlaying,
            positionSeconds: progress || 0,
            durationSeconds: duration || 0
        });
    }, [currentSong, isPlaying, duration]);

    // Keep MediaSession position in sync so Android shows proper seekbar + timestamps.
    // Throttle to ~1s while playing to avoid spamming the native bridge.
    useEffect(() => {
        if (!isNativePlatform()) return;
        if (!currentSong) return;

        let timer = null;

        const push = () => {
            updateNowPlaying({
                title: currentSong.title || 'Vikify',
                artist: currentSong.artist || '',
                isPlaying: !!isPlaying,
                positionSeconds: progress || 0,
                durationSeconds: duration || 0
            });
        };

        // Always push once on effect start.
        push();

        if (isPlaying) {
            timer = setInterval(push, 1000);
        }

        return () => {
            if (timer) clearInterval(timer);
        };
    }, [currentSong, isPlaying, duration, progress]);

    useEffect(() => {
        if (!isNativePlatform()) return;

        const sub = onNowPlayingAction(({ action, positionMs }) => {
            switch (action) {
                case 'com.vikify.app.NOW_PLAY':
                    if (playerRef.current?.paused) {
                        playerRef.current.play().catch(() => {});
                    }
                    setIsPlaying(true);
                    break;
                case 'com.vikify.app.NOW_PAUSE':
                    playerRef.current?.pause?.();
                    setIsPlaying(false);
                    break;
                case 'com.vikify.app.NOW_NEXT':
                    playNextRef.current?.();
                    break;
                case 'com.vikify.app.NOW_PREV':
                    playPreviousRef.current?.();
                    break;
                case 'com.vikify.app.NOW_STOP':
                    playerRef.current?.pause?.();
                    setIsPlaying(false);
                    clearNowPlaying();
                    break;
                case 'com.vikify.app.NOW_SEEK_TO':
                    if (typeof positionMs === 'number' && playerRef.current) {
                        const t = Math.max(0, (positionMs || 0) / 1000);
                        playerRef.current.currentTime = t;
                        setProgress(t);
                        updatePositionState(t);
                    }
                    break;
                default:
                    break;
            }
        });

        return () => {
            sub?.remove?.();
        };
    }, []);

    // Update Playback State
    useEffect(() => {
        if ('mediaSession' in navigator) {
            navigator.mediaSession.playbackState = isPlaying ? "playing" : "paused";
            updatePositionState();
        }
    }, [isPlaying, duration, updatePositionState]);

    // Cleanup blob URLs when song changes
    useEffect(() => {
        return () => {
            if (blobUrlRef.current) {
                URL.revokeObjectURL(blobUrlRef.current);
                blobUrlRef.current = null;
            }
        };
    }, [currentSong]);

    // Fetch related songs
    const fetchUpcomingSongs = useCallback(async () => {
        if (!currentSong) return;

        // 1. Try Spotify Recommendations
        if (isSpotifyAuthenticated && spotifyToken && currentSong.isSpotify) {
            console.log('[Autoplay] Fetching Spotify Recommendations...');
            try {
                const spotifyRecs = await getSpotifyRecommendations(currentSong.id, spotifyToken);
                if (spotifyRecs && spotifyRecs.length > 0) {
                    setUpNextQueue(spotifyRecs);
                    console.log('[Autoplay] Spotify Preloaded:', spotifyRecs.map(s => s.title));
                    return;
                }
            } catch (e) {
                console.warn('[Autoplay] Spotify Recs failed:', e);
            }
        }

        // 2. Fallback to YouTube Related Songs
        console.log('[Autoplay] Fetching up to 6 related songs (YouTube)...');
        try {
            const related = await getRelatedSongs(currentSong);
            if (related.length > 0) {
                const shuffled = related.sort(() => Math.random() - 0.5).slice(0, 6);
                setUpNextQueue(shuffled);
                console.log('[Autoplay] YouTube Preloaded:', shuffled.map(s => s.title));

                // Prefetch stable videoIds (fast + avoids repeating /search)
                shuffled.slice(0, 3).forEach((s) => {
                    prefetchYouTubeVideoId(`${s.title} ${s.artist}`);
                });
            }
        } catch (e) {
            console.error("Autoplay fetch failed", e);
        }
    }, [currentSong, isSpotifyAuthenticated, spotifyToken]);

    // Attempt to refresh stream URL when it expires or fails.
    const reloadCurrentStream = useCallback(async () => {
        if (!currentSong) return false;
        if (!navigator.onLine) return false;

        // Never reload offline blob playback.
        if (isOfflinePlayback) return false;

        try {
            console.log('[PlayerContext] Reloading stream URL...');
            const audioUrl = await searchYouTube(`${currentSong.title} ${currentSong.artist}`);
            if (audioUrl) {
                setYoutubeUrl(audioUrl);
                return true;
            }
        } catch (e) {
            console.warn('[PlayerContext] Stream reload failed:', e?.message || e);
        }
        return false;
    }, [currentSong, isOfflinePlayback]);

    // Trigger fetch when song changes
    useEffect(() => {
        if (currentSong) {
            fetchUpcomingSongs();
        }
    }, [currentSong, fetchUpcomingSongs]);



    /**
     * Play a song
     * @param {Object} song - The song to play
     * @param {Array} playlistQueue - Optional: the full playlist to use for next/previous
     */
    const playSong = async (song, playlistQueue = null) => {
        console.log("[PlayerContext] playSong:", song?.title);

        if (currentSong?.id === song.id) {
            togglePlay();
            return;
        }

        setIsLoading(true);
        setCurrentSong(song);
        setIsPlaying(false);
        setProgress(0);
        setYoutubeUrl(null);
        setIsOfflinePlayback(false);

        // If a playlist queue is provided, set it
        if (playlistQueue && Array.isArray(playlistQueue) && playlistQueue.length > 0) {
            setQueue(playlistQueue);
            const idx = playlistQueue.findIndex(s => s.id === song.id);
            setQueueIndex(idx >= 0 ? idx : 0);
            setUpNextQueue([]); // Clear suggested songs when playing from playlist
            console.log('[PlayerContext] Playing from playlist queue, index:', idx);
        }

        // Revoke previous blob URL if exists
        if (blobUrlRef.current) {
            URL.revokeObjectURL(blobUrlRef.current);
            blobUrlRef.current = null;
        }

        // Remove from upNextQueue if playing from there
        if (upNextQueue.some(s => s.id === song.id)) {
            setUpNextQueue(prev => prev.filter(s => s.id !== song.id));
        }

        try {
            // Priority 1: Check IndexedDB for cached audio (OFFLINE MODE)
            const cachedBlob = await getAudioBlob(song.id);
            if (cachedBlob) {
                console.log("[PlayerContext] ✅ Playing from offline cache!");
                const blobUrl = URL.createObjectURL(cachedBlob);
                blobUrlRef.current = blobUrl;
                setYoutubeUrl(blobUrl);
                setIsOfflinePlayback(true);
                setIsLoading(false);
                return;
            }

            // Priority 2: Local file
            if (song.url && !song.url.startsWith('http')) {
                console.log("[PlayerContext] ✅ Playing local file");
                setYoutubeUrl(song.url);
            }
            // Priority 3: Online - search YouTube
            else {
                if (!navigator.onLine) {
                    console.error("[PlayerContext] ❌ Offline and song not cached");
                    setIsLoading(false);
                    return;
                }

                console.log("[PlayerContext] Searching YouTube...");
                const audioUrl = await searchYouTube(`${song.title} ${song.artist}`);

                if (audioUrl) {
                    console.log("[PlayerContext] ✅ Got audio stream!");
                    setYoutubeUrl(audioUrl);
                } else if (song.previewUrl) {
                    console.log("[PlayerContext] ⚠️ Using iTunes preview");
                    setYoutubeUrl(song.previewUrl);
                } else {
                    console.error("[PlayerContext] ❌ No audio source");
                }
            }
        } catch (error) {
            console.error("[PlayerContext] Error:", error);
            if (song.previewUrl) {
                setYoutubeUrl(song.previewUrl);
            }
        }

        setIsLoading(false);
    };

    const onPlayerReady = () => {
        console.log("[PlayerContext] Audio ready, starting playback");
        setIsPlaying(true);
    };

    const togglePlay = () => {
        hapticLight();
        setIsPlaying(!isPlaying);
    };
    const toggleAutoplay = () => setAutoplay(!autoplay);

    const playNext = () => {
        hapticMedium();
        // Priority 1: Check playlist queue
        if (queue.length > 0 && queueIndex >= 0) {
            if (queueIndex < queue.length - 1) {
                const nextIdx = queueIndex + 1;
                setQueueIndex(nextIdx);
                playSong(queue[nextIdx], queue);
                console.log('[PlayerContext] Playing next in queue:', queue[nextIdx].title);
                return;
            } else {
                console.log('[PlayerContext] End of playlist queue');
            }
        }

        // Priority 2: If no queue and autoplay enabled, play from suggested songs
        if (autoplay && upNextQueue.length > 0) {
            const next = upNextQueue[0];
            console.log('[Autoplay] Playing suggested:', next.title);
            playSong(next);
        }
    };

    const playPrevious = () => {
        hapticMedium();
        // Check playlist queue
        if (queue.length > 0 && queueIndex > 0) {
            const prevIdx = queueIndex - 1;
            setQueueIndex(prevIdx);
            playSong(queue[prevIdx], queue);
            console.log('[PlayerContext] Playing previous in queue:', queue[prevIdx].title);
            return;
        }

        // If at beginning, restart current song
        if (playerRef.current) {
            playerRef.current.currentTime = 0;
            setProgress(0);
        }
    };

    // Update refs for Media Session handlers (to avoid stale closures)
    useEffect(() => {
        playNextRef.current = playNext;
        playPreviousRef.current = playPrevious;
    });

    const addToQueue = (song) => setQueue(prev => [...prev, song]);

    const clearQueue = () => {
        setQueue([]);
        setOriginalQueue([]);
        setQueueIndex(-1);
    };

    /**
     * Toggle shuffle mode and shuffle/unshuffle the queue
     */
    const toggleShuffle = () => {
        if (!queue.length) {
            setShuffle(!shuffle);
            return;
        }

        if (!shuffle) {
            // Enable shuffle: store original queue, then shuffle
            setOriginalQueue([...queue]);
            const currentSongInQueue = queue[queueIndex];
            const remainingSongs = queue.filter((_, i) => i !== queueIndex);
            const shuffled = remainingSongs.sort(() => Math.random() - 0.5);
            const newQueue = [currentSongInQueue, ...shuffled];
            setQueue(newQueue);
            setQueueIndex(0);
            console.log('[PlayerContext] Shuffle ON');
        } else {
            // Disable shuffle: restore original queue
            if (originalQueue.length > 0) {
                const currentSongInQueue = queue[queueIndex];
                const originalIdx = originalQueue.findIndex(s => s.id === currentSongInQueue?.id);
                setQueue(originalQueue);
                setQueueIndex(originalIdx >= 0 ? originalIdx : 0);
                setOriginalQueue([]);
            }
            console.log('[PlayerContext] Shuffle OFF');
        }
        setShuffle(!shuffle);
    };

    /**
     * Start playing playlist in shuffle mode
     */
    const shufflePlay = (songs) => {
        if (!songs || songs.length === 0) return;

        setOriginalQueue([...songs]);
        const shuffled = [...songs].sort(() => Math.random() - 0.5);
        setQueue(shuffled);
        setQueueIndex(0);
        setShuffle(true);
        playSong(shuffled[0], shuffled);
        console.log('[PlayerContext] Shuffle play started');
    };

    const skipAutoplaySong = () => {
        if (upNextQueue.length > 1) {
            setUpNextQueue(prev => prev.slice(1));
        }
    };

    const seek = (time) => {
        if (playerRef.current) {
            playerRef.current.currentTime = time;
            setProgress(time);
        }
    };

    // Volume controls
    const changeVolume = useCallback((newVolume) => {
        const vol = Math.max(0, Math.min(1, newVolume));
        setVolume(vol);
        setIsMuted(vol === 0);
        if (playerRef.current) {
            playerRef.current.volume = vol;
        }
    }, []);

    const toggleMute = useCallback(() => {
        setIsMuted(prev => {
            const newMuted = !prev;
            if (playerRef.current) {
                playerRef.current.volume = newMuted ? 0 : volume;
            }
            return newMuted;
        });
    }, [volume]);

    // Sleep Timer
    const startSleepTimer = useCallback((minutes) => {
        // Clear existing timer
        if (sleepTimerRef.current) {
            clearInterval(sleepTimerRef.current);
        }

        if (!minutes || minutes <= 0) {
            setSleepTimer(null);
            return;
        }

        setSleepTimer(minutes);
        console.log(`[Sleep Timer] Started: ${minutes} minutes`);

        sleepTimerRef.current = setInterval(() => {
            setSleepTimer(prev => {
                if (prev <= 1) {
                    // Timer expired - pause playback
                    setIsPlaying(false);
                    clearInterval(sleepTimerRef.current);
                    sleepTimerRef.current = null;
                    console.log('[Sleep Timer] Expired - music paused');
                    return null;
                }
                return prev - 1;
            });
        }, 60000); // Every minute
    }, []);

    const cancelSleepTimer = useCallback(() => {
        if (sleepTimerRef.current) {
            clearInterval(sleepTimerRef.current);
            sleepTimerRef.current = null;
        }
        setSleepTimer(null);
        console.log('[Sleep Timer] Cancelled');
    }, []);

    // Fullscreen toggle
    const toggleFullScreen = useCallback(() => {
        setIsFullScreen(prev => !prev);
    }, []);

    // Keyboard shortcuts
    useEffect(() => {
        const handleKeyDown = (e) => {
            // Don't trigger if typing in an input
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return;

            switch (e.code) {
                case 'Space':
                    e.preventDefault();
                    setIsPlaying(prev => !prev);
                    break;
                case 'ArrowRight':
                    if (e.shiftKey) {
                        // Skip forward 10 seconds
                        if (playerRef.current) {
                            const newTime = Math.min(duration, progress + 10);
                            playerRef.current.currentTime = newTime;
                            setProgress(newTime);
                        }
                    } else {
                        playNext();
                    }
                    break;
                case 'ArrowLeft':
                    if (e.shiftKey) {
                        // Skip backward 10 seconds
                        if (playerRef.current) {
                            const newTime = Math.max(0, progress - 10);
                            playerRef.current.currentTime = newTime;
                            setProgress(newTime);
                        }
                    } else {
                        playPrevious();
                    }
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    changeVolume(volume + VOLUME_STEP);
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    changeVolume(volume - VOLUME_STEP);
                    break;
                case 'KeyM':
                    toggleMute();
                    break;
                case 'KeyF':
                    toggleFullScreen();
                    break;
                case 'KeyS':
                    if (!e.ctrlKey && !e.metaKey) {
                        toggleShuffle();
                    }
                    break;
                case 'Escape':
                    setIsFullScreen(false);
                    break;
                default:
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [progress, duration, volume, changeVolume, toggleMute, toggleFullScreen]);

    // Cleanup sleep timer on unmount
    useEffect(() => {
        return () => {
            if (sleepTimerRef.current) {
                clearInterval(sleepTimerRef.current);
            }
            if (progressRafRef.current) {
                cancelAnimationFrame(progressRafRef.current);
                progressRafRef.current = null;
            }
        };
    }, []);

    const handleProgress = (state) => {
        const next = state?.playedSeconds;
        if (typeof next !== 'number' || Number.isNaN(next)) return;

        pendingProgressRef.current = next;
        if (progressRafRef.current) return;

        progressRafRef.current = requestAnimationFrame(() => {
            progressRafRef.current = null;
            if (typeof pendingProgressRef.current === 'number') {
                setProgress(pendingProgressRef.current);
            }
        });
    };
    const handleDuration = (d) => setDuration(d);
    const handleEnded = () => playNext();

    return (
        <PlayerContext.Provider value={{
            currentSong, isPlaying, queue, queueIndex, progress, duration,
            youtubeUrl, isLoading, playerRef, autoplay, upNextQueue,
            isOfflinePlayback, shuffle, volume, isMuted, sleepTimer, isFullScreen,
            playSong, togglePlay, toggleAutoplay, toggleShuffle, shufflePlay,
            playNext, playPrevious, changeVolume, toggleMute,
            startSleepTimer, cancelSleepTimer, toggleFullScreen,
            addToQueue, clearQueue, skipAutoplaySong, seek, handleProgress, handleDuration,
            handleEnded, onPlayerReady,
            reloadCurrentStream
        }}>
            {children}
        </PlayerContext.Provider>
    );
};
