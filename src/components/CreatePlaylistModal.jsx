import React, { useState } from 'react';
import { X, Music } from 'lucide-react';
import { usePlaylists } from '../context/PlaylistContext';

const CreatePlaylistModal = ({ isOpen, onClose }) => {
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const { createPlaylist } = usePlaylists();

    if (!isOpen) return null;

    const handleCreate = () => {
        if (name.trim()) {
            createPlaylist(name.trim(), description.trim());
            setName('');
            setDescription('');
            onClose();
        }
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter' && name.trim()) {
            handleCreate();
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
                    maxWidth: '400px',
                    boxShadow: '0 16px 48px rgba(0,0,0,0.5)'
                }}
                onClick={(e) => e.stopPropagation()}
            >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
                    <h2 style={{ fontSize: '24px', fontWeight: 700 }}>Create Playlist</h2>
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

                <div style={{
                    width: '200px',
                    height: '200px',
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
                </div>
            </div>
        </div>
    );
};

export default CreatePlaylistModal;
