import { Capacitor } from '@capacitor/core';

// Determine the backend URL based on the platform
// IMPORTANT: In Capacitor APK, the WebView runs on localhost too,
// so we MUST check isNativePlatform FIRST before checking hostname

let backendUrl = import.meta.env.VITE_BACKEND_URL;

// Check if we're in a Capacitor native app (APK)
const isNative = Capacitor.isNativePlatform();
const platform = Capacitor.getPlatform();

if (!backendUrl) {
    if (isNative) {
        // APK/Mobile: ALWAYS use production Railway backend
        backendUrl = 'https://vikify-production.up.railway.app';
    } else if (window.location.protocol === 'file:') {
        // File protocol also means native/local build
        backendUrl = 'https://vikify-production.up.railway.app';
    } else if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
        // Development on PC
        backendUrl = 'http://localhost:5000';
    } else {
        // Any other case (deployed web, etc.)
        backendUrl = 'https://vikify-production.up.railway.app';
    }
}

console.log(`[Config] Platform: ${platform}, Native: ${isNative}, Backend: ${backendUrl}`);

export const BACKEND_URL = backendUrl;
export const MAX_DOWNLOADS_HISTORY = 10000;
