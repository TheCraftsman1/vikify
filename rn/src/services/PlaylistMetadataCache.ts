import { safeJsonParse } from './storage';

export type PlaylistMetadata = {
  id: string;
  name: string;
  imageUrl?: string;
  ownerName?: string;
  trackCount?: number;
  snapshotId?: string;
  updatedAtEpochMs: number;
};

export type PlaylistCacheStorage = {
  getString(key: string): Promise<string | undefined>;
  setString(key: string, value: string): Promise<void>;
};

export class PlaylistMetadataCache {
  private readonly storage: PlaylistCacheStorage;
  private readonly key: string;
  private readonly ttlMs: number;

  constructor(storage: PlaylistCacheStorage, key = 'vikify.spotify.playlists.meta.v1', ttlMs = 7 * 24 * 60 * 60 * 1000) {
    this.storage = storage;
    this.key = key;
    this.ttlMs = ttlMs;
  }

  async save(playlists: Omit<PlaylistMetadata, 'updatedAtEpochMs'>[]) {
    const now = Date.now();
    const payload = {
      schemaVersion: 1,
      savedAtEpochMs: now,
      playlists: playlists.map((p) => ({ ...p, updatedAtEpochMs: now })),
    };
    await this.storage.setString(this.key, JSON.stringify(payload));
  }

  async load(): Promise<PlaylistMetadata[] | null> {
    const raw = await this.storage.getString(this.key);
    const parsed = safeJsonParse<{ schemaVersion: 1; savedAtEpochMs: number; playlists: PlaylistMetadata[] }>(raw);
    if (!parsed || parsed.schemaVersion !== 1) return null;

    const age = Date.now() - parsed.savedAtEpochMs;
    if (age > this.ttlMs) return null;

    return parsed.playlists;
  }
}
