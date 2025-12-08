import React, { useState } from 'react';
import {
    Play, Pause, SkipBack, SkipForward, Volume2, Volume1, VolumeX,
    Repeat, Shuffle, Heart, Loader, Maximize2, Mic2, ListMusic, Laptop2,
    Timer, X, ChevronDown
} from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import ReactPlayer from 'react-player';

const Player = () => {
    const {
        currentSong, isPlaying, togglePlay, progress, duration,
        seek, playNext, playPrevious, youtubeUrl, playerRef,
        handleProgress, handleDuration, handleEnded, isLoading, onPlayerReady,
        upNextQueue, queue, playSong,
        volume, isMuted, changeVolume, toggleMute,
        sleepTimer, startSleepTimer, cancelSleepTimer,
        isFullScreen, toggleFullScreen
    } = usePlayer();

    const [isLiked, setIsLiked] = useState(false);
    const [isShuffle, setIsShuffle] = useState(false);
    const [repeatMode, setRepeatMode] = useState(0);
    const [showQueue, setShowQueue] = useState(false);
    const [showSleepMenu, setShowSleepMenu] = useState(false);

    const isYoutube = youtubeUrl && (youtubeUrl.includes('youtube.com') || youtubeUrl.includes('youtu.be'));

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
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                height: '100%',
                padding: '0 16px',
                backgroundColor: '#000',
                borderTop: '1px solid #282828'
            }}>
                <div style={{ width: '30%', minWidth: '180px' }} />
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <button disabled style={{ color: '#4d4d4d' }}><Shuffle size={16} /></button>
                        <button disabled style={{ color: '#4d4d4d' }}><SkipBack size={20} /></button>
                        <div style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            backgroundColor: '#fff',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            opacity: 0.6
                        }}>
                            <Play size={16} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                        </div>
                        <button disabled style={{ color: '#4d4d4d' }}><SkipForward size={20} /></button>
                        <button disabled style={{ color: '#4d4d4d' }}><Repeat size={16} /></button>
                    </div>
                </div>
                <div style={{ width: '30%', minWidth: '180px' }} />
            </div>
        );
    }

    return (
        <div className="player-bar" style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            height: '100%',
            padding: '0 16px',
            backgroundColor: '#000',
            borderTop: '1px solid #282828'
        }}>
            {/* Audio Player - uses native audio for all sources including YouTube streams */}
            {youtubeUrl && (
                <audio
                    ref={playerRef}
                    src={youtubeUrl}
                    onTimeUpdate={(e) => {
                        handleProgress({ playedSeconds: e.currentTarget.currentTime });
                    }}
                    onLoadedMetadata={(e) => {
                        console.log('[Player] Audio loaded, duration:', e.currentTarget.duration);
                        handleDuration(e.currentTarget.duration);
                        onPlayerReady();
                    }}
                    onPlay={() => console.log('[Player] Playing!')}
                    onError={(e) => console.error('[Player] Audio error:', e)}
                    onEnded={handleEnded}
                    style={{ display: 'none' }}
                />
            )}

            {/* Left: Now Playing */}
            <div className="player-now-playing">
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
                <button
                    onClick={() => setIsLiked(!isLiked)}
                    className="hide-mobile"
                    style={{
                        color: isLiked ? '#1db954' : '#b3b3b3',
                        padding: '4px',
                        transition: 'all 0.2s'
                    }}
                    onMouseEnter={(e) => !isLiked && (e.currentTarget.style.color = '#fff')}
                    onMouseLeave={(e) => !isLiked && (e.currentTarget.style.color = '#b3b3b3')}
                >
                    <Heart size={16} fill={isLiked ? '#1db954' : 'none'} />
                </button>
            </div>

            {/* Center: Controls */}
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px', maxWidth: '40%', width: '100%' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <button
                        onClick={() => setIsShuffle(!isShuffle)}
                        className="hide-mobile"
                        style={{ color: isShuffle ? '#1db954' : '#b3b3b3', padding: '4px' }}
                        onMouseEnter={(e) => !isShuffle && (e.currentTarget.style.color = '#fff')}
                        onMouseLeave={(e) => !isShuffle && (e.currentTarget.style.color = '#b3b3b3')}
                    >
                        <Shuffle size={16} />
                    </button>
                    <button
                        onClick={playPrevious}
                        style={{ color: '#b3b3b3', padding: '4px' }}
                        onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                        onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                    >
                        <SkipBack size={20} fill="currentColor" />
                    </button>
                    <button
                        onClick={() => {
                            togglePlay();
                            if (playerRef.current && !isYoutube) {
                                if (isPlaying) playerRef.current.pause();
                                else playerRef.current.play().catch(() => { });
                            }
                        }}
                        disabled={isLoading}
                        style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            backgroundColor: '#fff',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            transition: 'transform 0.1s'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.06)'}
                        onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
                    >
                        {isLoading ? (
                            <Loader size={16} color="#000" className="animate-spin" />
                        ) : isPlaying ? (
                            <Pause size={16} fill="#000" color="#000" />
                        ) : (
                            <Play size={16} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                        )}
                    </button>
                    <button
                        onClick={playNext}
                        style={{ color: '#b3b3b3', padding: '4px' }}
                        onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                        onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                    >
                        <SkipForward size={20} fill="currentColor" />
                    </button>
                    <button
                        onClick={() => setRepeatMode((repeatMode + 1) % 3)}
                        className="hide-mobile"
                        style={{ color: repeatMode > 0 ? '#1db954' : '#b3b3b3', padding: '4px', position: 'relative' }}
                        onMouseEnter={(e) => repeatMode === 0 && (e.currentTarget.style.color = '#fff')}
                        onMouseLeave={(e) => repeatMode === 0 && (e.currentTarget.style.color = '#b3b3b3')}
                    >
                        <Repeat size={16} />
                        {repeatMode === 2 && <span style={{ position: 'absolute', bottom: '-2px', right: '-2px', fontSize: '10px', color: '#1db954' }}>1</span>}
                    </button>
                </div>

                {/* Progress Bar */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', maxWidth: '600px' }}>
                    <span style={{ fontSize: '11px', color: '#b3b3b3', fontVariantNumeric: 'tabular-nums', minWidth: '40px', textAlign: 'right' }}>
                        {formatTime(progress)}
                    </span>
                    <div
                        onClick={handleSeek}
                        style={{
                            flex: 1,
                            height: '4px',
                            backgroundColor: 'rgba(255,255,255,0.1)',
                            borderRadius: '2px',
                            cursor: 'pointer',
                            position: 'relative'
                        }}
                        className="progress-bar"
                    >
                        <div style={{
                            height: '100%',
                            backgroundColor: '#fff',
                            borderRadius: '2px',
                            width: `${(progress / (duration || 1)) * 100}%`,
                            position: 'relative'
                        }} className="progress-fill">
                            <div className="progress-thumb" style={{
                                position: 'absolute',
                                right: '-6px',
                                top: '-4px',
                                width: '12px',
                                height: '12px',
                                backgroundColor: '#fff',
                                borderRadius: '50%',
                                opacity: 0,
                                boxShadow: '0 2px 4px rgba(0,0,0,0.5)'
                            }} />
                        </div>
                    </div>
                    <span style={{ fontSize: '11px', color: '#b3b3b3', fontVariantNumeric: 'tabular-nums', minWidth: '40px' }}>
                        {formatTime(duration)}
                    </span>
                </div>
            </div>

            {/* Right: Volume & Extra - hidden on mobile */}
            <div className="hide-mobile" style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '8px', width: '30%', minWidth: '180px' }}>
                <button style={{ color: '#b3b3b3', padding: '4px' }}
                    onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                    onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}>
                    <Mic2 size={16} />
                </button>
                <button
                    style={{ color: showQueue ? '#1db954' : '#b3b3b3', padding: '4px' }}
                    onClick={() => setShowQueue(!showQueue)}
                    onMouseEnter={(e) => !showQueue && (e.currentTarget.style.color = '#fff')}
                    onMouseLeave={(e) => !showQueue && (e.currentTarget.style.color = '#b3b3b3')}>
                    <ListMusic size={16} />
                </button>

                {showQueue && (
                    <div style={{
                        position: 'fixed',
                        bottom: '90px',
                        right: '16px',
                        width: '350px',
                        maxHeight: 'calc(100vh - 180px)',
                        backgroundColor: '#121212',
                        borderRadius: '8px',
                        padding: '16px',
                        boxShadow: '0 -4px 32px rgba(0,0,0,0.5)',
                        overflowY: 'auto',
                        zIndex: 1000,
                        border: '1px solid #282828'
                    }}>
                        <h3 style={{ margin: '0 0 16px', color: '#fff', fontSize: '18px', fontWeight: 'bold' }}>Queue</h3>

                        <div style={{ marginBottom: '24px' }}>
                            <h4 style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '12px' }}>Now Playing</h4>
                            {currentSong && (
                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                    <img src={currentSong.image} alt="" style={{ width: '40px', height: '40px', borderRadius: '4px' }}
                                        onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                                    <div>
                                        <div style={{ color: '#1db954', fontSize: '14px', fontWeight: '500' }}>{currentSong.title}</div>
                                        <div style={{ color: '#b3b3b3', fontSize: '12px' }}>{currentSong.artist}</div>
                                    </div>
                                    <div style={{ marginLeft: 'auto', display: 'flex', gap: '4px' }}>
                                        <div style={{ width: '3px', height: '12px', background: '#1db954', animation: 'eq 1s infinite' }}></div>
                                        <div style={{ width: '3px', height: '12px', background: '#1db954', animation: 'eq 0.8s infinite' }}></div>
                                        <div style={{ width: '3px', height: '12px', background: '#1db954', animation: 'eq 1.2s infinite' }}></div>
                                    </div>
                                </div>
                            )}
                        </div>

                        {queue.length > 0 && (
                            <div style={{ marginBottom: '24px' }}>
                                <h4 style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '12px' }}>Next in Queue</h4>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                    {queue.map((song, i) => (
                                        <div key={i} className="queue-item" onClick={() => playSong(song)}
                                            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px', borderRadius: '4px', cursor: 'pointer' }}>
                                            <div style={{ color: '#b3b3b3', fontSize: '14px', width: '20px' }}>{i + 1}</div>
                                            <img src={song.image} alt="" style={{ width: '40px', height: '40px', borderRadius: '4px' }}
                                                onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ color: '#fff', fontSize: '14px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{song.title}</div>
                                                <div style={{ color: '#b3b3b3', fontSize: '12px' }}>{song.artist}</div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {upNextQueue.length > 0 && (
                            <div>
                                <h4 style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '12px' }}>Next From: Autoplay</h4>
                                <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                                    {upNextQueue.map((song, i) => (
                                        <div key={i} className="queue-item" onClick={() => playSong(song)}
                                            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px', borderRadius: '4px', cursor: 'pointer' }}>
                                            <img src={song.image} alt="" style={{ width: '40px', height: '40px', borderRadius: '4px' }}
                                                onError={(e) => { e.target.src = '/placeholder.svg'; }} />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ color: '#fff', fontSize: '14px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{song.title}</div>
                                                <div style={{ color: '#b3b3b3', fontSize: '12px' }}>{song.artist}</div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* Sleep Timer Button */}
                <div style={{ position: 'relative' }}>
                    <button
                        onClick={() => setShowSleepMenu(!showSleepMenu)}
                        style={{
                            color: sleepTimer ? '#1db954' : '#b3b3b3',
                            padding: '4px',
                            position: 'relative'
                        }}
                        onMouseEnter={(e) => !sleepTimer && (e.currentTarget.style.color = '#fff')}
                        onMouseLeave={(e) => !sleepTimer && (e.currentTarget.style.color = '#b3b3b3')}
                        title={sleepTimer ? `Sleep in ${sleepTimer} min` : 'Sleep Timer'}
                    >
                        <Timer size={16} />
                        {sleepTimer && (
                            <span style={{
                                position: 'absolute',
                                top: '-4px',
                                right: '-4px',
                                fontSize: '9px',
                                background: '#1db954',
                                color: '#000',
                                borderRadius: '50%',
                                width: '14px',
                                height: '14px',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontWeight: 'bold'
                            }}>{sleepTimer}</span>
                        )}
                    </button>

                    {/* Sleep Timer Dropdown */}
                    {showSleepMenu && (
                        <div style={{
                            position: 'absolute',
                            bottom: '40px',
                            right: '0',
                            backgroundColor: '#282828',
                            borderRadius: '8px',
                            padding: '8px 0',
                            minWidth: '150px',
                            boxShadow: '0 4px 24px rgba(0,0,0,0.5)',
                            zIndex: 1000
                        }}>
                            <div style={{ padding: '8px 16px', color: '#b3b3b3', fontSize: '12px', fontWeight: '600' }}>
                                SLEEP TIMER
                            </div>
                            {[5, 15, 30, 45, 60, 90].map(mins => (
                                <button
                                    key={mins}
                                    onClick={() => { startSleepTimer(mins); setShowSleepMenu(false); }}
                                    style={{
                                        display: 'block',
                                        width: '100%',
                                        padding: '10px 16px',
                                        textAlign: 'left',
                                        color: sleepTimer === mins ? '#1db954' : '#fff',
                                        fontSize: '14px',
                                        background: 'none',
                                        border: 'none',
                                        cursor: 'pointer'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                                >
                                    {mins} minutes
                                </button>
                            ))}
                            {sleepTimer && (
                                <button
                                    onClick={() => { cancelSleepTimer(); setShowSleepMenu(false); }}
                                    style={{
                                        display: 'block',
                                        width: '100%',
                                        padding: '10px 16px',
                                        textAlign: 'left',
                                        color: '#ff5555',
                                        fontSize: '14px',
                                        background: 'none',
                                        border: 'none',
                                        cursor: 'pointer',
                                        borderTop: '1px solid #404040',
                                        marginTop: '4px'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                                >
                                    Turn off timer
                                </button>
                            )}
                        </div>
                    )}
                </div>

                <button style={{ color: '#b3b3b3', padding: '4px' }}
                    onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                    onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}>
                    <Laptop2 size={16} />
                </button>

                <div style={{ display: 'flex', alignItems: 'center', gap: '4px', width: '125px' }}>
                    <button
                        onClick={toggleMute}
                        style={{ color: '#b3b3b3', padding: '4px' }}
                        onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                        onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                    >
                        {getVolumeIcon()}
                    </button>
                    <div
                        onClick={handleVolumeChange}
                        style={{
                            flex: 1,
                            height: '4px',
                            backgroundColor: 'rgba(255,255,255,0.1)',
                            borderRadius: '2px',
                            cursor: 'pointer'
                        }}
                        className="volume-bar"
                    >
                        <div style={{
                            height: '100%',
                            backgroundColor: '#fff',
                            borderRadius: '2px',
                            width: `${(isMuted ? 0 : volume) * 100}%`
                        }} className="volume-fill" />
                    </div>
                </div>

                <button
                    onClick={toggleFullScreen}
                    style={{ color: '#b3b3b3', padding: '4px' }}
                    onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                    onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                    title="Full screen (F)"
                >
                    <Maximize2 size={16} />
                </button>
            </div>

            {/* Full-Screen Now Playing Overlay */}
            {isFullScreen && currentSong && (
                <div style={{
                    position: 'fixed',
                    top: 0,
                    left: 0,
                    right: 0,
                    bottom: 0,
                    background: `linear-gradient(180deg, rgba(0,0,0,0.3) 0%, rgba(0,0,0,0.95) 100%), 
                                 url(${currentSong.image}) center/cover no-repeat`,
                    backdropFilter: 'blur(100px)',
                    zIndex: 9999,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    animation: 'fadeIn 0.3s ease'
                }}>
                    {/* Close Button */}
                    <button
                        onClick={toggleFullScreen}
                        style={{
                            position: 'absolute',
                            top: '24px',
                            right: '24px',
                            color: '#fff',
                            padding: '8px',
                            background: 'rgba(255,255,255,0.1)',
                            borderRadius: '50%',
                            transition: 'all 0.2s'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.2)'}
                        onMouseLeave={(e) => e.currentTarget.style.background = 'rgba(255,255,255,0.1)'}
                    >
                        <ChevronDown size={24} />
                    </button>

                    {/* Sleep Timer Badge */}
                    {sleepTimer && (
                        <div style={{
                            position: 'absolute',
                            top: '24px',
                            left: '24px',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            background: 'rgba(29, 185, 84, 0.2)',
                            padding: '8px 16px',
                            borderRadius: '20px',
                            color: '#1db954'
                        }}>
                            <Timer size={16} />
                            <span style={{ fontSize: '14px', fontWeight: '500' }}>
                                Sleep in {sleepTimer} min
                            </span>
                        </div>
                    )}

                    {/* Album Art */}
                    <div style={{
                        width: 'min(400px, 60vw)',
                        aspectRatio: '1',
                        borderRadius: '8px',
                        overflow: 'hidden',
                        boxShadow: '0 24px 80px rgba(0,0,0,0.6)',
                        marginBottom: '48px'
                    }}>
                        <img
                            src={currentSong.image}
                            alt={currentSong.title}
                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                        />
                    </div>

                    {/* Song Info */}
                    <div style={{ textAlign: 'center', marginBottom: '32px', maxWidth: '80%' }}>
                        <h1 style={{
                            fontSize: 'clamp(24px, 5vw, 48px)',
                            fontWeight: '700',
                            color: '#fff',
                            marginBottom: '8px',
                            textShadow: '0 2px 10px rgba(0,0,0,0.3)'
                        }}>
                            {currentSong.title}
                        </h1>
                        <p style={{
                            fontSize: 'clamp(16px, 3vw, 24px)',
                            color: '#b3b3b3'
                        }}>
                            {currentSong.artist}
                        </p>
                    </div>

                    {/* Progress Bar */}
                    <div style={{ width: 'min(600px, 80%)', marginBottom: '24px' }}>
                        <div
                            onClick={handleSeek}
                            style={{
                                width: '100%',
                                height: '6px',
                                backgroundColor: 'rgba(255,255,255,0.2)',
                                borderRadius: '3px',
                                cursor: 'pointer',
                                marginBottom: '8px'
                            }}
                        >
                            <div style={{
                                height: '100%',
                                backgroundColor: '#1db954',
                                borderRadius: '3px',
                                width: `${(progress / (duration || 1)) * 100}%`,
                                transition: 'width 0.1s linear'
                            }} />
                        </div>
                        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#b3b3b3', fontSize: '14px' }}>
                            <span>{formatTime(progress)}</span>
                            <span>{formatTime(duration)}</span>
                        </div>
                    </div>

                    {/* Controls */}
                    <div style={{ display: 'flex', alignItems: 'center', gap: '32px' }}>
                        <button
                            onClick={playPrevious}
                            style={{ color: '#fff', padding: '8px' }}
                        >
                            <SkipBack size={32} fill="currentColor" />
                        </button>
                        <button
                            onClick={togglePlay}
                            style={{
                                width: '72px',
                                height: '72px',
                                borderRadius: '50%',
                                backgroundColor: '#fff',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                transition: 'transform 0.2s'
                            }}
                            onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.05)'}
                            onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
                        >
                            {isLoading ? (
                                <Loader size={32} color="#000" className="animate-spin" />
                            ) : isPlaying ? (
                                <Pause size={32} fill="#000" color="#000" />
                            ) : (
                                <Play size={32} fill="#000" color="#000" style={{ marginLeft: '4px' }} />
                            )}
                        </button>
                        <button
                            onClick={playNext}
                            style={{ color: '#fff', padding: '8px' }}
                        >
                            <SkipForward size={32} fill="currentColor" />
                        </button>
                    </div>

                    {/* Keyboard Shortcuts Hint */}
                    <div style={{
                        position: 'absolute',
                        bottom: '24px',
                        color: 'rgba(255,255,255,0.4)',
                        fontSize: '12px'
                    }}>
                        Press <kbd style={{ background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>F</kbd> or <kbd style={{ background: 'rgba(255,255,255,0.1)', padding: '2px 6px', borderRadius: '4px' }}>Esc</kbd> to exit
                    </div>
                </div>
            )}

            <style>{`
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
                .animate-spin { animation: spin 1s linear infinite; }
                .progress-bar:hover { height: 6px !important; }
                .progress-bar:hover .progress-fill { background-color: #1db954 !important; }
                .progress-bar:hover .progress-thumb { opacity: 1 !important; }
                .volume-bar:hover .volume-fill { background-color: #1db954 !important; }
            `}</style>
        </div>
    );
};

export default Player;
