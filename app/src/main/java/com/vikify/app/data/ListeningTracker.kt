package com.vikify.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.vikify.app.db.MusicDatabase
import com.vikify.app.db.entities.PlayEvent
import com.vikify.app.db.entities.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
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
            } catch (e: Exception) {
                Log.e("ListeningTracker", "Failed to save local history", e)
            }
        }

        // 2. Buffer for Cloud Sync
        synchronized(_buffer) {
            _buffer.add(event)
            if (_buffer.size >= 5) { // Sync every 5 songs
                syncToCloud()
            }
        }
    }

    private fun syncToCloud() {
        val user = auth.currentUser
        if (user == null) {
            Log.w("ListeningTracker", "User not logged in, skipping cloud sync")
            return
        }

        val eventsToSync = synchronized(_buffer) { 
            val list = _buffer.toList()
            _buffer.clear()
            list
        }
        
        if (eventsToSync.isEmpty()) return

        Log.d("ListeningTracker", "Syncing ${eventsToSync.size} events to cloud")

        val batch = firestore.batch()
        val historyRef = firestore.collection("users").document(user.uid).collection("history")
        
        eventsToSync.forEach { event ->
            val doc = historyRef.document()
            batch.set(doc, event)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d("ListeningTracker", "Cloud sync successful")
                updateAggregateStats(user.uid, eventsToSync)
            }
            .addOnFailureListener { e ->
                Log.e("ListeningTracker", "Cloud sync failed", e)
                // Restore to buffer? Or just accept loss/retry from DB later?
                // For now, simpler to verify logic: just log error.
            }
    }
    
    // Client-side increment (Cheaper than Cloud Functions)
    private fun updateAggregateStats(uid: String, events: List<PlayEvent>) {
        val statsRef = firestore.collection("users").document(uid).collection("stats").document("summary")
        val newMins = events.sumOf { it.durationPlayed } / 60
        
        if (newMins == 0) return

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(statsRef)
            val currentMins = snapshot.getLong("total_minutes") ?: 0
            val currentArtist = snapshot.getString("top_artist") ?: "Unknown"
            
            // Note: True top artist calc requires querying history, stick to simple increment for now
            
            transaction.set(statsRef, mapOf(
                "total_minutes" to (currentMins + newMins),
                "last_updated" to System.currentTimeMillis()
            ), com.google.firebase.firestore.SetOptions.merge())
        }.addOnSuccessListener {
            Log.d("ListeningTracker", "Stats updated. +$newMins mins")
        }
    }
}
