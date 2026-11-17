# Vikify DJ companion

The Home page has a rectangular DJ card beneath the time-based greeting: “Let me pick a song for you.” It is part of the feed and scrolls away normally. Tap it to open the full-screen DJ AI studio. In Now Playing, a compact DJ card replaces the volume slider and opens the same studio while music continues. Closing the studio does not end playback.

## DJ AI studio

The studio follows the black/lavender reference with a native glowing mascot, atmosphere ribbons, DJ AI Beta title, four feature cards, and Start a Chat. Opening the studio does not automatically replace the current music.

- **Make a Playlist** opens chat, asks for a mood, and focuses the composer. A request curates a playback queue; it does not save a permanent library playlist.
- **Music Discovery** asks the DJ to pick a direction. **Based on Your Taste** starts a fresh personalized session.
- **Talk to DJ** and **Start a Chat** open the conversation. The plus menu offers requests, and the microphone uses Android speech recognition.
- Chat bubbles show actual session messages. Song rows show the current song and up to two upcoming songs, with working play/pause buttons.
- Foreground selection shows Crafting your playlist with actual taste-loading, discovery, and queue-building stages. Automatic background queue top-ups keep chat visible. There are no timed fake progress steps or sample tracks.
- The landing uses the app's original `FloatingBottomBar`, tab definitions, icons, theme colors, and safe-area spacing: Home, Explore, Library, and Search. The selected tab reflects the app section from which DJ was opened. A tab returns to that section; Search also resets and focuses its field, as in the main app. The bell opens recent DJ replies, the profile button controls spoken replies, and the chat menu also ends the DJ session.

## Native mascot

`VikifyMascot.kt` draws the waveform crown, visor, headphones, eyes, mouth, highlights, and rings in Compose Canvas. It does not use a mascot bitmap. Expressions follow thinking, speaking, listening, and playback state. The eyes blink and glance; a new song flashes the headphone rings for one second. Idle no longer runs a continuous body/mouth animation. The app's Reduce animation setting disables decorative motion. The cards observe only display-relevant state, rather than recomposing on every listening-time update.

## Recommendations

- No setup form and no fixed 90-song limit. The runtime uses the app's live music APIs.
- Opening inventory combines account recommendations, recent music, likes, and artists learned from previous DJ sessions. A guest with no evidence starts from provider recommendations and regional chart/discovery results.
- Songs arrive in six-track sections: personal picks, artist connections, and discovery. Future sections use actual listening and explicit positive feedback to choose radio seeds, including a second observed artist to avoid staying in one station.
- Rank considers provider order, learned artist preferences, freshness, section type, and artist diversity. Current/queued tracks, hidden/disliked tracks, and excluded artists are filtered. Previously heard tracks wait until fresh candidates run out.
- Unknown live-track BPM, key, genre, and acoustic features are not fabricated. The original metadata engine/catalog remain reference material and math tests; they do not cap live inventory.
- Learning is on-device and account/profile scoped. New sessions reuse learned artist priors. The app's personalization reset changes the scope.

## Conversation

**Let DJ pick** chooses a new direction; during an existing session it prepares replacement songs before advancing. Artist/song/language/mood requests search live music and replace the upcoming set while the current track continues. An unsuccessful request leaves existing queued music intact and shows an error.

Use voice input or type a request, for example: “Play Anirudh”, “Telugu love songs”, “More energy”, “Calmer”, or “Play Midnight City by M83”. “Avoid [artist]” supports artist names already in the inventory. “Skip”, “pause”, “resume”, and “replay” control playback. Greetings and thanks receive short replies. This is an on-device music request interface with template speech, not a general-purpose LLM chat service.

The voice toggle mutes spoken responses. The microphone launches the device's speech recognizer; if unavailable, text input stays available. Android text-to-speech provides the host voice. Music is ducked during speech and restored afterward.

## Phone checks

1. Open Home: verify the DJ card sits beneath the greeting without covering content. Swipe vertically from the DJ card, artwork, row text, and empty space. The whole feed should scroll, including the DJ card. Tap the card and check the DJ AI landing layout.
2. Start Based on Your Taste or submit a music request. Confirm the crafting screen returns to real playable results without configuring a persona, server, or API key.
3. Let six songs advance, or skip through them. The next section should introduce an artist connection; the following section should favor discovery. Watch the rings flash for new songs.
4. Open the mascot from Now Playing. Start a Chat, ask for a specific artist/song or language, and use Let DJ pick. Existing music should continue while the next songs load. Test a song-row play/pause button and the studio's Home/Explore/Library/Search navigation. Compare the landing's bar with the original app bar: the pill, icons, labels, colors, and bottom spacing should match.
5. Check voice input, mute/unmute, natural expressions during speech, and volume restoration after speech, pause, or interruption. In Now Playing, check previous/play/next remain usable, the DJ card has its own layout space, and the volume slider is gone.
6. Lock the phone and use notification controls. Queue loading and learning belong to the service, not the conversation screen.
7. Try an impossible song query or disconnect the network. Confirm existing music/queue is preserved and the error offers a retry.
8. End the DJ, play another song directly, switch accounts, or enter Listen Together/casting. DJ control must stop without replacing the new queue.

Build/check:

```powershell
.\gradlew.bat :app:testDevDebugUnitTest --tests com.music.vikify.DjBrainTest --tests com.music.vikify.DjLivePlannerTest --tests com.music.vikify.FirebaseProtoRuntimeTest :app:assembleDevDebug --offline
```

Device voices, recognition, network access, and region-specific availability need a device test. The current studio APK is `output/Vikify-DJ-original-nav.apk` after packaging.

For an optimized installable performance test using the same key as the debug APK:

```powershell
.\gradlew.bat :app:assembleDevRelease -PtestSigning=true --offline
```

This flag is opt-in and applies to the phone and its bundled watch APK. Ordinary release signing is unchanged. The Home/player layout update is delivered as `output/Vikify-DJ-layout-fix.apk`.

## Previous playback integration checks

- Signed universal and ARM64 debug APKs built successfully.
- All 21 unit tests passed: 10 original engine tests, 10 live-planner tests, and the Firestore packaging regression.
- Read-only Android emulator: guest onboarding, Home mascot placement, tap-to-open greeting, chat layout, live cold-start inventory, and actual music playback checked.
- The guest session received 50 live candidates and created a six-track queue. Android reported PLAYING with advancing position and buffered audio.
- A typed “Play Anirudh” request returned a response while the current song continued. Skipping then played “Ala Bolelo” credited to Anirudh Ravichander and collaborators; Android again reported PLAYING with no playback error.
- The emulator was started without audio output, so audible voice quality and microphone recognition still need a real-device check.

### Home/player layout update

- Optimized `devRelease` build with `-PtestSigning=true` completed, including the required release checks. APK signature verified; package/version and signing key match the previous test APK, and the APK is non-debuggable.
- In a fresh emulator after compilation, the Home card rendered beneath “Good afternoon” with “Let me pick a song for you.” Vertical swipes starting on both the DJ card and playlist artwork moved the feed; the DJ card scrolled out of view.
- Real DJ playback reached PLAYING. The main player showed previous/play/next followed by the bounded DJ card, with no volume row or floating mascot overlay. The previous control is also present in the DJ conversation.
- The optimized universal APK is approximately 52 MB. Final artifacts: `output/Vikify-DJ-layout-fix.apk`, `output/DJ-home-card.png`, and `output/DJ-player-card.png`.

### DJ AI studio redesign

- The reference-inspired landing, chat, and crafting screens compiled successfully. All 21 existing engine, planner, and Firebase packaging tests passed again.
- Debug emulator checks: guest setup, all four landing cards and Start a Chat fitting on screen, typed request submission, live song rows, and foreground crafting followed by real results. A song-row play button selected LISA's “Chill”; Android reported PLAYING with advancing position and buffered audio. Let DJ pick then selected a new artist-connection set.
- Visual checks caught dark header controls; the final build explicitly uses light back/menu icons. The loading subtitle follows the current query, and ending DJ clears its managed song controls while ordinary playback continues.
- Final optimized universal and ARM64 APKs built successfully, including release checks. The universal APK is non-debuggable, approximately 52 MB, and signed with the same certificate as the earlier test builds.
- Installable artifacts: `output/Vikify-DJ-studio.apk` and `output/Vikify-DJ-studio-arm64.apk`.
- Final signed APK installed in the emulator. Guest setup, landing/chat layout, real playback, a Calmer request, the actual crafting screen under simulated network delay, stopping DJ while music continues, and returning to Explore all passed. Final previews: `output/DJ-studio-home.png`, `output/DJ-studio-chat.png`, and `output/DJ-studio-crafting.png`.
- Emulator notes: software graphics stalled during a fresh boot; its trace waited in the emulator OpenGL pipe. Use `-gpu host -feature -Vulkan` for these checks. Play Store's WebView update also killed dependent app processes during an earlier attempt; checks completed after pausing updates in the temporary read-only emulator. This does not change phone app settings or APK behavior. Audible voice and microphone quality still require a physical phone.

### Original navigation bar restored in DJ

- Removed the separate DJ footer. The DJ landing now renders the same `FloatingBottomBar` and `tabs` list as the app, preserving its original theme outside the DJ's lavender palette. The bar owns its navigation-bar inset so its height and bottom spacing match.
- Optimized release build and required release checks passed. `output/Vikify-DJ-original-nav.apk` is signed with the previous test certificate.
- Emulator comparison: Home, Explore, Library, and Search icons and labels occupy the same screen bounds in Home and DJ. The same pill, border, selected state, typography, and icon definitions are reused. All landing actions remain visible.
- Tapping Search from DJ closed the studio and opened the original search page with its empty field focused and keyboard visible. No app crash entries were recorded during this check.
- Preview: `output/DJ-original-nav.png`. Comparison reference: `output/DJ-original-nav-main.png`.
