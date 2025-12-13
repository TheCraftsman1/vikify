import { logInfo } from '../utils/playbackLogger';
import { withRetryBackoff } from '../utils/retry';
import { initTrackPlayer, persistence } from '../wiring/playbackWiring';
import { trackPlayerEngine } from '../wiring/trackPlayerEngine';
import { restorePlayback } from '../services/PlaybackRestore';

// Call this BEFORE rendering your main navigation/UI.
// It ensures playback state is restored and we seek-before-play.
export const bootstrapPlayback = async (deps: {
  setActiveTrackById(trackId: string): Promise<void>;
  resolveStreamUrl(trackId: string): Promise<{ url: string; expiresAtEpochMs?: number }>;
}) => {
  logInfo('Bootstrap: start');

  await initTrackPlayer();

  // Start persistence early.
  persistence.start();

  const persisted = await persistence.load();

  const result = await restorePlayback(trackPlayerEngine, persisted, {
    setActiveTrackById: deps.setActiveTrackById,
    resolveStreamUrl: deps.resolveStreamUrl,
    withRetry: (fn, label) => withRetryBackoff(fn, label, { retries: 3 }),
  });

  logInfo('Bootstrap: done', { result });
  return result;
};
