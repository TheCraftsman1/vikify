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

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '120px' }} className="page-content">
                {/* Header */}
                <div className="playlist-header" style={{ display: 'flex', alignItems: 'flex-end', gap: '24px', marginBottom: '24px', paddingTop: '32px', flexWrap: 'wrap' }}>
                    <div className="playlist-header-image" style={{
                        width: '200px',
                        height: '200px',
                        borderRadius: '8px',
                        background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        boxShadow: '0 4px 60px rgba(0,0,0,0.5)',
                        flexShrink: 0
                    }}>
                        <Download size={64} color="#fff" />
                    </div>
                    <div style={{ flex: 1, minWidth: '200px' }}>
                        <span style={{ fontSize: '12px', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em' }}>Playlist</span>
                        <h1 style={{
                            fontSize: 'clamp(32px, 8vw, 72px)',
                            fontWeight: 900,
                            marginTop: '8px',
                            marginBottom: '12px',
                            lineHeight: 1
                        }}>Downloads</h1>
                        <p style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '8px' }}>
                            Your offline collection • {downloads.length} song{downloads.length !== 1 ? 's' : ''}
                        </p>
                        {/* Storage Info */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', fontSize: '13px', color: '#b3b3b3', flexWrap: 'wrap' }}>
                            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                <WifiOff size={14} />
                                {offlineCount} offline
                            </span>
                            <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                                <HardDrive size={14} />
                                {storageInfo.usedMB} MB
                            </span>
                        </div>
                    </div>
                </div>

                {/* Play Button */}
                {downloads.length > 0 && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '24px', marginBottom: '24px' }}>
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
                                transition: 'all 0.2s'
                            }}
                            onMouseEnter={(e) => { e.currentTarget.style.transform = 'scale(1.04)'; e.currentTarget.style.backgroundColor = '#1ed760'; }}
                            onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; e.currentTarget.style.backgroundColor = '#1db954'; }}
                        >
                            <Play size={24} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                        </button>
                        <button
                            onClick={clearAllDownloads}
                            style={{ color: '#b3b3b3', padding: '8px' }}
                            onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                            onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                            title="Clear all"
                        >
                            <Trash2 size={24} />
                        </button>
                    </div>
                )}

                {/* Content */}
                {downloads.length === 0 ? (
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
                            <Music size={48} color="#7f7f7f" />
                        </div>
                        <h2 style={{ fontSize: '32px', fontWeight: 700, marginBottom: '8px' }}>No downloads yet</h2>
                        <p style={{ color: '#b3b3b3', maxWidth: '400px' }}>
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
                                        style={{
                                            display: 'grid',
                                            gridTemplateColumns: '16px 4fr 2fr 1fr 64px',
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
                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: isThisSong ? '#1db954' : '#b3b3b3' }}>
                                            <span className="track-num">{index + 1}</span>
                                            {isThisSong && isPlaying ? (
                                                <Pause size={14} fill="#1db954" className="track-play" style={{ display: 'none' }} />
                                            ) : (
                                                <Play size={14} fill="#fff" className="track-play" style={{ display: 'none' }} />
                                            )}
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: 0 }}>
                                            <div style={{ position: 'relative' }}>
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

                                        <div style={{ display: 'flex', alignItems: 'center', color: '#b3b3b3', fontSize: '14px' }}>
                                            {song.album || 'Unknown'}
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', color: '#b3b3b3', fontSize: '14px' }}>
                                            {formatDuration(song.duration)}
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); removeDownload(song.id); }}
                                                className="action-btn"
                                                style={{ opacity: 0, padding: '4px', color: '#b3b3b3' }}
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
                .track-row:hover .track-num { display: none; }
                .track-row:hover .track-play { display: block !important; }
                .track-row:hover .action-btn { opacity: 1 !important; }
                .action-btn:hover { color: #fff !important; }
            `}</style>
        </div>
    );
};

export default Downloads;
