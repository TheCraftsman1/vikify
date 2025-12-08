/**
 * IndexedDB utility for offline audio storage
 * Stores audio blobs for offline playback
 */

const DB_NAME = 'vikify-offline';
const DB_VERSION = 1;
const AUDIO_STORE = 'audio-cache';
const METADATA_STORE = 'song-metadata';

let dbInstance = null;

/**
 * Initialize and get the database instance
 */
const getDB = () => {
    return new Promise((resolve, reject) => {
        if (dbInstance) {
            resolve(dbInstance);
            return;
        }

        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onerror = () => {
            console.error('[OfflineDB] Failed to open database:', request.error);
            reject(request.error);
        };

        request.onsuccess = () => {
            dbInstance = request.result;
            console.log('[OfflineDB] Database opened successfully');
            resolve(dbInstance);
        };

        request.onupgradeneeded = (event) => {
            const db = event.target.result;

            // Store for audio blobs
            if (!db.objectStoreNames.contains(AUDIO_STORE)) {
                db.createObjectStore(AUDIO_STORE, { keyPath: 'songId' });
                console.log('[OfflineDB] Created audio-cache store');
            }

            // Store for song metadata
            if (!db.objectStoreNames.contains(METADATA_STORE)) {
                const metaStore = db.createObjectStore(METADATA_STORE, { keyPath: 'songId' });
                metaStore.createIndex('downloadedAt', 'downloadedAt', { unique: false });
                console.log('[OfflineDB] Created song-metadata store');
            }
        };
    });
};

/**
 * Save audio blob to IndexedDB
 * @param {string} songId - Unique song identifier
 * @param {Blob} blob - Audio blob data
 * @param {Object} metadata - Song metadata (title, artist, image, etc.)
 */
export const saveAudioBlob = async (songId, blob, metadata = {}) => {
    try {
        const db = await getDB();

        // Save audio blob
        const audioTx = db.transaction(AUDIO_STORE, 'readwrite');
        const audioStore = audioTx.objectStore(AUDIO_STORE);

        await new Promise((resolve, reject) => {
            const request = audioStore.put({ songId, blob, savedAt: Date.now() });
            request.onsuccess = resolve;
            request.onerror = () => reject(request.error);
        });

        // Save metadata
        const metaTx = db.transaction(METADATA_STORE, 'readwrite');
        const metaStore = metaTx.objectStore(METADATA_STORE);

        await new Promise((resolve, reject) => {
            const request = metaStore.put({
                songId,
                ...metadata,
                downloadedAt: Date.now(),
                isOffline: true
            });
            request.onsuccess = resolve;
            request.onerror = () => reject(request.error);
        });

        console.log('[OfflineDB] Saved audio for:', songId);
        return true;
    } catch (error) {
        console.error('[OfflineDB] Error saving audio:', error);
        return false;
    }
};

/**
 * Get audio blob from IndexedDB
 * @param {string} songId - Unique song identifier
 * @returns {Blob|null} Audio blob or null if not found
 */
export const getAudioBlob = async (songId) => {
    try {
        const db = await getDB();
        const tx = db.transaction(AUDIO_STORE, 'readonly');
        const store = tx.objectStore(AUDIO_STORE);

        return new Promise((resolve, reject) => {
            const request = store.get(songId);
            request.onsuccess = () => {
                if (request.result) {
                    console.log('[OfflineDB] Found cached audio for:', songId);
                    resolve(request.result.blob);
                } else {
                    resolve(null);
                }
            };
            request.onerror = () => reject(request.error);
        });
    } catch (error) {
        console.error('[OfflineDB] Error getting audio:', error);
        return null;
    }
};

/**
 * Check if a song is available offline
 * @param {string} songId - Unique song identifier
 * @returns {boolean}
 */
export const isOffline = async (songId) => {
    try {
        const db = await getDB();
        const tx = db.transaction(AUDIO_STORE, 'readonly');
        const store = tx.objectStore(AUDIO_STORE);

        return new Promise((resolve) => {
            const request = store.count(IDBKeyRange.only(songId));
            request.onsuccess = () => resolve(request.result > 0);
            request.onerror = () => resolve(false);
        });
    } catch (error) {
        return false;
    }
};

/**
 * Delete audio from IndexedDB
 * @param {string} songId - Unique song identifier
 */
export const deleteAudioBlob = async (songId) => {
    try {
        const db = await getDB();

        // Delete audio
        const audioTx = db.transaction(AUDIO_STORE, 'readwrite');
        await new Promise((resolve, reject) => {
            const request = audioTx.objectStore(AUDIO_STORE).delete(songId);
            request.onsuccess = resolve;
            request.onerror = () => reject(request.error);
        });

        // Delete metadata
        const metaTx = db.transaction(METADATA_STORE, 'readwrite');
        await new Promise((resolve, reject) => {
            const request = metaTx.objectStore(METADATA_STORE).delete(songId);
            request.onsuccess = resolve;
            request.onerror = () => reject(request.error);
        });

        console.log('[OfflineDB] Deleted audio for:', songId);
        return true;
    } catch (error) {
        console.error('[OfflineDB] Error deleting audio:', error);
        return false;
    }
};

/**
 * Get all offline songs metadata
 * @returns {Array} Array of song metadata objects
 */
export const getAllOfflineSongs = async () => {
    try {
        const db = await getDB();
        const tx = db.transaction(METADATA_STORE, 'readonly');
        const store = tx.objectStore(METADATA_STORE);

        return new Promise((resolve, reject) => {
            const request = store.getAll();
            request.onsuccess = () => resolve(request.result || []);
            request.onerror = () => reject(request.error);
        });
    } catch (error) {
        console.error('[OfflineDB] Error getting all songs:', error);
        return [];
    }
};

/**
 * Get storage usage statistics
 * @returns {Object} { used: bytes, songs: count }
 */
export const getStorageUsage = async () => {
    try {
        const db = await getDB();
        const tx = db.transaction(AUDIO_STORE, 'readonly');
        const store = tx.objectStore(AUDIO_STORE);

        return new Promise((resolve) => {
            const request = store.getAll();
            request.onsuccess = () => {
                const items = request.result || [];
                let totalSize = 0;
                items.forEach(item => {
                    if (item.blob) {
                        totalSize += item.blob.size;
                    }
                });
                resolve({
                    used: totalSize,
                    songs: items.length,
                    usedMB: (totalSize / (1024 * 1024)).toFixed(2)
                });
            };
            request.onerror = () => resolve({ used: 0, songs: 0, usedMB: '0' });
        });
    } catch (error) {
        return { used: 0, songs: 0, usedMB: '0' };
    }
};

/**
 * Clear all offline data
 */
export const clearAllOfflineData = async () => {
    try {
        const db = await getDB();

        const audioTx = db.transaction(AUDIO_STORE, 'readwrite');
        await new Promise((resolve, reject) => {
            const request = audioTx.objectStore(AUDIO_STORE).clear();
            request.onsuccess = resolve;
            request.onerror = () => reject(request.error);
        });

        const metaTx = db.transaction(METADATA_STORE, 'readwrite');
        await new Promise((resolve, reject) => {
            const request = metaTx.objectStore(METADATA_STORE).clear();
            request.onsuccess = resolve;
            request.onerror = () => reject(request.error);
        });

        console.log('[OfflineDB] Cleared all offline data');
        return true;
    } catch (error) {
        console.error('[OfflineDB] Error clearing data:', error);
        return false;
    }
};
