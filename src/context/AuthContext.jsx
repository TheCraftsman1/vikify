import React, { createContext, useContext, useState, useEffect } from 'react';
import axios from 'axios';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [accessToken, setAccessToken] = useState(null);
    const [loading, setLoading] = useState(true);
    const [hasCompletedOnboarding, setHasCompletedOnboarding] = useState(false);

    const backendUrl = import.meta.env.VITE_BACKEND_URL || 'http://127.0.0.1:5000';

    // Check for tokens in URL (OAuth callback) or localStorage on mount
    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const token = params.get('access_token');
        const refreshToken = params.get('refresh_token');

        if (token) {
            // Got tokens from OAuth callback
            setAccessToken(token);
            localStorage.setItem('spotify_access_token', token);
            if (refreshToken) {
                localStorage.setItem('spotify_refresh_token', refreshToken);
            }
            // Clear URL params
            window.history.replaceState({}, document.title, window.location.pathname);
            // Fetch user profile
            fetchUserProfile(token);
        } else {
            // Check localStorage
            const savedToken = localStorage.getItem('spotify_access_token');
            if (savedToken) {
                setAccessToken(savedToken);
                fetchUserProfile(savedToken);
            } else {
                setLoading(false);
            }
        }

        // Check if onboarding was completed
        const onboarded = localStorage.getItem('vikify_onboarded');
        if (onboarded) {
            setHasCompletedOnboarding(true);
        }
    }, []);

    const fetchUserProfile = async (token) => {
        try {
            const response = await axios.get(`${backendUrl}/spotify/me`, {
                headers: { Authorization: `Bearer ${token}` }
            });
            if (response.data.success) {
                setUser(response.data.user);
            }
        } catch (error) {
            console.error('Failed to fetch user profile:', error);
            // Token might be expired, clear it
            logout();
        } finally {
            setLoading(false);
        }
    };

    const completeOnboarding = () => {
        localStorage.setItem('vikify_onboarded', 'true');
        setHasCompletedOnboarding(true);
    };

    const skipOnboarding = () => {
        completeOnboarding();
    };

    const logout = () => {
        setUser(null);
        setAccessToken(null);
        localStorage.removeItem('spotify_access_token');
        localStorage.removeItem('spotify_refresh_token');
    };

    const isAuthenticated = !!user && !!accessToken;

    return (
        <AuthContext.Provider value={{
            user,
            accessToken,
            isAuthenticated,
            loading,
            hasCompletedOnboarding,
            completeOnboarding,
            skipOnboarding,
            logout,
            backendUrl
        }}>
            {children}
        </AuthContext.Provider>
    );
};

export default AuthContext;
