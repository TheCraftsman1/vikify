/**
 * net.js
 *
 * Small retry helper for network calls (axios/fetch).
 * Keeps retries conservative to avoid hammering the backend.
 */

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export const isOffline = () => {
  if (typeof navigator === 'undefined') return false;
  return navigator.onLine === false;
};

export const withRetry = async (fn, {
  retries = 2,
  baseDelayMs = 400,
  maxDelayMs = 2500,
  jitter = 0.25,
  shouldRetry,
} = {}) => {
  let lastErr;

  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await fn(attempt);
    } catch (e) {
      lastErr = e;

      // If the device is offline, don't waste time retrying.
      if (isOffline()) break;

      const ok = shouldRetry ? shouldRetry(e, attempt) : true;
      if (!ok || attempt >= retries) break;

      const exp = baseDelayMs * Math.pow(2, attempt);
      const jitterMs = exp * jitter * (Math.random() - 0.5) * 2;
      const delay = Math.max(0, Math.min(maxDelayMs, exp + jitterMs));
      await sleep(delay);
    }
  }

  throw lastErr;
};

export const isLikelyTransientNetworkError = (err) => {
  const msg = String(err?.message || '').toLowerCase();
  const status = err?.response?.status;

  if (isOffline()) return false;

  if (status && (status === 408 || status === 429 || status >= 500)) return true;
  if (msg.includes('timeout')) return true;
  if (msg.includes('network error')) return true;
  if (msg.includes('failed to fetch')) return true;
  return false;
};
