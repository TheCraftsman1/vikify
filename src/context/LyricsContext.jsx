import React, { createContext, useContext, useState, useEffect } from 'react';
import { usePlayer } from './PlayerContext';
import { getLyrics } from '../services/lyricsService';
import { parseLRC } from '../utils/lyrics';

const LyricsContext = createContext();

export const useLyrics = () => useContext(LyricsContext);

export const LyricsProvider = ({ children }) => {
    const { currentSong } = usePlayer();
    const [lyrics, setLyrics] = useState([]); // Array of { time, text }
    const [plainLyrics, setPlainLyrics] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!currentSong) {
            setLyrics([]);
            setPlainLyrics('');
            return;
        }

        const fetchLyrics = async () => {
            setIsLoading(true);
            setError(null);
            try {
                const data = await getLyrics(currentSong);
                if (data) {
                    if (data.syncedLyrics) {
                        setLyrics(parseLRC(data.syncedLyrics));
                    } else {
                        setLyrics([]);
                    }
                    setPlainLyrics(data.plainLyrics || data.syncedLyrics || ''); // Fallback
                } else {
                    setLyrics([]);
                    setPlainLyrics('');
                }
            } catch (err) {
                console.error('Failed to fetch lyrics', err);
                setError(err);
                setLyrics([]);
                setPlainLyrics('');
            } finally {
                setIsLoading(false);
            }
        };

        // Debounce slightly to avoid rapid skips causing fetches
        const timer = setTimeout(fetchLyrics, 500);

        return () => clearTimeout(timer);
    }, [currentSong]);

    return (
        <LyricsContext.Provider value={{ lyrics, plainLyrics, isLoading, error }}>
            {children}
        </LyricsContext.Provider>
    );
};
