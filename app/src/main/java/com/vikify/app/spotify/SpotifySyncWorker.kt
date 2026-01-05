package com.vikify.app.spotify

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vikify.app.db.MusicDatabase
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * SpotifySyncWorker - Background worker for resolving Spotify tracks to YouTube IDs
 * 
 * Part of the "Silent Migration" protocol:
 * - Runs in background after Spotify playlist import
 * - Resolves YouTube IDs for tracks with UNRESOLVED_ prefix
 * - Persists mappings to database for instant playback
 * - Respects rate limits to avoid API blocks
 */
@HiltWorker
class SpotifySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: MusicDatabase
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SpotifySyncWorker"
        private const val WORK_NAME = "spotify_sync"
        private const val BATCH_SIZE = 50
        private const val RATE_LIMIT_MS = 500L  // 500ms between API calls
        private const val DURATION_TOLERANCE_SEC = 5  // Accept matches within ±5 seconds
        
        /**
         * Enqueue background sync work
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,  // Don't restart if already running
                OneTimeWorkRequestBuilder<SpotifySyncWorker>().build()
            )
        }
        
        /**
         * Cancel any running sync
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
    
    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting Spotify sync work...")
        
        try {
            val unresolvedSongs = database.unresolvedSpotifySongs(BATCH_SIZE).first()
            
            if (unresolvedSongs.isEmpty()) {
                Log.i(TAG, "No unresolved songs, work complete")
                return Result.success()
            }
            
            Log.i(TAG, "Found ${unresolvedSongs.size} unresolved songs")
            
            var resolved = 0
            var failed = 0
            
            for ((index, song) in unresolvedSongs.withIndex()) {
                val spotifyId = song.spotifyId ?: continue
                val title = song.spotifyTitle ?: song.title
                val artist = song.spotifyArtist ?: ""
                val duration = song.duration  // in seconds
                
                try {
                    // Rate limiting
                    if (index > 0) {
                        delay(RATE_LIMIT_MS)
                    }
                    
                    // Search YouTube for matching song
                    val youtubeId = findBestYouTubeMatch(title, artist, duration)
                    
                    if (youtubeId != null) {
                        // Update database with resolved YouTube ID
                        database.updateYouTubeIdBySpotifyId(spotifyId, youtubeId)
                        resolved++
                        Log.d(TAG, "Resolved: '$title' -> $youtubeId")
                    } else {
                        failed++
                        Log.w(TAG, "Could not find match for: '$title' by '$artist'")
                    }
                    
                    // Report progress
                    setProgress(workDataOf(
                        "resolved" to resolved,
                        "failed" to failed,
                        "total" to unresolvedSongs.size,
                        "currentTitle" to title
                    ))
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving '$title': ${e.message}")
                    failed++
                }
            }
            
            Log.i(TAG, "Batch complete: $resolved resolved, $failed failed")
            
            // Check if there are more unresolved songs
            val remaining = database.countUnresolvedSpotifySongs().first()
            if (remaining > 0) {
                // Re-enqueue to continue with next batch
                Log.i(TAG, "$remaining songs remaining, re-enqueuing...")
                enqueue(applicationContext)
            } else {
                Log.i(TAG, "All songs resolved!")
            }
            
            return Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed: ${e.message}", e)
            return Result.retry()  // Will retry with backoff
        }
    }
    
    /**
     * Find the best YouTube match for a Spotify track
     * 
     * Strategy:
     * 1. Search YouTube Music with "$title $artist"
     * 2. Filter by duration (within ±5 seconds)
     * 3. Return the best match
     */
    private suspend fun findBestYouTubeMatch(
        title: String, 
        artist: String, 
        durationSec: Int
    ): String? {
        val searchQuery = "$title $artist"
        
        val searchResult = YouTube.search(
            searchQuery, 
            YouTube.SearchFilter.FILTER_SONG
        ).getOrNull()
        
        val songs = searchResult?.items
            ?.filterIsInstance<SongItem>()
            ?: return null
        
        if (songs.isEmpty()) return null
        
        // If we have duration, filter by it
        if (durationSec > 0) {
            val durationMatches = songs.filter { 
                abs((it.duration ?: 0) - durationSec) <= DURATION_TOLERANCE_SEC 
            }
            if (durationMatches.isNotEmpty()) {
                return durationMatches.first().id
            }
        }
        
        // Fall back to first result if no duration match
        return songs.first().id
    }
}
