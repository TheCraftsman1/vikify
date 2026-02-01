# Vikify Plan / Checklist

This file captures the current implementation work and what to verify so the app behaves like an Android Spotify-lookalike.

## 1) Player UI interaction (seek/scrub)

**Goal:** Spotify-style seek bar interaction: tap anywhere to jump; drag to preview; commit seek on release.

**Implemented**
- Mobile full-screen player scrubbing:
  - Drag-to-scrub with preview time
  - Seek committed on pointer release
  - Haptics: light on start/end, selection ticks while dragging
- Mini player + fullscreen (desktop) progress bars:
  - Same scrubbing behavior, fixed so it always measures the *bar you touched*
- Reduced UI jank by throttling frequent progress updates to 1x per animation frame.

**Files**
- src/components/MobileFullScreenPlayer.jsx
- src/components/Player.jsx
- src/context/PlayerContext.jsx

**Verify (manual)**
- Start a song, then tap at ~50% on the progress bar → jumps near middle.
- Drag thumb left/right → displayed time follows finger, playback jumps only when releasing.
- Ensure it works on:
  - Mini player bar
  - Full-screen player

## 2) Notification shade “Now Playing” controls (Spotify-like)

**Goal:** When audio is playing, Android shows a media notification with song metadata and play/pause/next/prev controls.

**Implemented**
- Media Session integration already present in PlayerContext:
  - Sets `navigator.mediaSession.metadata`
  - Updates playback state + position state
  - Handles play/pause/next/previous/seek actions

- Native Android fallback notification (reliable in notification shade):
  - Custom Capacitor plugin `NowPlaying` posts a MediaStyle notification with play/pause/next/prev.
  - Action taps are bridged back to JS to control the web audio player.

**Important note**
- In Capacitor apps, the notification you see is typically driven by the WebView/MediaSession integration.
- A fully custom Spotify-level persistent notification (with background playback via a native foreground service) would require additional native work; we are currently using the simplest supported approach.

**Verify (manual, Android device)**
- Play any song
- Pull down the notification shade
- Expect to see media controls and correct title/artist (artwork uses app icon for now)
- Controls should work:
  - Play/Pause
  - Next/Previous
  - Seek (if Android shows a seek bar)

**Files**
- android/app/src/main/java/com/vikify/app/nowplaying/NowPlayingPlugin.java
- android/app/src/main/java/com/vikify/app/nowplaying/NowPlayingReceiver.java
- src/utils/nowPlaying.js
- src/context/PlayerContext.jsx

## 3) Permissions on app start

**Goal:** Ask required permissions immediately at app launch (not later).

**Implemented**
- Android 13+ notification permission requested on app start:
  - `POST_NOTIFICATIONS` is requested in `MainActivity.onCreate`
- Vibration permission is added so haptics work consistently:
  - `android.permission.VIBRATE`

**Files**
- android/app/src/main/java/com/vikify/app/MainActivity.java
- android/app/src/main/AndroidManifest.xml
- src/App.jsx (browser-only notification permission prompt; native handled in MainActivity)

**Verify (manual)**
- Fresh install on Android 13+ → permission prompt appears on first launch.

## 4) Android build stability (JDK / Gradle)

**Problem fixed**
- Gradle was running on Java 25 and failed with:
  - `Unsupported class file major version 69`

**Implemented**
- Forced Gradle to use Android Studio’s bundled JDK 21 via:
  - `org.gradle.java.home` in `android/gradle.properties`

**Verify (manual)**
- `cd android`
- `./gradlew.bat :app:compileDebugJavaWithJavac`

## 5) Tests

**Implemented**
- Vitest + Testing Library setup
- One interaction test that verifies seek is committed on pointer release.

**Files**
- vitest.config.js
- src/test/setupTests.js
- src/components/__tests__/MobileFullScreenPlayer.scrub.test.jsx

**Run**
- `npm test`

## Next recommended improvements

- Add test coverage for the Player.jsx mini progress bar scrub.
- Add a small Android manual QA checklist for notification controls across Android versions.
- (Optional) If you want true Spotify-level background playback reliability, implement a native foreground media service.
