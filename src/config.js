import { Capacitor } from '@capacitor/core';

// Determine the backend URL based on the platform
// Android Emulator uses 10.0.2.2 to access host localhost
// Physical devices need the actual LAN IP of the server
// Web uses localhost or the defined VITE_BACKEND_URL

let backendUrl = import.meta.env.VITE_BACKEND_URL;

if (!backendUrl) {
    if (Capacitor.getPlatform() === 'android') {
        // Production: Use Railway Backend
        backendUrl = 'https://vikify-production.up.railway.app';
    } else {
        // Development: Use localhost
        backendUrl = 'http://localhost:5000';
    }
}

console.log(`[Config] Using Backend URL: ${backendUrl} (Platform: ${Capacitor.getPlatform()})`);

export const BACKEND_URL = backendUrl;
export const MAX_DOWNLOADS_HISTORY = 10000;
