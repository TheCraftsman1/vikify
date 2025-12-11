import React, { useState } from 'react';
import { X, Music, Link as LinkIcon, Loader } from 'lucide-react';
import { usePlaylists } from '../context/PlaylistContext';
import { getPlaylist, getAlbum } from '../services/spotify';

const CreatePlaylistModal = ({ isOpen, onClose }) => {
    const [mode, setMode] = useState('create'); // 'create' or 'import'
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [spotifyLink, setSpotifyLink] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState('');

    const { createPlaylist } = usePlaylists();

    if (!isOpen) return null;

    const handleCreate = () => {
        if (name.trim()) {
            createPlaylist(
                name.trim(),
                description.trim(),
                null // No custom image for empty playlist
            );
            resetForm();
            onClose();
        }
    };

    const handleImport = async () => {
        if (!spotifyLink.trim()) return;

        setIsLoading(true);
        setError('');

        try {
            // Parse URL
            console.log('[Import] Parsing URL:', spotifyLink);
            const url = new URL(spotifyLink);
            const pathParts = url.pathname.split('/');
            console.log('[Import] Path parts:', pathParts);

            const type = pathParts[pathParts.length - 2];
            const id = pathParts[pathParts.length - 1];
            console.log('[Import] Type:', type, 'ID:', id);

            if (!id || (type !== 'playlist' && type !== 'album')) {
                throw new Error('Invalid Spotify link. Please use a Playlist or Album link.');
            }

            let data;
            if (type === 'playlist') {
                console.log('[Import] Fetching playlist...');
                data = await getPlaylist(id);
            } else {
                console.log('[Import] Fetching album...');
                data = await getAlbum(id);
            }

            console.log('[Import] Received data:', data ? data.title : 'null');

            if (!data) {
                throw new Error('Could not fetch data. Check the link and try again.');
            }

            // Create playlist with fetched data
            createPlaylist(
                data.title,
                data.description,
                data.image,
                data.songs
            );

            resetForm();
            onClose();

        } catch (err) {
            console.error('[Import] Error:', err);
            setError(err.message || 'Failed to import playlist');
        } finally {
            setIsLoading(false);
        }
    };

    const resetForm = () => {
        setName('');
        setDescription('');
        setSpotifyLink('');
        setMode('create');
        setError('');
        setIsLoading(false);
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter') {
            if (mode === 'create' && name.trim()) handleCreate();
            if (mode === 'import' && spotifyLink.trim()) handleImport();
        }
        if (e.key === 'Escape') {
            onClose();
        }
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
                    padding: '24px',
                    width: '100%',
                    maxWidth: '450px',
                    boxShadow: '0 16px 48px rgba(0,0,0,0.5)'
                }}
                onClick={(e) => e.stopPropagation()}
            >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                    <h2 style={{ fontSize: '24px', fontWeight: 700 }}>New Playlist</h2>
                    <button
                        onClick={onClose}
                        style={{
                            padding: '8px',
                            color: '#b3b3b3',
                            borderRadius: '50%'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
                        onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
                    >
                        <X size={24} />
                    </button>
                </div>

                {/* Tabs */}
                <div style={{
                    display: 'flex',
                    gap: '16px',
                    marginBottom: '24px',
                    borderBottom: '1px solid rgba(255,255,255,0.1)'
                }}>
                    <button
                        onClick={() => setMode('create')}
                        style={{
                            padding: '8px 4px',
                            background: 'none',
                            color: mode === 'create' ? '#1db954' : '#b3b3b3',
                            borderBottom: mode === 'create' ? '2px solid #1db954' : '2px solid transparent',
                            fontWeight: 700,
                            fontSize: '14px'
                        }}
                    >
                        Create Empty
                    </button>
                    <button
                        onClick={() => setMode('import')}
                        style={{
                            padding: '8px 4px',
                            background: 'none',
                            color: mode === 'import' ? '#1db954' : '#b3b3b3',
                            borderBottom: mode === 'import' ? '2px solid #1db954' : '2px solid transparent',
                            fontWeight: 700,
                            fontSize: '14px'
                        }}
                    >
                        Import from Spotify
                    </button>
                </div>

                {mode === 'create' ? (
                    <>
                        <div style={{
                            width: '160px',
                            height: '160px',
                            margin: '0 auto 24px',
                            borderRadius: '4px',
                            background: 'linear-gradient(135deg, #333, #555)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxShadow: '0 8px 24px rgba(0,0,0,0.4)'
                        }}>
                            <Music size={64} color="#7f7f7f" />
                        </div>

                        <input
                            type="text"
                            value={name}
                            onChange={(e) => setName(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder="Playlist name"
                            autoFocus
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                fontSize: '16px',
                                backgroundColor: '#3e3e3e',
                                border: '1px solid transparent',
                                borderRadius: '4px',
                                color: '#fff',
                                marginBottom: '12px',
                                outline: 'none'
                            }}
                            onFocus={(e) => e.target.style.borderColor = '#fff'}
                            onBlur={(e) => e.target.style.borderColor = 'transparent'}
                        />

                        <input
                            type="text"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder="Add an optional description"
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                fontSize: '14px',
                                backgroundColor: '#3e3e3e',
                                border: '1px solid transparent',
                                borderRadius: '4px',
                                color: '#fff',
                                marginBottom: '24px',
                                outline: 'none'
                            }}
                            onFocus={(e) => e.target.style.borderColor = '#fff'}
                            onBlur={(e) => e.target.style.borderColor = 'transparent'}
                        />
                    </>
                ) : (
                    <>
                        <div style={{
                            width: '100%',
                            padding: '32px',
                            margin: '0 auto 24px',
                            borderRadius: '8px',
                            background: 'rgba(255,255,255,0.05)',
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                            justifyContent: 'center',
                            textAlign: 'center'
                        }}>
                            <LinkIcon size={48} color="#1db954" style={{ marginBottom: '16px' }} />
                            <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '8px' }}>Paste a Spotify Link</h3>
                            <p style={{ color: '#b3b3b3', fontSize: '13px' }}>
                                Import any public Playlist or Album directly into your library.
                            </p>
                        </div>

                        <input
                            type="text"
                            value={spotifyLink}
                            onChange={(e) => { setSpotifyLink(e.target.value); setError(''); }}
                            onKeyDown={handleKeyDown}
                            placeholder="https://open.spotify.com/playlist/..."
                            autoFocus
                            style={{
                                width: '100%',
                                padding: '12px 16px',
                                fontSize: '14px',
                                backgroundColor: '#3e3e3e',
                                border: error ? '1px solid #ff5555' : '1px solid transparent',
                                borderRadius: '4px',
                                color: '#fff',
                                marginBottom: error ? '8px' : '24px',
                                outline: 'none'
                            }}
                            onFocus={(e) => e.target.style.borderColor = error ? '#ff5555' : '#fff'}
                            onBlur={(e) => e.target.style.borderColor = error ? '#ff5555' : 'transparent'}
                        />

                        {error && (
                            <div style={{ color: '#ff5555', fontSize: '13px', marginBottom: '24px', paddingLeft: '4px' }}>
                                {error}
                            </div>
                        )}
                    </>
                )}

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '12px' }}>
                    <button
                        onClick={onClose}
                        style={{
                            padding: '12px 32px',
                            fontSize: '14px',
                            fontWeight: 700,
                            backgroundColor: 'transparent',
                            color: '#fff',
                            borderRadius: '32px',
                            cursor: 'pointer'
                        }}
                        onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.02)'}
                        onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
                    >
                        Cancel
                    </button>

                    {mode === 'create' ? (
                        <button
                            onClick={handleCreate}
                            disabled={!name.trim()}
                            style={{
                                padding: '12px 32px',
                                fontSize: '14px',
                                fontWeight: 700,
                                backgroundColor: name.trim() ? '#1db954' : '#535353',
                                color: name.trim() ? '#000' : '#b3b3b3',
                                borderRadius: '32px',
                                cursor: name.trim() ? 'pointer' : 'not-allowed',
                                transition: 'all 0.2s'
                            }}
                            onMouseEnter={(e) => name.trim() && (e.currentTarget.style.backgroundColor = '#1ed760')}
                            onMouseLeave={(e) => name.trim() && (e.currentTarget.style.backgroundColor = '#1db954')}
                        >
                            Create
                        </button>
                    ) : (
                        <button
                            onClick={handleImport}
                            disabled={!spotifyLink.trim() || isLoading}
                            style={{
                                padding: '12px 32px',
                                fontSize: '14px',
                                fontWeight: 700,
                                backgroundColor: spotifyLink.trim() && !isLoading ? '#1db954' : '#535353',
                                color: spotifyLink.trim() && !isLoading ? '#000' : '#b3b3b3',
                                borderRadius: '32px',
                                cursor: spotifyLink.trim() && !isLoading ? 'pointer' : 'not-allowed',
                                transition: 'all 0.2s',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px'
                            }}
                            onMouseEnter={(e) => spotifyLink.trim() && !isLoading && (e.currentTarget.style.backgroundColor = '#1ed760')}
                            onMouseLeave={(e) => spotifyLink.trim() && !isLoading && (e.currentTarget.style.backgroundColor = '#1db954')}
                        >
                            {isLoading ? <Loader size={18} className="spin" /> : 'Import'}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

export default CreatePlaylistModal;
