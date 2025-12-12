import React, { createContext, useContext, useState, useRef, useEffect, useCallback } from 'react';
import { searchYouTube, getRelatedSongs } from '../utils/youtube';
import { getAudioBlob } from '../utils/offlineDB';

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


    // Control audio playback
    useEffect(() => {
        const audio = playerRef.current;
        if (!audio) return;

        if (isPlaying) {
            audio.play().catch(e => console.warn('[Player] Play failed:', e));
        } else {
            audio.pause();
        }

        // Media Session API (Lock Screen Controls)
        // Update metadata and handlers
        useEffect(() => {
            if ('mediaSession' in navigator && currentSong) {
                navigator.mediaSession.metadata = new MediaMetadata({
                    title: currentSong.title || 'Unknown Title',
                    artist: currentSong.artist || 'Unknown Artist',
                    album: currentSong.album || 'Vikify',
                    artwork: [
                        { src: currentSong.image || '/icon.png', sizes: '512x512', type: 'image/png' }
                    ]
                });

                // Handlers
                navigator.mediaSession.setActionHandler('play', () => {
                    // Ensure audio context is resumed (mobile chrome requirement)
                    if (playerRef.current && playerRef.current.paused) playerRef.current.play();
                    setIsPlaying(true);
                });
                navigator.mediaSession.setActionHandler('pause', () => setIsPlaying(false));
                navigator.mediaSession.setActionHandler('previoustrack', playPrevious);
                navigator.mediaSession.setActionHandler('nexttrack', playNext);
                navigator.mediaSession.setActionHandler('seekto', (details) => {
                    if (details.seekTime !== undefined && playerRef.current) {
                        playerRef.current.currentTime = details.seekTime;
                        setProgress(details.seekTime);
                        // Update position state immediately after seek
                        updatePositionState(details.seekTime);
                    }
                });
            }
        }, [currentSong]); // Only update metadata on song change

        // Update Playback State and Position State
        useEffect(() => {
            if ('mediaSession' in navigator) {
                navigator.mediaSession.playbackState = isPlaying ? "playing" : "paused";
                updatePositionState();
            }
        }, [isPlaying, duration]); // Update state on Play/Pause/Duration change

        const updatePositionState = (specificParams = null) => {
            if ('mediaSession' in navigator && duration > 0 && playerRef.current) {
                try {
                    navigator.mediaSession.setPositionState({
                        duration: duration,
                        playbackRate: isPlaying ? 1.0 : 0.0,
                        position: specificParams !== null ? specificParams : (playerRef.current.currentTime || 0)
                    });
                } catch (error) {
                    console.warn('Media Session position update failed:', error);
                }
            }
        };

        // Cleanup blob URLs when song changes
        useEffect(() => {
            return () => {
                if (blobUrlRef.current) {
                    URL.revokeObjectURL(blobUrlRef.current);
                    blobUrlRef.current = null;
                }
            };
        }, [currentSong]);

        // Fetch related songs only when NOT playing from a playlist queue
        useEffect(() => {
            if (currentSong && autoplay && queue.length === 0) {
                fetchUpcomingSongs();
            }
        }, [currentSong, queue.length]);

        const fetchUpcomingSongs = async () => {
            if (!currentSong) return;

            console.log('[Autoplay] Fetching up to 6 related songs...');
            const related = await getRelatedSongs(currentSong);

            if (related.length > 0) {
                const shuffled = related.sort(() => Math.random() - 0.5).slice(0, 6);
                setUpNextQueue(shuffled);
                console.log('[Autoplay] Preloaded songs:', shuffled.map(s => s.title));
            }
        };



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

        const togglePlay = () => setIsPlaying(!isPlaying);
        const toggleAutoplay = () => setAutoplay(!autoplay);

        const playNext = () => {
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
                    // Optionally loop or stop
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
            };
        }, []);

        const handleProgress = (state) => setProgress(state.playedSeconds);
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
                handleEnded, onPlayerReady
            }}>
                {children}
            </PlayerContext.Provider>
        );
    };
