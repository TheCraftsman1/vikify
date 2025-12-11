import axios from 'axios';
import { BACKEND_URL } from '../config';

const CLIENT_ID = '242fffd1ca15426ab8c7396a6931b780';
const CLIENT_SECRET = '5a479c5370ba48bc860048d89878ee4d';

let accessToken = null;
let tokenExpirationTime = null;

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
 * Get a valid Spotify Access Token using Client Credentials Flow
 */
const getAccessToken = async () => {
    if (accessToken && tokenExpirationTime && Date.now() < tokenExpirationTime) {
        return accessToken;
    }

    try {
        const response = await axios.post('https://accounts.spotify.com/api/token',
            new URLSearchParams({
                'grant_type': 'client_credentials'
            }), {
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                'Authorization': 'Basic ' + btoa(CLIENT_ID + ':' + CLIENT_SECRET)
            }
        });

        accessToken = response.data.access_token;
        // Set expiration 1 minute before actual expiry to be safe
        tokenExpirationTime = Date.now() + (response.data.expires_in - 60) * 1000;
        return accessToken;
    } catch (error) {
        console.error('Error fetching Spotify access token:', error);
        return null;
    }
};

/**
 * Fetch featured playlists from Spotify
 */
export const getFeaturedPlaylists = async (limit = 10) => {
    const token = await getAccessToken();
    if (!token) return [];

    try {
        // Use search endpoint as featured-playlists endpoint is often restricted/404
        const response = await axios.get(`https://api.spotify.com/v1/search?q=featured&type=playlist&limit=${limit}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        return response.data.playlists.items.map(mapSpotifyPlaylist);
    } catch (error) {
        console.error('Error fetching featured playlists:', error);
        return [];
    }
};

/**
 * Fetch new releases
 */
export const getNewReleases = async (limit = 10) => {
    const token = await getAccessToken();
    if (!token) return [];

    try {
        const response = await axios.get(`https://api.spotify.com/v1/browse/new-releases?limit=${limit}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        return response.data.albums.items.map(album => ({
            id: album.id,
            title: album.name,
            description: album.artists.map(a => a.name).join(', '),
            image: album.images[0]?.url,
            type: 'album',
            artist: album.artists[0].name
        }));
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

    try {
        const response = await axios.get(`${BACKEND_URL}/spotify/me/playlists`, {
            headers: { 'Authorization': `Bearer ${token}` }
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
