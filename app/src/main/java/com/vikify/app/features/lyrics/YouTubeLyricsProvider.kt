package com.vikify.app.features.lyrics

import android.content.Context
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint

/**
 * YouTube Music lyrics provider
 * 
 * Uses transcript API first (synced lyrics with timestamps),
 * then falls back to plain lyrics endpoint if unavailable.
 */
object YouTubeLyricsProvider : LyricsProvider {
    override val name = "YouTube Music"
    override fun isEnabled(context: Context) = true
    
    override suspend fun getLyrics(id: String, title: String, artist: String, duration: Int): Result<String> = runCatching {
        // TRY 1: Use transcript API - returns synced lyrics with timestamps like [00:15.000]
        // This is the same method YouTube Music uses for synced lyrics display
        try {
            val transcript = YouTube.transcript(id).getOrNull()
            if (!transcript.isNullOrBlank()) {
                return@runCatching transcript
            }
        } catch (e: Exception) {
            // Transcript not available, try fallback
        }
        
        // TRY 2: Fallback to plain lyrics endpoint (no timestamps)
        val nextResult = YouTube.next(WatchEndpoint(videoId = id)).getOrThrow()
        YouTube.lyrics(
            endpoint = nextResult.lyricsEndpoint ?: throw IllegalStateException("Lyrics endpoint not found")
        ).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
    }
}
