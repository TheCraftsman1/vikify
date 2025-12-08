import React, { createContext, useContext, useState, useEffect } from 'react';

const PlaylistContext = createContext();

export const usePlaylists = () => useContext(PlaylistContext);

const STORAGE_KEY = 'vikify-custom-playlists';

export const PlaylistProvider = ({ children }) => {
    const [playlists, setPlaylists] = useState([]);

    // Load from localStorage on mount
    useEffect(() => {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved) {
            try {
                setPlaylists(JSON.parse(saved));
                console.log('[Playlists] Loaded', JSON.parse(saved).length, 'playlists');
            } catch (e) {
                console.error('[Playlists] Error parsing saved data:', e);
            }
        }
    }, []);

    // Save to localStorage whenever playlists change
    useEffect(() => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(playlists));
    }, [playlists]);

    /**
     * Create a new playlist
     * @param {string} name - Playlist name
     * @param {string} description - Optional description
     * @returns {Object} Created playlist
     */
    const createPlaylist = (name, description = '') => {
        const newPlaylist = {
            id: `custom-${Date.now()}`,
            title: name,
            description: description || `Created ${new Date().toLocaleDateString()}`,
            songs: [],
            createdAt: Date.now(),
            updatedAt: Date.now(),
            image: null, // Will use first song's image or gradient
            isCustom: true
        };

        setPlaylists(prev => [...prev, newPlaylist]);
        console.log('[Playlists] Created:', name);
        return newPlaylist;
    };

    /**
     * Delete a playlist
     * @param {string} playlistId - Playlist ID
     */
    const deletePlaylist = (playlistId) => {
        setPlaylists(prev => prev.filter(p => p.id !== playlistId));
        console.log('[Playlists] Deleted:', playlistId);
    };

    /**
     * Add a song to a playlist
     * @param {string} playlistId - Playlist ID
     * @param {Object} song - Song object
     */
    const addToPlaylist = (playlistId, song) => {
        setPlaylists(prev => prev.map(p => {
            if (p.id === playlistId) {
                // Check if song already exists
                if (p.songs.some(s => s.id === song.id)) {
                    console.log('[Playlists] Song already in playlist');
                    return p;
                }
                const updatedSongs = [...p.songs, { ...song, addedAt: Date.now() }];
                console.log('[Playlists] Added', song.title, 'to', p.title);
                return {
                    ...p,
                    songs: updatedSongs,
                    updatedAt: Date.now(),
                    // Use first song's image as playlist image
                    image: p.image || song.image
                };
            }
            return p;
        }));
    };

    /**
     * Remove a song from a playlist
     * @param {string} playlistId - Playlist ID
     * @param {string} songId - Song ID
     */
    const removeFromPlaylist = (playlistId, songId) => {
        setPlaylists(prev => prev.map(p => {
            if (p.id === playlistId) {
                const updatedSongs = p.songs.filter(s => s.id !== songId);
                return {
                    ...p,
                    songs: updatedSongs,
                    updatedAt: Date.now(),
                    // Update image if we removed the first song
                    image: updatedSongs[0]?.image || null
                };
            }
            return p;
        }));
    };

    /**
     * Get a playlist by ID
     * @param {string} playlistId - Playlist ID
     * @returns {Object|null}
     */
    const getPlaylist = (playlistId) => {
        return playlists.find(p => p.id === playlistId) || null;
    };

    /**
     * Rename a playlist
     * @param {string} playlistId - Playlist ID
     * @param {string} newName - New name
     */
    const renamePlaylist = (playlistId, newName) => {
        setPlaylists(prev => prev.map(p => {
            if (p.id === playlistId) {
                return { ...p, title: newName, updatedAt: Date.now() };
            }
            return p;
        }));
    };

    return (
        <PlaylistContext.Provider value={{
            playlists,
            createPlaylist,
            deletePlaylist,
            addToPlaylist,
            removeFromPlaylist,
            getPlaylist,
            renamePlaylist
        }}>
            {children}
        </PlaylistContext.Provider>
    );
};
