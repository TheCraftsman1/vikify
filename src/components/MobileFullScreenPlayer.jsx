import React, { useState, useEffect, useRef } from 'react';
import ReactDOM from 'react-dom';
import {
    Play, Pause, SkipBack, SkipForward, Heart, ChevronDown,
    Share2, ListMusic, Sparkles, Timer, Plus, MoreHorizontal
} from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';

const MobileFullScreenPlayer = ({ isOpen, onClose }) => {
    const {
        currentSong, isPlaying, togglePlay, progress, duration,
        seek, playNext, playPrevious, playerRef, isLoading,
        upNextQueue, queue, playSong
    } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();

    const [showLyrics, setShowLyrics] = useState(false);
    const [showQueue, setShowQueue] = useState(false);
    const [dominantColor, setDominantColor] = useState('#1a1a2e');
    const scrollRef = useRef(null);

    // Extract dominant color from album art (simplified version)
    useEffect(() => {
        if (currentSong?.image) {
            // Generate color from image URL hash for consistency
            const hash = currentSong.image.split('').reduce((a, b) => {
                a = ((a << 5) - a) + b.charCodeAt(0);
                return a & a;
            }, 0);
            const hue = Math.abs(hash) % 360;
            setDominantColor(`hsl(${hue}, 45%, 20%)`);
        }
    }, [currentSong?.image]);

    const currentSongLiked = currentSong ? isLiked(currentSong.id) : false;

    const formatTime = (time) => {
        if (!time || isNaN(time)) return "0:00";
        const minutes = Math.floor(time / 60);
        const seconds = Math.floor(time % 60);
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    };

    const handlePlayPause = () => {
        if (!playerRef.current) {
            togglePlay();
            return;
        }
        const audio = playerRef.current;
        if (audio.paused) {
            audio.play().then(() => {
                if (!isPlaying) togglePlay();
            }).catch(console.error);
        } else {
            audio.pause();
            if (isPlaying) togglePlay();
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

    // Sample lyrics - in production, fetch from lyrics API
    const sampleLyrics = currentSong ? [
        { time: 0, text: "(Ooh) na-na, yeah" },
        { time: 5, text: "I saw you dancing in a crowded room, uh" },
        { time: 10, text: "You look so happy when I'm not with you" },
        { time: 15, text: "But then you saw me, caught you by surprise" },
        { time: 20, text: "A single teardrop falling from your eye" },
        { time: 25, text: "I don't know why I run away..." },
    ] : [];

    if (!isOpen || !currentSong) return null;

    // Use Portal to render at document.body level - bypasses all parent CSS constraints
    return ReactDOM.createPortal(
        <div
            className="mobile-fullscreen-player"
            style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                width: '100vw',
                height: '100vh',
                zIndex: 99999,
                background: `linear-gradient(180deg, ${dominantColor} 0%, #0a0a0a 60%)`,
                display: 'flex',
                flexDirection: 'column',
                animation: 'slideUp 0.3s ease-out',
                overflow: 'hidden'
            }}
        >
            {/* Header */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '16px 20px',
                paddingTop: 'max(16px, env(safe-area-inset-top))'
            }}>
                <button
                    onClick={onClose}
                    style={{
                        color: '#fff',
                        padding: '8px',
                        background: 'transparent',
                        border: 'none',
                        cursor: 'pointer'
                    }}
                >
                    <ChevronDown size={28} />
                </button>
                <div style={{
                    textAlign: 'center',
                    flex: 1
                }}>
                    <div style={{
                        fontSize: '11px',
                        color: 'rgba(255,255,255,0.7)',
                        textTransform: 'uppercase',
                        letterSpacing: '1px',
                        marginBottom: '2px'
                    }}>
                        Playing from playlist
                    </div>
                    <div style={{
                        fontSize: '13px',
                        color: '#fff',
                        fontWeight: '600'
                    }}>
                        {currentSong.album || 'Your Music'}
                    </div>
                </div>
                <button
                    style={{
                        color: '#fff',
                        padding: '8px',
                        background: 'transparent',
                        border: 'none',
                        cursor: 'pointer'
                    }}
                >
                    <MoreHorizontal size={24} />
                </button>
            </div>

            {/* Scrollable Content */}
            <div
                ref={scrollRef}
                style={{
                    flex: '1 1 auto',
                    height: 0, /* Force flex child to scroll */
                    overflowY: 'auto',
                    overflowX: 'hidden',
                    WebkitOverflowScrolling: 'touch'
                }}
            >
                {/* Main Player Section */}
                <div style={{
                    minHeight: '100%',
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    padding: '20px',
                    paddingTop: '10px',
                    boxSizing: 'border-box'
                }}>
                    {/* Album Art */}
                    <div style={{
                        width: 'min(320px, 80vw)',
                        aspectRatio: '1',
                        borderRadius: '8px',
                        overflow: 'hidden',
                        boxShadow: '0 24px 80px rgba(0,0,0,0.5)',
                        marginBottom: '32px',
                        marginTop: '10px'
                    }}>
                        <img
                            src={currentSong.image}
                            alt={currentSong.title}
                            style={{
                                width: '100%',
                                height: '100%',
                                objectFit: 'cover'
                            }}
                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                        />
                    </div>

                    {/* Song Info & Like Button */}
                    <div style={{
                        width: '100%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        marginBottom: '24px',
                        padding: '0 4px'
                    }}>
                        <div style={{ flex: 1, minWidth: 0 }}>
                            <h1 style={{
                                fontSize: '22px',
                                fontWeight: '700',
                                color: '#fff',
                                marginBottom: '4px',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis'
                            }}>
                                {currentSong.title}
                            </h1>
                            <p style={{
                                fontSize: '16px',
                                color: 'rgba(255,255,255,0.7)'
                            }}>
                                {currentSong.artist}
                            </p>
                        </div>
                        <button
                            onClick={() => toggleLike(currentSong)}
                            style={{
                                color: currentSongLiked ? '#1db954' : 'rgba(255,255,255,0.7)',
                                padding: '8px',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer',
                                transition: 'transform 0.2s'
                            }}
                        >
                            <Heart
                                size={28}
                                fill={currentSongLiked ? '#1db954' : 'none'}
                                strokeWidth={currentSongLiked ? 0 : 2}
                            />
                        </button>
                    </div>

                    {/* Progress Bar */}
                    <div style={{ width: '100%', marginBottom: '16px' }}>
                        <div
                            onClick={handleSeek}
                            style={{
                                width: '100%',
                                height: '4px',
                                backgroundColor: 'rgba(255,255,255,0.2)',
                                borderRadius: '2px',
                                cursor: 'pointer',
                                marginBottom: '8px',
                                position: 'relative'
                            }}
                        >
                            <div style={{
                                height: '100%',
                                backgroundColor: '#fff',
                                borderRadius: '2px',
                                width: `${(progress / (duration || 1)) * 100}%`,
                                position: 'relative'
                            }}>
                                <div style={{
                                    position: 'absolute',
                                    right: '-6px',
                                    top: '-4px',
                                    width: '12px',
                                    height: '12px',
                                    backgroundColor: '#fff',
                                    borderRadius: '50%',
                                    boxShadow: '0 2px 4px rgba(0,0,0,0.3)'
                                }} />
                            </div>
                        </div>
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            color: 'rgba(255,255,255,0.6)',
                            fontSize: '12px',
                            fontVariantNumeric: 'tabular-nums'
                        }}>
                            <span>{formatTime(progress)}</span>
                            <span>{formatTime(duration)}</span>
                        </div>
                    </div>

                    {/* Main Controls */}
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '28px',
                        marginBottom: '24px',
                        width: '100%'
                    }}>
                        <button
                            style={{
                                color: '#1db954',
                                padding: '8px',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                        >
                            <Sparkles size={24} />
                        </button>
                        <button
                            onClick={playPrevious}
                            style={{
                                color: '#fff',
                                padding: '8px',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                        >
                            <SkipBack size={32} fill="currentColor" />
                        </button>
                        <button
                            onClick={handlePlayPause}
                            style={{
                                width: '64px',
                                height: '64px',
                                borderRadius: '50%',
                                background: '#fff',
                                border: 'none',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                cursor: 'pointer',
                                boxShadow: '0 8px 24px rgba(0,0,0,0.3)',
                                transition: 'transform 0.1s'
                            }}
                        >
                            {isLoading ? (
                                <div className="spinner" style={{
                                    width: '28px',
                                    height: '28px',
                                    border: '3px solid #ccc',
                                    borderTopColor: '#000',
                                    borderRadius: '50%',
                                    animation: 'spin 1s linear infinite'
                                }} />
                            ) : isPlaying ? (
                                <Pause size={28} fill="#000" color="#000" />
                            ) : (
                                <Play size={28} fill="#000" color="#000" style={{ marginLeft: '3px' }} />
                            )}
                        </button>
                        <button
                            onClick={playNext}
                            style={{
                                color: '#fff',
                                padding: '8px',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                        >
                            <SkipForward size={32} fill="currentColor" />
                        </button>
                        <button
                            style={{
                                color: 'rgba(255,255,255,0.7)',
                                padding: '8px',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                        >
                            <Timer size={24} />
                        </button>
                    </div>

                    {/* Bottom Actions */}
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        width: '100%',
                        padding: '0 4px'
                    }}>
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            color: '#1db954',
                            fontSize: '12px'
                        }}>
                            <div style={{
                                width: '20px',
                                height: '20px',
                                borderRadius: '50%',
                                border: '2px solid #1db954',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center'
                            }}>
                                🎧
                            </div>
                            <span>Connected Device</span>
                        </div>
                        <div style={{ display: 'flex', gap: '16px' }}>
                            <button
                                style={{
                                    color: 'rgba(255,255,255,0.7)',
                                    padding: '8px',
                                    background: 'transparent',
                                    border: 'none',
                                    cursor: 'pointer'
                                }}
                            >
                                <Share2 size={22} />
                            </button>
                            <button
                                onClick={() => setShowQueue(!showQueue)}
                                style={{
                                    color: showQueue ? '#1db954' : 'rgba(255,255,255,0.7)',
                                    padding: '8px',
                                    background: 'transparent',
                                    border: 'none',
                                    cursor: 'pointer'
                                }}
                            >
                                <ListMusic size={22} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Lyrics Section - Scrolls up into view */}
                <div style={{
                    minHeight: '400px',
                    padding: '24px 20px',
                    scrollSnapAlign: 'start',
                    paddingBottom: '100px'
                }}>
                    {/* Lyrics Card */}
                    <div style={{
                        background: 'linear-gradient(135deg, #8B0000 0%, #5c1010 100%)',
                        borderRadius: '12px',
                        padding: '24px',
                        marginBottom: '24px'
                    }}>
                        <div style={{
                            fontSize: '13px',
                            color: 'rgba(255,255,255,0.8)',
                            marginBottom: '16px',
                            fontWeight: '600'
                        }}>
                            Lyrics preview
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                            {sampleLyrics.slice(0, 4).map((line, i) => (
                                <p
                                    key={i}
                                    style={{
                                        fontSize: '20px',
                                        fontWeight: '700',
                                        color: '#fff',
                                        lineHeight: 1.3,
                                        margin: 0
                                    }}
                                >
                                    {line.text}
                                </p>
                            ))}
                        </div>
                        <button
                            onClick={() => setShowLyrics(true)}
                            style={{
                                marginTop: '20px',
                                background: 'rgba(0,0,0,0.3)',
                                color: '#fff',
                                border: 'none',
                                borderRadius: '20px',
                                padding: '10px 20px',
                                fontSize: '14px',
                                fontWeight: '600',
                                cursor: 'pointer'
                            }}
                        >
                            Show lyrics
                        </button>
                    </div>

                    {/* About the Artist */}
                    <div style={{ marginBottom: '24px' }}>
                        <h3 style={{
                            fontSize: '18px',
                            fontWeight: '700',
                            color: '#fff',
                            marginBottom: '16px'
                        }}>
                            About the artist
                        </h3>
                        <div style={{
                            background: 'rgba(255,255,255,0.05)',
                            borderRadius: '12px',
                            overflow: 'hidden'
                        }}>
                            <div style={{
                                height: '200px',
                                background: `linear-gradient(180deg, transparent 0%, rgba(0,0,0,0.8) 100%), 
                                            url(${currentSong.image}) center/cover no-repeat`
                            }} />
                            <div style={{ padding: '16px' }}>
                                <div style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    marginBottom: '12px'
                                }}>
                                    <h4 style={{
                                        fontSize: '20px',
                                        fontWeight: '700',
                                        color: '#fff',
                                        margin: 0
                                    }}>
                                        {currentSong.artist}
                                    </h4>
                                    <button style={{
                                        background: 'transparent',
                                        border: '1px solid rgba(255,255,255,0.3)',
                                        borderRadius: '20px',
                                        color: '#fff',
                                        padding: '6px 16px',
                                        fontSize: '13px',
                                        fontWeight: '600',
                                        cursor: 'pointer'
                                    }}>
                                        Follow
                                    </button>
                                </div>
                                <p style={{
                                    color: 'rgba(255,255,255,0.6)',
                                    fontSize: '14px',
                                    lineHeight: 1.5,
                                    margin: 0
                                }}>
                                    One of the most acclaimed artists of our time, known for
                                    chart-topping hits and incredible live performances.
                                </p>
                            </div>
                        </div>
                    </div>

                    {/* Up Next Queue */}
                    {(queue.length > 0 || upNextQueue.length > 0) && (
                        <div>
                            <h3 style={{
                                fontSize: '18px',
                                fontWeight: '700',
                                color: '#fff',
                                marginBottom: '16px'
                            }}>
                                Up next
                            </h3>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                                {[...queue, ...upNextQueue].slice(0, 5).map((song, i) => (
                                    <div
                                        key={i}
                                        onClick={() => playSong(song)}
                                        style={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            gap: '12px',
                                            padding: '8px',
                                            borderRadius: '8px',
                                            cursor: 'pointer',
                                            transition: 'background 0.2s'
                                        }}
                                    >
                                        <img
                                            src={song.image}
                                            alt=""
                                            style={{
                                                width: '48px',
                                                height: '48px',
                                                borderRadius: '4px',
                                                objectFit: 'cover'
                                            }}
                                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                        />
                                        <div style={{ flex: 1, minWidth: 0 }}>
                                            <div style={{
                                                color: '#fff',
                                                fontSize: '15px',
                                                fontWeight: '500',
                                                whiteSpace: 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis'
                                            }}>
                                                {song.title}
                                            </div>
                                            <div style={{
                                                color: 'rgba(255,255,255,0.6)',
                                                fontSize: '13px'
                                            }}>
                                                {song.artist}
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}
                </div>
            </div>

            {/* Full Queue Modal */}
            {showQueue && (
                <div style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: 'linear-gradient(135deg, #1a1a2e 0%, #000 100%)',
                    zIndex: 10001,
                    display: 'flex',
                    flexDirection: 'column',
                    animation: 'slideUp 0.3s ease-out'
                }}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '16px 20px',
                        paddingTop: 'max(16px, env(safe-area-inset-top))',
                        borderBottom: '1px solid rgba(255,255,255,0.1)'
                    }}>
                        <button
                            onClick={() => setShowQueue(false)}
                            style={{ color: '#fff', padding: '8px', background: 'transparent', border: 'none', cursor: 'pointer' }}
                        >
                            <ChevronDown size={28} />
                        </button>
                        <div style={{ fontSize: '16px', fontWeight: '700', color: '#fff' }}>Queue</div>
                        <div style={{ width: '44px' }} />
                    </div>

                    <div style={{ flex: 1, overflowY: 'auto', padding: '16px' }}>
                        {/* Now Playing */}
                        <div style={{ marginBottom: '24px' }}>
                            <h3 style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '12px', fontWeight: '600' }}>Now Playing</h3>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px', background: 'rgba(255,255,255,0.05)', borderRadius: '8px' }}>
                                <img src={currentSong.image} alt="" style={{ width: '48px', height: '48px', borderRadius: '4px' }} onError={(e) => e.target.src = '/placeholder.svg'} />
                                <div>
                                    <div style={{ color: '#1db954', fontSize: '16px', fontWeight: '600' }}>{currentSong.title}</div>
                                    <div style={{ color: '#b3b3b3', fontSize: '14px' }}>{currentSong.artist}</div>
                                </div>
                                <div style={{ marginLeft: 'auto', display: 'flex', gap: '4px' }}>
                                    <div style={{ width: '3px', height: '16px', background: '#1db954', animation: 'eq 1s infinite' }}></div>
                                    <div style={{ width: '3px', height: '16px', background: '#1db954', animation: 'eq 0.8s infinite' }}></div>
                                    <div style={{ width: '3px', height: '16px', background: '#1db954', animation: 'eq 1.2s infinite' }}></div>
                                </div>
                            </div>
                        </div>

                        {/* Next In Queue */}
                        {queue.length > 0 && (
                            <div style={{ marginBottom: '24px' }}>
                                <h3 style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '12px', fontWeight: '600' }}>Next in Queue</h3>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                    {queue.map((song, i) => (
                                        <div key={i} onClick={() => { playSong(song); setShowQueue(false); }}
                                            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px', borderRadius: '8px' }}>
                                            <div style={{ color: '#b3b3b3', fontSize: '14px', width: '20px' }}>{i + 1}</div>
                                            <img src={song.image} alt="" style={{ width: '48px', height: '48px', borderRadius: '4px' }} onError={(e) => e.target.src = '/placeholder.svg'} />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ color: '#fff', fontSize: '16px', fontWeight: '500', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{song.title}</div>
                                                <div style={{ color: '#b3b3b3', fontSize: '14px' }}>{song.artist}</div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Up Next / Autoplay */}
                        {upNextQueue.length > 0 && (
                            <div>
                                <h3 style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '12px', fontWeight: '600' }}>Next From: Autoplay</h3>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                    {upNextQueue.map((song, i) => (
                                        <div key={i} onClick={() => { playSong(song); setShowQueue(false); }}
                                            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px', borderRadius: '8px' }}>
                                            <img src={song.image} alt="" style={{ width: '48px', height: '48px', borderRadius: '4px' }} onError={(e) => e.target.src = '/placeholder.svg'} />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ color: '#fff', fontSize: '16px', fontWeight: '500', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{song.title}</div>
                                                <div style={{ color: '#b3b3b3', fontSize: '14px' }}>{song.artist}</div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            )}

            {showLyrics && (
                <div style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: 'linear-gradient(135deg, #8B0000 0%, #2a0a0a 100%)',
                    zIndex: 10001,
                    display: 'flex',
                    flexDirection: 'column',
                    animation: 'slideUp 0.3s ease-out'
                }}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '16px 20px',
                        paddingTop: 'max(16px, env(safe-area-inset-top))'
                    }}>
                        <button
                            onClick={() => setShowLyrics(false)}
                            style={{
                                color: '#fff',
                                padding: '8px',
                                background: 'transparent',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                        >
                            <ChevronDown size={28} />
                        </button>
                        <div style={{
                            fontSize: '14px',
                            fontWeight: '600',
                            color: '#fff'
                        }}>
                            Lyrics
                        </div>
                        <div style={{ width: '44px' }} />
                    </div>
                    <div style={{
                        flex: 1,
                        overflowY: 'auto',
                        padding: '24px',
                        paddingBottom: '100px'
                    }}>
                        {sampleLyrics.map((line, i) => (
                            <p
                                key={i}
                                style={{
                                    fontSize: '28px',
                                    fontWeight: '700',
                                    color: progress >= line.time ? '#fff' : 'rgba(255,255,255,0.4)',
                                    lineHeight: 1.4,
                                    marginBottom: '20px',
                                    transition: 'color 0.3s'
                                }}
                            >
                                {line.text}
                            </p>
                        ))}
                    </div>
                </div>
            )}

            <style>{`
                @keyframes slideUp {
                    from { transform: translateY(100%); }
                    to { transform: translateY(0); }
                }
                @keyframes spin {
                    from { transform: rotate(0deg); }
                    to { transform: rotate(360deg); }
                }
                .mobile-fullscreen-player {
                    -webkit-tap-highlight-color: transparent;
                }
                .mobile-fullscreen-player button:active {
                    transform: scale(0.95);
                }
                .mobile-fullscreen-player::-webkit-scrollbar {
                    display: none;
                }
            `}</style>
        </div>,
        document.body
    );
};

export default MobileFullScreenPlayer;
