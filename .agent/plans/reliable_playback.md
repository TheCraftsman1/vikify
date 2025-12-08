# Implementation Plan - Reliable Playback & Direct Link Support

The current approach of using public Invidious/Piped instances for searching YouTube video IDs is proving unreliable due to rate limiting and blocking ("Network Error", "500"). To ensure the user can listen to music, we will pivot to a "Direct Link" approach as the primary reliable method, while attempting to improve the search as a secondary convenience.

## User Review Required

> [!IMPORTANT]
> The "Search" feature relies on public, free APIs which are currently unstable.
> **Proposed Solution:** We will add a "Paste Link" feature. You can paste any YouTube URL, and it will play instantly and reliably. This bypasses the need for the unstable search APIs.

## Proposed Changes

### 1. New Feature: Import/Paste Link
- **Page:** Create `src/pages/ImportPlaylist.jsx` (already started).
- **Functionality:**
    - Input field for YouTube URLs.
    - "Play" button.
    - When clicked, it sends the URL directly to the player.
- **Routing:** Add `/import` route in `App.jsx`.
- **Sidebar:** Update Sidebar to link to this new page.

### 2. Refactor Player Engine (`PlayerContext.jsx`)
- **Direct Play Support:** Modify `playSong` to check if the "song" object already has a valid `url`.
- **Skip Search:** If a URL is present, skip the `searchYouTube` step entirely.
- **Error Handling:** If search fails (for the Search page), show a toast/notification suggesting the user use "Paste Link" instead of just failing silently.

### 3. UI Improvements
- **Player Bar:** Fix the `onDuration` error in `Player.jsx` to ensure the progress bar works.
- **Search Page:** Add a "Can't find it? Paste Link" button/link when search fails.

## Verification Plan

### Automated Tests (Browser Subagent)
- **Test 1 (Direct Play):** Navigate to `/import`, paste a known working YouTube URL (e.g., the Coldplay one), click Play, verify playback starts and duration loads.
- **Test 2 (Search Fallback):** Try search again. If it fails, verify the app doesn't crash and provides feedback.

### Manual Verification
- User can paste a link and hear audio.
- Progress bar updates correctly.
