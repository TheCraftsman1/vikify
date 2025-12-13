import { logInfo, logWarn } from '../utils/playbackLogger';
import type { PlaybackEngine } from './PlaybackPersistenceService';
import type { PersistedPlaybackStateV1 } from './PlaybackPersistenceService';

export type StreamResolver = (trackId: string) => Promise<{ url: string; expiresAtEpochMs?: number }>; // refreshes if expired

export type RestoreOptions = {
  resumeThresholdMs?: number; // default: 6 hours
  // If your engine needs to set the media item before seeking, supply this callback.
  setActiveTrackById(trackId: string): Promise<void>;
  resolveStreamUrl: StreamResolver;
  // Optional network-aware retry wrapper.
  withRetry?<T>(fn: () => Promise<T>, label: string): Promise<T>;
};

const DEFAULTS: Required<Pick<RestoreOptions, 'resumeThresholdMs'>> = {
  resumeThresholdMs: 6 * 60 * 60 * 1000,
};

export type RestoreResult =
  | { status: 'no-state' }
  | { status: 'skipped'; reason: 'stale' | 'missing-track' }
  | { status: 'restored'; trackId: string; positionMs: number; startedPlaying: boolean };

export const restorePlayback = async (
  engine: PlaybackEngine,
  persisted: PersistedPlaybackStateV1 | null,
  opts: RestoreOptions,
): Promise<RestoreResult> => {
  if (!persisted) return { status: 'no-state' };
  if (!persisted.trackId) return { status: 'skipped', reason: 'missing-track' };

  const resumeThresholdMs = opts.resumeThresholdMs ?? DEFAULTS.resumeThresholdMs;
  const ageMs = Date.now() - persisted.savedAtEpochMs;

  logInfo('Restore attempt', { trackId: persisted.trackId, ageMs, positionMs: persisted.positionMs });

  const runner = opts.withRetry
    ? <T,>(fn: () => Promise<T>, label: string) => opts.withRetry!(fn, label)
    : <T,>(fn: () => Promise<T>) => fn();

  // If state is very old, we still restore track, but we force a "rebuffer" path:
  // resolve stream URL first, set active track, then seek before play.
  const shouldRebuffer = ageMs > resumeThresholdMs;

  // Refresh stream URL if needed.
  await runner(async () => {
    await opts.resolveStreamUrl(persisted.trackId!);
  }, 'resolveStreamUrl');

  await runner(async () => {
    await opts.setActiveTrackById(persisted.trackId!);
  }, 'setActiveTrackById');

  // Always seek before play to prevent restart-from-zero.
  const safePosition = Math.max(0, Math.floor(persisted.positionMs));
  await runner(async () => {
    await engine.seekToMs(safePosition);
  }, 'seekToMs');

  if (persisted.isPlaying) {
    // If the pause was long, some engines require a small delay to ensure the new stream is prepared.
    if (shouldRebuffer) {
      await new Promise((r) => setTimeout(r, 150));
    }

    await runner(async () => {
      await engine.play();
    }, 'play');

    return { status: 'restored', trackId: persisted.trackId, positionMs: safePosition, startedPlaying: true };
  }

  // paused
  await runner(async () => {
    await engine.pause();
  }, 'pause');

  return { status: 'restored', trackId: persisted.trackId, positionMs: safePosition, startedPlaying: false };
};

export const isRecentPlaybackSession = (persisted: PersistedPlaybackStateV1 | null, recentWindowMs = 24 * 60 * 60 * 1000) => {
  if (!persisted) return false;
  if (!persisted.trackId) return false;
  const age = Date.now() - persisted.savedAtEpochMs;
  if (age < 0) return true;
  if (age > recentWindowMs) {
    logWarn('Playback session too old', { ageMs: age });
    return false;
  }
  return true;
};
