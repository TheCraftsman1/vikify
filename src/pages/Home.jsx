import React from 'react';
import { Play, Pause } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { usePlayer } from '../context/PlayerContext';
import { albums } from '../data/songs';

const Home = () => {
    const navigate = useNavigate();
    const { playSong, currentSong, isPlaying, togglePlay } = usePlayer();

    const getGreeting = () => {
        const hour = new Date().getHours();
        if (hour < 12) return 'Good morning';
        if (hour < 18) return 'Good afternoon';
        return 'Good evening';
    };

    // Quick access with actual albums
    const quickAccess = [
        { id: 'liked', title: 'Liked Songs', image: 'https://misc.scdn.co/liked-songs/liked-songs-640.png' },
        ...albums.slice(0, 5).map(a => ({ id: a.id, title: a.title, image: a.image, songs: a.songs }))
    ];

    const handlePlayAlbum = (e, item) => {
        e.stopPropagation();
        if (item.songs && item.songs.length > 0) {
            const isThisPlaying = currentSong && item.songs.some(s => s.id === currentSong.id);
            if (isThisPlaying) {
                togglePlay();
            } else {
                playSong(item.songs[0]);
            }
        }
    };

    return (
        <div style={{ position: 'relative', minHeight: '100%' }}>
            {/* Gradient Background */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '332px',
                background: 'linear-gradient(180deg, #535353 0%, #121212 100%)',
                pointerEvents: 'none'
            }} />

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '120px' }}>
                {/* Greeting */}
                <h1 style={{
                    fontSize: '32px',
                    fontWeight: 700,
                    marginBottom: '24px',
                    color: '#fff'
                }}>{getGreeting()}</h1>

                {/* Quick Access Grid - Spotify Style */}
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                    gap: '12px',
                    marginBottom: '32px'
                }}>
                    {quickAccess.map((item) => {
                        const isThisPlaying = currentSong && item.songs?.some(s => s.id === currentSong.id);
                        return (
                            <div
                                key={item.id}
                                onClick={() => navigate(`/playlist/${item.id}`)}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    backgroundColor: 'rgba(255, 255, 255, 0.07)',
                                    borderRadius: '4px',
                                    overflow: 'hidden',
                                    cursor: 'pointer',
                                    transition: 'background 0.3s',
                                    height: '64px'
                                }}
                                onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.2)'}
                                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(255, 255, 255, 0.07)'}
                                className="group"
                            >
                                <div style={{ width: '64px', height: '64px', flexShrink: 0, boxShadow: '0 8px 24px rgba(0,0,0,0.5)' }}>
                                    <img
                                        src={item.image}
                                        alt={item.title}
                                        style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                    />
                                </div>
                                <div style={{
                                    flex: 1,
                                    padding: '0 16px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between',
                                    minWidth: 0
                                }}>
                                    <span style={{
                                        fontWeight: 700,
                                        fontSize: '14px',
                                        color: '#fff',
                                        whiteSpace: 'nowrap',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis'
                                    }}>{item.title}</span>
                                    {item.songs && (
                                        <button
                                            onClick={(e) => handlePlayAlbum(e, item)}
                                            style={{
                                                width: '40px',
                                                height: '40px',
                                                borderRadius: '50%',
                                                backgroundColor: '#1db954',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                boxShadow: '0 8px 16px rgba(0,0,0,0.3)',
                                                opacity: 0,
                                                transform: 'translateY(8px)',
                                                transition: 'all 0.3s ease',
                                                flexShrink: 0
                                            }}
                                            className="play-btn"
                                        >
                                            {isThisPlaying && isPlaying ? (
                                                <Pause size={18} fill="#000" color="#000" />
                                            ) : (
                                                <Play size={18} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                                            )}
                                        </button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>

                {/* Your Playlists Section */}
                <Section title="Your Playlists" items={albums} onPlay={handlePlayAlbum} />

                {/* Made For You */}
                <Section
                    title="Made For You"
                    items={[
                        { id: 'daily-1', title: 'Daily Mix 1', description: 'Based on your recent listening', image: 'https://dailymix-images.scdn.co/v2/img/ab6761610000e5eb5b4f72292f7502c34d402b85/1/en/large' },
                        { id: 'daily-2', title: 'Daily Mix 2', description: 'Your personalized mix', image: 'https://dailymix-images.scdn.co/v2/img/ab6761610000e5eb19c2794a34ce71b9e8293789/2/en/large' },
                        { id: 'daily-3', title: 'Daily Mix 3', description: 'Songs you love', image: 'https://dailymix-images.scdn.co/v2/img/ab6761610000e5eb0b096f9bf801538fc100a402/3/en/large' },
                        { id: 'daily-4', title: 'Daily Mix 4', description: 'New discoveries', image: 'https://dailymix-images.scdn.co/v2/img/ab6761610000e5eb8863bc11d2aa12b54f5aeb36/4/en/large' },
                    ]}
                />
            </div>

            {/* CSS for hover effects */}
            <style>{`
                .group:hover .play-btn {
                    opacity: 1 !important;
                    transform: translateY(0) !important;
                }
                .play-btn:hover {
                    transform: scale(1.04) !important;
                    background-color: #1ed760 !important;
                }
            `}</style>
        </div>
    );
};

const Section = ({ title, items, onPlay }) => {
    const navigate = useNavigate();
    const { currentSong, isPlaying } = usePlayer();

    return (
        <div style={{ marginBottom: '40px' }}>
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'flex-end',
                marginBottom: '16px'
            }}>
                <h2
                    style={{
                        fontSize: '24px',
                        fontWeight: 700,
                        color: '#fff',
                        cursor: 'pointer'
                    }}
                    onMouseEnter={(e) => e.currentTarget.style.textDecoration = 'underline'}
                    onMouseLeave={(e) => e.currentTarget.style.textDecoration = 'none'}
                >
                    {title}
                </h2>
                <span
                    style={{
                        fontSize: '14px',
                        fontWeight: 700,
                        color: '#b3b3b3',
                        cursor: 'pointer',
                        letterSpacing: '0.1em'
                    }}
                    onMouseEnter={(e) => { e.currentTarget.style.color = '#fff'; e.currentTarget.style.textDecoration = 'underline'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.color = '#b3b3b3'; e.currentTarget.style.textDecoration = 'none'; }}
                >
                    Show all
                </span>
            </div>

            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                gap: '24px'
            }}>
                {items.slice(0, 6).map((item) => {
                    const isThisPlaying = currentSong && item.songs?.some(s => s.id === currentSong.id);
                    return (
                        <div
                            key={item.id}
                            onClick={() => navigate(`/playlist/${item.id}`)}
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
                                borderRadius: '6px',
                                overflow: 'hidden',
                                boxShadow: '0 8px 24px rgba(0,0,0,0.5)'
                            }}>
                                <img
                                    src={item.image}
                                    alt={item.title}
                                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                    onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                />
                                {item.songs && (
                                    <button
                                        onClick={(e) => onPlay && onPlay(e, item)}
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
                            <h3 style={{
                                fontWeight: 700,
                                fontSize: '16px',
                                color: '#fff',
                                marginBottom: '4px',
                                whiteSpace: 'nowrap',
                                overflow: 'hidden',
                                textOverflow: 'ellipsis'
                            }}>{item.title}</h3>
                            <p style={{
                                fontSize: '14px',
                                color: '#b3b3b3',
                                display: '-webkit-box',
                                WebkitLineClamp: 2,
                                WebkitBoxOrient: 'vertical',
                                overflow: 'hidden',
                                lineHeight: '1.4'
                            }}>{item.description || `By ${item.artist}`}</p>
                        </div>
                    );
                })}
            </div>

            <style>{`
                .card-hover:hover .card-play-btn {
                    opacity: 1 !important;
                    transform: translateY(0) !important;
                }
                .card-play-btn:hover {
                    transform: scale(1.04) translateY(0) !important;
                    background-color: #1ed760 !important;
                }
            `}</style>
        </div>
    );
};

export default Home;
