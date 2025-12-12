import fs from 'fs';
import path from 'path';
import axios from 'axios';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const PROJECT_ROOT = path.join(__dirname, '..');

const CLIENT_ID = process.env.SPOTIFY_CLIENT_ID;
const CLIENT_SECRET = process.env.SPOTIFY_CLIENT_SECRET;

if (!CLIENT_ID || !CLIENT_SECRET) {
    throw new Error('Missing SPOTIFY_CLIENT_ID/SPOTIFY_CLIENT_SECRET environment variables');
}

const getAccessToken = async () => {
    const authString = Buffer.from(`${CLIENT_ID}:${CLIENT_SECRET}`).toString('base64');
    try {
        const response = await axios.post('https://accounts.spotify.com/api/token',
            new URLSearchParams({ grant_type: 'client_credentials' }), {
            headers: {
                'Authorization': `Basic ${authString}`,
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        });
        return response.data.access_token;
    } catch (error) {
        console.error('Error getting access token:', error.response?.data || error.message);
        process.exit(1);
    }
};

const searchTrack = async (token, query, artist) => {
    try {
        // Clean query: remove file extension, parentheses, etc for better search
        const cleanQuery = query.replace(/\.mp3$/i, '')
            .replace(/\(.*\)/g, '')
            .replace(/_/g, ' ')
            .trim();

        const q = artist ? `track:${cleanQuery} artist:${artist}` : `track:${cleanQuery}`;
        const response = await axios.get('https://api.spotify.com/v1/search', {
            headers: { 'Authorization': `Bearer ${token}` },
            params: { q, type: 'track', limit: 1 }
        });
        if (response.data.tracks.items.length > 0) {
            return response.data.tracks.items[0];
        }
        // Fallback: search just by track name
        const response2 = await axios.get('https://api.spotify.com/v1/search', {
            headers: { 'Authorization': `Bearer ${token}` },
            params: { q: cleanQuery, type: 'track', limit: 1 }
        });
        return response2.data.tracks.items[0];
    } catch (error) {
        console.error(`Error searching for ${query}:`, error.message);
        return null;
    }
};

const getSongsFromDir = (dirName, artistName) => {
    const dirPath = path.join(PROJECT_ROOT, 'public', 'music', dirName);
    if (!fs.existsSync(dirPath)) {
        console.warn(`Directory not found: ${dirPath}`);
        return [];
    }

    const files = fs.readdirSync(dirPath).filter(file => file.endsWith('.mp3'));
    return files.map((file, index) => ({
        id: `${dirName.toLowerCase()}-${index + 1}`,
        title: file.replace('.mp3', ''),
        artist: artistName,
        url: `/music/${dirName}/${file}`,
        image: 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80', // Placeholder
        duration: 180 // Placeholder
    }));
};

const albumsData = [
    {
        id: 'kk-playlist',
        title: 'Kk Playlist',
        artist: 'Various Artists',
        image: 'https://images.unsplash.com/photo-1493225255756-d9584f8606e9?w=800&q=80',
        songs: getSongsFromDir('Kk', 'Various Artists')
    },
    {
        id: 'og-ost',
        title: 'OG OST (Original Background Soundtrack)',
        artist: 'Thaman S',
        image: 'https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=800&q=80',
        songs: getSongsFromDir('OG_OST', 'Thaman S')
    }
];

const main = async () => {
    const token = await getAccessToken();
    console.log('Got access token');

    for (const album of albumsData) {
        console.log(`Processing album: ${album.title}`);

        // Try to find a cover for the album itself (using the first song or album name)
        if (album.id === 'kk-playlist') {
            // Kk playlist is mixed, maybe use the first song's album art or a generic one
        } else if (album.id === 'og-ost') {
            const track = await searchTrack(token, 'Hungry Cheetah', 'Thaman S');
            if (track && track.album && track.album.images.length > 0) {
                album.image = track.album.images[0].url;
            }
        }

        for (const song of album.songs) {
            console.log(`  Searching for: ${song.title}`);
            const track = await searchTrack(token, song.title, song.artist);
            if (track) {
                song.image = track.album.images[0]?.url || song.image;
                song.duration = Math.round(track.duration_ms / 1000); // Update duration from API
                song.artist = track.artists.map(a => a.name).join(', '); // Correct artist name
                song.title = track.name; // Correct title casing
                console.log(`    -> Found: ${track.name} (${song.duration}s)`);

                // If it's the KK playlist and we haven't set a good album image yet, use the first found track's image
                if (album.id === 'kk-playlist' && album.image.includes('unsplash') && track.album.images[0]) {
                    album.image = track.album.images[0].url;
                }
            } else {
                console.log('    -> Not found');
                song.image = 'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=800&q=80'; // Fallback
            }
        }
    }

    const output = `export const albums = ${JSON.stringify(albumsData, null, 4)};\n\nexport const songsData = [\n    ...albums[0].songs,\n    ...albums[1].songs\n];`;

    fs.writeFileSync('src/data/songs.js', output);
    console.log('Updated src/data/songs.js');
};

main();
