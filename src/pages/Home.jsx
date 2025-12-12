import React, { useEffect, useState } from 'react';
import { Play, Pause, Music2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { usePlayer } from '../context/PlayerContext';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import { albums } from '../data/songs';
import { getFeaturedPlaylists, getNewReleases, getPlaylist, getUserPlaylists } from '../services/spotify';
import { useOnlineStatus } from '../utils/online';

const Home = () => {
    const navigate = useNavigate();
    const isOnline = useOnlineStatus();
    const { playSong, currentSong, isPlaying, togglePlay } = usePlayer();
    const { logout, user, isAuthenticated, accessToken } = useAuth();
    const { openProfileMenu } = useUI();
    const [featured, setFeatured] = useState([]);

    // Derived user data for avatar
    const userInitials = isAuthenticated && Object.keys(user || {}).length > 0 && user.name ? user.name[0].toUpperCase() : 'V';
    const userImage = isAuthenticated && user?.image ? user.image : null;
    const [newReleases, setNewReleases] = useState([]);
    const [userPlaylists, setUserPlaylists] = useState([]);
    const [isLoadingData, setIsLoadingData] = useState(true);

    useEffect(() => {
        const loadSpotifyData = async () => {
            try {
                const promises = [
                    getFeaturedPlaylists(8),
                    getNewReleases(8)
                ];

                // If authenticated, also fetch user playlists
                if (isAuthenticated && accessToken && isOnline) {
                    promises.push(getUserPlaylists(accessToken));
                }

                const results = await Promise.all(promises);

                setFeatured(results[0]);
                setNewReleases(results[1]);

                if (isAuthenticated && results[2]) {
                    setUserPlaylists(results[2]);
                }
            } catch (error) {
                console.error("Failed to load Spotify data", error);
            } finally {
                setIsLoadingData(false);
            }
        };

        loadSpotifyData();
    }, [isAuthenticated, isOnline]);

    const getGreeting = () => {
        const hour = new Date().getHours();
        if (hour < 12) return 'Good morning';
        if (hour < 18) return 'Good afternoon';
        return 'Good evening';
    };

    // Quick access - Mix of Liked Songs + first few new releases
    const quickAccess = [
        { id: 'liked', title: 'Liked Songs', image: 'https://misc.scdn.co/liked-songs/liked-songs-640.png', gradient: 'linear-gradient(135deg, #450af5, #c4efd9)' },
        { id: 'downloads', title: 'Downloads', image: null, icon: true, gradient: 'linear-gradient(135deg, #1db954, #191414)' },
        ...newReleases.slice(0, 4).map(a => ({ ...a, type: 'album' }))
    ];

    const handlePlayAlbum = async (e, item) => {
        e.stopPropagation();

        if (item.id === 'liked') {
            navigate('/liked');
            return;
        }
        if (item.id === 'downloads') {
            navigate('/downloads');
            return;
        }

        let songsToPlay = item.songs;

        if (!songsToPlay || songsToPlay.length === 0) {
            try {
                const details = await getPlaylist(item.id);
                if (details && details.songs) {
                    songsToPlay = details.songs;
                }
            } catch (err) {
                console.error("Error fetching tracks to play", err);
                return;
            }
        }

        if (songsToPlay && songsToPlay.length > 0) {
            playSong(songsToPlay[0]);
        }
    };

    return (
        <div style={{ position: 'relative', minHeight: '100%' }}>
            {/* Gradient Background - Animated */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '400px',
                background: 'linear-gradient(180deg, rgba(83, 83, 83, 0.8) 0%, #121212 100%)',
                pointerEvents: 'none',
                transition: 'background 0.5s ease'
            }} />

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '140px' }}>
                {/* Header / Greeting */}
                <div style={{ padding: '24px 16px 16px' }}>
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
                        {/* Categories Chips */}
                        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', scrollbarWidth: 'none' }}>
                            <button style={{
                                background: 'rgba(255,255,255,0.1)',
                                border: 'none',
                                borderRadius: '16px',
                                padding: '6px 16px',
                                color: '#fff',
                                fontSize: '12px',
                                fontWeight: 600
                            }}>All</button>
                            <button style={{
                                background: 'transparent',
                                border: 'none',
                                borderRadius: '16px',
                                padding: '6px 16px',
                                color: '#fff',
                                fontSize: '12px',
                                fontWeight: 600
                            }}>Music</button>
                            <button style={{
                                background: 'transparent',
                                border: 'none',
                                borderRadius: '16px',
                                padding: '6px 16px',
                                color: '#fff',
                                fontSize: '12px',
                                fontWeight: 600
                            }}>Podcasts</button>
                        </div>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <h2 style={{ fontSize: '24px', fontWeight: 700 }}>{getGreeting()}</h2>
                    </div>
                </div>
                <p style={{
                    fontSize: '14px',
                    color: 'rgba(255,255,255,0.6)',
                    fontWeight: 400,
                    marginBottom: '20px',
                    padding: '0 16px'
                }}>Discover something new today</p>

                {/* Quick Access Grid - Spotify Style Horizontal Tiles */}
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(2, 1fr)',
                    gap: '8px',
                    marginBottom: '32px'
                }}>
                    {quickAccess.map((item) => (
                        <div
                            key={item.id}
                            onClick={() => {
                                if (item.id === 'liked') navigate('/liked');
                                else if (item.id === 'downloads') navigate('/downloads');
                                else navigate(`/playlist/${item.id}`);
                            }}
                            className="horizontal-playlist"
                        >
                            <div className="img-container">
                                <div className="img-section" style={{ background: item.gradient || '#282828' }}>
                                    {item.icon ? (
                                        <Music2 size={24} color="#fff" />
                                    ) : item.image ? (
                                        <img
                                            src={item.image}
                                            alt={item.title}
                                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                        />
                                    ) : (
                                        <Music2 size={24} color="#fff" />
                                    )}
                                </div>
                            </div>
                            <div className="text-container">
                                <div className="text-section">
                                    <span>{item.title}</span>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>


                {/* Loading State */}
                {
                    isLoadingData ? (
                        <div style={{ textAlign: 'center', padding: '40px 0' }}>
                            <div className="loading-pulse" style={{
                                display: 'inline-block',
                                width: '40px',
                                height: '40px',
                                borderRadius: '50%',
                                background: 'linear-gradient(45deg, #1db954, #1ed760)',
                                animation: 'pulse 1.5s ease-in-out infinite'
                            }} />
                            <p style={{ marginTop: '16px', color: '#b3b3b3', fontSize: '14px' }}>
                                Loading your music...
                            </p>
                        </div>
                    ) : (
                        <>
                            {/* Guest Mode: Show minimal content */}
                            {!isAuthenticated && (
                                <div style={{
                                    textAlign: 'center',
                                    padding: '40px 20px',
                                    background: 'rgba(255,255,255,0.05)',
                                    borderRadius: '12px',
                                    marginBottom: '40px'
                                }}>
                                    <h3 style={{ fontSize: '20px', fontWeight: '700', color: '#fff', marginBottom: '8px' }}>
                                        Welcome to Vikify
                                    </h3>
                                    <p style={{ color: '#b3b3b3', marginBottom: '24px', fontSize: '14px' }}>
                                        Login with Spotify to see your playlists and recommendations.
                                        You can still search and download songs without logging in.
                                    </p>
                                    <button
                                        onClick={() => navigate('/onboarding')}
                                        style={{
                                            background: '#1db954',
                                            color: '#000',
                                            border: 'none',
                                            padding: '12px 32px',
                                            borderRadius: '24px',
                                            fontSize: '14px',
                                            fontWeight: '700',
                                            cursor: 'pointer'
                                        }}
                                    >
                                        Connect Spotify
                                    </button>
                                </div>
                            )}

                            {/* Authenticated Content */}
                            {isAuthenticated && (
                                <>
                                    {/* User Playlists Only - No random recommendations */}
                                    {userPlaylists.length > 0 ? (
                                        <Section
                                            title="Your Playlists"
                                            items={userPlaylists}
                                            onPlay={handlePlayAlbum}
                                        />
                                    ) : (
                                        <div style={{
                                            textAlign: 'center',
                                            padding: '40px 20px',
                                            background: 'rgba(255,255,255,0.03)',
                                            borderRadius: '16px',
                                            border: '1px solid rgba(255,255,255,0.06)'
                                        }}>
                                            <p style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '16px' }}>
                                                No playlists found. Create one to get started!
                                            </p>
                                            <button
                                                onClick={() => navigate('/search')}
                                                style={{
                                                    background: '#1db954',
                                                    color: '#000',
                                                    border: 'none',
                                                    padding: '12px 24px',
                                                    borderRadius: '24px',
                                                    fontSize: '14px',
                                                    fontWeight: '700',
                                                    cursor: 'pointer'
                                                }}
                                            >
                                                Search Songs
                                            </button>
                                        </div>
                                    )}
                                </>
                            )}
                        </>
                    )
                }


            </div >

            {/* Animations */}
            <style>{`
                @keyframes pulse {
                    0%, 100% { transform: scale(1); opacity: 1; }
                    50% { transform: scale(1.1); opacity: 0.7; }
                }
            `}</style>
        </div >
    );
};

const Section = ({ title, items, onPlay }) => {
    const navigate = useNavigate();
    const { currentSong, isPlaying } = usePlayer();

    if (!items || items.length === 0) return null;

    return (
        <div style={{ marginBottom: '32px' }}>
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '16px'
            }}>
                <h2 className="playlist-header" style={{ marginBottom: 0 }}>
                    {title}
                </h2>
                <span className="hoverable-icon" style={{
                    fontSize: '12px',
                    fontWeight: 700,
                    letterSpacing: '0.08em',
                    textTransform: 'uppercase'
                }}>
                    Show all
                </span>
            </div>

            <div className="playlist-grid">
                {items.slice(0, 6).map((item) => {
                    const isThisPlaying = currentSong && item.songs?.some(s => s.id === currentSong.id);
                    return (
                        <div
                            key={item.id}
                            onClick={() => navigate(`/playlist/${item.id}`)}
                            className="playlist-card"
                        >
                            <div className="playlist-card-image">
                                <img
                                    src={item.image}
                                    alt={item.title}
                                    onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                />
                                <button
                                    onClick={(e) => onPlay && onPlay(e, item)}
                                    className="playlist-card-play-btn"
                                >
                                    {isThisPlaying && isPlaying ? (
                                        <Pause size={20} fill="#000" color="#000" />
                                    ) : (
                                        <Play size={20} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                                    )}
                                </button>
                            </div>
                            <h3 className="playlist-card-title">{item.title}</h3>
                            <p className="playlist-card-description">
                                {item.description || item.artist || 'Playlist'}
                            </p>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default Home;
