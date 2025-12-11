import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Play, Search, Grid, List, Plus, Heart, Download, Pause, Music, ArrowUpDown, Settings, Clock, Bell, UserPlus, X } from 'lucide-react';
import { albums } from '../data/songs';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { usePlaylists } from '../context/PlaylistContext';
import { useRef } from 'react';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import CreatePlaylistModal from '../components/CreatePlaylistModal';
import { getUserPlaylists } from '../services/spotify';

const Library = () => {
    const navigate = useNavigate();
    const { playSong, currentSong, isPlaying, togglePlay } = usePlayer();
    const { likedCount, getLikedSongs } = useLikedSongs();
    const { playlists } = usePlaylists();
    const { isAuthenticated, accessToken, loading, user } = useAuth();
    const { openProfileMenu } = useUI();
    const [userPlaylists, setUserPlaylists] = useState([]);

    // Fetch user playlists on mount if authenticated
    useEffect(() => {
        const fetchPlaylists = async () => {
            if (isAuthenticated && accessToken) {
                const data = await getUserPlaylists(accessToken);
                setUserPlaylists(data);
            }
        };
        fetchPlaylists();
    }, [isAuthenticated, accessToken]);

    // Default to list view on mobile
    const isMobile = window.innerWidth <= 768;
    const [viewMode, setViewMode] = useState(isMobile ? 'list' : 'grid');
    const [filter, setFilter] = useState('all');
    const [searchQuery, setSearchQuery] = useState('');
    const [showSearch, setShowSearch] = useState(false);
    const [showCreateModal, setShowCreateModal] = useState(false);

    // Derived user data
    const userInitials = isAuthenticated && Object.keys(user || {}).length > 0 && user.name ? user.name[0].toUpperCase() : 'V';
    const userImage = isAuthenticated && user?.image ? user.image : null;
    const userName = isAuthenticated && user?.name ? user.name : 'Viky User';

    // Update view mode on resize
    useEffect(() => {
        const handleResize = () => {
            if (window.innerWidth <= 768) {
                setViewMode('list');
            }
        };
        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    const likedSongs = getLikedSongs();

    const fixedPlaylists = [
        {
            id: 'liked',
            title: 'Liked Songs',
            type: 'playlist',
            description: `${likedCount} songs`,
            gradient: 'linear-gradient(135deg, #450af5, #c4efd9)',
            songs: likedSongs
        },
        {
            id: 'downloads',
            title: 'Downloads',
            type: 'playlist',
            description: 'Offline songs',
            gradient: 'linear-gradient(135deg, #3b82f6, #8b5cf6)'
        }
    ];

    // Combine fixed playlists, custom playlists, imported spotify playlists, and albums
    const allItems = [
        ...fixedPlaylists,
        ...playlists.map(p => ({ ...p, type: 'custom-playlist' })),
        ...userPlaylists,
        ...albums.map(a => ({ ...a, type: 'playlist' }))
    ];

    const filteredItems = allItems.filter(item => {
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            return item.title.toLowerCase().includes(q) || (item.artist && item.artist.toLowerCase().includes(q));
        }
        return true;
    });

    const handleItemClick = (item) => {
        if (item.id === 'liked') {
            navigate('/liked');
        } else if (item.id === 'downloads') {
            navigate('/downloads');
        } else if (item.type === 'custom-playlist') {
            navigate(`/playlist/${item.id}`);
        } else {
            navigate(`/playlist/${item.id}`);
        }
    };

    const handlePlayFirstSong = (e, playlist) => {
        e.stopPropagation();
        const isThisPlaying = currentSong && playlist.songs?.some(s => s.id === currentSong.id);
        if (isThisPlaying) {
            togglePlay();
        } else if (playlist.songs?.length > 0) {
            playSong(playlist.songs[0]);
        }
    };

    const FilterChip = ({ label, value }) => (
        <button
            onClick={() => setFilter(value)}
            style={{
                padding: '6px 12px',
                borderRadius: '16px',
                fontSize: '14px',
                fontWeight: 500,
                backgroundColor: filter === value ? '#fff' : 'rgba(255,255,255,0.07)',
                color: filter === value ? '#000' : '#fff',
                cursor: 'pointer',
                transition: 'all 0.2s',
                whiteSpace: 'nowrap'
            }}
            onMouseEnter={(e) => filter !== value && (e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)')}
            onMouseLeave={(e) => filter !== value && (e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.07)')}
        >
            {label}
        </button>
    );

    return (
        <div className="library-page" style={{ minHeight: '100%', backgroundColor: '#121212' }}>
            {/* Header */}
            <div className="library-header" style={{ position: 'sticky', top: 0, zIndex: 20, backgroundColor: '#121212', padding: '16px' }}>
                {/* Top row: Avatar + Title + Search + Plus */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
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
                            cursor: 'pointer'
                        }}
                    >
                        {!userImage && userInitials}
                    </div>

                    <h1 style={{ flex: 1, fontSize: '22px', fontWeight: 700 }}>Your Library</h1>

                    <button
                        onClick={() => setShowSearch(!showSearch)}
                        style={{ padding: '8px', color: '#fff' }}
                    >
                        <Search size={22} />
                    </button>

                    <button
                        onClick={() => setShowCreateModal(true)}
                        style={{ padding: '8px', color: '#fff' }}
                    >
                        <Plus size={22} />
                    </button>
                </div>

                {/* Filter Chips */}
                <div className="filter-chips" style={{
                    display: 'flex',
                    gap: '8px',
                    marginBottom: '16px',
                    overflowX: 'auto',
                    paddingBottom: '4px'
                }}>
                    <FilterChip label="Playlists" value="all" />
                    <FilterChip label="Podcasts" value="podcasts" />
                    <FilterChip label="Albums" value="albums" />
                    <FilterChip label="Downloaded" value="downloaded" />
                </div>

                {/* Sort Row - Recents + View Toggle (hide toggle on mobile) */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <button
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            color: '#fff',
                            fontSize: '13px',
                            fontWeight: 500
                        }}
                    >
                        <ArrowUpDown size={14} />
                        <span>Recents</span>
                    </button>

                    {/* View mode toggle - desktop only */}
                    <div className="view-toggle hide-on-mobile" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <button
                            onClick={() => setViewMode('grid')}
                            style={{ color: viewMode === 'grid' ? '#fff' : '#b3b3b3', padding: '4px' }}
                        >
                            <Grid size={18} />
                        </button>
                        <button
                            onClick={() => setViewMode('list')}
                            style={{ color: viewMode === 'list' ? '#fff' : '#b3b3b3', padding: '4px' }}
                        >
                            <List size={18} />
                        </button>
                    </div>
                </div>
            </div>

            {/* Content */}
            <div className="library-content" style={{ padding: '0 16px 160px' }}>
                {viewMode === 'grid' ? (
                    <div style={{
                        display: 'grid',
                        gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                        gap: '24px'
                    }}>
                        {filteredItems.map((item) => {
                            const isThisPlaying = currentSong && item.songs?.some(s => s.id === currentSong.id);
                            return (
                                <div
                                    key={item.id}
                                    onClick={() => handleItemClick(item)}
                                    style={{
                                        padding: '16px',
                                        backgroundColor: '#181818',
                                        borderRadius: '8px',
                                        cursor: 'pointer',
                                        transition: 'background 0.3s',
                                        position: 'relative'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.backgroundColor = '#282828'}
                                    onMouseLeave={(e) => e.currentTarget.style.backgroundColor = '#181818'}
                                    className="card-hover"
                                >
                                    <div style={{
                                        position: 'relative',
                                        width: '100%',
                                        aspectRatio: '1',
                                        marginBottom: '16px',
                                        borderRadius: '4px',
                                        overflow: 'hidden',
                                        boxShadow: '0 8px 24px rgba(0,0,0,0.5)'
                                    }}>
                                        {item.gradient ? (
                                            <div style={{
                                                width: '100%',
                                                height: '100%',
                                                background: item.gradient,
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center'
                                            }}>
                                                {item.id === 'liked' ? <Heart size={48} fill="#fff" color="#fff" /> : <Download size={48} color="#fff" />}
                                            </div>
                                        ) : item.image ? (
                                            <img
                                                src={item.image}
                                                alt={item.title}
                                                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                            />
                                        ) : (
                                            <div style={{
                                                width: '100%',
                                                height: '100%',
                                                background: 'linear-gradient(135deg, #333, #555)',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center'
                                            }}>
                                                <Music size={48} color="#7f7f7f" />
                                            </div>
                                        )}
                                        {item.songs && item.songs.length > 0 && (
                                            <button
                                                onClick={(e) => handlePlayFirstSong(e, item)}
                                                className="card-play-btn"
                                                style={{
                                                    position: 'absolute',
                                                    bottom: '8px',
                                                    right: '8px',
                                                    width: '48px',
                                                    height: '48px',
                                                    borderRadius: '50%',
                                                    backgroundColor: '#1db954',
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    boxShadow: '0 8px 16px rgba(0,0,0,0.3)',
                                                    opacity: 0,
                                                    transform: 'translateY(8px)',
                                                    transition: 'all 0.3s ease'
                                                }}
                                            >
                                                {isThisPlaying && isPlaying ? (
                                                    <Pause size={22} fill="#000" color="#000" />
                                                ) : (
                                                    <Play size={22} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                                                )}
                                            </button>
                                        )}
                                    </div>
                                    <h3 style={{ fontWeight: 700, fontSize: '16px', marginBottom: '4px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                                        {item.title}
                                    </h3>
                                    <p style={{ fontSize: '14px', color: '#b3b3b3' }}>
                                        {item.description || (item.songs ? `${item.songs.length} songs` : `By ${item.artist}`)}
                                    </p>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <div>
                        {filteredItems.map((item) => (
                            <div
                                key={item.id}
                                onClick={() => handleItemClick(item)}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px',
                                    padding: '8px',
                                    borderRadius: '4px',
                                    cursor: 'pointer',
                                    transition: 'background 0.2s'
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                            >
                                <div style={{
                                    width: '48px',
                                    height: '48px',
                                    borderRadius: '4px',
                                    overflow: 'hidden',
                                    flexShrink: 0
                                }}>
                                    {item.gradient ? (
                                        <div style={{
                                            width: '100%',
                                            height: '100%',
                                            background: item.gradient,
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center'
                                        }}>
                                            {item.id === 'liked' ? <Heart size={20} fill="#fff" color="#fff" /> : <Download size={20} color="#fff" />}
                                        </div>
                                    ) : item.image ? (
                                        <img
                                            src={item.image}
                                            alt={item.title}
                                            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                        />
                                    ) : (
                                        <div style={{
                                            width: '100%',
                                            height: '100%',
                                            background: 'linear-gradient(135deg, #333, #555)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center'
                                        }}>
                                            <Music size={20} color="#7f7f7f" />
                                        </div>
                                    )}
                                </div>
                                <div>
                                    <div style={{ fontWeight: 500, fontSize: '15px' }}>{item.title}</div>
                                    <div style={{ color: '#b3b3b3', fontSize: '13px' }}>
                                        {item.type === 'custom-playlist' ? 'Playlist' : 'Playlist'} • {item.songs?.length || 0} songs
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* Create Playlist Modal */}
            <CreatePlaylistModal
                isOpen={showCreateModal}
                onClose={() => setShowCreateModal(false)}
            />

            <style>{`
                .card-hover:hover .card-play-btn {
                    opacity: 1 !important;
                    transform: translateY(0) !important;
                }
                .card-play-btn:hover {
                    transform: scale(1.04) !important;
                    background-color: #1ed760 !important;
                }
            `}</style>
        </div>
    );
};

export default Library;
