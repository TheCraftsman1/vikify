import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { App } from '@capacitor/app';
import axios from 'axios';
import { Haptics, ImpactStyle } from '@capacitor/haptics';

import { BACKEND_URL } from '../config';
import {
    saveTokens,
    getTokens,
    clearTokens,
    isTokenExpired,
    saveUserData,
    getUserData
} from '../utils/authStorage';

const AuthContext = createContext();

export const useAuth = () => useContext(AuthContext);

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [accessToken, setAccessToken] = useState(null);
    const [loading, setLoading] = useState(true);
    const [hasCompletedOnboarding, setHasCompletedOnboarding] = useState(false);

    // Use ref to ensure stable backend URL reference
    const backendUrlRef = useRef(BACKEND_URL);

    /**
     * Fetch user profile from Spotify API via backend
     */
    const fetchUserProfile = useCallback(async (token) => {
        console.log('[Auth] Fetching user profile...');
        try {
            const response = await axios.get(`${backendUrlRef.current}/spotify/me`, {
                headers: { Authorization: `Bearer ${token}` },
                timeout: 10000
            });

            console.log('[Auth] Profile response:', response.data);

            if (response.data.success) {
                const userData = response.data.user;
                setUser(userData);
                await saveUserData(userData);
                console.log('[Auth] ✅ User loaded:', userData.display_name || userData.id);
                return true;
            } else {
                console.error('[Auth] ❌ Profile response not successful:', response.data);
                return false;
            }
        } catch (error) {
            console.error('[Auth] ❌ Failed to fetch user profile:', error.message);
            if (error.response?.status === 401) {
                console.log('[Auth] Token invalid, clearing...');
                await clearTokens();
                setAccessToken(null);
                setUser(null);
            }
            return false;
        } finally {
            setLoading(false);
        }
    }, []);

    /**
     * Handle deep links from OAuth redirect (for native apps)
     */
    const handleDeepLink = useCallback(async (url) => {
        console.log('[Auth] 🔗 Handling deep link:', url);

        try {
            // Handle both "vikify://?" and "vikify:?" formats
            // Extract everything after the first "?"
            const queryIndex = url.indexOf('?');
            if (queryIndex === -1) {
                console.log('[Auth] ❌ No query string in deep link');
                setLoading(false);
                return;
            }

            const queryString = url.substring(queryIndex + 1);
            console.log('[Auth] Query string length:', queryString.length);

            const urlParams = new URLSearchParams(queryString);
            const token = urlParams.get('access_token');
            const refreshToken = urlParams.get('refresh_token');
            const expiresIn = urlParams.get('expires_in');
            const authError = urlParams.get('auth_error');

            console.log('[Auth] Parsed - token:', !!token, 'refresh:', !!refreshToken, 'expires:', expiresIn);

            if (authError) {
                console.error('[Auth] ❌ Auth error from callback:', authError);
                setLoading(false);
                return;
            }

            if (token) {
                console.log('[Auth] ✅ Got access token from deep link');

                // Save tokens
                await saveTokens({
                    accessToken: token,
                    refreshToken: refreshToken || '',
                    expiresIn: parseInt(expiresIn || '3600')
                });

                // Set token in state
                setAccessToken(token);

                // Fetch user profile
                const success = await fetchUserProfile(token);

                if (success) {
                    // Haptic feedback on successful login
                    try {
                        await Haptics.impact({ style: ImpactStyle.Medium });
                    } catch (e) { /* Ignore on web */ }

                    console.log('[Auth] ✅ Login complete via Deep Link!');
                } else {
                    console.error('[Auth] ❌ Failed to load user after deep link');
                }
            } else {
                console.log('[Auth] ❌ No access_token in deep link query');
            }
        } catch (error) {
            console.error('[Auth] ❌ Error handling deep link:', error);
            setLoading(false);
        }
    }, [fetchUserProfile]);

    /**
     * Initialize auth state from secure storage or URL params
     */
    const initializeAuth = useCallback(async () => {
        console.log('[Auth] 🚀 Initializing auth...');

        try {
            // Check for OAuth callback params in URL (web/browser redirect)
            const params = new URLSearchParams(window.location.search);
            const urlToken = params.get('access_token');
            const urlRefreshToken = params.get('refresh_token');
            const expiresIn = params.get('expires_in');

            if (urlToken) {
                console.log('[Auth] Found token in URL params (web callback)');
                await saveTokens({
                    accessToken: urlToken,
                    refreshToken: urlRefreshToken || '',
                    expiresIn: parseInt(expiresIn || '3600')
                });
                setAccessToken(urlToken);

                // Clear URL params for security
                window.history.replaceState({}, document.title, window.location.pathname);

                // Fetch user profile
                await fetchUserProfile(urlToken);

                try {
                    await Haptics.impact({ style: ImpactStyle.Medium });
                } catch (e) { /* Ignore */ }

                return;
            }

            // Check secure storage for existing tokens
            console.log('[Auth] Checking stored tokens...');
            const tokens = await getTokens();

            if (tokens?.accessToken) {
                console.log('[Auth] Found stored token');
                const expired = await isTokenExpired();

                if (expired) {
                    console.log('[Auth] Token expired');
                    // For now, clear expired tokens (token refresh can be added later)
                    await clearTokens();
                    setLoading(false);
                } else {
                    console.log('[Auth] Token valid, loading user...');
                    setAccessToken(tokens.accessToken);

                    // Try to load cached user data first (faster startup)
                    const cachedUser = await getUserData();
                    if (cachedUser) {
                        console.log('[Auth] Using cached user data');
                        setUser(cachedUser);
                        setLoading(false);
                    }

                    // Refresh user data in background
                    fetchUserProfile(tokens.accessToken);
                }
            } else {
                console.log('[Auth] No stored tokens');
                setLoading(false);
            }
        } catch (error) {
            console.error('[Auth] ❌ Initialization error:', error);
            setLoading(false);
        }
    }, [fetchUserProfile]);

    // Initialize on mount
    useEffect(() => {
        // Check if onboarding was completed
        const onboarded = localStorage.getItem('vikify_onboarded');
        if (onboarded) {
            setHasCompletedOnboarding(true);
        }

        // Initialize auth
        initializeAuth();

        // Set up deep link listeners for native apps
        let cleanupListener = null;

        const setupDeepLinks = async () => {
            try {
                // Check if app was launched with a URL (cold start)
                const launchUrl = await App.getLaunchUrl();
                console.log('[Auth] Launch URL:', launchUrl);

                if (launchUrl?.url?.startsWith('vikify:')) {
                    console.log('[Auth] Processing launch URL...');
                    await handleDeepLink(launchUrl.url);
                }

                // Listen for URLs while app is running (warm start)
                cleanupListener = await App.addListener('appUrlOpen', (data) => {
                    console.log('[Auth] appUrlOpen event:', data.url);
                    if (data.url.startsWith('vikify:')) {
                        console.log('[Auth] ✅ URL matches vikify: scheme, processing...');
                        handleDeepLink(data.url);
                    } else {
                        console.log('[Auth] ❌ URL does not match vikify: scheme');
                    }
                });
            } catch (e) {
                console.log('[Auth] Deep link setup skipped (web mode):', e.message);
            }
        };

        setupDeepLinks();

        return () => {
            if (cleanupListener?.remove) {
                cleanupListener.remove();
            }
        };
    }, [initializeAuth, handleDeepLink]);

    const completeOnboarding = () => {
        localStorage.setItem('vikify_onboarded', 'true');
        setHasCompletedOnboarding(true);
    };

    const skipOnboarding = () => {
        completeOnboarding();
    };

    /**
     * Secure logout - clears all tokens from encrypted storage
     */
    const logout = async () => {
        console.log('[Auth] Logging out...');
        setUser(null);
        setAccessToken(null);
        await clearTokens();

        try {
            await Haptics.impact({ style: ImpactStyle.Light });
        } catch (e) { /* Ignore */ }
    };

    const isAuthenticated = !!user && !!accessToken;
    const isSpotifyAuthenticated = !!accessToken;

    return (
        <AuthContext.Provider value={{
            user,
            accessToken,
            spotifyToken: accessToken,
            isAuthenticated,
            isSpotifyAuthenticated,
            loading,
            hasCompletedOnboarding,
            completeOnboarding,
            skipOnboarding,
            logout,
            backendUrl: BACKEND_URL
        }}>
            {children}
        </AuthContext.Provider>
    );
};

export default AuthContext;
