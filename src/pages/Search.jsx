import React, { useState } from 'react';
import { Search as SearchIcon, X, Play, Download, Heart, Plus, Loader, Music2 } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { useOffline } from '../context/OfflineContext';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import axios from 'axios';
import { downloadSong } from '../utils/download';
import AddToPlaylistModal from '../components/AddToPlaylistModal';
import { BACKEND_URL } from '../config';

const Search = () => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(null);
    const [addToPlaylistSong, setAddToPlaylistSong] = useState(null);
    const [focused, setFocused] = useState(false);
    const { playSong, currentSong, isPlaying } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();

    const { isSongOffline } = useOffline();
    const { user, isAuthenticated } = useAuth();
    const { openProfileMenu } = useUI();

    // Derived user data
    const userInitials = isAuthenticated && Object.keys(user || {}).length > 0 && user.name ? user.name[0].toUpperCase() : 'V';
    const userImage = isAuthenticated && user?.image ? user.image : null;

    const categories = [
        { id: 1, title: 'Music', color: '#dc148c', icon: '🎵' },
        { id: 2, title: 'Podcasts', color: '#006450', icon: '🎙️' },
        { id: 3, title: 'Live Events', color: '#8400e7', icon: '🎪' },
        { id: 4, title: 'Made For You', color: '#1e3264', icon: '✨' },
        { id: 5, title: 'New Releases', color: '#e8115b', icon: '🆕' },
        { id: 6, title: 'Pop', color: '#148a08', icon: '🎤' },
        { id: 7, title: 'Hip-Hop', color: '#bc5900', icon: '🔥' },
        { id: 8, title: 'Rock', color: '#e91429', icon: '🎸' },
        { id: 9, title: 'Indie', color: '#7358ff', icon: '🌙' },
        { id: 10, title: 'Chill', color: '#503750', icon: '😌' },
        { id: 11, title: 'Party', color: '#0d73ec', icon: '🎉' },
        { id: 12, title: 'Workout', color: '#777777', icon: '💪' },
    ];

    const handleSearch = async (e) => {
        e.preventDefault();
        if (!query.trim()) return;

        setLoading(true);
        try {
            const response = await axios.get(
                `${BACKEND_URL}/itunes/search?q=${encodeURIComponent(query)}&limit=30`
            );

            if (response.data.success) {
                setResults(response.data.results);
            } else {
                console.error("Search failed:", response.data.error);
                setResults([]);
            }
        } catch (error) {
            console.error("Search failed:", error);
            setResults([]);
        } finally {
            setLoading(false);
        }
    };

    const formatDuration = (ms) => {
        if (!ms) return '0:00';
        const minutes = Math.floor(ms / 60000);
        const seconds = Math.floor((ms % 60000) / 1000);
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    };

    const handleDownload = async (e, song) => {
        e.stopPropagation();
        setDownloading(song.id);
        await downloadSong(song);
        setDownloading(null);
    };

    return (
        <div style={{ minHeight: '100%', backgroundColor: '#121212' }}>
            {/* Premium Search Header */}
            <div style={{
                position: 'sticky',
                top: 0,
                zIndex: 20,
                background: 'linear-gradient(180deg, #1a1a1a 0%, #121212 100%)',
                padding: '20px 24px 16px',
                borderBottom: focused ? '1px solid rgba(255,255,255,0.1)' : '1px solid transparent',
                transition: 'border-color 0.3s',
                display: 'flex',
                alignItems: 'center',
                gap: '16px'
            }}>
                {/* Profile Avatar */}
                <div
                    onClick={openProfileMenu}
                    style={{
                        width: '32px',
                        height: '32px',
                        borderRadius: '50%',
                        background: userImage ? `url(${userImage}) no-repeat center/cover` : 'linear-gradient(135deg, #ff6b35, #f7c59f)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '14px',
                        fontWeight: 700,
                        color: userImage ? 'transparent' : '#000',
                        cursor: 'pointer',
                        flexShrink: 0
                    }}
                >
                    {!userImage && userInitials}
                </div>

                <form onSubmit={handleSearch} style={{ position: 'relative', maxWidth: '500px', flex: 1 }}>
                    <div style={{
                        position: 'relative',
                        background: focused ? 'rgba(255,255,255,0.15)' : 'rgba(255,255,255,0.1)',
                        borderRadius: '12px',
                        border: focused ? '2px solid #1db954' : '2px solid transparent',
                        transition: 'all 0.2s ease',
                        overflow: 'hidden'
                    }}>
                        <SearchIcon
                            size={20}
                            style={{
                                position: 'absolute',
                                left: '16px',
                                top: '50%',
                                transform: 'translateY(-50%)',
                                color: focused ? '#1db954' : '#b3b3b3',
                                transition: 'color 0.2s'
                            }}
                        />
                        <input
                            type="text"
                            placeholder="Artists, songs, or albums"
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                            onFocus={() => setFocused(true)}
                            onBlur={() => setFocused(false)}
                            style={{
                                width: '100%',
                                padding: '14px 44px 14px 48px',
                                backgroundColor: 'transparent',
                                border: 'none',
                                fontSize: '15px',
                                fontWeight: 500,
                                color: '#fff',
                                outline: 'none'
                            }}
                        />
                        {query && (
                            <button
                                type="button"
                                onClick={() => { setQuery(''); setResults([]); }}
                                style={{
                                    position: 'absolute',
                                    right: '12px',
                                    top: '50%',
                                    transform: 'translateY(-50%)',
                                    background: 'rgba(255,255,255,0.2)',
                                    border: 'none',
                                    borderRadius: '50%',
                                    width: '24px',
                                    height: '24px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: '#fff',
                                    cursor: 'pointer',
                                    transition: 'background 0.2s'
                                }}
                            >
                                <X size={14} />
                            </button>
                        )}
                    </div>
                </form>
            </div>

            <div style={{ padding: '0 24px 140px' }}>
                {query ? (
                    loading ? (
                        <div style={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            padding: '80px 0'
                        }}>
                            <div style={{
                                width: '48px',
                                height: '48px',
                                borderRadius: '50%',
                                background: 'linear-gradient(45deg, #1db954, #1ed760)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                animation: 'pulse 1.5s ease-in-out infinite'
                            }}>
                                <Music2 size={24} color="#000" />
                            </div>
                            <p style={{ marginTop: '16px', color: '#b3b3b3', fontSize: '14px' }}>
                                Searching for "{query}"...
                            </p>
                        </div>
                    ) : results.length > 0 ? (
                        <div style={{ paddingTop: '16px' }}>
                            <h2 style={{
                                fontSize: '20px',
                                fontWeight: 700,
                                marginBottom: '20px',
                                color: '#fff',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px'
                            }}>
                                <Music2 size={20} style={{ color: '#1db954' }} />
                                Songs
                            </h2>

                            {/* Track List */}
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                {results.map((song, index) => {
                                    const isThisSong = currentSong?.id === song.id;
                                    const liked = isLiked(song.id);
                                    const isOffline = isSongOffline(song.id);

                                    return (
                                        <div
                                            key={song.id}
                                            onClick={() => playSong(song)}
                                            className="track-row"
                                            style={{
                                                display: 'grid',
                                                gridTemplateColumns: '50px 1fr auto',
                                                alignItems: 'center',
                                                gap: '12px',
                                                padding: '8px',
                                                borderRadius: '8px',
                                                cursor: 'pointer',
                                                transition: 'background 0.2s',
                                                backgroundColor: isThisSong ? 'rgba(29, 185, 84, 0.15)' : 'transparent'
                                            }}
                                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.08)'}
                                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = isThisSong ? 'rgba(29, 185, 84, 0.15)' : 'transparent'}
                                        >
                                            {/* Album Art */}
                                            <div style={{ position: 'relative' }}>
                                                <img
                                                    src={song.image}
                                                    alt={song.title}
                                                    style={{
                                                        width: '50px',
                                                        height: '50px',
                                                        borderRadius: '4px',
                                                        objectFit: 'cover'
                                                    }}
                                                    onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                />
                                                {isOffline && (
                                                    <div style={{
                                                        position: 'absolute',
                                                        bottom: '-2px',
                                                        right: '-2px',
                                                        width: '16px',
                                                        height: '16px',
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

                                            {/* Song Info - MUST show */}
                                            <div style={{ overflow: 'hidden' }}>
                                                <p style={{
                                                    color: isThisSong ? '#1db954' : '#fff',
                                                    fontSize: '15px',
                                                    fontWeight: 500,
                                                    margin: 0,
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>
                                                    {song.title || 'Unknown Title'}
                                                </p>
                                                <p style={{
                                                    color: '#b3b3b3',
                                                    fontSize: '13px',
                                                    margin: '2px 0 0 0',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>
                                                    {song.artist || 'Unknown Artist'}
                                                </p>
                                            </div>

                                            {/* Duration & Actions */}
                                            <div style={{
                                                display: 'flex',
                                                alignItems: 'center',
                                                gap: '4px',
                                                flexShrink: 0
                                            }}>
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); toggleLike(song); }}
                                                    style={{
                                                        padding: '6px',
                                                        color: liked ? '#1db954' : '#666',
                                                        background: 'none',
                                                        border: 'none',
                                                        cursor: 'pointer',
                                                        display: 'flex'
                                                    }}
                                                >
                                                    <Heart size={16} fill={liked ? '#1db954' : 'none'} />
                                                </button>
                                                <button
                                                    onClick={(e) => handleDownload(e, song)}
                                                    style={{
                                                        padding: '6px',
                                                        color: '#666',
                                                        background: 'none',
                                                        border: 'none',
                                                        cursor: 'pointer',
                                                        display: 'flex'
                                                    }}
                                                >
                                                    {downloading === song.id ? (
                                                        <Loader size={16} className="animate-spin" />
                                                    ) : (
                                                        <Download size={16} />
                                                    )}
                                                </button>
                                                <span style={{
                                                    color: '#b3b3b3',
                                                    fontSize: '14px',
                                                    fontWeight: 500,
                                                    minWidth: '42px',
                                                    textAlign: 'right',
                                                    fontVariantNumeric: 'tabular-nums'
                                                }}>
                                                    {formatDuration(song.duration)}
                                                </span>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    ) : (
                        <div style={{ textAlign: 'center', padding: '80px 20px' }}>
                            <div style={{
                                width: '80px',
                                height: '80px',
                                borderRadius: '50%',
                                background: 'rgba(255,255,255,0.05)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                margin: '0 auto 20px'
                            }}>
                                <SearchIcon size={32} color="#b3b3b3" />
                            </div>
                            <h3 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '8px', color: '#fff' }}>
                                No results found
                            </h3>
                            <p style={{ color: '#b3b3b3', fontSize: '14px' }}>
                                Try different keywords or check your spelling
                            </p>
                        </div>
                    )
                ) : (
                    <div style={{ paddingTop: '16px' }}>
                        <h2 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '20px', color: '#fff' }}>
                            Browse all
                        </h2>
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                            gap: '16px'
                        }}>
                            {categories.map((cat) => (
                                <div
                                    key={cat.id}
                                    style={{
                                        position: 'relative',
                                        height: '100px',
                                        borderRadius: '8px',
                                        overflow: 'hidden',
                                        cursor: 'pointer',
                                        backgroundColor: cat.color,
                                        transition: 'transform 0.2s, box-shadow 0.2s',
                                        boxShadow: '0 4px 12px rgba(0,0,0,0.2)'
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.transform = 'scale(1.02)';
                                        e.currentTarget.style.boxShadow = '0 8px 24px rgba(0,0,0,0.3)';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.transform = 'scale(1)';
                                        e.currentTarget.style.boxShadow = '0 4px 12px rgba(0,0,0,0.2)';
                                    }}
                                >
                                    <span style={{
                                        position: 'absolute',
                                        top: '12px',
                                        left: '12px',
                                        fontSize: '15px',
                                        fontWeight: 700,
                                        color: '#fff'
                                    }}>{cat.title}</span>
                                    <span style={{
                                        position: 'absolute',
                                        bottom: '8px',
                                        right: '8px',
                                        fontSize: '28px',
                                        opacity: 0.9
                                    }}>{cat.icon}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            {/* Add to Playlist Modal */}
            <AddToPlaylistModal
                isOpen={!!addToPlaylistSong}
                onClose={() => setAddToPlaylistSong(null)}
                song={addToPlaylistSong}
            />

            <style>{`
                @keyframes pulse {
                    0%, 100% { transform: scale(1); opacity: 1; }
                    50% { transform: scale(1.1); opacity: 0.8; }
                }
                @keyframes eq {
                    0%, 100% { transform: scaleY(1); }
                    50% { transform: scaleY(0.5); }
                }
                .track-row:hover .track-num { display: none; }
                .track-row:hover .track-play { display: block !important; }
                .track-row:hover .action-btn { opacity: 1 !important; }
                .action-btn:hover { 
                    color: #fff !important; 
                    background: rgba(255,255,255,0.1) !important;
                }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                .animate-spin { animation: spin 1s linear infinite; }
            `}</style>
        </div>
    );
};

export default Search;
