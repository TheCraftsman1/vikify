import { createCache } from './cacheController';

// Public, non-sensitive payloads only.
// We intentionally cache only mapped arrays (not tokens, not raw auth responses).
const featuredPlaylistsCache = createCache({
  name: 'spotify.featuredPlaylists',
  persist: true,
  defaultTtlMs: 10 * 60 * 1000,
  maxEntries: 10,
});

const newReleasesCache = createCache({
  name: 'spotify.newReleases',
  persist: true,
  defaultTtlMs: 10 * 60 * 1000,
  maxEntries: 10,
});

export function getCachedFeaturedPlaylists(limit) {
  const v = featuredPlaylistsCache.get(String(limit));
  return Array.isArray(v) ? v : null;
}

export function setCachedFeaturedPlaylists(limit, playlists) {
  if (Array.isArray(playlists)) featuredPlaylistsCache.set(String(limit), playlists);
}

export function getCachedNewReleases(limit) {
  const v = newReleasesCache.get(String(limit));
  return Array.isArray(v) ? v : null;
}

export function setCachedNewReleases(limit, albums) {
  if (Array.isArray(albums)) newReleasesCache.set(String(limit), albums);
}

export function pruneSpotifyPublicCaches() {
  featuredPlaylistsCache.prune();
  newReleasesCache.prune();
}

export function clearSpotifyPublicCaches() {
  featuredPlaylistsCache.clear();
  newReleasesCache.clear();
}
