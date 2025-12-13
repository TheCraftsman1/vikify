import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import {
    Play, Pause, SkipBack, SkipForward, Heart, ChevronDown,
    Share2, ListMusic, Sparkles, Timer, Download, MoreHorizontal
} from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { hapticLight, hapticMedium, hapticSelection } from '../utils/haptics';
import { downloadSong } from '../utils/download';
import { useOffline } from '../context/OfflineContext';

const MobileFullScreenPlayer = ({ isOpen, onClose }) => {
    const {
        currentSong, isPlaying, togglePlay, progress, duration,
        seek, playNext, playPrevious, playerRef, isLoading,
        upNextQueue, queue, playSong
    } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();
    const { isSongOffline } = useOffline();

    const [showLyrics, setShowLyrics] = useState(false);
    const [showQueue, setShowQueue] = useState(false);
    const [showMoreMenu, setShowMoreMenu] = useState(false);
    const [menuBusy, setMenuBusy] = useState(false);
    const [dominantColor, setDominantColor] = useState('#1a1a2e');
    const [isScrubbing, setIsScrubbing] = useState(false);
    const [scrubTime, setScrubTime] = useState(0);
    const scrollRef = useRef(null);
    const progressTrackRef = useRef(null);
    const scrubbingRef = useRef(false);
    const rafRef = useRef(null);
    const lastSelectionSecondRef = useRef(-1);

    // Extract dominant color from album art
    useEffect(() => {
        if (currentSong?.image) {
            const hash = currentSong.image.split('').reduce((a, b) => {
                a = ((a << 5) - a) + b.charCodeAt(0);
                return a & a;
            }, 0);
            const hue = Math.abs(hash) % 360;
            setDominantColor(`hsl(${hue}, 45%, 20%)`);
        }
    }, [currentSong?.image]);

    const currentSongLiked = currentSong ? isLiked(currentSong.id) : false;
    const currentSongOffline = currentSong ? isSongOffline(currentSong.id) : false;

    const formatTime = (time) => {
        if (!time || isNaN(time)) return "0:00";
        const minutes = Math.floor(time / 60);
        const seconds = Math.floor(time % 60);
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    };

    const handlePlayPause = () => {
        hapticMedium();
        togglePlay(); // PlayerContext's useEffect handles actual audio.play()/pause()
    };

    const handleDownloadCurrent = async () => {
        if (!currentSong || menuBusy) return;
        if (currentSongOffline) {
            setShowMoreMenu(false);
            return;
        }
        setMenuBusy(true);
        try {
            hapticLight();
            await downloadSong(currentSong, true);
        } finally {
            setMenuBusy(false);
            setShowMoreMenu(false);
        }
    };

    const handleSeek = (e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        const time = percent * (duration || 0);
        seek(time);
        if (playerRef.current) {
            playerRef.current.currentTime = time;
        }
    };

    const clampScrubTimeFromClientX = (clientX, element) => {
        const target = element || progressTrackRef.current;
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
        if (playerRef.current) {
            playerRef.current.currentTime = finalTime;
        }
    };

    const onScrubPointerUp = (e) => { e.preventDefault(); endScrub(e.clientX, e.currentTarget); };
    const onScrubPointerCancel = (e) => { e.preventDefault(); endScrub(e.clientX, e.currentTarget); };

    useEffect(() => {
        return () => { if (rafRef.current) { cancelAnimationFrame(rafRef.current); rafRef.current = null; } };
    }, []);

    // Sample lyrics
    const sampleLyrics = currentSong ? [
        { time: 0, text: "(Ooh) na-na, yeah" },
        { time: 5, text: "I saw you dancing in a crowded room, uh" },
        { time: 10, text: "You look so happy when I'm not with you" },
        { time: 15, text: "But then you saw me, caught you by surprise" },
    ] : [];

    if (!isOpen || !currentSong) return null;

    const displayedProgress = isScrubbing ? scrubTime : progress;
    const progressPercent = (displayedProgress / (duration || 1)) * 100;

    return ReactDOM.createPortal(
        <div
            className="mobile-fullscreen-player"
            style={{ background: `linear-gradient(180deg, ${dominantColor} 0%, #0a0a0a 60%)` }}
        >
            {/* Header */}
            <div className="mfp-header">
                <button onClick={onClose} className="mfp-close-btn">
                    <ChevronDown size={28} />
                </button>
                <div className="mfp-header-info">
                    <div className="mfp-header-subtitle">Playing from playlist</div>
                    <div className="mfp-header-title">{currentSong.album || 'Your Music'}</div>
                </div>
                <button
                    onClick={() => { hapticSelection(); setShowMoreMenu(v => !v); }}
                    className="mfp-more-btn"
                >
                    <MoreHorizontal size={24} />
                </button>
            </div>

            {/* More Menu */}
            {showMoreMenu && (
                <div className="mfp-menu-overlay" onClick={() => setShowMoreMenu(false)}>
                    <div className="mfp-menu" onClick={e => e.stopPropagation()}>
                        <button
                            onClick={handleDownloadCurrent}
                            disabled={menuBusy || currentSongOffline}
                            className="mfp-menu-item"
                        >
                            <Download size={18} />
                            {currentSongOffline ? 'Downloaded' : (menuBusy ? 'Downloading…' : 'Download')}
                        </button>
                    </div>
                </div>
            )}

            {/* Scrollable Content */}
            <div ref={scrollRef} className="mfp-scroll-content">
                {/* Main Player Section */}
                <div className="mfp-main">
                    {/* Album Art */}
                    <div className="mfp-album-art">
                        <img
                            src={currentSong.image}
                            alt={currentSong.title}
                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                        />
                    </div>

                    {/* Song Info & Like Button */}
                    <div className="mfp-song-row">
                        <div className="mfp-song-info">
                            <h1 className="mfp-song-title">{currentSong.title}</h1>
                            <p className="mfp-song-artist">{currentSong.artist}</p>
                        </div>
                        <button
                            onClick={() => toggleLike(currentSong)}
                            className={`mfp-like-btn ${currentSongLiked ? 'liked' : ''}`}
                        >
                            <Heart
                                size={28}
                                fill={currentSongLiked ? '#1db954' : 'none'}
                                strokeWidth={currentSongLiked ? 0 : 2}
                            />
                        </button>
                    </div>

                    {/* Progress Bar */}
                    <div className="mfp-progress">
                        <div
                            className="mfp-progress-scrubber"
                            onPointerDown={onScrubPointerDown}
                            onPointerMove={onScrubPointerMove}
                            onPointerUp={onScrubPointerUp}
                            onPointerCancel={onScrubPointerCancel}
                            onClick={handleSeek}
                        >
                            <div ref={progressTrackRef} className="mfp-progress-track">
                                <div className="mfp-progress-fill" style={{ width: `${progressPercent}%` }}>
                                    <div className="mfp-progress-thumb" />
                                </div>
                            </div>
                        </div>
                        <div className="mfp-progress-times">
                            <span>{formatTime(displayedProgress)}</span>
                            <span>{formatTime(duration)}</span>
                        </div>
                    </div>

                    {/* Main Controls */}
                    <div className="mfp-controls">
                        <button className="mfp-control-btn accent">
                            <Sparkles size={24} />
                        </button>
                        <button onClick={playPrevious} className="mfp-control-btn">
                            <SkipBack size={32} fill="currentColor" />
                        </button>
                        <button onClick={handlePlayPause} className="mfp-play-btn">
                            {isLoading ? (
                                <div className="mfp-spinner" />
                            ) : isPlaying ? (
                                <Pause size={28} fill="#000" color="#000" />
                            ) : (
                                <Play size={28} fill="#000" color="#000" style={{ marginLeft: '3px' }} />
                            )}
                        </button>
                        <button onClick={playNext} className="mfp-control-btn">
                            <SkipForward size={32} fill="currentColor" />
                        </button>
                        <button className="mfp-control-btn muted">
                            <Timer size={24} />
                        </button>
                    </div>

                    {/* Bottom Actions */}
                    <div className="mfp-actions">
                        <div className="mfp-device-indicator">
                            <div className="mfp-device-icon">🎧</div>
                            <span>Connected Device</span>
                        </div>
                        <div className="mfp-action-btns">
                            <button className="mfp-action-btn">
                                <Share2 size={22} />
                            </button>
                            <button
                                onClick={() => setShowQueue(!showQueue)}
                                className={`mfp-action-btn ${showQueue ? 'active' : ''}`}
                            >
                                <ListMusic size={22} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Lyrics Section */}
                <div className="mfp-lyrics-section">
                    <div className="mfp-lyrics-card">
                        <div className="mfp-lyrics-title">Lyrics preview</div>
                        <div className="mfp-lyrics-content">
                            {sampleLyrics.map((line, i) => (
                                <p key={i} className="mfp-lyrics-line">{line.text}</p>
                            ))}
                        </div>
                        <button onClick={() => setShowLyrics(true)} className="mfp-lyrics-btn">
                            Show lyrics
                        </button>
                    </div>

                    {/* About the Artist */}
                    <div className="mfp-artist-section">
                        <h3 className="mfp-section-title">About the artist</h3>
                        <div className="mfp-artist-card">
                            <div
                                className="mfp-artist-image"
                                style={{ backgroundImage: `url(${currentSong.image})` }}
                            />
                            <div className="mfp-artist-content">
                                <div className="mfp-artist-header">
                                    <h4 className="mfp-artist-name">{currentSong.artist}</h4>
                                    <button className="mfp-follow-btn">Follow</button>
                                </div>
                                <p className="mfp-artist-bio">
                                    One of the most acclaimed artists of our time, known for
                                    chart-topping hits and incredible live performances.
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Up Next Queue */}
                    {(queue.length > 0 || upNextQueue.length > 0) && (
                        <div className="mfp-queue-section">
                            <h3 className="mfp-section-title">Up next</h3>
                            <div className="mfp-queue-list">
                                {[...queue, ...upNextQueue].slice(0, 5).map((song, i) => (
                                    <div key={i} onClick={() => playSong(song)} className="mfp-queue-item">
                                        <img
                                            src={song.image}
                                            alt=""
                                            className="mfp-queue-item-image"
                                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                        />
                                        <div className="mfp-queue-item-info">
                                            <div className="mfp-queue-item-title">{song.title}</div>
                                            <div className="mfp-queue-item-artist">{song.artist}</div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>,
        document.body
    );
};

export default MobileFullScreenPlayer;
