import React, { useState } from 'react';
import { usePlayer } from '../context/PlayerContext';
import { Play } from 'lucide-react';

const ImportPlaylist = () => {
    const [url, setUrl] = useState('');
    const { playSong } = usePlayer();

    const handleImport = () => {
        if (!url) return;

        // Create a dummy song object from the URL
        const song = {
            id: url,
            title: 'Imported Song',
            artist: 'YouTube',
            image: '/placeholder.svg',
            previewUrl: null // No preview, force YouTube search/play
        };

        const directSong = {
            ...song,
            isDirectUrl: true,
            url: url
        };

        playSong(directSong);
    };

    return (
        <div className="p-8">
            <h2 className="text-2xl font-bold text-white mb-6">Import Music</h2>
            <div className="glass p-6 rounded-lg max-w-xl">
                <p className="text-gray-300 mb-4">Paste a YouTube link to play it directly.</p>
                <div className="flex gap-4 mb-6">
                    <input
                        type="text"
                        value={url}
                        onChange={(e) => setUrl(e.target.value)}
                        placeholder="https://www.youtube.com/watch?v=..."
                        className="flex-1 bg-white/10 border border-white/20 rounded px-4 py-2 text-white focus:outline-none focus:border-primary"
                    />
                    <button
                        onClick={handleImport}
                        className="bg-primary text-black font-bold py-2 px-6 rounded hover:scale-105 transition-transform flex items-center gap-2"
                    >
                        <Play size={18} fill="currentColor" />
                        Play
                    </button>
                </div>

                <div className="mt-8 p-4 bg-white/5 rounded-lg border border-white/10">
                    <h3 className="text-lg font-bold mb-2">Troubleshooting</h3>
                    <p className="text-sm text-gray-400 mb-4">If you can't hear anything, try this test button to check your system audio.</p>
                    <button
                        onClick={() => {
                            const audio = new Audio('https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3');
                            audio.volume = 0.5;
                            audio.play().catch(e => alert("Audio test failed: " + e.message));
                        }}
                        className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors text-sm"
                    >
                        Test System Audio (Beep)
                    </button>
                </div>
            </div>
        </div>
    );
};

export default ImportPlaylist;
