import { Capacitor, registerPlugin } from '@capacitor/core';

// Native-only plugin. On web, we no-op.
const NowPlaying = registerPlugin('NowPlaying');

export const isNative = () => Capacitor.isNativePlatform();

export async function updateNowPlaying({ title, artist, isPlaying, positionSeconds, durationSeconds, artworkUrl }) {
  if (!isNative()) return;
  try {
    await NowPlaying.update({ title, artist, isPlaying, positionSeconds, durationSeconds, artworkUrl });
  } catch (e) {
    console.warn('[NowPlaying] update failed:', e);
  }
}

export async function clearNowPlaying() {
  if (!isNative()) return;
  try {
    await NowPlaying.clear();
  } catch (e) {
    console.warn('[NowPlaying] clear failed:', e);
  }
}

export function onNowPlayingAction(handler) {
  if (!isNative()) return { remove: async () => {} };
  return NowPlaying.addListener('action', handler);
}
