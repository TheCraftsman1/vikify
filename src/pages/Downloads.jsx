import React, { useState, useEffect } from 'react';
import { Download, Play, Trash2, Music, Clock, Pause, WifiOff, HardDrive } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useOffline } from '../context/OfflineContext';
import { getStorageUsage, getAllOfflineSongs } from '../utils/offlineDB';

const Downloads = () => {
    const { playSong, currentSong, isPlaying, togglePlay } = usePlayer();
    const { isSongOffline, removeFromOffline } = useOffline();
    const [downloads, setDownloads] = useState([]);
    const [sortBy, setSortBy] = useState('date');
    const [storageInfo, setStorageInfo] = useState({ usedMB: '0', songs: 0 });

    useEffect(() => {
        loadDownloads();
        loadStorageInfo();
    }, []);

    const loadDownloads = async () => {
        // Load from localStorage (download history)
        const savedDownloads = JSON.parse(localStorage.getItem('downloads') || '[]');

        // Also get all offline songs from IndexedDB
        const offlineSongs = await getAllOfflineSongs();

        // Merge: prioritize localStorage data but mark with offline status from IndexedDB
        const offlineIds = new Set(offlineSongs.map(s => s.songId));
        const merged = savedDownloads.map(d => ({
            ...d,
            isOffline: offlineIds.has(d.id)
        }));

        setDownloads(merged);
    };

    const loadStorageInfo = async () => {
        const usage = await getStorageUsage();
        setStorageInfo(usage);
    };

    const removeDownload = async (id) => {
        // Remove from localStorage
        const updated = downloads.filter(d => d.id !== id);
        setDownloads(updated);
        localStorage.setItem('downloads', JSON.stringify(updated));

        // Also remove from IndexedDB
        await removeFromOffline(id);
        await loadStorageInfo();
    };

    const clearAllDownloads = async () => {
        if (window.confirm('Are you sure you want to clear all downloads? This will remove cached audio.')) {
            // Clear all from IndexedDB
            for (const d of downloads) {
                await removeFromOffline(d.id);
            }
            setDownloads([]);
            localStorage.setItem('downloads', JSON.stringify([]));
            await loadStorageInfo();
        }
    };

    const formatDate = (dateString) => {
        const date = new Date(dateString);
        const now = new Date();
        const diffDays = Math.floor((now - date) / (1000 * 60 * 60 * 24));
        if (diffDays === 0) return 'Today';
        if (diffDays === 1) return 'Yesterday';
        if (diffDays < 7) return `${diffDays} days ago`;
        return date.toLocaleDateString();
    };

    const formatDuration = (ms) => {
        if (!ms) return '0:00';
        // Handle both milliseconds and seconds
        const seconds = ms > 1000 ? Math.floor(ms / 1000) : ms;
        const minutes = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${minutes}:${secs.toString().padStart(2, '0')}`;
    };

    const handlePlay = (song) => {
        if (currentSong?.id === song.id) {
            togglePlay();
        } else {
            playSong(song);
        }
    };

    const sortedDownloads = [...downloads].sort((a, b) => {
        switch (sortBy) {
            case 'title': return (a.title || '').localeCompare(b.title || '');
            case 'artist': return (a.artist || '').localeCompare(b.artist || '');
            default: return new Date(b.downloadedAt) - new Date(a.downloadedAt);
        }
    });

    // Count how many are actually cached offline
    const offlineCount = downloads.filter(d => d.isOffline).length;

    return (
        <div className="downloads-page" style={{ minHeight: '100%', position: 'relative' }}>
            {/* Header Gradient */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '380px',
                background: 'linear-gradient(180deg, #5038a0 0%, #121212 100%)',
                pointerEvents: 'none'
            }} />

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '160px' }} className="page-content">
                {/* Header - Centered on Mobile */}
                <div className="downloads-header" style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    textAlign: 'center',
                    marginBottom: '32px',
                    paddingTop: '24px'
                }}>
                    {/* Download Icon Box with Shine Border */}
                    <div className="downloads-icon-wrapper" style={{
                        position: 'relative',
                        marginBottom: '24px'
                    }}>
                        <div className="downloads-icon-box" style={{
                            width: '180px',
                            height: '180px',
                            borderRadius: '12px',
                            background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 8px 32px rgba(91, 56, 160, 0.5)',
                            position: 'relative',
                            zIndex: 1
                        }}>
                            <Download size={64} color="#fff" />
                        </div>
                    </div>

                    {/* Info Section */}
                    <span style={{
                        fontSize: '11px',
                        fontWeight: 700,
                        textTransform: 'uppercase',
                        letterSpacing: '1.5px',
                        color: 'rgba(255,255,255,0.7)',
                        marginBottom: '8px'
                    }}>Playlist</span>

                    <h1 style={{
                        fontSize: '32px',
                        fontWeight: 800,
                        marginBottom: '12px',
                        lineHeight: 1
                    }}>Downloads</h1>

                    <p style={{
                        color: '#b3b3b3',
                        fontSize: '14px',
                        marginBottom: '12px'
                    }}>
                        Your offline collection • {downloads.length} song{downloads.length !== 1 ? 's' : ''}
                    </p>

                    {/* Storage Info - Horizontal Pills */}
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '12px',
                        fontSize: '13px',
                        color: '#b3b3b3'
                    }}>
                        <span style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            background: 'rgba(255,255,255,0.08)',
                            padding: '6px 12px',
                            borderRadius: '16px'
                        }}>
                            <WifiOff size={14} />
                            {offlineCount} offline
                        </span>
                        <span style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            background: 'rgba(255,255,255,0.08)',
                            padding: '6px 12px',
                            borderRadius: '16px'
                        }}>
                            <HardDrive size={14} />
                            {storageInfo.usedMB} MB
                        </span>
                    </div>
                </div>

                {/* Action Buttons - Centered */}
                {downloads.length > 0 && (
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '20px',
                        marginBottom: '32px'
                    }}>
                        <button
                            onClick={() => downloads.length > 0 && handlePlay(downloads[0])}
                            style={{
                                width: '56px',
                                height: '56px',
                                borderRadius: '50%',
                                backgroundColor: '#1db954',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                boxShadow: '0 8px 16px rgba(0,0,0,0.3)',
                                transition: 'all 0.2s',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                        >
                            <Play size={24} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                        </button>
                        <button
                            onClick={clearAllDownloads}
                            style={{
                                color: '#b3b3b3',
                                padding: '12px',
                                background: 'rgba(255,255,255,0.08)',
                                borderRadius: '50%',
                                border: 'none',
                                cursor: 'pointer'
                            }}
                            title="Clear all"
                        >
                            <Trash2 size={22} />
                        </button>
                    </div>
                )}

                {/* Empty State - Properly Centered */}
                {downloads.length === 0 ? (
                    <div style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        justifyContent: 'center',
                        padding: '48px 24px',
                        textAlign: 'center'
                    }}>
                        <div style={{
                            width: '100px',
                            height: '100px',
                            borderRadius: '50%',
                            backgroundColor: '#282828',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            marginBottom: '24px'
                        }}>
                            <Music size={40} color="#7f7f7f" />
                        </div>
                        <h2 style={{
                            fontSize: '22px',
                            fontWeight: 700,
                            marginBottom: '8px',
                            color: '#fff'
                        }}>No downloads yet</h2>
                        <p style={{
                            color: '#b3b3b3',
                            maxWidth: '280px',
                            fontSize: '14px',
                            lineHeight: 1.5
                        }}>
                            Songs you download will appear here and be available offline!
                        </p>
                    </div>
                ) : (
                    <>
                        {/* Table Header */}
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: '16px 4fr 2fr 1fr 64px',
                            gap: '16px',
                            padding: '8px 16px',
                            borderBottom: '1px solid rgba(255,255,255,0.1)',
                            color: '#b3b3b3',
                            fontSize: '14px',
                            textTransform: 'uppercase',
                            letterSpacing: '0.1em'
                        }}>
                            <span>#</span>
                            <span>Title</span>
                            <span>Album</span>
                            <span style={{ display: 'flex', alignItems: 'center' }}><Clock size={16} /></span>
                            <span></span>
                        </div>

                        {/* Songs */}
                        <div style={{ marginTop: '8px' }}>
                            {sortedDownloads.map((song, index) => {
                                const isThisSong = currentSong?.id === song.id;
                                return (
                                    <div
                                        key={`${song.id}-${index}`}
                                        onClick={() => handlePlay(song)}
                                        className="downloads-grid track-row"
                                        style={{
                                            padding: '8px 16px',
                                            borderRadius: '4px',
                                            cursor: 'pointer',
                                            transition: 'background 0.2s',
                                            alignItems: 'center'
                                        }}
                                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                                    >
                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: isThisSong ? '#1db954' : '#b3b3b3' }}>
                                            <span className="track-num">{index + 1}</span>
                                            {isThisSong && isPlaying ? (
                                                <Pause size={14} fill="#1db954" className="track-play" style={{ display: 'none' }} />
                                            ) : (
                                                <Play size={14} fill="#fff" className="track-play" style={{ display: 'none' }} />
                                            )}
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: 0 }}>
                                            <div style={{ position: 'relative', flexShrink: 0 }}>
                                                <img
                                                    src={song.image}
                                                    alt={song.title}
                                                    style={{ width: '40px', height: '40px', borderRadius: '4px', objectFit: 'cover' }}
                                                    onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                />
                                                {/* Offline indicator */}
                                                {song.isOffline && (
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
                                            <div style={{ minWidth: 0, overflow: 'hidden' }}>
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

                                        <div className="hide-on-mobile" style={{ display: 'flex', alignItems: 'center', color: '#b3b3b3', fontSize: '14px' }}>
                                            {song.album || 'Unknown'}
                                        </div>

                                        <div className="hide-on-mobile" style={{ display: 'flex', alignItems: 'center', color: '#b3b3b3', fontSize: '14px' }}>
                                            {formatDuration(song.duration)}
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); removeDownload(song.id); }}
                                                className="action-btn"
                                                style={{ opacity: 0, padding: '4px', color: '#b3b3b3', background: 'none', border: 'none' }}
                                                title="Remove"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </>
                )}
            </div>

            <style>{`
                @keyframes downloadShine {
                    0%, 100% { opacity: 0.5; transform: rotate(0deg); }
                    50% { opacity: 1; transform: rotate(180deg); }
                }
                .downloads-icon-wrapper::before {
                    content: '';
                    position: absolute;
                    inset: -3px;
                    background: linear-gradient(135deg, #3b82f6, #8b5cf6, #3b82f6, #8b5cf6);
                    background-size: 300% 300%;
                    border-radius: 14px;
                    z-index: 0;
                    animation: downloadShine 3s ease-in-out infinite;
                }
                .downloads-grid {
                    display: grid;
                    grid-template-columns: 16px 4fr 2fr 1fr 64px;
                    gap: 16px;
                }
                @media (max-width: 768px) {
                    .downloads-grid {
                        grid-template-columns: 24px 1fr 40px !important;
                        gap: 12px;
                    }
                    .hide-on-mobile {
                        display: none !important;
                    }
                }
                .track-row:hover .track-num { display: none; }
                .track-row:hover .track-play { display: block !important; }
                .track-row:hover .action-btn { opacity: 1 !important; }
                .action-btn:hover { color: #fff !important; }
            `}</style>
        </div>
    );
};

export default Downloads;
