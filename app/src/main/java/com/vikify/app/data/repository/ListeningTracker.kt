package com.vikify.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vikify.app.data.repository.db.MusicDatabase
import com.vikify.app.data.repository.db.entities.entities.PlayEvent
import com.vikify.app.data.repository.db.entities.entities.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import android.util.Log

@Singleton
class ListeningTracker @Inject constructor(
    private val database: MusicDatabase,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val _buffer = mutableListOf<PlayEvent>()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Call this when playback progress updates (e.g. every 30s or on finish)
    fun recordProgress(song: SongEntity, artistName: String, secondsPlayed: Int) {
        if (secondsPlayed < 30) return // Ignore skips

        val event = PlayEvent(
            songId = song.id,
            title = song.title,
            artist = artistName,
            genre = null,

            durationPlayed = secondsPlayed,
            timestamp = System.currentTimeMillis()
        )
        
        Log.d("ListeningTracker", "Recording event: ${event.title} (${event.durationPlayed}s)")

        // 1. Save Local (Instant)
        scope.launch {
            try {
                database.insert(event)
                // Also increment aggregate play count for Profile stats
                database.incrementPlayCount(song.id)
            } catch (e: Exception) {
                Log.e("ListeningTracker", "Failed to save local history", e)
            }
        }

        // 2. Buffer for Cloud Sync
        synchronized(_buffer) {
            _buffer.add(event)
            // FIXED: Sync every song to ensure "minutes played" is accurate immediately
            if (_buffer.size >= 1) { 
                // Launch sync in background to avoid blocking
                scope.launch {
                    syncToCloud()
                }
            }
        }
    }

    private suspend fun syncToCloud() {
        var user = auth.currentUser
        if (user == null) {
            Log.w("ListeningTracker", "User null, attempting anonymous auth...")
            try {
                // Try to sign in anonymously to save stats
                val result = auth.signInAnonymously().await()
                user = result.user
            } catch (e: Exception) {
                Log.e("ListeningTracker", "Anonymous auth failed during sync", e)
            }
        }

        if (user == null) {
            Log.w("ListeningTracker", "Still no user, skipping cloud sync")
            return
        }

        val eventsToSync = synchronized(_buffer) { 
            val list = _buffer.toList()
            _buffer.clear()
            list
        }
        
        if (eventsToSync.isEmpty()) return

        Log.d("ListeningTracker", "Syncing ${eventsToSync.size} events to cloud for ${user.uid}")

        val batch = firestore.batch()
        val historyRef = firestore.collection("users").document(user.uid).collection("history")
        
        eventsToSync.forEach { event ->
            val doc = historyRef.document()
            batch.set(doc, event)
        }

        try {
            batch.commit().await()
            Log.d("ListeningTracker", "Cloud sync successful")
            updateAggregateStats(user.uid, eventsToSync)
        } catch (e: Exception) {
            Log.e("ListeningTracker", "Cloud sync failed", e)
            // Optional: Re-add to buffer on failure
        }
    }
    
    // Client-side increment (Cheaper than Cloud Functions)
    private fun updateAggregateStats(uid: String, events: List<PlayEvent>) {
        val statsRef = firestore.collection("users").document(uid).collection("stats").document("summary")
        val newMins = events.sumOf { it.durationPlayed } / 60
        
        // Even if 0 mins (short usage), we might want to update timestamps, but usually wait for >1 min
        if (newMins == 0 && events.sumOf { it.durationPlayed } < 60) return

        // Use firestore.runTransaction but don't block
        scope.launch {
            try {
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(statsRef)
                    val currentMins = snapshot.getLong("total_minutes") ?: 0
                    val currentArtist = snapshot.getString("top_artist") ?: "Pending..."
                    
                    transaction.set(statsRef, mapOf(
                        "total_minutes" to (currentMins + newMins),
                        "last_updated" to System.currentTimeMillis()
                    ), com.google.firebase.firestore.SetOptions.merge())
                }.await()
                Log.d("ListeningTracker", "Stats updated. +$newMins mins")
            } catch (e: Exception) {
                 Log.e("ListeningTracker", "Stats update failed", e)
            }
        }
    }
}
