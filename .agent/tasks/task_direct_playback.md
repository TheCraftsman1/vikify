# Task: Implement Direct Link Playback

## Context
The user is experiencing issues with the automated YouTube search due to API blocking. We need to provide a reliable way to play music. The most reliable method is allowing the user to paste a direct YouTube link, which `react-player` can handle natively without extra API calls.

## Steps

1.  **Update `PlayerContext.jsx`**:
    -   Modify `playSong` to accept a song object that might already have a `url` property.
    -   If `song.url` exists, skip the `searchYouTube` call and use it directly.
    -   Ensure metadata (title, artist, image) is handled gracefully if missing (use placeholders or extract from URL if possible, though extraction is hard without API).

2.  **Finish `ImportPlaylist.jsx`**:
    -   Ensure the `handleImport` function creates a proper song object with `url`, `title` (can default to "Imported Song"), and `artist`.
    -   Call `playSong` with this object.

3.  **Update `App.jsx`**:
    -   Add the route for `/import`.

4.  **Update `Sidebar.jsx`**:
    -   Make the "Create Playlist" or a new button link to `/import`.

5.  **Fix `Player.jsx`**:
    -   Address the `onDuration` error (likely a prop mismatch or `react-player` version quirk).
    -   Ensure the progress bar works for direct links.

## Acceptance Criteria
-   User can navigate to `/import`.
-   User can paste a YouTube URL (e.g., `https://www.youtube.com/watch?v=...`).
-   Clicking "Play" starts the audio immediately.
-   The Player bar shows the song (even with placeholder info) and the progress bar moves.
