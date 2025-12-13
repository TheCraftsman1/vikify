import { useEffect, useMemo, useRef, useState } from 'react';
import { AppState, type AppStateStatus } from 'react-native';
import { logInfo } from '../utils/playbackLogger';
import type { PlaybackPersistenceService, PersistenceStorage, PlaybackEngine } from '../services/PlaybackPersistenceService';
import { restorePlayback, isRecentPlaybackSession, type RestoreOptions } from '../services/PlaybackRestore';

export type LifecycleDeps = {
  engine: PlaybackEngine;
  persistence: PlaybackPersistenceService;
  restoreOptions: RestoreOptions;
  storage: PersistenceStorage;
};

export type UsePlaybackLifecycleResult = {
  isRestoring: boolean;
  lastRestoreStatus: string | null;
};

const RESTORE_SPINNER_DELAY_MS = 300;

export const usePlaybackLifecycle = ({ engine, persistence, restoreOptions }: LifecycleDeps): UsePlaybackLifecycleResult => {
  const [isRestoring, setIsRestoring] = useState(false);
  const [lastRestoreStatus, setLastRestoreStatus] = useState<string | null>(null);
  const appStateRef = useRef<AppStateStatus>(AppState.currentState);

  const restoringTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const beginRestoreIndicator = () => {
    if (restoringTimerRef.current) clearTimeout(restoringTimerRef.current);
    restoringTimerRef.current = setTimeout(() => setIsRestoring(true), RESTORE_SPINNER_DELAY_MS);
  };

  const endRestoreIndicator = () => {
    if (restoringTimerRef.current) clearTimeout(restoringTimerRef.current);
    restoringTimerRef.current = null;
    setIsRestoring(false);
  };

  const restoreNow = useMemo(() => {
    return async (reason: 'cold-start' | 'resume') => {
      beginRestoreIndicator();
      try {
        const persisted = await persistence.load();
        if (!isRecentPlaybackSession(persisted)) {
          setLastRestoreStatus(`${reason}:no-recent-session`);
          return;
        }

        const result = await restorePlayback(engine, persisted, restoreOptions);
        setLastRestoreStatus(`${reason}:${result.status}`);
        logInfo('Restore finished', { reason, result });
      } finally {
        endRestoreIndicator();
      }
    };
  }, [engine, persistence, restoreOptions]);

  useEffect(() => {
    // Persist continuously while app is alive.
    persistence.start();

    // Cold start restore should happen as early as possible (before UI renders).
    // If you need it truly before UI, call restoreNow('cold-start') during bootstrap.

    const onAppStateChange = (nextState: AppStateStatus) => {
      const prev = appStateRef.current;
      appStateRef.current = nextState;

      if ((prev === 'inactive' || prev === 'background') && nextState === 'active') {
        void restoreNow('resume');
      }

      if (nextState === 'background') {
        void persistence.persist('background');
      }

      logInfo('AppState', { prev, nextState });
    };

    const sub = AppState.addEventListener('change', onAppStateChange);

    return () => {
      sub.remove();
      persistence.stop();
    };
  }, [persistence, restoreNow]);

  return { isRestoring, lastRestoreStatus };
};
