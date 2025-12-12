import React, { createContext, useContext, useState, useEffect } from 'react';
import { App } from '@capacitor/app';
import axios from 'axios';

import { BACKEND_URL } from '../config';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [accessToken, setAccessToken] = useState(null);
    const [loading, setLoading] = useState(true);
    const [hasCompletedOnboarding, setHasCompletedOnboarding] = useState(false);

    const backendUrl = BACKEND_URL;

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

        // Listen for Deep Links (Android/iOS)
        try {
            App.addListener('appUrlOpen', (data) => {
                console.log('[Auth] App opened with URL:', data.url);
                if (data.url.includes('vikify://')) {
                    const queryString = data.url.split('?')[1];
                    if (queryString) {
                        const urlParams = new URLSearchParams(queryString);
                        const token = urlParams.get('access_token');
                        const refreshToken = urlParams.get('refresh_token');

                        if (token) {
                            setAccessToken(token);
                            localStorage.setItem('spotify_access_token', token);
                            if (refreshToken) {
                                localStorage.setItem('spotify_refresh_token', refreshToken);
                            }
                            fetchUserProfile(token);
                            console.log('[Auth] Logged in via Deep Link!');
                        }
                    }
                }
            });
        } catch (e) {
            console.log('[Auth] Deep link listener not available (web mode)');
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
