# DJ UI refresh

Applied the four supplied navy studio replacements: `DjScreen.kt`,
`DjStudioVisuals.kt`, `VikifyMascot.kt`, and `DjCompanionControl.kt`.
The original ZIP at `dist/Vikify-DJ-UI-Navy.zip` records the initial navy refresh.
`dist/Vikify-DJ-UI-Headphones.zip` contains the current UI sources and headphones.
Original UI sources were preserved in `tmp/dj-ui-before-refresh/`.

The composer now has a full-width text field, microphone, and send button. Its
`+` shortcut button was removed at the user's request. Request shortcuts,
activity, preferences, and spoken replies appear in the DJ AI pill / chat
overflow menu.

The studio and companion card use the user's original transparent photographic
headphones asset (`drawable-nodpi/dj_realistic_headphones.png`). A uniform canvas
transform fits the existing animated face into the opening, behind the cushions.
Blink, gaze, reduced motion, and voice-reactive mouth behavior remain live.
Chat and preferences show whether selection used local rules or a real Nemotron
response; no configured backend means local recommendations.

Integration adjustments to the supplied code:

- The first nonblank edit opens chat. A single composer stays mounted across
  landing/chat so the IME connection, cursor, and text survive that transition.
- The headphones follow-up uses a `TextFieldState` draft so rapid edits are
  synchronized independently of screen recomposition and keep their selection.
- Landing displays service errors as well as command errors.
- Zero-sized halos skip drawing instead of constructing a zero-radius gradient.
- Chat uses a smaller mascot header in constrained layouts and hides that header
  when there is too little space, reserving the viewport for messages and input.

## Initial navy refresh verification

- Phone APK assembly and TV Kotlin compilation passed using
  `:app:assembleDevDebug :tv:compileDevDebugKotlin`.
- The universal debug APK signature verified successfully.
- Emulator checks confirmed the landing design, all four + shortcuts, the DJ AI
  options, return navigation, and automatic chat entry while preserving `chill`
  in the typed order. The original field-recreation bug produced `hillc` before
  the shared-composer fix.
- A 320dp-wide compact viewport reproduced the fixed-header crowding problem.
  The adaptive fix compiles, but its final emulator retest was interrupted by a
  pre-existing playback-service startup ANR and remains incomplete.
- All five DJ backend source hashes match their values before this UI change.

The captured startup ANR waits in `SharedPreferencesImpl.awaitLoadedLocked`,
called by `MediaWidgetSnapshot.load` and `PlaybackService.publishWidgetState`
during `PlaybackService.onCreate`. The report has no new DJ UI frames. Android
system app updates and unrelated system-app failures also interrupted the test
session. No backend changes were made to address these separate startup issues.

Build log: `tmp/dj-ui-refresh-build.log`. Emulator captures and UI trees:
`tmp/dj-ui-*.png` / `tmp/dj-ui-*.xml`. Startup diagnostic: `tmp/dj-ui-anr.txt`.

## Headphones follow-up

The enlarged studio mascot and compact Home companion were visually checked on
the emulator at 1080x2340. The supplied headphones fit around the live face and
the composer has no `+` control. Captures: `tmp/headphones-dj.png` and
`tmp/headphones-studio.png`. The emulator initially displayed a System UI ANR;
it recovered after choosing Wait. This is not a clean physical-device playback
test.

The current integration also replaces repetitive unready Home collections with
usable personal mixes, explicit pins, and saved native playlists. A failed
catalog fetch no longer returns hardcoded starter songs as successful results.
See `dj-brain/VMIE_SETUP.md` for the server-side Nemotron connection; acoustic
analysis, lyrics analysis, and full recording-identity linking remain future
work.
