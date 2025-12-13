import TrackPlayer, { Capability } from 'react-native-track-player';
import AsyncStorage from '@react-native-async-storage/async-storage';

import { createAsyncStorageAdapter } from '../services/storage';
import { PlaybackPersistenceService } from '../services/PlaybackPersistenceService';
import { trackPlayerEngine } from './trackPlayerEngine';

export const storage = createAsyncStorageAdapter(AsyncStorage);

export const persistence = new PlaybackPersistenceService(trackPlayerEngine, storage, {
  intervalMs: 750,
});

export const initTrackPlayer = async () => {
  await TrackPlayer.setupPlayer({
    // keep defaults; set buffering, etc. as needed
  });

  await TrackPlayer.updateOptions({
    // Notification + lockscreen actions
    capabilities: [
      Capability.Play,
      Capability.Pause,
      Capability.SeekTo,
      Capability.SkipToNext,
      Capability.SkipToPrevious,
    ],
    compactCapabilities: [Capability.Play, Capability.Pause, Capability.SkipToNext],
    progressUpdateEventInterval: 1, // seconds -> aligns with 500–1000ms persistence requirement

    android: {
      // Foreground service is enabled internally by TrackPlayer on Android when playback starts.
      // Ensure your AndroidManifest has foreground service permission.
    },
  });
};
