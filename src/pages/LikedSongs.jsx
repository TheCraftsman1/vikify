import React from 'react';
import { Play, Pause, Clock, Heart, Download, Shuffle, MoreHorizontal } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { useOffline } from '../context/OfflineContext';
import { downloadSong } from '../utils/download';

const LikedSongs = () => {
    const { playSong, currentSong, isPlaying, togglePlay, shufflePlay, shuffle } = usePlayer();
    const { getLikedSongs, toggleLike } = useLikedSongs();
    const { isSongOffline } = useOffline();

    const likedSongs = getLikedSongs();

    const formatDuration = (seconds) => {
        if (!seconds) return '0:00';
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    };

    const handleSongClick = (song) => {
        if (currentSong?.id === song.id) {
            togglePlay();
        } else {
            playSong(song, likedSongs);
        }
    };

    const totalDuration = likedSongs.reduce((acc, s) => acc + (s.duration || 0), 0);
    const isCurrentPlaylistPlaying = currentSong && likedSongs.some(s => s.id === currentSong.id);

    const handlePlayClick = () => {
        if (isCurrentPlaylistPlaying) {
            togglePlay();
        } else if (likedSongs.length > 0) {
            playSong(likedSongs[0], likedSongs);
        }
    };

    return (
        <div style={{ minHeight: '100%', position: 'relative' }}>
            {/* Header Gradient */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '332px',
                background: 'linear-gradient(180deg, #5038a0 0%, #121212 100%)',
                pointerEvents: 'none'
            }} />

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '120px' }}>
                {/* Header */}
                <div className="liked-header" style={{ display: 'flex', alignItems: 'flex-end', gap: '20px', marginBottom: '24px', paddingTop: '24px', flexWrap: 'wrap' }}>
                    <div className="liked-icon-wrapper" style={{ position: 'relative', flexShrink: 0 }}>
                        <div className="liked-icon-box" style={{
                            width: 'min(200px, 56vw)',
                            height: 'min(200px, 56vw)',
                            borderRadius: '12px',
                            background: 'linear-gradient(135deg, #450af5, #c4efd9)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 4px 60px rgba(0,0,0,0.5)',
                            position: 'relative',
                            zIndex: 1
                        }}>
                            <Heart size={80} fill="#fff" color="#fff" />
                        </div>
                    </div>
                    <div style={{ flex: 1 }}>
                        <span style={{ fontSize: '14px', fontWeight: 700, textTransform: 'uppercase' }}>Playlist</span>
                        <h1 style={{
                            fontSize: 'clamp(36px, 9vw, 72px)',
                            fontWeight: 900,
                            marginTop: '8px',
                            marginBottom: '24px',
                            lineHeight: 1
                        }}>Liked Songs</h1>
                        <p style={{ color: '#b3b3b3', fontSize: '14px' }}>
                            {likedSongs.length} songs, about {Math.floor(totalDuration / 60)} min
                        </p>
                    </div>
                </div>

                {/* Action Bar */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '24px', marginBottom: '24px' }}>
                    {likedSongs.length > 0 && (
                        <>
                            <button
                                onClick={handlePlayClick}
                                style={{
                                    width: '56px',
                                    height: '56px',
                                    borderRadius: '50%',
                                    backgroundColor: '#1db954',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    boxShadow: '0 8px 16px rgba(0,0,0,0.3)',
                                    transition: 'all 0.2s'
                                }}
                                onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.04)'; e.currentTarget.style.backgroundColor = '#1ed760'; }}
                                onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.backgroundColor = '#1db954'; }}
                            >
                                {isCurrentPlaylistPlaying && isPlaying ? (
                                    <Pause size={24} fill="#000" color="#000" />
                                ) : (
                                    <Play size={24} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                                )}
                            </button>
                            <button
                                onClick={() => shufflePlay(likedSongs)}
                                style={{ color: shuffle ? '#1db954' : '#b3b3b3', padding: '8px' }}
                                onMouseEnter={(e) => e.currentTarget.style.color = shuffle ? '#1db954' : '#fff'}
                                onMouseLeave={(e) => e.currentTarget.style.color = shuffle ? '#1db954' : '#b3b3b3'}
                                title="Shuffle play"
                            >
                                <Shuffle size={24} />
                            </button>
                        </>
                    )}
                </div>

                {/* Content */}
                {likedSongs.length === 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '64px', textAlign: 'center' }}>
                        <div style={{
                            width: '128px',
                            height: '128px',
                            borderRadius: '50%',
                            backgroundColor: '#282828',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            marginBottom: '24px'
                        }}>
                            <Heart size={48} color="#7f7f7f" />
                        </div>
                        <h2 style={{ fontSize: '32px', fontWeight: 700, marginBottom: '8px' }}>Songs you like will appear here</h2>
                        <p style={{ color: '#b3b3b3', maxWidth: '400px' }}>
                            Save songs by tapping the heart icon.
                        </p>
                    </div>
                ) : (
                    <>
                        {/* Table Header */}
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: '32px 4fr 3fr 72px',
                            gap: '16px',
                            padding: '8px 16px',
                            borderBottom: '1px solid rgba(255,255,255,0.1)',
                            color: '#b3b3b3',
                            fontSize: '14px',
                            textTransform: 'uppercase',
                            letterSpacing: '0.1em'
                        }}>
                            <span style={{ textAlign: 'center' }}>#</span>
                            <span>Title</span>
                            <span className="hide-mobile"></span>
                            <span style={{ textAlign: 'right' }}><Clock size={16} /></span>
                        </div>

                        {/* Songs */}
                        <div style={{ marginTop: '8px' }}>
                            {likedSongs.map((song, index) => {
                                const isThisSong = currentSong?.id === song.id;
                                const isOffline = isSongOffline(song.id);

                                return (
                                    <div
                                        key={song.id}
                                        onClick={() => handleSongClick(song)}
                                        style={{
                                            display: 'grid',
                                            gridTemplateColumns: '32px 4fr 3fr 72px',
                                            gap: '16px',
                                            padding: '8px 16px',
                                            borderRadius: '4px',
                                            cursor: 'pointer',
                                            transition: 'background 0.2s'
                                        }}
                                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                                        className="track-row"
                                    >
                                        {/* Number */}
                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '14px', color: isThisSong ? '#1db954' : '#b3b3b3' }}>
                                            <span className="track-num">{index + 1}</span>
                                            {isThisSong && isPlaying ? (
                                                <Pause size={14} fill="#1db954" className="track-play" style={{ display: 'none' }} />
                                            ) : (
                                                <Play size={14} fill="#fff" className="track-play" style={{ display: 'none' }} />
                                            )}
                                        </div>

                                        {/* Song Info */}
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: 0 }}>
                                            <div style={{ position: 'relative' }}>
                                                <img
                                                    src={song.image}
                                                    alt={song.title}
                                                    style={{ width: '40px', height: '40px', borderRadius: '4px', objectFit: 'cover' }}
                                                    onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                />
                                                {isOffline && (
                                                    <div style={{
                                                        position: 'absolute',
                                                        bottom: '-4px',
                                                        right: '-4px',
                                                        width: '14px',
                                                        height: '14px',
                                                        borderRadius: '50%',
                                                        backgroundColor: '#1db954',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center'
                                                    }}>
                                                        <Download size={8} color="#000" />
                                                    </div>
                                                )}
                                            </div>
                                            <div style={{ minWidth: 0 }}>
                                                <div style={{
                                                    color: isThisSong ? '#1db954' : '#fff',
                                                    fontSize: '16px',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{song.title}</div>
                                                <div style={{
                                                    color: '#b3b3b3',
                                                    fontSize: '14px',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{song.artist}</div>
                                            </div>
                                        </div>

                                        {/* Album */}
                                        <div className="hide-mobile" style={{ display: 'flex', alignItems: 'center', color: '#b3b3b3', fontSize: '14px' }}>
                                            {song.album || '-'}
                                        </div>

                                        {/* Duration & Actions */}
                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '12px' }}>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); toggleLike(song); }}
                                                className="action-btn"
                                                style={{ padding: '4px', color: '#1db954' }}
                                            >
                                                <Heart size={16} fill="#1db954" />
                                            </button>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); downloadSong(song); }}
                                                className="action-btn"
                                                style={{ opacity: 0, padding: '4px', color: '#b3b3b3' }}
                                            >
                                                <Download size={16} />
                                            </button>
                                            <span style={{ color: '#b3b3b3', fontSize: '14px', fontVariantNumeric: 'tabular-nums' }}>
                                                {formatDuration(song.duration)}
                                            </span>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </>
                )}
            </div>

            <style>{`
                @keyframes likedShine {
                    0%, 100% { opacity: 0.4; }
                    50% { opacity: 0.8; }
                }
                .liked-icon-wrapper::before {
                    content: '';
                    position: absolute;
                    inset: -3px;
                    background: linear-gradient(135deg, #450af5, #c4efd9, #450af5);
                    background-size: 200% 200%;
                    border-radius: 14px;
                    z-index: 0;
                    animation: likedShine 2s ease-in-out infinite;
                }
                .track-row:hover .track-num { display: none; }
                .track-row:hover .track-play { display: block !important; }
                .track-row:hover .action-btn { opacity: 1 !important; }
                .action-btn:hover { color: #fff !important; }
                @media (max-width: 768px) {
                    .hide-mobile { display: none !important; }
                    .liked-header {
                        flex-direction: column !important;
                        align-items: center !important;
                        text-align: center !important;
                    }
                    .liked-header h1 {
                        font-size: 36px !important;
                    }
                    .liked-icon-box {
                        width: 180px !important;
                        height: 180px !important;
                    }
                }
            `}</style>
        </div>
    );
};

export default LikedSongs;
