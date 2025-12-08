import axios from 'axios';
import { searchYouTube } from './youtube.js';

const RAPIDAPI_KEY = 'db265c87acmsh63e2fe36b9673a3p182b87jsne1fdbe07fddc';
const RAPIDAPI_HOST = 'spotify-downloader9.p.rapidapi.com';

/**
 * Downloads a track from Spotify using RapidAPI
 * @param {Object|string} song - The song object {id, title, artist} or just the ID string
 */
export const downloadSpotifyTrack = async (song) => {
    let trackId = '';
    let query = '';

    if (typeof song === 'string') {
        trackId = song;
    } else {
        trackId = String(song.id || ''); // Convert to string to handle numeric IDs
        query = `${song.title || ''} ${song.artist || ''}`.trim();
    }

    // Spotify IDs are typically 22 alphanumeric characters
    if (trackId.includes('spotify.com/track/')) {
        trackId = trackId.split('track/')[1].split('?')[0];
    }

    const looksLikeSpotifyId = trackId.length > 20 && !trackId.includes(' ') && !/^\d+$/.test(trackId);

    // If it's not a valid Spotify ID, use YouTube directly
    if (!looksLikeSpotifyId) {
        if (query) {
            console.log(`[Spotify] Not a Spotify ID. Using YouTube for: ${query}`);
            return await searchYouTube(query);
        } else {
            console.warn("[Spotify] Invalid ID and no metadata to search.");
            return null;
        }
    }

    // Valid Spotify ID - download from RapidAPI
    const options = {
        method: 'GET',
        url: `https://${RAPIDAPI_HOST}/downloadSong`,
        params: {
            songId: `https://open.spotify.com/track/${trackId}`
        },
        headers: {
            'x-rapidapi-key': RAPIDAPI_KEY,
            'x-rapidapi-host': RAPIDAPI_HOST
        }
    };

    try {
        console.log(`[Spotify] Downloading track ${trackId}...`);
        const response = await axios.request(options);

        // Response structure: { success: true, data: { downloadLink: "url..." } }
        if (response.data?.success && response.data?.data?.downloadLink) {
            console.log(`[Spotify] ✓ Got full track download link!`);
            return response.data.data.downloadLink;
        }

        // Fallback checks
        if (response.data?.data?.downloadLink) {
            return response.data.data.downloadLink;
        }
        if (response.data?.downloadLink) {
            return response.data.downloadLink;
        }

        console.warn("[Spotify] No download link in response. Falling back to YouTube.");
        console.log("Response:", response.data);
        if (query) return await searchYouTube(query);
        return null;

    } catch (error) {
        console.error("[Spotify] Download API Error:", error.message);
        if (query) {
            console.log("[Spotify] Falling back to YouTube...");
            return await searchYouTube(query);
        }
        return null;
    }
};
