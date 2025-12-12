/**
 * Secure Auth Storage Utility
 * Uses Capacitor Preferences (encrypted on Android) for secure token storage
 * Best practice for 2025: Never store auth tokens in localStorage on mobile
 */

import { Preferences } from '@capacitor/preferences';
import { Capacitor } from '@capacitor/core';

const KEYS = {
    ACCESS_TOKEN: 'spotify_access_token',
    REFRESH_TOKEN: 'spotify_refresh_token',
    TOKEN_EXPIRY: 'spotify_token_expiry',
    USER_DATA: 'spotify_user_data'
};

/**
 * Save authentication tokens securely
 * @param {Object} tokens - { accessToken, refreshToken, expiresIn }
 */
export const saveTokens = async (tokens) => {
    const { accessToken, refreshToken, expiresIn } = tokens;
    const expiryTime = Date.now() + (expiresIn || 3600) * 1000;

    try {
        await Preferences.set({ key: KEYS.ACCESS_TOKEN, value: accessToken });
        if (refreshToken) {
            await Preferences.set({ key: KEYS.REFRESH_TOKEN, value: refreshToken });
        }
        await Preferences.set({ key: KEYS.TOKEN_EXPIRY, value: expiryTime.toString() });
        console.log('[AuthStorage] Tokens saved securely');
        return true;
    } catch (error) {
        console.error('[AuthStorage] Failed to save tokens:', error);
        // Fallback to localStorage for web testing
        if (!Capacitor.isNativePlatform()) {
            localStorage.setItem(KEYS.ACCESS_TOKEN, accessToken);
            if (refreshToken) localStorage.setItem(KEYS.REFRESH_TOKEN, refreshToken);
            localStorage.setItem(KEYS.TOKEN_EXPIRY, expiryTime.toString());
        }
        return false;
    }
};

/**
 * Get stored authentication tokens
 * @returns {Promise<Object|null>} - { accessToken, refreshToken, expiryTime } or null
 */
export const getTokens = async () => {
    try {
        const { value: accessToken } = await Preferences.get({ key: KEYS.ACCESS_TOKEN });
        const { value: refreshToken } = await Preferences.get({ key: KEYS.REFRESH_TOKEN });
        const { value: expiryTime } = await Preferences.get({ key: KEYS.TOKEN_EXPIRY });

        if (!accessToken) {
            // Fallback: Check localStorage for web
            if (!Capacitor.isNativePlatform()) {
                const lsToken = localStorage.getItem(KEYS.ACCESS_TOKEN);
                if (lsToken) {
                    return {
                        accessToken: lsToken,
                        refreshToken: localStorage.getItem(KEYS.REFRESH_TOKEN),
                        expiryTime: parseInt(localStorage.getItem(KEYS.TOKEN_EXPIRY) || '0')
                    };
                }
            }
            return null;
        }

        return {
            accessToken,
            refreshToken,
            expiryTime: parseInt(expiryTime || '0')
        };
    } catch (error) {
        console.error('[AuthStorage] Failed to get tokens:', error);
        return null;
    }
};

/**
 * Check if the current token is expired (with 5 minute buffer)
 * @returns {Promise<boolean>}
 */
export const isTokenExpired = async () => {
    const tokens = await getTokens();
    if (!tokens || !tokens.expiryTime) return true;

    const bufferMs = 5 * 60 * 1000; // 5 minutes buffer
    return Date.now() > (tokens.expiryTime - bufferMs);
};

/**
 * Clear all stored authentication data (logout)
 */
export const clearTokens = async () => {
    try {
        await Preferences.remove({ key: KEYS.ACCESS_TOKEN });
        await Preferences.remove({ key: KEYS.REFRESH_TOKEN });
        await Preferences.remove({ key: KEYS.TOKEN_EXPIRY });
        await Preferences.remove({ key: KEYS.USER_DATA });

        // Also clear localStorage fallback
        localStorage.removeItem(KEYS.ACCESS_TOKEN);
        localStorage.removeItem(KEYS.REFRESH_TOKEN);
        localStorage.removeItem(KEYS.TOKEN_EXPIRY);

        console.log('[AuthStorage] All tokens cleared');
        return true;
    } catch (error) {
        console.error('[AuthStorage] Failed to clear tokens:', error);
        return false;
    }
};

/**
 * Save user data securely
 * @param {Object} user - User profile data
 */
export const saveUserData = async (user) => {
    try {
        await Preferences.set({ key: KEYS.USER_DATA, value: JSON.stringify(user) });
        return true;
    } catch (error) {
        console.error('[AuthStorage] Failed to save user data:', error);
        return false;
    }
};

/**
 * Get stored user data
 * @returns {Promise<Object|null>}
 */
export const getUserData = async () => {
    try {
        const { value } = await Preferences.get({ key: KEYS.USER_DATA });
        return value ? JSON.parse(value) : null;
    } catch (error) {
        console.error('[AuthStorage] Failed to get user data:', error);
        return null;
    }
};
