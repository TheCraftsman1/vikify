import React from 'react';
import { X, Plus, Music, Check } from 'lucide-react';
import { usePlaylists } from '../context/PlaylistContext';

const AddToPlaylistModal = ({ isOpen, onClose, song }) => {
    const { playlists, addToPlaylist, createPlaylist } = usePlaylists();

    if (!isOpen || !song) return null;

    const handleAddToPlaylist = (playlistId) => {
        addToPlaylist(playlistId, song);
        onClose();
    };

    const handleCreateAndAdd = () => {
        const newPlaylist = createPlaylist(`My Playlist #${playlists.length + 1}`);
        addToPlaylist(newPlaylist.id, song);
        onClose();
    };

    const isSongInPlaylist = (playlist) => {
        return playlist.songs.some(s => s.id === song.id);
    };

    return (
        <div
            style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                backgroundColor: 'rgba(0,0,0,0.7)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 1000
            }}
            onClick={onClose}
        >
            <div
                style={{
                    backgroundColor: '#282828',
                    borderRadius: '8px',
                    padding: '16px 0',
                    width: '100%',
                    maxWidth: '350px',
                    maxHeight: '400px',
                    overflow: 'hidden',
                    boxShadow: '0 16px 48px rgba(0,0,0,0.5)'
                }}
                onClick={(e) => e.stopPropagation()}
            >
                <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    padding: '0 16px 12px',
                    borderBottom: '1px solid rgba(255,255,255,0.1)'
                }}>
                    <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Add to playlist</h3>
                    <button
                        onClick={onClose}
                        style={{ padding: '4px', color: '#b3b3b3' }}
                        onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                        onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                    >
                        <X size={20} />
                    </button>
                </div>

                {/* Song preview */}
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '12px',
                    padding: '12px 16px',
                    borderBottom: '1px solid rgba(255,255,255,0.1)'
                }}>
                    <img
                        src={song.image}
                        alt={song.title}
                        style={{ width: '40px', height: '40px', borderRadius: '4px', objectFit: 'cover' }}
                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                    />
                    <div style={{ minWidth: 0 }}>
                        <div style={{
                            color: '#fff',
                            fontSize: '14px',
                            fontWeight: 500,
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                        }}>{song.title}</div>
                        <div style={{
                            color: '#b3b3b3',
                            fontSize: '12px',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                        }}>{song.artist}</div>
                    </div>
                </div>

                {/* Playlist list */}
                <div style={{ maxHeight: '200px', overflowY: 'auto' }}>
                    {/* Create new playlist option */}
                    <button
                        onClick={handleCreateAndAdd}
                        style={{
                            width: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '12px',
                            padding: '12px 16px',
                            cursor: 'pointer',
                            transition: 'background 0.2s'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                    >
                        <div style={{
                            width: '40px',
                            height: '40px',
                            borderRadius: '4px',
                            backgroundColor: '#3e3e3e',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}>
                            <Plus size={20} color="#b3b3b3" />
                        </div>
                        <span style={{ color: '#fff', fontWeight: 500 }}>New playlist</span>
                    </button>

                    {/* Existing playlists */}
                    {playlists.map((playlist) => {
                        const inPlaylist = isSongInPlaylist(playlist);
                        return (
                            <button
                                key={playlist.id}
                                onClick={() => !inPlaylist && handleAddToPlaylist(playlist.id)}
                                disabled={inPlaylist}
                                style={{
                                    width: '100%',
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '12px',
                                    padding: '12px 16px',
                                    cursor: inPlaylist ? 'default' : 'pointer',
                                    opacity: inPlaylist ? 0.5 : 1,
                                    transition: 'background 0.2s'
                                }}
                                onMouseEnter={(e) => !inPlaylist && (e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)')}
                                onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                            >
                                <div style={{
                                    width: '40px',
                                    height: '40px',
                                    borderRadius: '4px',
                                    overflow: 'hidden',
                                    flexShrink: 0
                                }}>
                                    {playlist.image ? (
                                        <img
                                            src={playlist.image}
                                            alt={playlist.title}
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
                                            <Music size={16} color="#7f7f7f" />
                                        </div>
                                    )}
                                </div>
                                <div style={{ flex: 1, textAlign: 'left' }}>
                                    <div style={{ color: '#fff', fontWeight: 500 }}>{playlist.title}</div>
                                    <div style={{ color: '#b3b3b3', fontSize: '12px' }}>{playlist.songs.length} songs</div>
                                </div>
                                {inPlaylist && (
                                    <Check size={18} color="#1db954" />
                                )}
                            </button>
                        );
                    })}

                    {playlists.length === 0 && (
                        <div style={{ padding: '24px 16px', textAlign: 'center', color: '#b3b3b3' }}>
                            <p>No playlists yet</p>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

export default AddToPlaylistModal;
