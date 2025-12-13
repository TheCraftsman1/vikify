import { logError, logInfo, logWarn } from '../utils/playbackLogger';
import { safeJsonParse } from './storage';

export type PersistedPlaybackStateV1 = {
  schemaVersion: 1;
  savedAtEpochMs: number;
  trackId: string | null;
  positionMs: number;
  isPlaying: boolean;
  rate: number;
  outputRoute: string | null;
  queueTrackIds?: string[];
};

export type PlaybackSnapshot = {
  trackId: string | null;
  positionMs: number;
  isPlaying: boolean;
  rate: number;
  outputRoute: string | null;
  queueTrackIds?: string[];
};

export type PlaybackEngine = {
  getCurrentTrackId(): Promise<string | null>;
  getPositionMs(): Promise<number>;
  getRate(): Promise<number>;
  getIsPlaying(): Promise<boolean>;
  getOutputRoute(): Promise<string | null>;
  getQueueTrackIds?(): Promise<string[]>;
  seekToMs(positionMs: number): Promise<void>;
  play(): Promise<void>;
  pause(): Promise<void>;
};

export type PersistenceStorage = {
  getString(key: string): Promise<string | undefined>;
  setString(key: string, value: string): Promise<void>;
  delete(key: string): Promise<void>;
};

export type PersistenceOptions = {
  key?: string;
  intervalMs?: number; // target 500–1000ms
};

const DEFAULTS: Required<PersistenceOptions> = {
  key: 'vikify.playback.state.v1',
  intervalMs: 750,
};

const clampMs = (ms: number) => {
  if (!Number.isFinite(ms)) return 0;
  return Math.max(0, Math.floor(ms));
};

export class PlaybackPersistenceService {
  private readonly engine: PlaybackEngine;
  private readonly storage: PersistenceStorage;
  private readonly key: string;
  private readonly intervalMs: number;

  private timer: ReturnType<typeof setInterval> | null = null;
  private lastWriteAt = 0;
  private lastWritten?: PersistedPlaybackStateV1;

  constructor(engine: PlaybackEngine, storage: PersistenceStorage, opts?: PersistenceOptions) {
    this.engine = engine;
    this.storage = storage;
    this.key = opts?.key ?? DEFAULTS.key;
    this.intervalMs = opts?.intervalMs ?? DEFAULTS.intervalMs;
  }

  start() {
    if (this.timer) return;
    this.timer = setInterval(() => {
      void this.persist('interval');
    }, this.intervalMs);
    logInfo('Persistence started', { intervalMs: this.intervalMs });
  }

  stop() {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
      logInfo('Persistence stopped');
    }
  }

  async persist(reason: 'interval' | 'background' | 'remote-action' | 'manual') {
    // Avoid double writes if multiple triggers happen nearly simultaneously.
    const now = Date.now();
    if (now - this.lastWriteAt < 250 && reason !== 'background') return;
    this.lastWriteAt = now;

    try {
      const snapshot = await this.captureSnapshot();
      const payload: PersistedPlaybackStateV1 = {
        schemaVersion: 1,
        savedAtEpochMs: now,
        ...snapshot,
      };

      // Reduce unnecessary storage churn.
      if (this.lastWritten && shallowEqualState(this.lastWritten, payload)) return;

      await this.storage.setString(this.key, JSON.stringify(payload));
      this.lastWritten = payload;
    } catch (error) {
      logWarn('Persist failed', { reason, error: String(error) });
    }
  }

  async clear() {
    await this.storage.delete(this.key);
    this.lastWritten = undefined;
  }

  async load(): Promise<PersistedPlaybackStateV1 | null> {
    try {
      const raw = await this.storage.getString(this.key);
      const parsed = safeJsonParse<PersistedPlaybackStateV1>(raw);
      if (!parsed || parsed.schemaVersion !== 1) return null;
      return {
        ...parsed,
        positionMs: clampMs(parsed.positionMs),
      };
    } catch (error) {
      logError('Load failed', { error: String(error) });
      return null;
    }
  }

  private async captureSnapshot(): Promise<PlaybackSnapshot> {
    const [trackId, positionMs, isPlaying, rate, outputRoute] = await Promise.all([
      this.engine.getCurrentTrackId(),
      this.engine.getPositionMs(),
      this.engine.getIsPlaying(),
      this.engine.getRate(),
      this.engine.getOutputRoute(),
    ]);

    const queueTrackIds = this.engine.getQueueTrackIds ? await this.engine.getQueueTrackIds() : undefined;

    return {
      trackId,
      positionMs: clampMs(positionMs),
      isPlaying,
      rate: Number.isFinite(rate) ? rate : 1,
      outputRoute,
      queueTrackIds,
    };
  }
}

const shallowEqualState = (a: PersistedPlaybackStateV1, b: PersistedPlaybackStateV1) => {
  return (
    a.trackId === b.trackId &&
    a.positionMs === b.positionMs &&
    a.isPlaying === b.isPlaying &&
    a.rate === b.rate &&
    a.outputRoute === b.outputRoute
  );
};
