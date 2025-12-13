import { searchSpotify } from '../services/spotify';

// ... (keep existing imports)

import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { useOffline } from '../context/OfflineContext';
import { usePlaylists } from '../context/PlaylistContext'; // Added
import { useUI } from '../context/UIContext';
import { BACKEND_URL } from '../config';
import axios from 'axios';
import { Heart, Download, Music2, Search as SearchIcon, X, Plus, Loader } from 'lucide-react';
// import Loader from '../components/Loader'; // Removed because file doesn't exist
import { formatDuration } from '../utils/formatters';
import { useOnlineStatus } from '../utils/online';

const categories = [
    { id: 'podcasts', title: 'Podcasts', color: '#E13E3E', icon: '🎙️' },
    { id: 'made-for-you', title: 'Made For You', color: '#1E3264', icon: '✨' },
    { id: 'new-releases', title: 'New Releases', color: '#A5678E', icon: '🆕' },
    { id: 'charts', title: 'Charts', color: '#8D67AB', icon: '📈' },
    { id: 'discover', title: 'Discover', color: '#503750', icon: '🔍' },
    { id: 'live-events', title: 'Live Events', color: '#AF2896', icon: '🎤' },
    { id: 'hip-hop', title: 'Hip Hop', color: '#BC5900', icon: '🎤' },
    { id: 'pop', title: 'Pop', color: '#8D67AB', icon: '🌟' },
    { id: 'rock', title: 'Rock', color: '#E8115B', icon: '🎸' },
    { id: 'indie', title: 'Indie', color: '#AF2896', icon: '🌸' },
    { id: 'mood', title: 'Mood', color: '#BA5D07', icon: '😌' },
    { id: 'workout', title: 'Workout', color: '#006450', icon: '💪' },
];

const Search = () => {
    const navigate = useNavigate();
    const isOnline = useOnlineStatus();
    const [query, setQuery] = useState('');
    const [results, setResults] = useState([]);
    const [playlists, setPlaylists] = useState([]); // New state for playlists
    const [activeFilter, setActiveFilter] = useState('all'); // 'all', 'songs', 'playlists'
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(null);
    const [addToPlaylistSong, setAddToPlaylistSong] = useState(null);
    const [optionsMenuSong, setOptionsMenuSong] = useState(null);
    const [focused, setFocused] = useState(false);
    const { playSong, currentSong, isPlaying, addToQueue } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();
    const { createPlaylist } = usePlaylists();

    const { isSongOffline } = useOffline();
    const { user, isAuthenticated, spotifyToken, isSpotifyAuthenticated } = useAuth(); // Get Spotify auth
    const { openProfileMenu } = useUI();

    // ... (keep categories, user derived data)
    const userImage = user?.profileImage;
    const userInitials = user?.username ? user.username.charAt(0).toUpperCase() : '';

    // Filter Logic
    const filteredResults = {
        songs: activeFilter === 'all' || activeFilter === 'songs' ? results : [],
        playlists: activeFilter === 'all' || activeFilter === 'playlists' ? playlists : []
    };

    const performSearch = async (nextQuery) => {
        const q = (nextQuery ?? query).trim();
        if (!q) return;

        if (!isOnline) {
            setLoading(false);
            setResults([]);
            setPlaylists([]);
            return;
        }

        setLoading(true);
        setResults([]);
        setPlaylists([]);
        setActiveFilter('all');

        try {
            if (isSpotifyAuthenticated && spotifyToken) {
                const spotifyResults = await searchSpotify(q, spotifyToken, 'playlist,track');

                if (spotifyResults.songs) setResults(spotifyResults.songs);
                if (spotifyResults.playlists) setPlaylists(spotifyResults.playlists);
            } else {
                const response = await axios.get(
                    `${BACKEND_URL}/itunes/search?q=${encodeURIComponent(q)}&limit=30`,
                    { timeout: 12000 }
                );
                if (response.data.success) {
                    setResults(response.data.results);
                }
            }
        } catch (error) {
            console.error('Search failed:', error);
        } finally {
            setLoading(false);
        }
    };

    const handleSearch = async (e) => {
        e.preventDefault();
        await performSearch(query);
    };

    const handleAddPlaylist = (e, playlist) => {
        e.stopPropagation();
        // Create a local reference to this Spotify playlist
        createPlaylist(playlist.title, playlist.description, playlist.image);
        // Show feedback (toast or simple alert/console for now)
        console.log("Added playlist:", playlist.title);
    };

    const handleSongOption = (action, song) => {
        if (action === 'queue') {
            addToQueue(song);
        } else if (action === 'playlist') {
            // Open add to playlist modal (Simplification: just log for now or skip implementation if complex)
        }
        setOptionsMenuSong(null);
    };

    // ... (keep helper functions)
    const handleDownload = async (e, song) => {
        e.stopPropagation();
        if (isSongOffline(song.id)) {
            console.log("Song already offline:", song.title);
            return;
        }
        setDownloading(song.id);
        try {
            const response = await axios.post(`${BACKEND_URL}/download/song`, { songId: song.id, songUrl: song.audio }, { responseType: 'blob' });
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', `${song.title} - ${song.artist}.mp3`);
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
            console.log("Download successful");
        } catch (error) {
            console.error("Download failed:", error);
        } finally {
            setDownloading(null);
        }
    };

    // Render logic update
    return (
        <div style={{ minHeight: '100%', backgroundColor: '#121212', paddingBottom: '140px' }}>
            {/* Sticky Header with Search + Filters */}
            <div style={{
                position: 'sticky',
                top: 0,
                zIndex: 30,
                backgroundColor: '#121212',
                paddingTop: '16px'
            }}>
                {/* Search Bar Row */}
                <div style={{ padding: '0 16px 12px', display: 'flex', gap: '16px', alignItems: 'center' }}>
                    <div
                        onClick={openProfileMenu}
                        style={{
                            width: '32px', height: '32px', borderRadius: '50%',
                            background: userImage ? `url(${userImage}) no-repeat center/cover` : 'linear-gradient(135deg, #f59e0b, #d97706)',
                            flexShrink: 0, cursor: 'pointer',
                            display: 'flex', alignItems: 'center', justifyContent: 'center',
                            fontSize: '14px', fontWeight: '700', color: '#000'
                        }}
                    >
                        {!userImage && userInitials}
                    </div>

                    <form onSubmit={handleSearch} style={{ flex: 1, position: 'relative' }}>
                        <div style={{
                            background: '#2a2a2a',
                            borderRadius: '4px',
                            display: 'flex', alignItems: 'center',
                            height: '48px',
                            border: focused ? '1px solid #fff' : '1px solid transparent'
                        }}>
                            <SearchIcon size={24} color={focused ? '#fff' : '#b3b3b3'} style={{ marginLeft: '12px' }} />
                            <input
                                type="text"
                                value={query}
                                onChange={e => setQuery(e.target.value)}
                                onFocus={() => setFocused(true)}
                                onBlur={() => setFocused(false)}
                                placeholder="What do you want to listen to?"
                                style={{
                                    background: 'transparent', border: 'none', color: '#fff',
                                    fontSize: '16px', fontWeight: 500,
                                    width: '100%', height: '100%',
                                    padding: '0 12px', outline: 'none'
                                }}
                            />
                            {query && (
                                <button type="button" onClick={() => { setQuery(''); setResults([]); setPlaylists([]); }} style={{ background: 'none', border: 'none', padding: '12px', cursor: 'pointer', color: '#fff' }}>
                                    <X size={20} />
                                </button>
                            )}
                        </div>
                    </form>
                </div>

                {/* Filter Pills (Only show if searching) */}
                {query && !loading && (results.length > 0 || playlists.length > 0) && (
                    <div style={{
                        padding: '0 16px 12px',
                        display: 'flex', gap: '8px',
                        overflowX: 'auto',
                        scrollbarWidth: 'none',
                        borderBottom: '1px solid #282828'
                    }}>
                        {['all', 'songs', 'playlists'].map(filter => (
                            <button
                                key={filter}
                                onClick={() => setActiveFilter(filter)}
                                style={{
                                    backgroundColor: activeFilter === filter ? '#1db954' : '#2a2a2a',
                                    color: activeFilter === filter ? '#000' : '#fff',
                                    border: 'none',
                                    padding: '8px 16px',
                                    borderRadius: '16px',
                                    fontSize: '13px',
                                    fontWeight: 600,
                                    textTransform: 'capitalize',
                                    whiteSpace: 'nowrap',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s'
                                }}
                            >
                                {filter}
                            </button>
                        ))}
                    </div>
                )}
            </div>

            {/* Content Area */}
            <div style={{ padding: '0 16px' }}>
                {loading ? (
                    <div style={{ display: 'flex', justifyContent: 'center', padding: '80px 0' }}>
                        <Loader size={40} className="animate-spin" color="#1db954" />
                    </div>
                ) : !query ? (
                    /* Default Browse Categories */
                    <div style={{ paddingTop: '12px' }}>
                                                {!isOnline && (
                                                    <div style={{
                                                        textAlign: 'center',
                                                        padding: '20px 0',
                                                        color: '#b3b3b3',
                                                        fontSize: '14px'
                                                    }}>
                                                        You're offline. Search needs internet.
                                                    </div>
                                                )}
                        <h2 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '16px', color: '#fff' }}>Start browsing</h2>
                        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                            {categories.map(cat => (
                                <div
                                    key={cat.id}
                                    onClick={() => {
                                        setQuery(cat.title);
                                        performSearch(cat.title);
                                    }}
                                    style={{
                                    backgroundColor: cat.color,
                                    height: '100px',
                                    borderRadius: '4px',
                                    position: 'relative',
                                    overflow: 'hidden',
                                    padding: '12px',
                                    cursor: 'pointer'
                                }}>
                                    <span style={{ fontSize: '16px', fontWeight: 700, color: '#fff' }}>{cat.title}</span>
                                    <div style={{ position: 'absolute', bottom: '-10px', right: '-10px', transform: 'rotate(25deg)', fontSize: '50px' }}>
                                        {cat.icon}
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                ) : (
                    /* Search Results */
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px', paddingTop: '16px' }}>

                        {/* Playlists List View */}
                        {filteredResults.playlists.length > 0 && (
                            <div>
                                {activeFilter === 'all' && <h3 style={{ fontSize: '18px', fontWeight: 700, color: '#fff', marginBottom: '12px' }}>Playlists</h3>}
                                <div style={{ display: 'flex', flexDirection: 'column' }}>
                                    {filteredResults.playlists.map(playlist => (
                                        <div
                                            key={playlist.id}
                                            onClick={() => navigate(`/playlist/${playlist.id}`)}
                                            style={{
                                                display: 'flex', alignItems: 'center', gap: '12px',
                                                padding: '8px 0',
                                                cursor: 'pointer'
                                            }}
                                        >
                                            <img src={playlist.image || '/placeholder.svg'} style={{ width: '48px', height: '48px', objectFit: 'cover' }} alt="" />
                                            <div style={{ flex: 1, minWidth: 0 }}>
                                                <div style={{ color: '#fff', fontSize: '16px', fontWeight: 400, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{playlist.title}</div>
                                                <div style={{ color: '#b3b3b3', fontSize: '13px' }}>Playlist • Spotify</div>
                                            </div>
                                            <button
                                                onClick={(e) => handleAddPlaylist(e, playlist)}
                                                style={{ background: 'none', border: '1px solid #727272', borderRadius: '50%', width: '24px', height: '24px', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}
                                            >
                                                <Plus size={14} />
                                            </button>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        {/* Songs List View */}
                        {filteredResults.songs.length > 0 && (
                            <div>
                                {activeFilter === 'all' && <h3 style={{ fontSize: '18px', fontWeight: 700, color: '#fff', marginBottom: '12px' }}>Songs</h3>}
                                <div style={{ display: 'flex', flexDirection: 'column' }}>
                                    {filteredResults.songs.map(song => {
                                        const isThisSong = currentSong?.id === song.id;
                                        return (
                                            <div
                                                key={song.id}
                                                onClick={() => playSong(song)}
                                                style={{
                                                    display: 'flex', alignItems: 'center', gap: '12px',
                                                    padding: '8px 0',
                                                    cursor: 'pointer',
                                                    position: 'relative'
                                                }}
                                            >
                                                <img src={song.image || '/placeholder.svg'} style={{ width: '48px', height: '48px', objectFit: 'cover' }} alt="" />
                                                <div style={{ flex: 1, minWidth: 0 }}>
                                                    <div style={{ color: isThisSong ? '#1db954' : '#fff', fontSize: '16px', fontWeight: 400, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{song.title}</div>
                                                    <div style={{ color: '#b3b3b3', fontSize: '13px' }}>Song • {song.artist}</div>
                                                </div>
                                                <div style={{ display: 'flex', gap: '16px', alignItems: 'center', color: '#b3b3b3' }}>
                                                    {isLiked(song.id) && <Heart size={16} fill="#1db954" color="#1db954" />}
                                                    <button
                                                        onClick={(e) => {
                                                            e.stopPropagation();
                                                            setOptionsMenuSong(optionsMenuSong === song.id ? null : song.id);
                                                        }}
                                                        style={{ background: 'none', border: 'none', color: '#b3b3b3', padding: '4px' }}
                                                    >
                                                        <span style={{ fontSize: '20px', lineHeight: '10px', verticalAlign: 'middle' }}>⋮</span>
                                                    </button>
                                                </div>

                                                {/* Options Popup */}
                                                {optionsMenuSong === song.id && (
                                                    <div style={{
                                                        position: 'absolute',
                                                        right: '40px',
                                                        top: '30px',
                                                        backgroundColor: '#282828',
                                                        borderRadius: '4px',
                                                        boxShadow: '0 4px 12px rgba(0,0,0,0.5)',
                                                        zIndex: 10,
                                                        minWidth: '150px'
                                                    }}>
                                                        <div
                                                            onClick={(e) => { e.stopPropagation(); toggleLike(song); setOptionsMenuSong(null); }}
                                                            style={{ padding: '12px', color: '#fff', fontSize: '14px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px' }}
                                                        >
                                                            <Heart size={14} fill={isLiked(song.id) ? '#1db954' : 'none'} color={isLiked(song.id) ? '#1db954' : '#fff'} />
                                                            {isLiked(song.id) ? 'Liked' : 'Like'}
                                                        </div>
                                                        <div
                                                            onClick={(e) => { e.stopPropagation(); handleDownload(e, song); setOptionsMenuSong(null); }}
                                                            style={{ padding: '12px', color: '#fff', fontSize: '14px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px' }}
                                                        >
                                                            <Download size={14} /> Download
                                                        </div>
                                                        <div
                                                            onClick={(e) => { e.stopPropagation(); handleSongOption('queue', song); }}
                                                            style={{ padding: '12px', color: '#fff', fontSize: '14px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '8px' }}
                                                        >
                                                            <Music2 size={14} /> Add to Queue
                                                        </div>
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* No Results Fallback */}
                        {filteredResults.songs.length === 0 && filteredResults.playlists.length === 0 && (
                            <div style={{ textAlign: 'center', padding: '40px 0', color: '#fff' }}>
                                <div style={{ fontSize: '18px', fontWeight: 700, marginBottom: '8px' }}>
                                    {isOnline ? 'No results found' : 'Offline'}
                                </div>
                                <div style={{ color: '#b3b3b3', fontSize: '14px' }}>
                                    {isOnline
                                        ? 'Please check your spelling or try different keywords.'
                                        : 'Connect to the internet to search.'}
                                </div>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
};

export default Search;
