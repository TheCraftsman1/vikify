import React, { useState, useEffect, useRef } from 'react';
import {
    Play, Pause, SkipBack, SkipForward, Volume2, Volume1, VolumeX,
    Repeat, Shuffle, Heart, Loader, Maximize2, Mic2, ListMusic, Laptop2,
    Timer, X, ChevronDown
} from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import MobileFullScreenPlayer from './MobileFullScreenPlayer';
import LyricsOverlay from './LyricsOverlay';
import { hapticLight, hapticSelection } from '../utils/haptics';

const Player = () => {
    const {
        currentSong, isPlaying, togglePlay, progress, duration,
        seek, playNext, playPrevious, youtubeUrl, playerRef,
        activePlayer, playerRefA, playerRefB, urlA, urlB,
        handleProgress, handleDuration, handleEnded, isLoading, onPlayerReady,
        upNextQueue, queue, playSong,
        volume, isMuted, changeVolume, toggleMute,
        sleepTimer, startSleepTimer, cancelSleepTimer,
        isFullScreen, toggleFullScreen,
        reloadCurrentStream
    } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();

    const [isShuffle, setIsShuffle] = useState(false);
    const [repeatMode, setRepeatMode] = useState(0);
    const [showQueue, setShowQueue] = useState(false);
    const [showLyrics, setShowLyrics] = useState(false);
    const [showSleepMenu, setShowSleepMenu] = useState(false);
    const [showMobileFullScreen, setShowMobileFullScreen] = useState(false);
    const [isMobile, setIsMobile] = useState(window.innerWidth <= 768);
    const [dominantColor, setDominantColor] = useState('#121212');

    // Scrubbing state
    const [isScrubbing, setIsScrubbing] = useState(false);
    const [scrubTime, setScrubTime] = useState(0);
    const activeScrubElRef = useRef(null);
    const scrubbingRef = useRef(false);
    const rafRef = useRef(null);
    const lastSelectionSecondRef = useRef(-1);
    const streamReloadGuardRef = useRef({ songId: null, attempts: 0 });

    // Detect mobile screen
    useEffect(() => {
        const handleResize = () => setIsMobile(window.innerWidth <= 768);
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    // Generate color from current song
    useEffect(() => {
        if (currentSong?.image) {
            const hash = currentSong.image.split('').reduce((a, b) => {
                a = ((a << 5) - a) + b.charCodeAt(0);
                return a & a;
            }, 0);
            const hue = Math.abs(hash) % 360;
            setDominantColor(`hsl(${hue}, 35%, 25%)`);
        }
    }, [currentSong?.image]);

    const currentSongLiked = currentSong ? isLiked(currentSong.id) : false;

    // Just toggle state - PlayerContext's useEffect handles actual audio.play()/pause()
    const handlePlayPause = () => {
        togglePlay();
    };

    const isYoutube = youtubeUrl && (youtubeUrl.includes('youtube.com') || youtubeUrl.includes('youtu.be'));
    const displayedProgress = isScrubbing ? scrubTime : progress;

    const formatTime = (time) => {
        if (!time || isNaN(time)) return "0:00";
        const minutes = Math.floor(time / 60);
        const seconds = Math.floor(time % 60);
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    };

    const handleSeek = (e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        const time = percent * (duration || 0);
        seek(time);
        if (playerRef.current && !isYoutube) {
            playerRef.current.currentTime = time;
        }
    };

    const clampScrubTimeFromClientX = (clientX, element) => {
        const target = element || activeScrubElRef.current;
        if (!target) return 0;
        const rect = target.getBoundingClientRect();
        const percent = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
        return percent * (duration || 0);
    };

    const scheduleScrubUpdate = (nextTime) => {
        if (rafRef.current) return;
        rafRef.current = requestAnimationFrame(() => {
            rafRef.current = null;
            setScrubTime(nextTime);
            const nextSecond = Math.floor(nextTime);
            if (nextSecond !== lastSelectionSecondRef.current) {
                lastSelectionSecondRef.current = nextSecond;
                hapticSelection();
            }
        });
    };

    const onScrubPointerDown = (e) => {
        if (!duration) return;
        e.preventDefault();
        activeScrubElRef.current = e.currentTarget;
        scrubbingRef.current = true;
        setIsScrubbing(true);
        const nextTime = clampScrubTimeFromClientX(e.clientX, e.currentTarget);
        lastSelectionSecondRef.current = Math.floor(nextTime);
        setScrubTime(nextTime);
        hapticLight();
        try { e.currentTarget.setPointerCapture?.(e.pointerId); } catch { }
    };

    const onScrubPointerMove = (e) => {
        if (!scrubbingRef.current || !duration) return;
        e.preventDefault();
        scheduleScrubUpdate(clampScrubTimeFromClientX(e.clientX, e.currentTarget));
    };

    const endScrub = (finalClientX, element) => {
        if (!scrubbingRef.current) return;
        scrubbingRef.current = false;
        const finalTime = clampScrubTimeFromClientX(finalClientX, element);
        setIsScrubbing(false);
        setScrubTime(finalTime);
        hapticLight();
        seek(finalTime);
        if (playerRef.current && !isYoutube) {
            playerRef.current.currentTime = finalTime;
        }
    };

    const onScrubPointerUp = (e) => { e.preventDefault(); endScrub(e.clientX, e.currentTarget); };
    const onScrubPointerCancel = (e) => { e.preventDefault(); endScrub(e.clientX, e.currentTarget); };

    useEffect(() => {
        return () => { if (rafRef.current) { cancelAnimationFrame(rafRef.current); rafRef.current = null; } };
    }, []);

    const handleVolumeChange = (e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const newVolume = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        changeVolume(newVolume);
    };

    const getVolumeIcon = () => {
        if (isMuted || volume === 0) return <VolumeX size={16} />;
        if (volume < 0.5) return <Volume1 size={16} />;
        return <Volume2 size={16} />;
    };

    // Empty state
    if (!currentSong) {
        return (
            <div className="player-bar">
                <div style={{ width: '30%', minWidth: '180px' }} />
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <button disabled className="player-icon-btn"><Shuffle size={16} /></button>
                        <button disabled className="player-icon-btn"><SkipBack size={20} /></button>
                        <div className="player-main-play-btn" style={{ opacity: 0.6 }}>
                            <Play size={16} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                        </div>
                        <button disabled className="player-icon-btn"><SkipForward size={20} /></button>
                        <button disabled className="player-icon-btn"><Repeat size={16} /></button>
                    </div>
                </div>
                <div style={{ width: '30%', minWidth: '180px' }} />
            </div>
        );
    }

    const progressPercent = (displayedProgress / (duration || 1)) * 100;

    return (
        <div
            className="player-bar"
            style={{
                position: 'relative',
                '--dominant-color': dominantColor,
                background: isMobile ? `linear-gradient(90deg, ${dominantColor} 0%, rgba(18,18,18,0.98) 100%)` : '#000'
            }}
        >
            {/* Mobile Progress Indicator */}
            <div className="mobile-progress-indicator">
                <div className="mobile-progress-indicator-fill" style={{ width: `${progressPercent}%` }} />
            </div>

            {/* Dual Audio Players for Crossfade */}
            <audio
                ref={playerRefA}
                src={urlA}
                preload="auto"
                playsInline
                onTimeUpdate={(e) => activePlayer === 'A' && handleProgress({ playedSeconds: e.currentTarget.currentTime }, 'A', currentSong?.id)}
                onLoadedMetadata={(e) => {
                    if (activePlayer === 'A') {
                        console.log('[Player] Audio A loaded, duration:', e.currentTarget.duration, 'track:', currentSong?.id);
                        handleDuration(e.currentTarget.duration, 'A', currentSong?.id);
                        onPlayerReady();
                    }
                }}
                onError={async (e) => {
                    if (activePlayer === 'A') {
                        console.error('[Player] Audio A error:', e);
                        // Retry logic... (simplified for brevity, main logic in Context)
                    }
                }}
                onEnded={() => activePlayer === 'A' && handleEnded(currentSong?.id)}
                style={{ display: 'none' }}
            />
            <audio
                ref={playerRefB}
                src={urlB}
                preload="auto"
                playsInline
                onTimeUpdate={(e) => activePlayer === 'B' && handleProgress({ playedSeconds: e.currentTarget.currentTime }, 'B', currentSong?.id)}
                onLoadedMetadata={(e) => {
                    if (activePlayer === 'B') {
                        console.log('[Player] Audio B loaded, duration:', e.currentTarget.duration, 'track:', currentSong?.id);
                        handleDuration(e.currentTarget.duration, 'B', currentSong?.id);
                        onPlayerReady();
                    }
                }}
                onError={async (e) => {
                    if (activePlayer === 'B') {
                        console.error('[Player] Audio B error:', e);
                    }
                }}
                onEnded={() => activePlayer === 'B' && handleEnded(currentSong?.id)}
                style={{ display: 'none' }}
            />

            {/* Left: Now Playing - Spotify style */}
            <div
                className="player-now-playing"
                onClick={() => isMobile && setShowMobileFullScreen(true)}
                style={{ cursor: isMobile ? 'pointer' : 'default' }}
            >
                <div className="player-album-art">
                    <img
                        src={currentSong.image}
                        alt={currentSong.title}
                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                    />
                </div>
                <div className="player-song-info">
                    <div className="player-song-title">{currentSong.title}</div>
                    <div className="player-song-artist">{currentSong.artist}</div>
                </div>
            </div>

            {/* Mobile Player Controls - Spotify style */}
            <div className="mobile-player-controls">
                {/* Device icon - green headphones style */}
                <button className="mobile-device-btn" title="Connected Device">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="#1db954">
                        <path d="M12 1a9 9 0 0 0-9 9v7c0 1.66 1.34 3 3 3h3v-8H5v-2c0-3.87 3.13-7 7-7s7 3.13 7 7v2h-4v8h3c1.66 0 3-1.34 3-3v-7a9 9 0 0 0-9-9z"/>
                    </svg>
                </button>

                {/* Like button - Spotify checkmark style */}
                <button
                    onClick={(e) => { e.stopPropagation(); if (currentSong) toggleLike(currentSong); }}
                    className={`mobile-like-btn ${currentSongLiked ? 'liked' : ''}`}
                    title={currentSongLiked ? 'Remove from Liked Songs' : 'Add to Liked Songs'}
                >
                    {currentSongLiked ? (
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                            <circle cx="12" cy="12" r="12" fill="#1db954"/>
                            <path d="M17.5 8.5L10 16L6.5 12.5" stroke="#000" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
                        </svg>
                    ) : (
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
                            <circle cx="12" cy="12" r="11" stroke="rgba(255,255,255,0.7)" strokeWidth="2"/>
                            <path d="M12 7V17M7 12H17" stroke="rgba(255,255,255,0.7)" strokeWidth="2" strokeLinecap="round" />
                        </svg>
                    )}
                </button>

                {/* Play button - Spotify white triangle style */}
                <button onClick={(e) => { e.stopPropagation(); handlePlayPause(); }} className="mobile-play-btn">
                    {isLoading ? (
                        <Loader size={22} color="#fff" className="animate-spin" />
                    ) : isPlaying ? (
                        <Pause size={26} fill="#fff" color="#fff" />
                    ) : (
                        <Play size={26} fill="#fff" color="#fff" style={{ marginLeft: '3px' }} />
                    )}
                </button>
            </div>

            {/* Desktop Controls */}
            <div className="desktop-player-controls hide-mobile">
                <button onClick={playPrevious} className="player-icon-btn">
                    <SkipBack size={22} fill="currentColor" />
                </button>
                <button onClick={handlePlayPause} className="player-main-play-btn medium">
                    {isLoading ? (
                        <Loader size={20} color="#000" className="animate-spin" />
                    ) : isPlaying ? (
                        <Pause size={22} fill="#000" color="#000" />
                    ) : (
                        <Play size={22} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                    )}
                </button>
                <button onClick={playNext} className="player-icon-btn">
                    <SkipForward size={22} fill="currentColor" />
                </button>
            </div>

            {/* Center: Controls + Progress */}
            <div className="player-controls">
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <button onClick={() => setIsShuffle(!isShuffle)} className={`player-icon-btn hide-mobile ${isShuffle ? 'active' : ''}`}>
                        <Shuffle size={16} />
                    </button>
                    <button onClick={playPrevious} className="player-icon-btn">
                        <SkipBack size={20} fill="currentColor" />
                    </button>
                    <button onClick={handlePlayPause} disabled={isLoading} className="player-main-play-btn">
                        {isLoading ? (
                            <Loader size={16} color="#000" className="animate-spin" />
                        ) : isPlaying ? (
                            <Pause size={16} fill="#000" color="#000" />
                        ) : (
                            <Play size={16} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                        )}
                    </button>
                    <button onClick={playNext} className="player-icon-btn">
                        <SkipForward size={20} fill="currentColor" />
                    </button>
                    <button onClick={() => setRepeatMode((repeatMode + 1) % 3)} className={`player-icon-btn hide-mobile ${repeatMode > 0 ? 'active' : ''}`} style={{ position: 'relative' }}>
                        <Repeat size={16} />
                        {repeatMode === 2 && <span className="player-sleep-badge">1</span>}
                    </button>
                </div>

                {/* Progress Bar */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', maxWidth: '600px' }}>
                    <span className="player-time player-time-left">{formatTime(displayedProgress)}</span>
                    <div
                        className="player-progress-wrapper"
                        onPointerDown={onScrubPointerDown}
                        onPointerMove={onScrubPointerMove}
                        onPointerUp={onScrubPointerUp}
                        onPointerCancel={onScrubPointerCancel}
                        onClick={handleSeek}
                    >
                        <div className="player-progress-track">
                            <div className="player-progress-fill" style={{ width: `${progressPercent}%` }}>
                                <div className="player-progress-thumb" />
                            </div>
                        </div>
                    </div>
                    <span className="player-time player-time-right">{formatTime(duration)}</span>
                </div>
            </div>

            {/* Right: Extra Controls */}
            <div className="player-extra-controls hide-mobile">
                <button
                    className={`player-icon-btn ${showLyrics ? 'active' : ''}`}
                    onClick={() => setShowLyrics(!showLyrics)}
                    title="Lyrics"
                >
                    <Mic2 size={16} />
                </button>

                <button onClick={() => setShowQueue(!showQueue)} className={`player-icon-btn ${showQueue ? 'active' : ''}`}>
                    <ListMusic size={16} />
                </button>

                {/* Queue Panel */}
                {showQueue && (
                    <div className="player-queue-panel">
                        <h3 className="player-queue-title">Queue</h3>

                        <div className="player-queue-section">
                            <h4 className="player-queue-section-title">Now Playing</h4>
                            {currentSong && (
                                <div className="player-queue-item">
                                    <img src={currentSong.image} alt="" className="player-queue-item-image" onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                                    <div className="player-queue-item-info">
                                        <div className="player-queue-item-title playing">{currentSong.title}</div>
                                        <div className="player-queue-item-artist">{currentSong.artist}</div>
                                    </div>
                                    <div className="player-eq-bars">
                                        <div className="player-eq-bar"></div>
                                        <div className="player-eq-bar"></div>
                                        <div className="player-eq-bar"></div>
                                    </div>
                                </div>
                            )}
                        </div>

                        {queue.length > 0 && (
                            <div className="player-queue-section">
                                <h4 className="player-queue-section-title">Next in Queue</h4>
                                {queue.map((song, i) => (
                                    <div key={i} className="player-queue-item" onClick={() => playSong(song)}>
                                        <div style={{ color: '#b3b3b3', fontSize: '14px', width: '20px' }}>{i + 1}</div>
                                        <img src={song.image} alt="" className="player-queue-item-image" onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                                        <div className="player-queue-item-info">
                                            <div className="player-queue-item-title">{song.title}</div>
                                            <div className="player-queue-item-artist">{song.artist}</div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {upNextQueue.length > 0 && (
                            <div className="player-queue-section">
                                <h4 className="player-queue-section-title">Next From: Autoplay</h4>
                                {upNextQueue.map((song, i) => (
                                    <div key={i} className="player-queue-item" onClick={() => playSong(song)}>
                                        <img src={song.image} alt="" className="player-queue-item-image" onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                                        <div className="player-queue-item-info">
                                            <div className="player-queue-item-title">{song.title}</div>
                                            <div className="player-queue-item-artist">{song.artist}</div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                )}

                {/* Sleep Timer */}
                <div style={{ position: 'relative' }}>
                    <button onClick={() => setShowSleepMenu(!showSleepMenu)} className={`player-icon-btn ${sleepTimer ? 'active' : ''}`} title={sleepTimer ? `Sleep in ${sleepTimer} min` : 'Sleep Timer'}>
                        <Timer size={16} />
                        {sleepTimer && <span className="player-sleep-badge">{sleepTimer}</span>}
                    </button>

                    {showSleepMenu && (
                        <div className="player-sleep-menu">
                            <div className="player-sleep-menu-title">SLEEP TIMER</div>
                            {[5, 15, 30, 45, 60, 90].map(mins => (
                                <button
                                    key={mins}
                                    onClick={() => { startSleepTimer(mins); setShowSleepMenu(false); }}
                                    className={`player-sleep-menu-item ${sleepTimer === mins ? 'active' : ''}`}
                                >
                                    {mins} minutes
                                </button>
                            ))}
                            {sleepTimer && (
                                <button onClick={() => { cancelSleepTimer(); setShowSleepMenu(false); }} className="player-sleep-menu-item cancel">
                                    Turn off timer
                                </button>
                            )}
                        </div>
                    )}
                </div>

                <button className="player-icon-btn"><Laptop2 size={16} /></button>

                {/* Volume */}
                <div className="player-volume-wrapper">
                    <button onClick={toggleMute} className="player-icon-btn">{getVolumeIcon()}</button>
                    <div onClick={handleVolumeChange} className="player-volume-bar">
                        <div className="player-volume-fill" style={{ width: `${(isMuted ? 0 : volume) * 100}%` }} />
                    </div>
                </div>

                <button onClick={toggleFullScreen} className="player-icon-btn" title="Full screen (F)">
                    <Maximize2 size={16} />
                </button>
            </div>

            {/* Lyrics Overlay */}
            {showLyrics && (
                <LyricsOverlay onClose={() => setShowLyrics(false)} />
            )}

            {/* Full-Screen Overlay */}
            {isFullScreen && currentSong && (
                <div
                    className="player-fullscreen-overlay"
                    style={{ background: `linear-gradient(180deg, rgba(0,0,0,0.3) 0%, rgba(0,0,0,0.95) 100%), url(${currentSong.image}) center/cover no-repeat` }}
                >
                    <button onClick={toggleFullScreen} className="player-fullscreen-close">
                        <ChevronDown size={24} />
                    </button>

                    {sleepTimer && (
                        <div className="player-fullscreen-sleep-badge">
                            <Timer size={16} />
                            <span style={{ fontSize: '14px', fontWeight: '500' }}>Sleep in {sleepTimer} min</span>
                        </div>
                    )}

                    <div className="player-fullscreen-album-art">
                        <img src={currentSong.image} alt={currentSong.title} onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                    </div>

                    <div className="player-fullscreen-info">
                        <h1 className="player-fullscreen-title">{currentSong.title}</h1>
                        <p className="player-fullscreen-artist">{currentSong.artist}</p>
                    </div>

                    <div className="player-fullscreen-progress">
                        <div
                            className="player-fullscreen-progress-bar"
                            onPointerDown={onScrubPointerDown}
                            onPointerMove={onScrubPointerMove}
                            onPointerUp={onScrubPointerUp}
                            onPointerCancel={onScrubPointerCancel}
                            onClick={handleSeek}
                        >
                            <div className="player-fullscreen-progress-track">
                                <div className="player-fullscreen-progress-fill" style={{ width: `${progressPercent}%` }} />
                            </div>
                        </div>
                        <div className="player-fullscreen-times">
                            <span>{formatTime(displayedProgress)}</span>
                            <span>{formatTime(duration)}</span>
                        </div>
                    </div>

                    <div className="player-fullscreen-controls">
                        <button onClick={playPrevious} className="player-fullscreen-skip-btn">
                            <SkipBack size={32} fill="currentColor" />
                        </button>
                        <button onClick={handlePlayPause} className="player-main-play-btn large">
                            {isLoading ? (
                                <Loader size={32} color="#000" className="animate-spin" />
                            ) : isPlaying ? (
                                <Pause size={32} fill="#000" color="#000" />
                            ) : (
                                <Play size={32} fill="#000" color="#000" style={{ marginLeft: '4px' }} />
                            )}
                        </button>
                        <button onClick={playNext} className="player-fullscreen-skip-btn">
                            <SkipForward size={32} fill="currentColor" />
                        </button>
                    </div>

                    <div className="player-fullscreen-hint">
                        Press <kbd>F</kbd> or <kbd>Esc</kbd> to exit
                    </div>
                </div>
            )}

            {/* Mobile Full Screen Player */}
            <MobileFullScreenPlayer
                isOpen={showMobileFullScreen}
                onClose={() => setShowMobileFullScreen(false)}
            />
        </div>
    );
};

export default Player;
