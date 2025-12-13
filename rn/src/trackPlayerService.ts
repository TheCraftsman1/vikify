// This file is meant to be registered with react-native-track-player
// TrackPlayer.registerPlaybackService(() => require('./trackPlayerService'));

import TrackPlayer, { Event } from 'react-native-track-player';
import { logInfo } from './utils/playbackLogger';

// These imports are intentionally lazy-friendly. In a real app, you would inject these via a container.
// Keeping it explicit here for "full implementation code".
import { persistence } from './wiring/playbackWiring';

export default async function trackPlayerService() {
  // Remote actions MUST work even if the UI JS bundle is not running.
  // This service runs in the TrackPlayer playback service context.

  TrackPlayer.addEventListener(Event.RemotePlay, async () => {
    await TrackPlayer.play();
    await persistence.persist('remote-action');
    logInfo('RemotePlay');
  });

  TrackPlayer.addEventListener(Event.RemotePause, async () => {
    await TrackPlayer.pause();
    await persistence.persist('remote-action');
    logInfo('RemotePause');
  });

  TrackPlayer.addEventListener(Event.RemoteSeek, async (evt: { position?: number }) => {
    // position is seconds
    const positionMs = Math.max(0, Math.floor((evt.position ?? 0) * 1000));
    await TrackPlayer.seekTo(positionMs / 1000);
    await persistence.persist('remote-action');
    logInfo('RemoteSeek', { positionMs });
  });

  TrackPlayer.addEventListener(Event.RemoteNext, async () => {
    await TrackPlayer.skipToNext();
    await persistence.persist('remote-action');
    logInfo('RemoteNext');
  });

  TrackPlayer.addEventListener(Event.RemotePrevious, async () => {
    await TrackPlayer.skipToPrevious();
    await persistence.persist('remote-action');
    logInfo('RemotePrevious');
  });

  // Keep persisted position fresh without polling.
  // TrackPlayer emits this at your configured progressUpdateEventInterval.
  TrackPlayer.addEventListener(Event.PlaybackProgressUpdated, async () => {
    await persistence.persist('interval');
  });
}
