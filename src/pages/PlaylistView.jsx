import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Play, Pause, Clock, Download, Heart, Shuffle, MoreHorizontal, Plus, Trash2, Music, Loader } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { usePlaylists } from '../context/PlaylistContext';
import { useOffline } from '../context/OfflineContext';
import { albums } from '../data/songs';
import { downloadSong } from '../utils/download';
import AddToPlaylistModal from '../components/AddToPlaylistModal';

const PlaylistView = () => {
    const { id } = useParams();
    const navigate = useNavigate();
    const { playSong, currentSong, isPlaying, togglePlay, shufflePlay, shuffle } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();
    const { getPlaylist, removeFromPlaylist, deletePlaylist } = usePlaylists();
    const { isSongOffline, downloadPlaylist, isDownloading } = useOffline();

    const [addToPlaylistSong, setAddToPlaylistSong] = useState(null);

    // Check if this is a custom playlist or an album
    const customPlaylist = getPlaylist(id);
    const album = customPlaylist || albums.find(a => a.id === id);

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

    const totalDuration = album.songs.reduce((acc, s) => acc + (s.duration || 0), 0);
    const isCurrentAlbumPlaying = currentSong && album.songs.some(s => s.id === currentSong.id);

    const handlePlayClick = () => {
        if (isCurrentAlbumPlaying) {
            togglePlay();
        } else if (album.songs.length > 0) {
            playSong(album.songs[0], album.songs);
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

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '120px' }}>
                {/* Playlist Header */}
                <div style={{ display: 'flex', alignItems: 'flex-end', gap: '24px', marginBottom: '24px', paddingTop: '32px' }}>
                    <div style={{
                        width: '232px',
                        height: '232px',
                        borderRadius: '4px',
                        overflow: 'hidden',
                        boxShadow: '0 4px 60px rgba(0,0,0,0.5)',
                        flexShrink: 0
                    }}>
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
                                <Music size={80} color="#7f7f7f" />
                            </div>
                        )}
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                        <span style={{ fontSize: '14px', fontWeight: 700, textTransform: 'uppercase' }}>
                            {customPlaylist ? 'Playlist' : 'Playlist'}
                        </span>
                        <h1 style={{
                            fontSize: '72px',
                            fontWeight: 900,
                            marginTop: '8px',
                            marginBottom: '16px',
                            lineHeight: 0.9,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                        }}>{album.title}</h1>
                        <p style={{ color: '#b3b3b3', fontSize: '14px', marginBottom: '8px' }}>
                            {album.description || 'A collection of amazing songs'}
                        </p>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: '14px' }}>
                            <span style={{ fontWeight: 700 }}>{album.artist || 'Your playlist'}</span>
                            <span style={{ color: '#b3b3b3' }}>•</span>
                            <span style={{ color: '#b3b3b3' }}>{album.songs.length} songs,</span>
                            <span style={{ color: '#b3b3b3' }}>about {Math.floor(totalDuration / 60)} min</span>
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
                @media (max-width: 768px) {
                    .hide-mobile { display: none !important; }
                }
            `}</style>
        </div>
    );
};

export default PlaylistView;
