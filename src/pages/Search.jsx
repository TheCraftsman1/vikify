import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search as SearchIcon, X, Play, Download, Heart, Plus, Loader, Music2, Disc, Users, ListMusic, Pause } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { useOffline } from '../context/OfflineContext';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import axios from 'axios';
import { downloadSong } from '../utils/download';
import AddToPlaylistModal from '../components/AddToPlaylistModal';
import { BACKEND_URL } from '../config';
import { searchSpotify } from '../services/spotify';

const Search = () => {
    const [debugUrl, setDebugUrl] = useState('');
    useEffect(() => {
        import('@capacitor/app').then(({ App }) => App.addListener('appUrlOpen', d => setDebugUrl(d.url)));
    }, []);
    const navigate = useNavigate();
    const [query, setQuery] = useState('');
    const [results, setResults] = useState({ songs: [], playlists: [], artists: [], albums: [] });
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(null);
    const [addToPlaylistSong, setAddToPlaylistSong] = useState(null);
    const [focused, setFocused] = useState(false);
    const [activeTab, setActiveTab] = useState('all');
    const searchTimeoutRef = useRef(null);
    const abortControllerRef = useRef(null);

    const { playSong, currentSong, isPlaying, togglePlay } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();
    const { isSongOffline } = useOffline();
    const { user, isAuthenticated } = useAuth();
    const { openProfileMenu } = useUI();

    // User data
    const userInitials = isAuthenticated && user?.name ? user.name[0].toUpperCase() : 'V';
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

    // Debounced live search
    const performSearch = useCallback(async (searchQuery) => {
        if (!searchQuery.trim()) {
            setResults({ songs: [], playlists: [], artists: [], albums: [] });
            setLoading(false);
            return;
        }

        // Cancel previous request
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }
        abortControllerRef.current = new AbortController();

        setLoading(true);
        try {
            // Parallel search: iTunes for songs + Spotify for playlists/artists/albums
            const [itunesRes, spotifyRes] = await Promise.allSettled([
                axios.get(
                    `${BACKEND_URL}/itunes/search?q=${encodeURIComponent(searchQuery)}&limit=20`,
                    { signal: abortControllerRef.current.signal }
                ),
                searchSpotify(searchQuery).catch(() => null)
            ]);

            const songs = itunesRes.status === 'fulfilled' && itunesRes.value?.data?.success
                ? itunesRes.value.data.results
                : [];

            const spotify = spotifyRes.status === 'fulfilled' && spotifyRes.value
                ? spotifyRes.value
                : { playlists: [], artists: [], albums: [] };

            setResults({
                songs,
                playlists: spotify.playlists || [],
                artists: spotify.artists || [],
                albums: spotify.albums || []
            });
        } catch (error) {
            if (error.name !== 'AbortError') {
                console.error("Search failed:", error);
            }
        } finally {
            setLoading(false);
        }
    }, []);

    // Live search effect with debounce
    useEffect(() => {
        if (searchTimeoutRef.current) {
            clearTimeout(searchTimeoutRef.current);
        }

        if (query.trim()) {
            setLoading(true);
            searchTimeoutRef.current = setTimeout(() => {
                performSearch(query);
            }, 300); // 300ms debounce for fast typing
        } else {
            setResults({ songs: [], playlists: [], artists: [], albums: [] });
            setLoading(false);
        }

        return () => {
            if (searchTimeoutRef.current) {
                clearTimeout(searchTimeoutRef.current);
            }
        };
    }, [query, performSearch]);

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

    const handlePlaySong = (song) => {
        console.log('[Search] handlePlaySong called with:', song?.title, song?.id);
        if (currentSong?.id === song.id) {
            togglePlay();
        } else {
            playSong(song, results.songs);
        }
    };

    const hasResults = results.songs.length > 0 || results.playlists.length > 0 ||
        results.artists.length > 0 || results.albums.length > 0;

    const tabs = [
        { id: 'all', label: 'All' },
        { id: 'songs', label: 'Songs', count: results.songs.length },
        { id: 'playlists', label: 'Playlists', count: results.playlists.length },
        { id: 'artists', label: 'Artists', count: results.artists.length },
        { id: 'albums', label: 'Albums', count: results.albums.length },
    ];

    return (
        <div className="search-page" style={{ minHeight: '100%', backgroundColor: '#121212' }}>
            <div style={{ position: 'fixed', top: 50, left: 0, right: 0, zIndex: 9999, background: 'rgba(0,0,0,0.9)', color: '#0f0', fontSize: '12px', padding: '8px', pointerEvents: 'none' }}>
                URL: {debugUrl || 'Waiting...'} <br />
                Auth: {isAuthenticated ? 'YES' : 'NO'} | User: {user ? user.name : 'None'}
            </div>
            {/* Premium Search Header */}
            <div className="search-header" style={{
                position: 'sticky',
                top: 0,
                zIndex: 20,
                background: 'linear-gradient(180deg, #1a1a1a 0%, #121212 100%)',
                padding: '20px 24px 12px',
                borderBottom: focused ? '1px solid rgba(255,255,255,0.1)' : '1px solid transparent',
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    {/* Profile Avatar */}
                    <div
                        onClick={openProfileMenu}
                        className="profile-avatar"
                        style={{
                            width: '36px',
                            height: '36px',
                            borderRadius: '50%',
                            background: userImage ? `url(${userImage}) no-repeat center/cover` : 'linear-gradient(135deg, #1db954, #191414)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '14px',
                            fontWeight: 700,
                            color: userImage ? 'transparent' : '#fff',
                            cursor: 'pointer',
                            flexShrink: 0,
                            boxShadow: '0 2px 8px rgba(0,0,0,0.3)'
                        }}
                    >
                        {!userImage && userInitials}
                    </div>

                    {/* Search Input */}
                    <div className="search-input-wrapper" style={{
                        position: 'relative',
                        maxWidth: '520px',
                        flex: 1
                    }}>
                        <div className="search-input-container" style={{
                            position: 'relative',
                            background: focused ? 'rgba(255,255,255,0.15)' : 'rgba(255,255,255,0.1)',
                            borderRadius: '24px',
                            border: focused ? '2px solid #1db954' : '2px solid transparent',
                            transition: 'all 0.2s ease',
                            overflow: 'hidden',
                            boxShadow: focused ? '0 0 20px rgba(29, 185, 84, 0.2)' : 'none'
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
                                placeholder="What do you want to play?"
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
                                    onClick={() => { setQuery(''); setResults({ songs: [], playlists: [], artists: [], albums: [] }); }}
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
                                        cursor: 'pointer'
                                    }}
                                >
                                    <X size={14} />
                                </button>
                            )}
                            {loading && (
                                <div style={{
                                    position: 'absolute',
                                    right: query ? '44px' : '12px',
                                    top: '50%',
                                    transform: 'translateY(-50%)'
                                }}>
                                    <Loader size={18} className="animate-spin" color="#1db954" />
                                </div>
                            )}
                        </div>
                    </div>
                </div>

                {/* Filter Tabs - Show when there are results */}
                {query && hasResults && (
                    <div className="search-tabs" style={{
                        display: 'flex',
                        gap: '8px',
                        marginTop: '16px',
                        overflowX: 'auto',
                        paddingBottom: '4px'
                    }}>
                        {tabs.map(tab => (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id)}
                                className="search-tab"
                                style={{
                                    padding: '8px 16px',
                                    borderRadius: '20px',
                                    fontSize: '13px',
                                    fontWeight: 600,
                                    border: 'none',
                                    cursor: 'pointer',
                                    whiteSpace: 'nowrap',
                                    transition: 'all 0.2s',
                                    background: activeTab === tab.id ? '#fff' : 'rgba(255,255,255,0.07)',
                                    color: activeTab === tab.id ? '#000' : '#fff'
                                }}
                            >
                                {tab.label}
                            </button>
                        ))}
                    </div>
                )}
            </div>

            <div style={{ padding: '0 24px 160px' }}>
                {query ? (
                    loading && !hasResults ? (
                        <div style={{
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            padding: '80px 0'
                        }}>
                            <div className="search-loading-icon" style={{
                                width: '56px',
                                height: '56px',
                                borderRadius: '50%',
                                background: 'linear-gradient(135deg, #1db954, #1ed760)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                animation: 'pulse 1.5s ease-in-out infinite',
                                boxShadow: '0 0 30px rgba(29, 185, 84, 0.4)'
                            }}>
                                <Music2 size={28} color="#000" />
                            </div>
                            <p style={{ marginTop: '16px', color: '#b3b3b3', fontSize: '14px' }}>
                                Searching for "{query}"...
                            </p>
                        </div>
                    ) : hasResults ? (
                        <div style={{ paddingTop: '20px' }}>
                            {/* Top Result + Songs Row (Spotify-like layout) */}
                            {(activeTab === 'all' || activeTab === 'songs') && results.songs.length > 0 && (
                                <div className="search-section songs-section" style={{
                                    marginBottom: '32px',
                                    position: 'relative',
                                    zIndex: 10,
                                    isolation: 'isolate'
                                }}>
                                    <h2 className="section-title" style={{
                                        fontSize: '22px',
                                        fontWeight: 700,
                                        marginBottom: '16px',
                                        color: '#fff',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '8px'
                                    }}>
                                        <Music2 size={22} style={{ color: '#1db954' }} />
                                        Songs
                                    </h2>

                                    <div className="songs-list" style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                                        {(activeTab === 'all' ? results.songs.slice(0, 6) : results.songs).map((song) => {
                                            const isThisSong = currentSong?.id === song.id;
                                            const liked = isLiked(song.id);
                                            const isOffline = isSongOffline(song.id);

                                            return (
                                                <div
                                                    key={song.id}
                                                    onClick={() => handlePlaySong(song)}
                                                    className="track-row song-item"
                                                    style={{
                                                        display: 'grid',
                                                        gridTemplateColumns: '56px 1fr auto',
                                                        alignItems: 'center',
                                                        gap: '12px',
                                                        padding: '8px 12px',
                                                        borderRadius: '8px',
                                                        cursor: 'pointer',
                                                        transition: 'all 0.15s',
                                                        backgroundColor: isThisSong ? 'rgba(29, 185, 84, 0.15)' : 'transparent',
                                                        border: '1px solid transparent'
                                                    }}
                                                >
                                                    {/* Album Art */}
                                                    <div className="song-image" style={{ position: 'relative' }}>
                                                        <img
                                                            src={song.image}
                                                            alt={song.title}
                                                            style={{
                                                                width: '56px',
                                                                height: '56px',
                                                                borderRadius: '6px',
                                                                objectFit: 'cover',
                                                                boxShadow: '0 4px 12px rgba(0,0,0,0.3)'
                                                            }}
                                                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                        />
                                                        <div className="play-overlay" style={{
                                                            position: 'absolute',
                                                            inset: 0,
                                                            borderRadius: '6px',
                                                            background: 'rgba(0,0,0,0.5)',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            justifyContent: 'center',
                                                            opacity: 0,
                                                            transition: 'opacity 0.2s'
                                                        }}>
                                                            {isThisSong && isPlaying ? (
                                                                <Pause size={20} fill="#fff" color="#fff" />
                                                            ) : (
                                                                <Play size={20} fill="#fff" color="#fff" style={{ marginLeft: '2px' }} />
                                                            )}
                                                        </div>
                                                        {isOffline && (
                                                            <div style={{
                                                                position: 'absolute',
                                                                bottom: '-2px',
                                                                right: '-2px',
                                                                width: '18px',
                                                                height: '18px',
                                                                borderRadius: '50%',
                                                                backgroundColor: '#1db954',
                                                                display: 'flex',
                                                                alignItems: 'center',
                                                                justifyContent: 'center',
                                                                border: '2px solid #121212'
                                                            }}>
                                                                <Download size={9} color="#000" />
                                                            </div>
                                                        )}
                                                    </div>

                                                    {/* Song Info */}
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
                                                            color: '#a0a0a0',
                                                            fontSize: '13px',
                                                            margin: '4px 0 0 0',
                                                            whiteSpace: 'nowrap',
                                                            overflow: 'hidden',
                                                            textOverflow: 'ellipsis'
                                                        }}>
                                                            {song.artist || 'Unknown Artist'}
                                                        </p>
                                                    </div>

                                                    {/* Actions */}
                                                    <div style={{
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '6px',
                                                        flexShrink: 0
                                                    }}>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); toggleLike(song); }}
                                                            className="action-btn"
                                                            style={{
                                                                padding: '8px',
                                                                color: liked ? '#1db954' : '#666',
                                                                background: 'none',
                                                                border: 'none',
                                                                cursor: 'pointer',
                                                                display: 'flex',
                                                                borderRadius: '50%',
                                                                transition: 'all 0.2s'
                                                            }}
                                                        >
                                                            <Heart size={18} fill={liked ? '#1db954' : 'none'} />
                                                        </button>
                                                        <button
                                                            onClick={(e) => { e.stopPropagation(); setAddToPlaylistSong(song); }}
                                                            className="action-btn"
                                                            style={{
                                                                padding: '8px',
                                                                color: '#666',
                                                                background: 'none',
                                                                border: 'none',
                                                                cursor: 'pointer',
                                                                display: 'flex',
                                                                borderRadius: '50%',
                                                                transition: 'all 0.2s'
                                                            }}
                                                        >
                                                            <Plus size={18} />
                                                        </button>
                                                        <button
                                                            onClick={(e) => handleDownload(e, song)}
                                                            className="action-btn"
                                                            style={{
                                                                padding: '8px',
                                                                color: '#666',
                                                                background: 'none',
                                                                border: 'none',
                                                                cursor: 'pointer',
                                                                display: 'flex',
                                                                borderRadius: '50%',
                                                                transition: 'all 0.2s'
                                                            }}
                                                        >
                                                            {downloading === song.id ? (
                                                                <Loader size={18} className="animate-spin" />
                                                            ) : (
                                                                <Download size={18} />
                                                            )}
                                                        </button>
                                                        <span style={{
                                                            color: '#888',
                                                            fontSize: '13px',
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
                            )}

                            {/* Playlists Section */}
                            {(activeTab === 'all' || activeTab === 'playlists') && results.playlists.length > 0 && (
                                <div className="search-section" style={{ marginBottom: '32px' }}>
                                    <h2 className="section-title" style={{
                                        fontSize: '22px',
                                        fontWeight: 700,
                                        marginBottom: '16px',
                                        color: '#fff',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '8px'
                                    }}>
                                        <ListMusic size={22} style={{ color: '#1db954' }} />
                                        Playlists
                                    </h2>
                                    <div className="playlist-grid" style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                                        gap: '20px'
                                    }}>
                                        {(activeTab === 'all' ? results.playlists.slice(0, 6) : results.playlists).map(playlist => (
                                            <div
                                                key={`playlist-${playlist.id}`}
                                                onClick={() => navigate(`/playlist/${playlist.id}`)}
                                                className="playlist-card shine-card"
                                                style={{
                                                    background: 'rgba(255,255,255,0.05)',
                                                    borderRadius: '12px',
                                                    padding: '16px',
                                                    cursor: 'pointer',
                                                    transition: 'all 0.25s',
                                                    position: 'relative',
                                                    overflow: 'hidden'
                                                }}
                                            >
                                                <div style={{
                                                    width: '100%',
                                                    aspectRatio: '1',
                                                    borderRadius: '8px',
                                                    overflow: 'hidden',
                                                    marginBottom: '12px',
                                                    boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
                                                    position: 'relative'
                                                }}>
                                                    <img
                                                        src={playlist.image || '/placeholder.svg'}
                                                        alt={playlist.name}
                                                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                    />
                                                    <div className="card-play-btn" style={{
                                                        position: 'absolute',
                                                        bottom: '8px',
                                                        right: '8px',
                                                        width: '44px',
                                                        height: '44px',
                                                        borderRadius: '50%',
                                                        background: '#1db954',
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        opacity: 0,
                                                        transform: 'translateY(8px)',
                                                        transition: 'all 0.2s',
                                                        boxShadow: '0 4px 16px rgba(0,0,0,0.4)'
                                                    }}>
                                                        <Play size={20} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                                                    </div>
                                                </div>
                                                <p style={{
                                                    color: '#fff',
                                                    fontSize: '15px',
                                                    fontWeight: 600,
                                                    margin: 0,
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{playlist.name}</p>
                                                <p style={{
                                                    color: '#a0a0a0',
                                                    fontSize: '13px',
                                                    margin: '4px 0 0 0',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{playlist.owner || 'Playlist'}</p>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Artists Section */}
                            {(activeTab === 'all' || activeTab === 'artists') && results.artists.length > 0 && (
                                <div className="search-section" style={{ marginBottom: '32px' }}>
                                    <h2 className="section-title" style={{
                                        fontSize: '22px',
                                        fontWeight: 700,
                                        marginBottom: '16px',
                                        color: '#fff',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '8px'
                                    }}>
                                        <Users size={22} style={{ color: '#1db954' }} />
                                        Artists
                                    </h2>
                                    <div className="artist-grid" style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                                        gap: '20px'
                                    }}>
                                        {(activeTab === 'all' ? results.artists.slice(0, 6) : results.artists).map(artist => (
                                            <div
                                                key={`artist-${artist.id}`}
                                                className="artist-card"
                                                style={{
                                                    background: 'rgba(255,255,255,0.03)',
                                                    borderRadius: '12px',
                                                    padding: '16px',
                                                    cursor: 'pointer',
                                                    transition: 'all 0.25s',
                                                    textAlign: 'center'
                                                }}
                                            >
                                                <div style={{
                                                    width: '100%',
                                                    aspectRatio: '1',
                                                    borderRadius: '50%',
                                                    overflow: 'hidden',
                                                    marginBottom: '12px',
                                                    boxShadow: '0 4px 20px rgba(0,0,0,0.4)'
                                                }}>
                                                    <img
                                                        src={artist.image || '/placeholder.svg'}
                                                        alt={artist.name}
                                                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                    />
                                                </div>
                                                <p style={{
                                                    color: '#fff',
                                                    fontSize: '15px',
                                                    fontWeight: 600,
                                                    margin: 0,
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{artist.name}</p>
                                                <p style={{
                                                    color: '#a0a0a0',
                                                    fontSize: '12px',
                                                    margin: '4px 0 0 0',
                                                    textTransform: 'uppercase',
                                                    letterSpacing: '0.5px'
                                                }}>Artist</p>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}

                            {/* Albums Section */}
                            {(activeTab === 'all' || activeTab === 'albums') && results.albums.length > 0 && (
                                <div className="search-section" style={{ marginBottom: '32px' }}>
                                    <h2 className="section-title" style={{
                                        fontSize: '22px',
                                        fontWeight: 700,
                                        marginBottom: '16px',
                                        color: '#fff',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '8px'
                                    }}>
                                        <Disc size={22} style={{ color: '#1db954' }} />
                                        Albums
                                    </h2>
                                    <div className="album-grid" style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                                        gap: '20px'
                                    }}>
                                        {(activeTab === 'all' ? results.albums.slice(0, 6) : results.albums).map(album => (
                                            <div
                                                key={`album-${album.id}`}
                                                onClick={() => navigate(`/playlist/${album.id}`)}
                                                className="album-card shine-card"
                                                style={{
                                                    background: 'rgba(255,255,255,0.05)',
                                                    borderRadius: '12px',
                                                    padding: '16px',
                                                    cursor: 'pointer',
                                                    transition: 'all 0.25s'
                                                }}
                                            >
                                                <div style={{
                                                    width: '100%',
                                                    aspectRatio: '1',
                                                    borderRadius: '8px',
                                                    overflow: 'hidden',
                                                    marginBottom: '12px',
                                                    boxShadow: '0 8px 24px rgba(0,0,0,0.4)'
                                                }}>
                                                    <img
                                                        src={album.image || '/placeholder.svg'}
                                                        alt={album.name}
                                                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                    />
                                                </div>
                                                <p style={{
                                                    color: '#fff',
                                                    fontSize: '15px',
                                                    fontWeight: 600,
                                                    margin: 0,
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{album.name}</p>
                                                <p style={{
                                                    color: '#a0a0a0',
                                                    fontSize: '13px',
                                                    margin: '4px 0 0 0',
                                                    whiteSpace: 'nowrap',
                                                    overflow: 'hidden',
                                                    textOverflow: 'ellipsis'
                                                }}>{album.artist || 'Album'}</p>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            )}
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
                    <div style={{ paddingTop: '20px' }}>
                        <h2 style={{ fontSize: '22px', fontWeight: 700, marginBottom: '20px', color: '#fff' }}>
                            Browse all
                        </h2>
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(170px, 1fr))',
                            gap: '16px'
                        }}>
                            {categories.map((cat) => (
                                <div
                                    key={cat.id}
                                    onClick={() => setQuery(cat.title)}
                                    className="category-card"
                                    style={{
                                        position: 'relative',
                                        height: '110px',
                                        borderRadius: '10px',
                                        overflow: 'hidden',
                                        cursor: 'pointer',
                                        backgroundColor: cat.color,
                                        transition: 'transform 0.2s, box-shadow 0.2s',
                                        boxShadow: '0 4px 12px rgba(0,0,0,0.2)'
                                    }}
                                >
                                    <span style={{
                                        position: 'absolute',
                                        top: '14px',
                                        left: '14px',
                                        fontSize: '16px',
                                        fontWeight: 700,
                                        color: '#fff'
                                    }}>{cat.title}</span>
                                    <span style={{
                                        position: 'absolute',
                                        bottom: '10px',
                                        right: '10px',
                                        fontSize: '32px',
                                        transform: 'rotate(15deg)',
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
                    0%, 100% { transform: scale(1); box-shadow: 0 0 30px rgba(29, 185, 84, 0.4); }
                    50% { transform: scale(1.05); box-shadow: 0 0 50px rgba(29, 185, 84, 0.6); }
                }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                .animate-spin { animation: spin 1s linear infinite; }
                
                .song-item:hover {
                    background-color: rgba(255,255,255,0.08) !important;
                    border-color: rgba(255,255,255,0.06) !important;
                }
                .song-item:hover .play-overlay {
                    opacity: 1 !important;
                }
                .song-item:hover .action-btn {
                    color: #b3b3b3 !important;
                }
                .action-btn:hover {
                    color: #fff !important;
                    background: rgba(255,255,255,0.1) !important;
                }
                
                .playlist-card:hover,
                .album-card:hover {
                    background: rgba(255,255,255,0.1) !important;
                    transform: translateY(-4px);
                }
                .playlist-card:hover .card-play-btn,
                .album-card:hover .card-play-btn {
                    opacity: 1 !important;
                    transform: translateY(0) !important;
                }
                
                .artist-card:hover {
                    background: rgba(255,255,255,0.08) !important;
                }
                
                .category-card:hover {
                    transform: scale(1.02);
                    box-shadow: 0 8px 24px rgba(0,0,0,0.35);
                }
                
                .shine-card::before {
                    content: '';
                    position: absolute;
                    inset: 0;
                    background: linear-gradient(135deg, transparent 40%, rgba(255,255,255,0.05) 50%, transparent 60%);
                    opacity: 0;
                    transition: opacity 0.3s;
                    pointer-events: none;
                }
                .shine-card:hover::before {
                    opacity: 1;
                }
                
                .search-tab:hover {
                    background: rgba(255,255,255,0.12) !important;
                }
                
                @media (max-width: 768px) {
                    .playlist-grid, .album-grid {
                        grid-template-columns: repeat(2, 1fr) !important;
                        gap: 10px !important;
                    }
                    .playlist-card, .album-card {
                        padding: 10px !important;
                        border-radius: 8px !important;
                    }
                    .playlist-card > div:first-child,
                    .album-card > div:first-child {
                        border-radius: 6px !important;
                        margin-bottom: 8px !important;
                    }
                    .playlist-card p, .album-card p {
                        font-size: 13px !important;
                        line-height: 1.3 !important;
                    }
                    .playlist-card p:first-of-type, .album-card p:first-of-type {
                        font-size: 14px !important;
                        font-weight: 600 !important;
                    }
                    .artist-grid {
                        grid-template-columns: repeat(3, 1fr) !important;
                        gap: 10px !important;
                    }
                    .artist-card {
                        padding: 10px !important;
                    }
                    .artist-card p:first-of-type {
                        font-size: 13px !important;
                    }
                    .song-item {
                        grid-template-columns: 48px 1fr auto !important;
                        padding: 6px 8px !important;
                    }
                    .song-image img {
                        width: 48px !important;
                        height: 48px !important;
                    }
                    .songs-section h2,
                    .search-section h2 {
                        font-size: 18px !important;
                    }
                }
            `}</style>
        </div>
    );
};

export default Search;
