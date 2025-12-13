import TrackPlayer from 'react-native-track-player';
import type { PlaybackEngine } from '../services/PlaybackPersistenceService';

// Adapter to satisfy the generic engine interface expected by persistence/restore.
export const trackPlayerEngine: PlaybackEngine = {
  async getCurrentTrackId() {
    const trackId = await TrackPlayer.getCurrentTrack();
    return trackId ? String(trackId) : null;
  },

  async getPositionMs() {
    const seconds = await TrackPlayer.getPosition();
    return Math.floor((seconds ?? 0) * 1000);
  },

  async getRate() {
    const rate = await TrackPlayer.getRate();
    return Number.isFinite(rate) ? rate : 1;
  },

  async getIsPlaying() {
    const state = await TrackPlayer.getPlaybackState();
    // TrackPlayer state varies by version:
    // - some versions return a number enum
    // - others return an object like { state: 'playing' }
    // Keep this conservative and avoid tight coupling to a specific enum.
    const maybeObjectState = (state as any)?.state;
    if (maybeObjectState === 'playing') return true;
    if (typeof state === 'number') {
      // 3 is commonly "playing" in older TrackPlayer enums
      return state === 3;
    }
    return false;
  },

  async getOutputRoute() {
    // Output route detection is platform-specific; implement with native module if needed.
    // For now, return null.
    return null;
  },

  async seekToMs(positionMs: number) {
    await TrackPlayer.seekTo(Math.max(0, positionMs) / 1000);
  },

  async play() {
    await TrackPlayer.play();
  },

  async pause() {
    await TrackPlayer.pause();
  },
};
