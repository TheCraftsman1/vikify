import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Play, Pause, Clock, Download, Heart, Shuffle, MoreHorizontal, Plus, Trash2, Music, Loader } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { usePlaylists } from '../context/PlaylistContext';
import { useOffline } from '../context/OfflineContext';
import { albums } from '../data/songs';
import { downloadSong } from '../utils/download';
import AddToPlaylistModal from '../components/AddToPlaylistModal';
import { getPlaylist as getSpotifyPlaylist, getAlbum as getSpotifyAlbum } from '../services/spotify';

const PlaylistView = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const { playSong, currentSong, isPlaying, togglePlay, shufflePlay, shuffle } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();
    const { getPlaylist, removeFromPlaylist, deletePlaylist } = usePlaylists();
    const { isSongOffline, downloadPlaylist, isDownloading } = useOffline();

    const [addToPlaylistSong, setAddToPlaylistSong] = useState(null);
    const [fetchedAlbum, setFetchedAlbum] = useState(null);
    const [isLoading, setIsLoading] = useState(false);

    // Check if this is a custom playlist or an album
    const customPlaylist = getPlaylist(id);
    const localAlbum = albums.find(a => a.id === id);

    // Effective album (local or fetched)
    const album = customPlaylist || localAlbum || fetchedAlbum;

    useEffect(() => {
        if (!customPlaylist && !localAlbum) {
            const fetchSpotifyData = async () => {
                setIsLoading(true);
                try {
                    // Try fetching as album first (higher success rate)
                    let data = await getSpotifyAlbum(id);
                    if (!data) {
                        // Fallback to playlist
                        data = await getSpotifyPlaylist(id);
                    }
                    if (data) {
                        setFetchedAlbum(data);
                    }
                } catch (e) {
                    console.error("Error fetching spotify data", e);
                } finally {
                    setIsLoading(false);
                }
            };
            fetchSpotifyData();
        }
    }, [id, customPlaylist, localAlbum]);

    if (isLoading) {
        return (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100%', padding: '24px' }}>
                <Loader className="animate-spin" size={32} color="#1db954" />
            </div>
        );
    }

    if (!album) {
        return (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '100%', padding: '24px' }}>
                <div style={{ fontSize: '72px', marginBottom: '16px' }}>🎵</div>
                <h2 style={{ fontSize: '32px', fontWeight: 700, marginBottom: '8px' }}>Playlist not found</h2>
                <p style={{ color: '#b3b3b3' }}>The playlist you're looking for doesn't exist.</p>
            </div>
        );
    }

    const formatDuration = (seconds) => {
        if (!seconds) return '0:00';
        const mins = Math.floor(seconds / 60);
        const secs = Math.floor(seconds % 60);
        return `${mins}:${secs.toString().padStart(2, '0')}`;
    };

    const totalDuration = album.songs ? album.songs.reduce((acc, s) => acc + (s.duration || 0), 0) : 0;
    const isCurrentAlbumPlaying = currentSong && album.songs.some(s => s.id === currentSong.id);

    const handlePlayClick = () => {
        console.log('[PlaylistView] handlePlayClick called');
        console.log('[PlaylistView] album.songs:', album?.songs?.length, album?.songs);
        console.log('[PlaylistView] isCurrentAlbumPlaying:', isCurrentAlbumPlaying);

        if (isCurrentAlbumPlaying) {
            togglePlay();
        } else if (album?.songs?.length > 0) {
            console.log('[PlaylistView] Playing first song:', album.songs[0]);
            playSong(album.songs[0], album.songs);
        } else {
            console.warn('[PlaylistView] No songs to play!');
        }
    };

    const handleSongClick = (song) => {
        if (currentSong?.id === song.id) {
            togglePlay();
        } else {
            playSong(song, album.songs);
        }
    };

    const handleDeletePlaylist = () => {
        if (window.confirm(`Delete "${album.title}"?`)) {
            deletePlaylist(id);
            navigate('/library');
        }
    };

    const handleRemoveFromPlaylist = (e, songId) => {
        e.stopPropagation();
        removeFromPlaylist(id, songId);
    };

    // Generate color from string
    const generateColor = (str) => {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = str.charCodeAt(i) + ((hash << 5) - hash);
        }
        return `hsl(${Math.abs(hash) % 360}, 40%, 30%)`;
    };

    const headerColor = album.color || generateColor(album.title);

    return (
        <div style={{ minHeight: '100%', position: 'relative' }}>
            {/* Header Gradient */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '332px',
                background: `linear-gradient(180deg, ${headerColor} 0%, #121212 100%)`,
                pointerEvents: 'none'
            }} />

            <div className="playlist-page-content" style={{ position: 'relative', padding: '24px', paddingBottom: '120px' }}>
                {/* Playlist Header */}
                <div className="playlist-header">
                    <div className="playlist-header-image">
                        {album.image ? (
                            <img
                                src={album.image}
                                alt={album.title}
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
                                <Music size={80} color="#7f7f7f" className="playlist-header-icon" />
                            </div>
                        )}
                    </div>
                    <div className="playlist-header-info">
                        <span className="playlist-type-label">
                            {customPlaylist ? 'Playlist' : 'Playlist'}
                        </span>
                        <h1 className="playlist-title">{album.title}</h1>
                        <p className="playlist-description">
                            {album.description || 'A collection of amazing songs'}
                        </p>
                        <div className="playlist-meta">
                            <span className="playlist-artist">{album.artist || 'Your playlist'}</span>
                            <span className="playlist-meta-dot">•</span>
                            <span className="playlist-meta-info">{album.songs.length} songs,</span>
                            <span className="playlist-meta-info">about {Math.floor(totalDuration / 60)} min</span>
                        </div>
                    </div>
                </div>

                {/* Action Bar */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '24px', marginBottom: '24px' }}>
                    {album.songs.length > 0 && (
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
                            {isCurrentAlbumPlaying && isPlaying ? (
                                <Pause size={24} fill="#000" color="#000" />
                            ) : (
                                <Play size={24} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
                            )}
                        </button>
                    )}
                    <button
                        onClick={() => shufflePlay(album.songs)}
                        style={{
                            color: shuffle ? '#1db954' : '#b3b3b3',
                            padding: '8px'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.color = shuffle ? '#1db954' : '#fff'}
                        onMouseLeave={(e) => e.currentTarget.style.color = shuffle ? '#1db954' : '#b3b3b3'}
                        title="Shuffle play"
                    >
                        <Shuffle size={24} />
                    </button>
                    {/* Download All Button */}
                    {album.songs.length > 0 && (
                        <button
                            onClick={() => downloadPlaylist(album.songs, album.title)}
                            disabled={isDownloading}
                            style={{
                                color: isDownloading ? '#555' : '#b3b3b3',
                                padding: '8px',
                                cursor: isDownloading ? 'not-allowed' : 'pointer'
                            }}
                            onMouseEnter={(e) => !isDownloading && (e.currentTarget.style.color = '#1db954')}
                            onMouseLeave={(e) => !isDownloading && (e.currentTarget.style.color = '#b3b3b3')}
                            title={isDownloading ? 'Download in progress...' : 'Download all for offline'}
                        >
                            {isDownloading ? (
                                <Loader size={24} className="spin" />
                            ) : (
                                <Download size={24} />
                            )}
                        </button>
                    )}
                    {customPlaylist && (
                        <button
                            onClick={handleDeletePlaylist}
                            style={{ color: '#b3b3b3', padding: '8px' }}
                            onMouseEnter={(e) => e.currentTarget.style.color = '#ff5555'}
                            onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                            title="Delete playlist"
                        >
                            <Trash2 size={24} />
                        </button>
                    )}
                </div>

                {/* Empty State for Custom Playlists */}
                {album.songs.length === 0 && (
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
                        <h2 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>This playlist is empty</h2>
                        <p style={{ color: '#b3b3b3', maxWidth: '400px' }}>
                            Add songs by clicking the + icon on any song.
                        </p>
                    </div>
                )}

                {/* Table Header */}
                {album.songs.length > 0 && (
                    <>
                        <div className="track-list-header" style={{
                            display: 'grid',
                            gap: '16px',
                            padding: '8px 16px',
                            borderBottom: '1px solid rgba(255,255,255,0.1)',
                            color: '#b3b3b3',
                            fontSize: '14px',
                            textTransform: 'uppercase',
                            letterSpacing: '0.1em',
                            position: 'sticky',
                            top: 0,
                            backgroundColor: '#121212',
                            zIndex: 1
                        }}>
                            <span style={{ textAlign: 'center' }}>#</span>
                            <span>Title</span>
                            <span className="hide-mobile">Album</span>
                            <span style={{ textAlign: 'right' }}><Clock size={16} /></span>
                        </div>

                        {/* Songs */}
                        <div style={{ marginTop: '8px' }}>
                            {album.songs.map((song, index) => {
                                const isThisSong = currentSong?.id === song.id;
                                const liked = isLiked(song.id);
                                const isOffline = isSongOffline(song.id);

                                return (
                                    <div
                                        key={song.id}
                                        onClick={() => handleSongClick(song)}
                                        style={{
                                            display: 'grid',
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

                                        {/* Album name */}
                                        <div className="hide-mobile" style={{ display: 'flex', alignItems: 'center', color: '#b3b3b3', fontSize: '14px' }}>
                                            {album.title}
                                        </div>

                                        {/* Duration & Actions */}
                                        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '8px' }}>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); toggleLike(song); }}
                                                className="action-btn"
                                                style={{
                                                    opacity: liked ? 1 : 0,
                                                    padding: '4px',
                                                    color: liked ? '#1db954' : '#b3b3b3'
                                                }}
                                                title={liked ? 'Remove from Liked Songs' : 'Add to Liked Songs'}
                                            >
                                                <Heart size={16} fill={liked ? '#1db954' : 'none'} />
                                            </button>
                                            <button
                                                onClick={(e) => { e.stopPropagation(); setAddToPlaylistSong(song); }}
                                                className="action-btn"
                                                style={{ opacity: 0, padding: '4px', color: '#b3b3b3' }}
                                                title="Add to playlist"
                                            >
                                                <Plus size={16} />
                                            </button>
                                            {customPlaylist && (
                                                <button
                                                    onClick={(e) => handleRemoveFromPlaylist(e, song.id)}
                                                    className="action-btn"
                                                    style={{ opacity: 0, padding: '4px', color: '#b3b3b3' }}
                                                    title="Remove from playlist"
                                                >
                                                    <Trash2 size={16} />
                                                </button>
                                            )}
                                            <button
                                                onClick={(e) => { e.stopPropagation(); downloadSong(song); }}
                                                className="action-btn"
                                                style={{ opacity: 0, padding: '4px', color: '#b3b3b3' }}
                                                title="Download"
                                            >
                                                <Download size={16} />
                                            </button>
                                            <span style={{ color: '#b3b3b3', fontSize: '14px', fontVariantNumeric: 'tabular-nums', minWidth: '40px', textAlign: 'right' }}>
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

            {/* Add to Playlist Modal */}
            <AddToPlaylistModal
                isOpen={!!addToPlaylistSong}
                onClose={() => setAddToPlaylistSong(null)}
                song={addToPlaylistSong}
            />

            <style>{`
                .track-row:hover .track-num { display: none; }
                .track-row:hover .track-play { display: block !important; }
                .track-row:hover .action-btn { opacity: 1 !important; }
                .action-btn:hover { color: #fff !important; }
                
                /* Playlist Header Styles */
                .playlist-header {
                    display: flex;
                    align-items: flex-end;
                    gap: 24px;
                    margin-bottom: 24px;
                    padding-top: 32px;
                }
                .playlist-header-image {
                    width: 232px;
                    height: 232px;
                    border-radius: 4px;
                    overflow: hidden;
                    box-shadow: 0 4px 60px rgba(0,0,0,0.5);
                    flex-shrink: 0;
                }
                .playlist-header-info {
                    flex: 1;
                    min-width: 0;
                }
                .playlist-type-label {
                    font-size: 14px;
                    font-weight: 700;
                    text-transform: uppercase;
                }
                .playlist-title {
                    font-size: 72px;
                    font-weight: 900;
                    margin-top: 8px;
                    margin-bottom: 16px;
                    line-height: 0.9;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }
                .playlist-description {
                    color: #b3b3b3;
                    font-size: 14px;
                    margin-bottom: 8px;
                }
                .playlist-meta {
                    display: flex;
                    align-items: center;
                    gap: 4px;
                    font-size: 14px;
                    flex-wrap: wrap;
                }
                .playlist-artist {
                    font-weight: 700;
                }
                .playlist-meta-dot,
                .playlist-meta-info {
                    color: #b3b3b3;
                }
                
                /* MOBILE RESPONSIVE */
                @media (max-width: 768px) {
                    .hide-mobile { display: none !important; }
                    
                    .playlist-page-content {
                        padding: 16px !important;
                        padding-bottom: 160px !important;
                    }
                    
                    .playlist-header {
                        flex-direction: column;
                        align-items: center;
                        text-align: center;
                        gap: 16px;
                        padding-top: 16px;
                    }
                    
                    .playlist-header-image {
                        width: 180px;
                        height: 180px;
                    }
                    
                    .playlist-header-icon {
                        width: 48px !important;
                        height: 48px !important;
                    }
                    
                    .playlist-header-info {
                        width: 100%;
                    }
                    
                    .playlist-title {
                        font-size: 28px !important;
                        line-height: 1.1;
                        white-space: normal;
                        word-wrap: break-word;
                    }
                    
                    .playlist-meta {
                        justify-content: center;
                    }
                    
                    /* Track list mobile */
                    .track-list-header {
                        display: none !important;
                    }
                    
                    .track-row {
                        display: flex !important;
                        align-items: center !important;
                        padding: 10px 0 !important;
                        gap: 12px !important;
                    }
                    
                    /* Hide track number on mobile */
                    .track-row > div:first-child {
                        display: none !important;
                    }
                    
                    /* Song info container */
                    .track-row > div:nth-child(2) {
                        flex: 1 !important;
                        min-width: 0 !important;
                        display: flex !important;
                        align-items: center !important;
                        gap: 12px !important;
                    }
                    
                    /* Hide album column */
                    .track-row > div:nth-child(3) {
                        display: none !important;
                    }
                    
                    /* Actions column - simplified */
                    .track-row > div:nth-child(4) {
                        flex-shrink: 0 !important;
                        gap: 4px !important;
                    }
                    
                    /* Hide action buttons except like on mobile */
                    .track-row .action-btn {
                        display: none !important;
                    }
                    
                    /* Keep heart visible if liked */
                    .track-row .action-btn:first-child {
                        display: flex !important;
                        opacity: 1 !important;
                    }
                    
                    /* Song image */
                    .track-row img {
                        width: 48px !important;
                        height: 48px !important;
                        border-radius: 4px;
                    }
                    
                    /* Song text container */
                    .track-row > div:nth-child(2) > div:last-child {
                        min-width: 0;
                    }
                    
                    /* Song title - mobile */
                    .track-row > div:nth-child(2) > div:last-child > div:first-child {
                        font-size: 15px !important;
                        font-weight: 500 !important;
                    }
                    
                    /* Artist name - mobile */
                    .track-row > div:nth-child(2) > div:last-child > div:last-child {
                        font-size: 13px !important;
                    }
                    
                    /* Action bar mobile */
                    .action-bar {
                        gap: 16px !important;
                    }
                }
            `}</style>
        </div>
    );
};

export default PlaylistView;
