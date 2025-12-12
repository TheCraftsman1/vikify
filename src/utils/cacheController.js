/**
 * cacheController.js
 *
 * Small, dependency-free cache with:
 * - in-memory hot cache
 * - optional localStorage persistence
 * - TTL (time-to-live)
 * - max entry pruning (LRU-ish based on lastAccess)
 *
 * Security notes:
 * - Do NOT store access tokens or secrets.
 * - Prefer caching derived/public data (videoIds, playlist metadata).
 */

const now = () => Date.now();

const safeJsonParse = (s, fallback) => {
  try {
    return JSON.parse(s);
  } catch {
    return fallback;
  }
};

const clamp = (n, min, max) => Math.max(min, Math.min(max, n));

export const createCache = ({
  name,
  persist = true,
  defaultTtlMs = 10 * 60 * 1000,
  maxEntries = 300,
} = {}) => {
  if (!name) throw new Error('createCache requires a name');

  const prefix = `vikify:cache:${name}:`;
  const indexKey = `${prefix}__index__`;
  const mem = new Map();

  const readIndex = () => {
    if (!persist || typeof localStorage === 'undefined') return [];
    const raw = localStorage.getItem(indexKey);
    return safeJsonParse(raw, []);
  };

  const writeIndex = (idx) => {
    if (!persist || typeof localStorage === 'undefined') return;
    try {
      localStorage.setItem(indexKey, JSON.stringify(idx));
    } catch {
      // ignore storage full / denied
    }
  };

  const touchIndex = (key) => {
    const idx = readIndex();
    const t = now();
    const next = idx.filter((it) => it?.k !== key);
    next.unshift({ k: key, at: t });
    writeIndex(next.slice(0, maxEntries * 2));
  };

  const removeKey = (key) => {
    mem.delete(key);
    if (!persist || typeof localStorage === 'undefined') return;
    try {
      localStorage.removeItem(prefix + key);
    } catch {
      // ignore
    }
    const idx = readIndex().filter((it) => it?.k !== key);
    writeIndex(idx);
  };

  const prune = () => {
    if (!persist || typeof localStorage === 'undefined') return;

    const idx = readIndex();
    const t = now();

    // remove expired
    const survivors = [];
    for (const it of idx) {
      const k = it?.k;
      if (!k) continue;
      const raw = localStorage.getItem(prefix + k);
      if (!raw) continue;
      const rec = safeJsonParse(raw, null);
      if (!rec || (rec.exp && t > rec.exp)) {
        try {
          localStorage.removeItem(prefix + k);
        } catch {}
        mem.delete(k);
        continue;
      }
      survivors.push(it);
    }

    // enforce maxEntries using last-access ordering
    const limited = survivors.slice(0, maxEntries);
    const toRemove = survivors.slice(maxEntries);
    for (const it of toRemove) {
      if (it?.k) {
        try {
          localStorage.removeItem(prefix + it.k);
        } catch {}
        mem.delete(it.k);
      }
    }

    writeIndex(limited);
  };

  const get = (key) => {
    if (!key) return null;
    const cached = mem.get(key);
    const t = now();

    if (cached) {
      if (cached.exp && t > cached.exp) {
        removeKey(key);
        return null;
      }
      cached.at = t;
      return cached.v;
    }

    if (!persist || typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(prefix + key);
    if (!raw) return null;
    const rec = safeJsonParse(raw, null);
    if (!rec) return null;
    if (rec.exp && t > rec.exp) {
      removeKey(key);
      return null;
    }
    mem.set(key, { v: rec.v, exp: rec.exp || 0, at: t });
    touchIndex(key);
    return rec.v;
  };

  const set = (key, value, ttlMs = defaultTtlMs) => {
    if (!key) return;
    const ttl = clamp(ttlMs, 1000, 30 * 24 * 60 * 60 * 1000); // 1s .. 30d
    const exp = now() + ttl;

    mem.set(key, { v: value, exp, at: now() });

    if (persist && typeof localStorage !== 'undefined') {
      try {
        localStorage.setItem(prefix + key, JSON.stringify({ v: value, exp }));
        touchIndex(key);
      } catch {
        // Storage may be full; prune and retry once.
        try {
          prune();
          localStorage.setItem(prefix + key, JSON.stringify({ v: value, exp }));
          touchIndex(key);
        } catch {
          // ignore
        }
      }
    }
  };

  const clear = () => {
    mem.clear();
    if (!persist || typeof localStorage === 'undefined') return;
    const idx = readIndex();
    for (const it of idx) {
      if (it?.k) {
        try {
          localStorage.removeItem(prefix + it.k);
        } catch {}
      }
    }
    try {
      localStorage.removeItem(indexKey);
    } catch {}
  };

  const getOrSet = async (key, producer, ttlMs) => {
    const hit = get(key);
    if (hit !== null && hit !== undefined) return hit;
    const v = await producer();
    if (v !== null && v !== undefined) set(key, v, ttlMs);
    return v;
  };

  return {
    name,
    get,
    set,
    remove: removeKey,
    clear,
    prune,
    getOrSet,
  };
};
