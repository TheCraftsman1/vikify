# Audit implementation status

Date: November 2, 2026

This pass implements targeted fixes from the two Vikify audits. It does not
complete every recommendation or establish device-level performance and audio
behavior. Existing local work was preserved. The official Spotify developer
platform and OAuth integration were excluded; Spotify retains its website
session and web-player token flow.

## Implemented in this pass

### Independent account connections

- YouTube Music and Spotify open independently of Vikify cloud sign-in. Neither
  borrows the cloud Gmail by default. Explicit device-account selection supplies
  only an optional music-service email hint and does not change Firebase identity.
- Onboarding identifies stored music credentials as **Session saved**, rather
  than treating cookie presence as proof of a currently valid connection.
- Browser profiles separate YouTube account sessions, Spotify interactive login,
  and Spotify token harvesting when the WebView provider supports `MULTI_PROFILE`.
  Unsupported providers retain a shared-jar fallback with service-scoped cleanup;
  that fallback cannot provide equivalent browser identity isolation.
- Profile capture and validation reject duplicate requests. Dismissing or
  superseding the login cancels validation, and stale callbacks cannot submit or
  dismiss a later screen. Account/channel responses have identity guards.
- `SignedIn` exposes a read-only flow and derives updates from persisted usable
  credentials after account mutations. The encrypted account registry caches its
  parsed representation instead of reparsing unchanged JSON on every lookup.

### DJ reliability and voice presence

- Cancellable volume ramps duck music before speech and restore it afterward.
  Volume ownership tracks listener adjustments instead of relying on the old
  float-equality restoration check.
- A DJ session snapshots the prior queue, position, playback flags, repeat and
  shuffle settings. Recovery includes the bounded queue context behind the
  player's visible queue projection. Ownership checks avoid replacing a queue
  the listener has subsequently changed.
- Queue recovery is **in memory only**. It does not survive process death.
- Downloaded songs provide fallback candidates when network discovery fails.
  Explicit requests still need matching candidates; this is not a guarantee that
  every request can be fulfilled offline.
- DJ phases distinguish thinking, ducking, speaking and listening. TTS PCM
  amplitude drives speaking visuals through a separate drawing signal, and
  supported TTS word-range callbacks highlight the current speech caption.
  Availability and timing depend on the device's TTS engine.

### Home and Now Playing

- Home shelves and nested card rows have stable, duplicate-safe keys and content
  types. Local/service cards no longer run continuous mesh animation.
- The scrubber uses an interpolated playback clock and reads slider progress in
  the drawing phase. Elapsed-time labels update at their displayed precision.
  Existing manual-seek settling behavior is retained.
- Slider gestures distinguish a tap or horizontal drag from vertical movement
  before committing a seek, allowing vertical sheet gestures to proceed.
- Still-artwork palette work runs off the main thread, and the artwork seed LRU
  cache is synchronized. Lyric-strip derivation avoids state writes during
  composition.

The browser-profile API basis is Android's
[WebViewCompat.setProfile documentation](https://developer.android.com/reference/androidx/webkit/WebViewCompat#setProfile(android.webkit.WebView,java.lang.String))
and [Profile documentation](https://developer.android.com/reference/androidx/webkit/Profile).

## Deferred audit work

- Room/history persistence and broader migration of large library JSON stores.
- DataStore/settings persistence and asynchronous storage/keystore startup work.
- Full playback-service and activity/view-model decomposition.
- Music-beat visualization, artwork-derived DJ vibe palettes and richer set
  transition visuals.
- Durable DJ queue stashing and process-death recovery.
- A unified account connection model/hub and proactive provider expiration
  validation or reauthentication states.
- In-place speech recognition, DJ crossfade and secondary-player voice routing.
- Settings sections that collect only their own flows.

## Verification

| Check | Status |
| --- | --- |
| Android source compilation | Passed |
| Focused app unit tests | Passed — 48 tests |
| Focused shared UI tests | Passed — 19 tests |
| TV source compilation | Passed |
| Physical device checks | Pending — no Android device attached |

The focused suites cover account selection/login origin rules, DJ planning and
audio envelopes, bounded queue persistence, Home item identity, and playback
clock behavior. They do not exercise live provider authorization or physical
audio output. Build logs are in `tmp/audit-final.log` and
`tmp/audit-final-compile.log`; XML results are under
`app/build/test-results/testDevDebugUnitTest` and
`sharedUi/build/test-results/jvmTest`.

TV shares the authentication sources and now declares their WebKit/Credential
Manager dependencies. It has no Google Services client configuration: its empty
client-ID resource causes an explicit configuration error if native Google
sign-in is requested. Enabling that TV feature still requires legitimate TV
Firebase configuration and replacing the placeholder with its generated resource.

Before treating the changes as device-verified, check:

1. Connect three distinct accounts for Vikify, YouTube Music and Spotify; skip
   cloud sign-in in a separate run. Switching or disconnecting one music service
   must preserve the other identities.
2. Exercise profile switching and scoped cleanup on WebView providers with
   `MULTI_PROFILE` support and with the fallback. Check rapid confirmation taps,
   cancellation during slow validation and delayed page callbacks.
3. Listen to DJ speech through phone and Bluetooth output. Adjust volume while
   ducked, mute speech, stop mid-sentence and interrupt a pending utterance;
   verify smooth gain restoration and voice/caption behavior.
4. Start and stop DJ from a queue with a nonzero position, shuffle/repeat settings
   and bounded context. Confirm recovery, then replace the queue manually and
   verify stopping DJ preserves the replacement.
5. Disable networking with downloaded songs available. Verify eligible local
   picks and a useful response for an unavailable explicit request.
6. Check smooth progress, pause/resume, buffering and manual seeking. Tap and
   drag the slider horizontally, then dismiss the sheet vertically across it;
   vertical gestures must not cause an unintended seek.
