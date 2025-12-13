import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { 
    getListeningHistory, 
    addToHistory as addToService, 
    clearHistory as clearService,
    getListeningStats,
    getMostPlayedSongs,
    getTopArtists,
    getRecommendations
} from '../services/historyService';

const HistoryContext = createContext();

export const useHistory = () => useContext(HistoryContext);

export const HistoryProvider = ({ children }) => {
    const [history, setHistory] = useState([]);
    const [stats, setStats] = useState(null);

    useEffect(() => {
        // Load initial history
        setHistory(getListeningHistory());
        setStats(getListeningStats(7)); // Last 7 days stats
    }, []);

    const addToHistory = useCallback((song) => {
        if (!song) return;

        // Update service (localStorage)
        addToService(song);

        // Update local state to reflect changes immediately
        setHistory(getListeningHistory());
        
        // Refresh stats less frequently (only on add)
        setStats(getListeningStats(7));
    }, []);

    const clearHistory = useCallback(() => {
        clearService();
        setHistory([]);
        setStats(null);
    }, []);

    const refreshHistory = useCallback(() => {
        setHistory(getListeningHistory());
        setStats(getListeningStats(7));
    }, []);

    // Get recommendations based on history
    const getPersonalizedRecommendations = useCallback((availableSongs, limit = 10) => {
        return getRecommendations(availableSongs, limit);
    }, []);

    return (
        <HistoryContext.Provider value={{ 
            history, 
            stats,
            addToHistory, 
            clearHistory,
            refreshHistory,
            getMostPlayed: getMostPlayedSongs,
            getTopArtists,
            getRecommendations: getPersonalizedRecommendations
        }}>
            {children}
        </HistoryContext.Provider>
    );
};
