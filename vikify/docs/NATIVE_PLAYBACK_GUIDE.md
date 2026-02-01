# Vikify Native Playback Integration Guide

## Overview

You already have the **complete OuterTune source code** in the `native/` directory! This is the production-ready Android app with:

- Full InnerTube API client with all parsers
- NewPipe signature deobfuscation
- ANDROID_VR_NO_AUTH client (no PoToken needed, fastest)
- IOS client fallback
- Dual-layer cache (download + streaming)
- Audio normalization
- And much more...

## Architecture Summary

### What You Have

```
native/
├── innertube/              # Complete YouTube Music API client
│   └── src/main/java/com/zionhuang/innertube/
│       ├── YouTube.kt      # Main API singleton (837 lines!)
│       ├── InnerTube.kt    # HTTP client
│       ├── NewPipe.kt      # Signature deobfuscation
│       ├── models/         # All data models
│       ├── pages/          # Response parsers (SearchPage, AlbumPage, etc.)
│       └── utils/          # Helper utilities
│
├── app/                    # Full Android music player app
│   └── src/main/java/com/dd3boh/outertune/
│       ├── playback/
│       │   ├── MusicService.kt     # ExoPlayer service with caching
│       │   ├── QueueBoard.kt       # Queue management
│       │   ├── PlayerConnection.kt # Player state management
│       │   └── DownloadUtil.kt     # Download management
│       │
│       ├── utils/
│       │   ├── YTPlayerUtils.kt    # Stream URL resolution
│       │   └── potoken/            # PoToken generation
│       │
│       ├── db/                     # Room database
│       ├── di/                     # Hilt dependency injection
│       └── ui/                     # Compose UI
│
├── media/                  # Modified Media3 library
├── kugou/                  # Lyrics provider
└── lrclib/                 # Another lyrics provider
```

### Key Classes Explained

#### 1. YTPlayerUtils.kt (Stream Resolution)
```kotlin
// Main client - fastest, no auth needed
private val MAIN_CLIENT: YouTubeClient = ANDROID_VR_NO_AUTH

// Fallback for problematic videos
private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(IOS)

// Resolution flow:
// 1. Get signature timestamp from NewPipe
// 2. Get player response from ANDROID_VR_NO_AUTH
// 3. Find best audio format
// 4. Deobfuscate stream URL with NewPipe
// 5. Validate URL with HEAD request
// 6. Fallback to IOS if needed
```

#### 2. MusicService.kt (Playback Pipeline)
```kotlin
// 5-step resolution pipeline:
fun createDataSourceFactory() = ResolvingDataSource.Factory { dataSpec ->
    // 1. Check if local file
    if (song?.localPath != null && song.isLocal) return file.toUri()
    
    // 2. Check download cache
    if (downloadCache.isCached(mediaId)) return dataSpec
    
    // 3. Check player cache
    if (playerCache.isCached(mediaId)) return dataSpec
    
    // 4. Check URL cache (in-memory, expires after ~6 hours)
    songUrlCache[mediaId]?.let { return it.first.toUri() }
    
    // 5. Fetch from YouTube (slowest)
    val playbackData = YTPlayerUtils.playerResponseForPlayback(...)
    songUrlCache[mediaId] = streamUrl to expiry
    return dataSpec.withUri(streamUrl.toUri())
}
```

#### 3. YouTube.kt (API Singleton)
```kotlin
// Full API support:
YouTube.search(query, filter)           // Search
YouTube.album(albumId)                  // Album details
YouTube.artist(artistId)                // Artist page
YouTube.playlist(playlistId)            // Playlist details
YouTube.player(videoId)                 // Stream URLs
YouTube.next(videoId)                   // Related songs
YouTube.searchSuggestions(query)        // Autocomplete
YouTube.home()                          // Home page
```

## Integration Options

### Option 1: Use Native App Directly (Recommended)
The `native/` directory IS a complete, working music player app.
You can build and run it directly:

```bash
cd native
./gradlew :app:assembleDebug
```

### Option 2: Capacitor Bridge to Native Module
If you want to keep your Capacitor/React frontend but use native playback:

1. **Create a Capacitor Plugin** that bridges to the native innertube module
2. **Expose key methods:**
   - `search(query, filter)` → `YouTube.search()`
   - `getStreamUrl(videoId)` → `YTPlayerUtils.playerResponseForPlayback()`
   - `play(videoId)` → Trigger native MusicService

Example plugin structure:
```kotlin
@CapacitorPlugin(name = "NativePlayer")
class NativePlayerPlugin : Plugin() {
    
    @PluginMethod
    fun search(call: PluginCall) {
        val query = call.getString("query") ?: return
        scope.launch {
            YouTube.search(query).onSuccess { result ->
                call.resolve(JSObject().apply {
                    put("items", result.items.toJson())
                })
            }
        }
    }
    
    @PluginMethod
    fun play(call: PluginCall) {
        val videoId = call.getString("videoId") ?: return
        // Start MusicService with this video ID
    }
}
```

### Option 3: Port to Capacitor Android Activity
Embed the native player as a foreground service within your Capacitor app.

## Quick Start

### Build the Native App
```bash
cd c:\Users\vishn\Desktop\vikify\native
./gradlew :app:assembleDebug
```

The APK will be at: `native/app/build/outputs/apk/debug/app-debug.apk`

### Key Dependencies (from native/innertube/build.gradle.kts)
```kotlin
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.okhttp)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.json)
implementation(libs.ktor.client.encoding)
implementation(libs.brotli)
implementation(libs.newpipe.extractor)  // For signature deobfuscation
```

## Why This Is Faster Than yt-dlp

1. **Native Kotlin** - No Python interpreter overhead
2. **ANDROID_VR_NO_AUTH client** - Fastest response times, no PoToken
3. **NewPipe extractor** - Efficient signature deobfuscation
4. **Chunked streaming** - 512KB chunks for instant playback start
5. **Multi-layer caching** - Memory → Disk → Network
6. **URL caching** - ~6 hour validity, no re-fetch needed

## Cleanup Note

The `android/innertube/` directory I created is redundant since `native/innertube/` already has the complete implementation. You can delete:
- `c:\Users\vishn\Desktop\vikify\android\innertube\` (the one I created)

The actual OuterTune innertube module is at:
- `c:\Users\vishn\Desktop\vikify\native\innertube\` (complete, 85+ files)

## Next Steps

1. **Decide on integration approach** (native app vs Capacitor bridge)
2. **Build and test the native app** to verify it works
3. **Create Capacitor plugin if needed** for React frontend integration
4. **Implement notification controls** for background playback
