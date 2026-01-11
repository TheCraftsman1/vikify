# Feature: Local Playlist Management & UI Enhancements

## 🚀 Changes
This PR introduces comprehensive local playlist management and several UI updates.

### 🎵 Local Playlist Management
- **Create Playlist**: New dialog in `LibraryScreen` to create local playlists.
- **Add to Playlist**: Integrated into `SongContextMenu` via a new `PlaylistPickerDialog`.
- **Edit/Delete**: Context menu on playlist items to Rename or Delete.
- **Playback**: Seamless playback of local playlists via `PlayerViewModel` integration.
- **Hybrid Sync**: Support for mixed local and Spotify playlists.

### 🎨 UI Enhancements
- **Voice Search**: Added microphone to Search bar with `SpeechRecognizer`.
- **Edit Profile**: Dialog to update user display name.
- **Time Capsule**: Enhanced visuals with 3-layer parallax starfield.
- **Moods**: Updated cards to support high-quality local assets.

## 🛠️ Technical Details
- **Architecture**: Clean MVVM implementation using `PlaylistRepository` and `PlayerViewModel`.
- **Database**: Updated `PlaylistsDao` and `PlaylistEntity` for local management.
- **Tests**: Verified manually (see "Verification" below).

## ✅ Verification
1. **Create**: Library -> "+" -> "My Playlist" -> Exists.
2. **Add**: Song Menu -> "Add to Playlist" -> Select "My Playlist" -> Toast "Added".
3. **Play**: Click "My Playlist" -> Songs play correctly.
4. **Build**: Project compiles successfully.
