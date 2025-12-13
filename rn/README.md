# RN Background Playback Persistence (Vikify)

This folder contains a **React Native** implementation that solves the lifecycle issue where the UI process is backgrounded/killed and playback state is lost.

It is designed for a **native playback engine** (ExoPlayer/AVPlayer via `react-native-track-player`) so **notification controls keep working even if the UI is killed**.

## Root cause (why “fresh launch” happens)
- On Android and iOS, the **app UI process can be suspended or killed** while in background.
- If playback is driven by **in-memory JS state** (React state/context) the state disappears when the JS VM is restarted.
- The **notification** can remain (MediaSession/NowPlaying), but without persisted state the UI cold-starts and reinitializes the player at position 0.

## Architecture fix (diagram level)

UI (React)  ↔  Playback Engine (TrackPlayer / ExoPlayer / AVPlayer)
   │                      │
   │ AppState events      │ Remote commands/events
   ▼                      ▼
PlaybackLifecycle      Playback Service (background)
   │                      │
   ├─ restore on boot      ├─ handles RemotePlay/Pause/Seek/Next/Prev
   └─ persist on bg        └─ emits progress updates
           │
           ▼
   Persistent Storage (AsyncStorage/MMKV/SQLite)
   - trackId
   - positionMs
   - isPlaying
   - rate
   - outputRoute (optional)
   - savedAt

## What’s included
- `rn/src/services/PlaybackPersistenceService.ts`
  - Persists state every ~750ms (configurable)
  - Immediate persist on background
  - Production-safe: throttles writes and avoids redundant writes

- `rn/src/services/PlaybackRestore.ts`
  - Restores track + seeks **before** play to prevent restart-from-zero
  - 6-hour threshold: if paused too long, force rebuffer but still seek-before-play
  - Supports stream URL refresh via `resolveStreamUrl(trackId)`

- `rn/src/lifecycle/usePlaybackLifecycle.ts`
  - AppState listeners: background → persist, active → restore
  - Minimal UI indicator: if restore takes >300ms set `isRestoring=true`

- `rn/src/bootstrap/bootstrapPlayback.ts`
   - One-shot bootstrap that restores playback **before UI renders**
   - Uses seek-before-play to prevent restart-from-zero

- `rn/src/utils/retry.ts`
   - Small backoff helper for transient network errors (optional)

- `rn/src/trackPlayerService.ts`
  - Notification / lockscreen actions (RemotePlay/Pause/Seek/Next/Prev)
  - Runs even when UI JS is killed (TrackPlayer service context)

- `rn/src/services/PlaylistMetadataCache.ts`
  - Persists Spotify playlist **metadata only** for fast next launch

## Integration steps (high level)
1. Install dependencies in your RN app:
   - `react-native-track-player`
   - `@react-native-async-storage/async-storage` (or swap to MMKV)

2. Register the playback service:
   - `TrackPlayer.registerPlaybackService(() => require('./src/trackPlayerService'))`

3. Initialize TrackPlayer early (app bootstrap):
   - call `initTrackPlayer()` from `rn/src/wiring/playbackWiring.ts`

4. Restore before rendering UI (recommended):
   - Call `bootstrapPlayback(...)` (or `persistence.load()` + `restorePlayback(...)`) before mounting navigation.
   - Show `PlaybackRestoreGate` if `isRestoring` becomes true.

## Platform notes
### Android
- Uses a foreground service during playback (handled by TrackPlayer).
- Ensure you have foreground service permissions in AndroidManifest.

### iOS
- Enable background audio mode in Xcode (Capabilities → Background Modes → Audio).
- TrackPlayer wires to Remote Command Center.

## Stream URL expiration + network loss
The restore path calls `resolveStreamUrl(trackId)` **before** seeking/playing. Implement it to:
- refresh expired URLs
- retry with backoff on transient network errors

## Output device route
Persisting the "audio output route" (speaker/Bluetooth/AirPlay) requires platform APIs (AVAudioSession route, Android AudioDeviceInfo).
This code includes the field but returns `null` by default; add a tiny native module if you need exact route persistence.

---

If you want, I can also wire this RN module into a full RN app scaffold in this repo, but that’s a bigger migration from the current Capacitor web build.
