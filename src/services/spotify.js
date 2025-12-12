import axios from 'axios';
import { BACKEND_URL } from '../config';

import {
    getCachedFeaturedPlaylists,
    getCachedNewReleases,
    setCachedFeaturedPlaylists,
    setCachedNewReleases
} from '../utils/spotifyCache';

import { isOffline } from '../utils/net';

// Helper Mappers (defined first or hoisted)
const mapSpotifyPlaylist = (playlist) => ({
    id: playlist.id,
    title: playlist.name,
    description: playlist.description || `By ${playlist.owner.display_name}`,
    image: playlist.images[0]?.url,
    type: 'playlist',
    songs: [] // Will be populated if fetching full details
});

const mapSpotifyTrack = (track) => ({
    id: track.id,
    title: track.name,
    artist: track.artists.map(a => a.name).join(', '),
    image: track.album.images[0]?.url, // Use album art
    duration: track.duration_ms / 1000,
    album: track.album.name,
    isSpotify: true // Flag to indicate this needs YouTube search
});


/**
 * Fetch featured playlists from Spotify
 */
export const getFeaturedPlaylists = async (limit = 10) => {
    try {
        const cached = getCachedFeaturedPlaylists(limit);
        if (cached) return cached;

        if (isOffline()) return [];

        const response = await axios.get(`${BACKEND_URL}/spotify/featured-playlists`, {
            params: { limit },
            timeout: 12000
        });

        const playlists = response.data?.success ? (response.data.playlists || []) : [];
        setCachedFeaturedPlaylists(limit, playlists);
        return playlists;
    } catch (error) {
        console.error('Error fetching featured playlists:', error);
        return [];
    }
};

/**
 * Fetch new releases
 */
export const getNewReleases = async (limit = 10) => {
    try {
        const cached = getCachedNewReleases(limit);
        if (cached) return cached;

        if (isOffline()) return [];

        const response = await axios.get(`${BACKEND_URL}/spotify/new-releases`, {
            params: { limit },
            timeout: 12000
        });

        const albums = response.data?.success ? (response.data.albums || []) : [];
        setCachedNewReleases(limit, albums);
        return albums;
    } catch (error) {
        console.error('Error fetching new releases:', error);
        return [];
    }
};

/**
 * Fetch a specific playlist by ID (via backend proxy to bypass CORS)
 */
export const getPlaylist = async (playlistId) => {
    console.log('[Spotify] Fetching playlist via proxy:', playlistId);

    try {
        const response = await axios.get(`${BACKEND_URL}/spotify/playlist/${playlistId}`);

        if (response.data.success) {
            console.log('[Spotify] ✅ Playlist fetched:', response.data.data.title);
            return response.data.data;
        } else {
            console.error('[Spotify] Backend error:', response.data.error);
            return null;
        }
    } catch (error) {
        console.error('[Spotify] Proxy error:', error.response?.data || error.message);
        return null;
    }
};

/**
 * Fetch a specific Album by ID (via backend proxy to bypass CORS)
 */
export const getAlbum = async (albumId) => {
    console.log('[Spotify] Fetching album via proxy:', albumId);

    try {
        const response = await axios.get(`${BACKEND_URL}/spotify/album/${albumId}`);

        if (response.data.success) {
            console.log('[Spotify] ✅ Album fetched:', response.data.data.title);
            return response.data.data;
        } else {
            console.error('[Spotify] Backend error:', response.data.error);
            return null;
        }
    } catch (error) {
        console.error('[Spotify] Proxy error:', error.response?.data || error.message);
        return null;
    }
};

/**
 * Fetch User Playlists (requires user to be logged in and token passed)
 */
export const getUserPlaylists = async (token) => {
    if (!token) {
        console.warn("getUserPlaylists called without token");
        return [];
    }

    if (isOffline()) return [];

    try {
        const response = await axios.get(`${BACKEND_URL}/spotify/me/playlists`, {
            headers: { 'Authorization': `Bearer ${token}` }
            ,timeout: 12000
        });

        if (response.data.success) {
            // Map backend-formatted playlists to frontend format if needed
            // The backend already returns { id, title, image, tracksCount }
            return response.data.playlists.map(p => ({
                id: p.id,
                title: p.title,
                description: `${p.tracksCount} songs`,
                image: p.image,
                type: 'playlist',
                songs: []
            }));
        } else {
            console.error('Error fetching user playlists:', response.data.error);
            return [];
        }
    } catch (error) {
        console.error('Network error fetching user playlists:', error);
        return [];
    }
};

/**
 * Get Recommendations based on Seed Tracks
 */
export const getRecommendations = async (seedTrackId, token) => {
    if (!token || !seedTrackId) return [];

    if (isOffline()) return [];

    try {
        const response = await axios.get(`${BACKEND_URL}/spotify/recommendations`, {
            headers: { 'Authorization': `Bearer ${token}` },
            params: { seed_tracks: seedTrackId },
            timeout: 12000
        });

        if (response.data.success) {
            return response.data.tracks;
        }
        return [];
    } catch (error) {
        console.error('Error in getRecommendations:', error);
        return [];
    }
};

/**
 * Search Spotify for Playlists (Proxy)
 */
export const searchSpotify = async (query, token, type = 'playlist') => {
    if (!token || !query) return { playlists: [], songs: [] };

    if (isOffline()) return { playlists: [], songs: [] };

    try {
        const response = await axios.get(`${BACKEND_URL}/spotify/search`, {
            headers: { 'Authorization': `Bearer ${token}` },
            params: { q: query, type: type },
            timeout: 12000
        });

        if (response.data.success) {
            return response.data.results;
        }
        return { playlists: [] };
    } catch (error) {
        console.error('Error searching Spotify:', error);
        return { playlists: [] };
    }
};
