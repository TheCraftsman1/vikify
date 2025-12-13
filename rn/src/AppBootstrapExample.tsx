import React, { useEffect, useState } from 'react';

import { PlaybackRestoreGate } from './components/PlaybackRestoreGate';
import { bootstrapPlayback } from './bootstrap/bootstrapPlayback';

// Example only. Wire these to your actual player/queue + backend.
const setActiveTrackById = async (trackId: string) => {
  // TODO: Set TrackPlayer track/queue using your metadata
  // e.g. TrackPlayer.reset(); TrackPlayer.add([{ id: trackId, url, title, artist }])
  void trackId;
};

const resolveStreamUrl = async (trackId: string) => {
  // TODO: Call your backend to get a fresh stream URL if expired.
  // return await fetch(`/stream?trackId=${trackId}`).then(r => r.json());
  return { url: `https://example.invalid/stream/${trackId}` };
};

export const AppBootstrapExample = ({ children }: { children: React.ReactNode }) => {
  const [ready, setReady] = useState(false);
  const [restoring, setRestoring] = useState(false);

  useEffect(() => {
    let cancelled = false;

    const run = async () => {
      setRestoring(true);
      try {
        await bootstrapPlayback({ setActiveTrackById, resolveStreamUrl });
      } finally {
        if (!cancelled) {
          setRestoring(false);
          setReady(true);
        }
      }
    };

    void run();

    return () => {
      cancelled = true;
    };
  }, []);

  if (!ready) {
    return <PlaybackRestoreGate isRestoring={true}>{null}</PlaybackRestoreGate>;
  }

  return <PlaybackRestoreGate isRestoring={restoring}>{children}</PlaybackRestoreGate>;
};
