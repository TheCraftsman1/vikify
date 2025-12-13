import React, { createContext, useContext, useState, useRef, useEffect, useCallback } from 'react';
import { searchYouTube, getRelatedSongs } from '../utils/youtube';
import { getAudioBlob } from '../utils/offlineDB';
import { useAuth } from './AuthContext';
import { useHistory } from './HistoryContext';
import { useCrossfade } from './CrossfadeContext';
import { getRecommendations as getSpotifyRecommendations } from '../services/spotify';
import { hapticMedium, hapticLight } from '../utils/haptics';
import { updateNowPlaying, clearNowPlaying, onNowPlayingAction, isNative as isNativePlatform } from '../utils/nowPlaying';
import { preloadService } from '../services/preloadService';

const PlayerContext = createContext();

// Audio quality and volume constants
const VOLUME_MIN = 0;
const VOLUME_MAX = 1;
const VOLUME_DEFAULT = 0.8; // Start at 80% for ear safety
const VOLUME_STEP = 0.05; // 5% increments for keyboard shortcuts

export const usePlayer = () => useContext(PlayerContext);

export const PlayerProvider = ({ children }) => {
    const [currentSong, setCurrentSong] = useState(null);
    const [nextSong, setNextSong] = useState(null); // Song capable of being crossfaded to
    const [isPlaying, setIsPlaying] = useState(false);
    const [queue, setQueue] = useState([]); // Current playlist queue
    const [originalQueue, setOriginalQueue] = useState([]); // Store original order for unshuffle
    const [queueIndex, setQueueIndex] = useState(-1); // Current index in queue
    const [progress, setProgress] = useState(0);
    const [duration, setDuration] = useState(0);

    // Dual Player State for Crossfade
    const [activePlayer, setActivePlayer] = useState('A'); // 'A' or 'B'
    const [urlA, setUrlA] = useState(null);
    const [urlB, setUrlB] = useState(null);
    const playerRefA = useRef(null);
    const playerRefB = useRef(null);
    const activePlayerRef = useRef('A'); // Keep ref in sync for callbacks

    // Update activePlayerRef when activePlayer changes
    useEffect(() => {
        activePlayerRef.current = activePlayer;
    }, [activePlayer]);

    // Helper to get current active audio element (safe for callbacks)
    const getActiveAudio = useCallback(() => {
        return activePlayerRef.current === 'A' ? playerRefA.current : playerRefB.current;
    }, []);

    // Legacy support (points to active player)
    const playerRef = activePlayer === 'A' ? playerRefA : playerRefB;
    const youtubeUrl = activePlayer === 'A' ? urlA : urlB;
    const setYoutubeUrl = (url) => {
        if (activePlayer === 'A') setUrlA(url);
        else setUrlB(url);
    };

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

    const blobUrlRef = useRef(null);
    const sleepTimerRef = useRef(null);
    const progressRafRef = useRef(null);
    const pendingProgressRef = useRef(null);
    const isLoadingRef = useRef(false); // Sync guard against race conditions
    const currentTrackIdRef = useRef(null); // Track ID for event guards
    const playbackGenRef = useRef(0); // Generation counter to invalidate stale events

    // Crossfade Hook
    const { startCrossfade, enabled: crossfadeEnabled, duration: crossfadeDuration } = useCrossfade();

    // Stream-stall recovery (YouTube direct URLs can intermittently stall/expire)
    const pendingSeekAfterReloadRef = useRef(null); // seconds
    const stallMonitorRef = useRef({
        lastTime: 0,
        lastCheckEpochMs: 0,
        stallCount: 0,
        lastReloadEpochMs: 0,
        songId: null,
    });

    // Refs for Media Session handlers (to avoid stale closures)
    const playNextRef = useRef(null);
    const playPreviousRef = useRef(null);

    // Add useAuth hook inside component
    const { spotifyToken, isSpotifyAuthenticated } = useAuth();
    const { addToHistory } = useHistory();

    // Preload next songs when current song changes
    useEffect(() => {
        if (currentSong && queue.length > 0) {
            const currentIndex = queue.findIndex(s => s.id === currentSong.id);
            if (currentIndex >= 0) {
                preloadService.preloadQueue(queue, currentIndex, 3);
            }
        }
    }, [currentSong, queue]);

    // Control audio playback - use activePlayer to select correct ref
    useEffect(() => {
        const audio = activePlayer === 'A' ? playerRefA.current : playerRefB.current;
        console.log('[PlayerContext] Play effect triggered, isPlaying:', isPlaying, 'audio:', audio ? 'exists' : 'null', 'src:', audio?.src?.substring(0, 50));
        if (!audio) return;

        if (isPlaying) {
            console.log('[PlayerContext] Calling audio.play()');
            audio.play().catch(e => console.warn('[Player] Play failed:', e));
        } else {
            console.log('[PlayerContext] Calling audio.pause()');
            audio.pause();
        }
    }, [isPlaying, activePlayer]);

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

        // Play handler - use getActiveAudio() for current audio element
        navigator.mediaSession.setActionHandler('play', () => {
            hapticLight();
            const audio = getActiveAudio();
            if (audio?.paused) audio.play();
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
            const audio = getActiveAudio();
            if (details.seekTime !== undefined && audio) {
                audio.currentTime = details.seekTime;
                setProgress(details.seekTime);
                updatePositionState(details.seekTime);
            }
        });

        // Seek backward handler (skip back 10 seconds)
        navigator.mediaSession.setActionHandler('seekbackward', (details) => {
            const audio = getActiveAudio();
            const skipTime = details.seekOffset || 10;
            if (audio) {
                const newTime = Math.max(0, audio.currentTime - skipTime);
                audio.currentTime = newTime;
                setProgress(newTime);
                updatePositionState(newTime);
            }
        });

        // Seek forward handler (skip forward 10 seconds)
        navigator.mediaSession.setActionHandler('seekforward', (details) => {
            const audio = getActiveAudio();
            const skipTime = details.seekOffset || 10;
            if (audio && duration > 0) {
                const newTime = Math.min(duration, audio.currentTime + skipTime);
                audio.currentTime = newTime;
                setProgress(newTime);
                updatePositionState(newTime);
            }
        });

        // Stop handler
        navigator.mediaSession.setActionHandler('stop', () => {
            const audio = getActiveAudio();
            setIsPlaying(false);
            if (audio) {
                audio.currentTime = 0;
                setProgress(0);
            }
        });

    }, [currentSong, duration, updatePositionState, getActiveAudio]);

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
            durationSeconds: duration || 0,
            artworkUrl: currentSong.image || null
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
                durationSeconds: duration || 0,
                artworkUrl: currentSong.image || null
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
            const audio = getActiveAudio();
            switch (action) {
                case 'com.vikify.app.NOW_PLAY':
                    if (audio?.paused) {
                        audio.play().catch(() => { });
                    }
                    setIsPlaying(true);
                    break;
                case 'com.vikify.app.NOW_PAUSE':
                    audio?.pause?.();
                    setIsPlaying(false);
                    break;
                case 'com.vikify.app.NOW_NEXT':
                    playNextRef.current?.();
                    break;
                case 'com.vikify.app.NOW_PREV':
                    playPreviousRef.current?.();
                    break;
                case 'com.vikify.app.NOW_STOP':
                    audio?.pause?.();
                    setIsPlaying(false);
                    clearNowPlaying();
                    break;
                case 'com.vikify.app.NOW_SEEK_TO':
                    if (typeof positionMs === 'number' && audio) {
                        const t = Math.max(0, (positionMs || 0) / 1000);
                        audio.currentTime = t;
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
    }, [getActiveAudio, updatePositionState]);

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

                // OPTIMIZED: Prefetch FULL stream URLs (not just videoIds) for instant transitions
                // This runs searchYouTube with include_stream=true, populating both caches
                shuffled.slice(0, 2).forEach((s) => {
                    // Fire and forget - don't await, let it run in background
                    searchYouTube(`${s.title} ${s.artist}`).catch(() => { });
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

    /**
     * Prefetch a song's stream URL (for hover preloading).
     * Call this on mouseenter to warm the cache before user clicks play.
     */
    const prefetchSong = useCallback((song) => {
        if (!song || !navigator.onLine) return;
        const query = `${song.title} ${song.artist}`;
        console.log('[PlayerContext] Prefetching on hover:', song.title);
        // Fire and forget - populates cache for instant playback
        searchYouTube(query).catch(() => { });
    }, []);

    // Trigger fetch when song changes
    useEffect(() => {
        if (currentSong) {
            fetchUpcomingSongs();
        }
    }, [currentSong, fetchUpcomingSongs]);

    // ----------------------------------------------------------------------------------
    // CROSSFADE & PRELOADING LOGIC
    // ----------------------------------------------------------------------------------

    // 1. Identify next song for preloading
    useEffect(() => {
        if (!currentSong || (!queue.length && !upNextQueue.length)) return;

        let next = null;
        // Priority 1: Next in current queue
        if (queue.length > 0 && queueIndex < queue.length - 1) {
            next = queue[queueIndex + 1];
        }
        // Priority 2: Autoplay/Suggested
        else if (autoplay && upNextQueue.length > 0) {
            next = upNextQueue[0];
        }

        // Only update if changed (prevents loops)
        if (next && next.id !== nextSong?.id) {
            console.log('[Crossfade] Identified next song:', next.title);
            setNextSong(next);
        } else if (!next) {
            setNextSong(null);
        }
    }, [currentSong, queue, queueIndex, upNextQueue, autoplay]);

    // 2. Load URL for next song into INACTIVE player
    useEffect(() => {
        if (!nextSong) return;

        // Determine target (inactive) player
        const targetPlayer = activePlayer === 'A' ? 'B' : 'A';
        const targetSetUrl = targetPlayer === 'A' ? setUrlA : setUrlB;
        const targetUrl = targetPlayer === 'A' ? urlA : urlB;

        // Validations
        if (targetUrl) return; // Already loaded? (simplistic check)

        const loadNextUrl = async () => {
            console.log(`[Crossfade] Preloading ${nextSong.title} into Player ${targetPlayer}`);

            // Check offline cache first
            const cachedBlob = await getAudioBlob(nextSong.id);
            if (cachedBlob) {
                const blobUrl = URL.createObjectURL(cachedBlob);
                targetSetUrl(blobUrl);
                return;
            }

            // Fallback to Online
            if (!navigator.onLine) return;

            try {
                const url = await searchYouTube(`${nextSong.title} ${nextSong.artist}`);
                if (url) targetSetUrl(url);
            } catch (e) {
                console.warn('[Crossfade] Preload failed', e);
            }
        };

        // Delay preloading slightly to prioritize current playback resources?
        // Actually, trigger when we are > 50% through current song?
        // For now, load immediately when identified to ensure availability.
        loadNextUrl();

    }, [nextSong, activePlayer]);

    // 3. Monitor for Crossfade Trigger
    useEffect(() => {
        if (!crossfadeEnabled || !isPlaying || !currentSong || !nextSong) return;
        if (duration < 15) return; // Skip crossfade for short songs

        const timeRemaining = duration - progress;

        // Trigger window: between (duration - fadeTime) and end
        if (timeRemaining <= crossfadeDuration && timeRemaining > 0.5) {

            // Helper to get refs
            const outgoing = activePlayer === 'A' ? playerRefA.current : playerRefB.current;
            const incoming = activePlayer === 'A' ? playerRefB.current : playerRefA.current;
            const incomingUrl = activePlayer === 'A' ? urlB : urlA;

            // Guard: ensure incoming is ready
            // readyState 3 = HAVE_FUTURE_DATA, 4 = HAVE_ENOUGH_DATA
            if (incoming && incomingUrl && incoming.readyState >= 3) {
                triggerCrossfade(outgoing, incoming);
            } else if (incoming && incomingUrl && incoming.readyState < 3) {
                // Force load if not ready?
                incoming.load();
            }
        }
    }, [progress, duration, crossfadeEnabled, isPlaying, nextSong, activePlayer, crossfadeDuration, urlA, urlB]);

    // 4. Execute Crossfade
    const triggerCrossfade = useCallback((outgoing, incoming) => {
        console.log('[Crossfade] Triggering transition...');

        if (startCrossfade(outgoing, incoming)) {
            // Swap Active Player State immediately so UI updates
            const nextP = activePlayer === 'A' ? 'B' : 'A';
            setActivePlayer(nextP);
            setCurrentSong(nextSong);

            // Update Queue Index logic
            if (queue.length > 0 && queueIndex < queue.length - 1) {
                setQueueIndex(prev => prev + 1);
            } else if (autoplay && upNextQueue.length > 0) {
                // Transitioning to autoplay song
                // Note: Ideally we move upNextQueue[0] to Queue, but simple logic:
                setUpNextQueue(prev => prev.slice(1));
            }

            // History
            addToHistory(nextSong);

            // Clear prepared next song to prevent double-trigger
            setNextSong(null);
        }
    }, [activePlayer, nextSong, queue, queueIndex, autoplay, upNextQueue, startCrossfade, addToHistory]);

    /**
     * Play a song
     * @param {Object} song - The song to play
     * @param {Array} playlistQueue - Optional: the full playlist to use for next/previous
     */
    const playSong = async (song, playlistQueue = null) => {
        console.log("[PlayerContext] playSong:", song?.title);

        // Guard: prevent race conditions from rapid clicks
        if (isLoadingRef.current) {
            console.log("[PlayerContext] Already loading, ignoring click");
            return;
        }

        if (currentSong?.id === song.id) {
            togglePlay();
            return;
        }

        isLoadingRef.current = true;
        setIsLoading(true);

        // CRITICAL: Update trackId ref FIRST to invalidate stale events from previous song
        const prevTrackId = currentTrackIdRef.current;
        currentTrackIdRef.current = song.id;
        playbackGenRef.current += 1;
        const thisGen = playbackGenRef.current;
        console.log(`[Autoplay] Track change: ${prevTrackId} → ${song.id} (gen=${thisGen})`);

        setCurrentSong(song);
        setNextSong(null); // Reset pending crossfades
        setIsPlaying(false);
        setProgress(0);
        setDuration(0); // Reset duration immediately
        console.log(`[Autoplay] State reset: progress=0, duration=0 for ${song.title}`);

        // HARD RESET PLAYERS for immediate click
        // Only keep Active Player logic simple: Reset to 'A' or keep current?
        // Let's keep current active player, just change URL.
        const targetSetUrl = activePlayer === 'A' ? setUrlA : setUrlB;
        const otherSetUrl = activePlayer === 'A' ? setUrlB : setUrlA;

        // Stop both
        if (playerRefA.current) { playerRefA.current.pause(); playerRefA.current.currentTime = 0; }
        if (playerRefB.current) { playerRefB.current.pause(); playerRefB.current.currentTime = 0; }

        // Reset URLs
        targetSetUrl(null);
        otherSetUrl(null); // Clear other buffer

        setIsOfflinePlayback(false);

        // Track in listening history
        addToHistory(song);

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
                targetSetUrl(blobUrl);
                setIsOfflinePlayback(true);
                isLoadingRef.current = false;
                setIsLoading(false);
                return;
            }

            // Priority 2: Local file
            if (song.url && !song.url.startsWith('http')) {
                console.log("[PlayerContext] ✅ Playing local file");
                targetSetUrl(song.url);
            }
            // Priority 3: Online - search YouTube
            else {
                if (!navigator.onLine) {
                    console.error("[PlayerContext] ❌ Offline and song not cached");
                    isLoadingRef.current = false;
                    setIsLoading(false);
                    return;
                }

                console.log("[PlayerContext] Searching YouTube...");
                const audioUrl = await searchYouTube(`${song.title} ${song.artist}`);

                if (audioUrl) {
                    console.log("[PlayerContext] ✅ Got audio stream!");
                    targetSetUrl(audioUrl);
                } else if (song.previewUrl) {
                    console.log("[PlayerContext] ⚠️ Using iTunes preview");
                    targetSetUrl(song.previewUrl);
                } else {
                    console.error("[PlayerContext] ❌ No audio source");
                    isLoadingRef.current = false;
                    setIsLoading(false);
                }
            }
        } catch (error) {
            console.error("[PlayerContext] Error:", error);
            if (song.previewUrl) {
                targetSetUrl(song.previewUrl);
            }
        }

        isLoadingRef.current = false;
        setIsLoading(false);
    };

    const onPlayerReady = () => {
        console.log("[PlayerContext] Audio ready, starting playback");
        setIsPlaying(true);

        // If we reloaded an expired/stalled stream, seek back before resuming playback.
        const pending = pendingSeekAfterReloadRef.current;
        const audio = getActiveAudio();
        if (typeof pending === 'number' && audio) {
            try {
                audio.currentTime = Math.max(0, pending);
                setProgress(Math.max(0, pending));
            } catch {
                // ignore
            } finally {
                pendingSeekAfterReloadRef.current = null;
            }
        }
    };

    // Detect buffering/stalls that don't fire "error" (common with expiring stream URLs)
    // and attempt a single safe recovery: refresh stream URL and seek back.
    useEffect(() => {
        const audio = getActiveAudio();
        if (!audio) return;
        if (!currentSong) return;

        // Reset per-song state
        if (stallMonitorRef.current.songId !== currentSong.id) {
            stallMonitorRef.current = {
                lastTime: 0,
                lastCheckEpochMs: 0,
                stallCount: 0,
                lastReloadEpochMs: 0,
                songId: currentSong.id,
            };
            pendingSeekAfterReloadRef.current = null;
        }

        // Only monitor online streaming (offline blobs/local files shouldn't be reloaded)
        if (!youtubeUrl || isOfflinePlayback) return;

        const markProgress = () => {
            stallMonitorRef.current.lastTime = audio.currentTime || 0;
            stallMonitorRef.current.lastCheckEpochMs = Date.now();
            stallMonitorRef.current.stallCount = 0;
        };

        const onPlaying = () => {
            setIsLoading(false);
            markProgress();
        };

        const onWaiting = () => {
            // UI hint; recovery is handled by the interval below.
            setIsLoading(true);
        };

        const onCanPlay = () => {
            setIsLoading(false);
        };

        audio.addEventListener('timeupdate', markProgress);
        audio.addEventListener('playing', onPlaying);
        audio.addEventListener('waiting', onWaiting);
        audio.addEventListener('stalled', onWaiting);
        audio.addEventListener('canplay', onCanPlay);

        const interval = setInterval(async () => {
            const a = getActiveAudio();
            if (!a) return;
            if (!isPlaying) return;
            if (!navigator.onLine) return;

            if (a.seeking) return;

            const now = Date.now();
            const currentTimeSec = a.currentTime || 0;
            const { lastTime, lastCheckEpochMs, stallCount, lastReloadEpochMs } = stallMonitorRef.current;

            // If time is advancing, all good.
            const advancing = Math.abs(currentTimeSec - lastTime) > 0.08;
            if (advancing) {
                stallMonitorRef.current.lastTime = currentTimeSec;
                stallMonitorRef.current.lastCheckEpochMs = now;
                stallMonitorRef.current.stallCount = 0;
                return;
            }

            // Ignore very early startup or cases where we have no baseline yet.
            if (!lastCheckEpochMs) {
                stallMonitorRef.current.lastTime = currentTimeSec;
                stallMonitorRef.current.lastCheckEpochMs = now;
                return;
            }

            // If we're not sufficiently buffered, count as a stall tick.
            // readyState: 0=HAVE_NOTHING, 1=HAVE_METADATA, 2=HAVE_CURRENT_DATA, 3=HAVE_FUTURE_DATA, 4=HAVE_ENOUGH_DATA
            const underBuffered = (a.readyState || 0) < 3;
            if (underBuffered) {
                stallMonitorRef.current.stallCount = stallCount + 1;
                setIsLoading(true);
            } else {
                // If we have buffer but time doesn't advance, don't overreact.
                return;
            }

            // After ~15s of no progress (10 ticks at 1.5s), attempt a controlled reload.
            // This is a last resort for truly stalled streams, not normal buffering.
            const cooldownMs = 60_000; // 60 seconds between reload attempts
            if (stallMonitorRef.current.stallCount >= 10 && now - lastReloadEpochMs > cooldownMs) {
                stallMonitorRef.current.lastReloadEpochMs = now;
                pendingSeekAfterReloadRef.current = currentTimeSec;

                console.warn('[PlayerContext] Detected stalled stream; attempting refresh', {
                    songId: currentSong.id,
                    t: currentTimeSec,
                });

                const ok = await reloadCurrentStream();
                if (!ok) {
                    // If refresh fails, clear pending seek so we don't seek incorrectly later.
                    pendingSeekAfterReloadRef.current = null;
                    setIsLoading(false);
                }
            }
        }, 1500);

        return () => {
            clearInterval(interval);
            audio.removeEventListener('timeupdate', markProgress);
            audio.removeEventListener('playing', onPlaying);
            audio.removeEventListener('waiting', onWaiting);
            audio.removeEventListener('stalled', onWaiting);
            audio.removeEventListener('canplay', onCanPlay);
        };
    }, [currentSong, youtubeUrl, isOfflinePlayback, isPlaying, reloadCurrentStream]);

    const togglePlay = () => {
        console.log('[PlayerContext] togglePlay called, current isPlaying:', isPlaying);
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
        const audio = getActiveAudio();
        if (audio) {
            audio.currentTime = 0;
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
        const audio = getActiveAudio();
        if (audio) {
            audio.currentTime = time;
            setProgress(time);
        }
    };

    // Volume controls
    const changeVolume = useCallback((newVolume) => {
        const vol = Math.max(0, Math.min(1, newVolume));
        setVolume(vol);
        setIsMuted(vol === 0);
        const audio = getActiveAudio();
        if (audio) {
            audio.volume = vol;
        }
    }, [getActiveAudio]);

    const toggleMute = useCallback(() => {
        setIsMuted(prev => {
            const newMuted = !prev;
            const audio = getActiveAudio();
            if (audio) {
                audio.volume = newMuted ? 0 : volume;
            }
            return newMuted;
        });
    }, [volume, getActiveAudio]);

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

            const audio = getActiveAudio();
            switch (e.code) {
                case 'Space':
                    e.preventDefault();
                    setIsPlaying(prev => !prev);
                    break;
                case 'ArrowRight':
                    if (e.shiftKey) {
                        // Skip forward 10 seconds
                        if (audio) {
                            const newTime = Math.min(duration, progress + 10);
                            audio.currentTime = newTime;
                            setProgress(newTime);
                        }
                    } else {
                        playNext();
                    }
                    break;
                case 'ArrowLeft':
                    if (e.shiftKey) {
                        // Skip backward 10 seconds
                        if (audio) {
                            const newTime = Math.max(0, progress - 10);
                            audio.currentTime = newTime;
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

    const handleProgress = useCallback((state, playerId, trackId) => {
        // Guard: Ignore progress from inactive players
        if (playerId && playerId !== activePlayer) return;

        // Guard: Ignore stale progress events from previous tracks
        if (trackId && trackId !== currentTrackIdRef.current) {
            console.log(`[Autoplay] Ignoring stale progress: trackId=${trackId}, current=${currentTrackIdRef.current}`);
            return;
        }

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
    }, [activePlayer]);

    const handleDuration = useCallback((d, playerId, trackId) => {
        // Guard: Ignore duration from inactive players to prevent stale state
        if (playerId && playerId !== activePlayer) {
            console.log(`[Autoplay] Ignoring duration from ${playerId} (Active: ${activePlayer})`);
            return;
        }

        // Guard: Ignore stale duration events from previous tracks (ROOT CAUSE FIX)
        if (trackId && trackId !== currentTrackIdRef.current) {
            console.log(`[Autoplay] ⚠️ BLOCKED stale duration: trackId=${trackId}, current=${currentTrackIdRef.current}, duration=${d}`);
            return;
        }

        console.log(`[Autoplay] ✅ Duration set: ${d}s for track=${trackId || currentTrackIdRef.current}`);
        setDuration(d);
    }, [activePlayer]);

    const handleEnded = useCallback((trackId) => {
        // Guard: Ignore ended events from previous tracks to prevent double-skip
        if (trackId && trackId !== currentTrackIdRef.current) {
            console.log(`[Autoplay] ⚠️ BLOCKED stale ended event: trackId=${trackId}, current=${currentTrackIdRef.current}`);
            return;
        }
        console.log(`[Autoplay] Song ended: ${trackId || currentTrackIdRef.current}, triggering playNext`);
        playNext();
    }, []);

    return (
        <PlayerContext.Provider value={{
            currentSong,
            setCurrentSong,
            nextSong, // Exposed for debug or UI
            isPlaying,
            togglePlay,
            progress,
            duration,
            youtubeUrl, // Legacy (active)
            setYoutubeUrl, // Legacy (active)
            activePlayer,
            playerRefA,
            playerRefB,
            urlA,
            urlB,
            seek,
            playNext,
            playPrevious,
            queue,
            upNextQueue,
            playSong,
            addToQueue,
            playerRef, // Legacy (active)
            handleProgress,
            handleDuration,
            handleEnded,
            isLoading,
            onPlayerReady,
            volume,
            isMuted,
            changeVolume,
            toggleMute,
            sleepTimer,
            startSleepTimer,
            cancelSleepTimer,
            isFullScreen,
            toggleFullScreen,
            reloadCurrentStream,
            prefetchSong,
            shuffle,
            toggleShuffle,
            shufflePlay,
            skipAutoplaySong,
            toggleAutoplay,
            autoplay
        }}>
            {children}
        </PlayerContext.Provider>
    );
};
