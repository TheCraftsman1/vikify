import React, { createContext, useContext, useState, useEffect } from 'react';

const LikedSongsContext = createContext();

export const useLikedSongs = () => useContext(LikedSongsContext);

const STORAGE_KEY = 'vikify-liked-songs';

export const LikedSongsProvider = ({ children }) => {
    const [likedSongs, setLikedSongs] = useState([]);

    // Load from localStorage on mount
    useEffect(() => {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved) {
            try {
                setLikedSongs(JSON.parse(saved));
                console.log('[LikedSongs] Loaded', JSON.parse(saved).length, 'liked songs');
            } catch (e) {
                console.error('[LikedSongs] Error parsing saved data:', e);
            }
        }
    }, []);

    // Save to localStorage whenever likedSongs changes
    useEffect(() => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(likedSongs));
    }, [likedSongs]);

    /**
     * Toggle like status for a song
     * @param {Object} song - Song object with id, title, artist, image, etc.
     */
    const toggleLike = (song) => {
        setLikedSongs(prev => {
            const isLiked = prev.some(s => s.id === song.id);
            if (isLiked) {
                console.log('[LikedSongs] Unliked:', song.title);
                return prev.filter(s => s.id !== song.id);
            } else {
                console.log('[LikedSongs] Liked:', song.title);
                return [...prev, { ...song, likedAt: Date.now() }];
            }
        });
    };

    /**
     * Check if a song is liked
     * @param {string} songId - Song ID
     * @returns {boolean}
     */
    const isLiked = (songId) => {
        return likedSongs.some(s => s.id === songId);
    };

    /**
     * Get all liked songs sorted by most recent
     * @returns {Array}
     */
    const getLikedSongs = () => {
        return [...likedSongs].sort((a, b) => (b.likedAt || 0) - (a.likedAt || 0));
    };

    return (
        <LikedSongsContext.Provider value={{
            likedSongs,
            toggleLike,
            isLiked,
            getLikedSongs,
            likedCount: likedSongs.length
        }}>
            {children}
        </LikedSongsContext.Provider>
    );
};
