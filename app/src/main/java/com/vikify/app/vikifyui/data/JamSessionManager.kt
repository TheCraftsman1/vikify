package com.vikify.app.vikifyui.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * JAM SESSION MANAGER
 * 
 * Handles real-time synchronization of Jam sessions using Firebase Realtime Database.
 * Includes timeout handling and local fallback for reliability.
 * 
 * Features:
 * - Create session with 6-digit code
 * - Join session as guest
 * - Real-time playback sync (position, play/pause)
 * - Track changes broadcast
 * - Session cleanup on leave/expire
 */
@Singleton
class JamSessionManager @Inject constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth
) {
    companion object {
        private const val TAG = "JamSessionManager"
        private const val SESSIONS_REF = "jam_sessions"
        private const val CODES_REF = "jam_codes"
        private const val CHAT_REF = "jam_chat"
        private const val TIMEOUT_MS = 10000L // 10 second timeout
    }
    
    private val sessionsRef: DatabaseReference get() = database.getReference(SESSIONS_REF)
    private val codesRef: DatabaseReference get() = database.getReference(CODES_REF)
    private val chatRef: DatabaseReference get() = database.getReference(CHAT_REF)
    
    // Track if Firebase is available
    private var useLocalFallback = false
    
    /**
     * Get current user ID, signing in anonymously if needed
     * Returns a local ID if Firebase auth fails
     */
    private suspend fun ensureUserId(): String {
        return auth.currentUser?.uid ?: run {
            try {
                withTimeout(5000L) {
                    val result = auth.signInAnonymously().await()
                    result.user?.uid ?: "local_${System.currentTimeMillis()}"
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Auth timeout, using local fallback")
                useLocalFallback = true
                "local_${System.currentTimeMillis()}"
            } catch (e: Exception) {
                Log.e(TAG, "Auth failed, using local fallback", e)
                useLocalFallback = true
                "local_${System.currentTimeMillis()}"
            }
        }
    }
    
    /**
     * Generate a random 6-digit session code
     */
    private fun generateSessionCode(): String {
        return (100000 + Random.nextInt(900000)).toString()
    }
    
    /**
     * Create a new Jam session as host
     * Falls back to local-only mode if Firebase is unavailable
     */
    suspend fun createSession(
        hostName: String,
        hostAvatar: String? = null
    ): Result<JamSession> {
        return try {
            val userId = ensureUserId()
            val code = generateSessionCode()
            val sessionId = "session_${System.currentTimeMillis()}"
            
            val session = JamSession(
                sessionId = sessionId,
                sessionCode = code,
                hostId = userId,
                hostName = hostName,
                hostAvatar = hostAvatar
            )
            
            // Try Firebase with timeout, fallback to local if it fails
            if (!useLocalFallback) {
                try {
                    val firebaseResult = withTimeout(TIMEOUT_MS) {
                        // Write to Firebase
                        val firebaseSessionId = sessionsRef.push().key ?: sessionId
                        val firebaseSession = session.copy(sessionId = firebaseSessionId)
                        
                        val updates = hashMapOf<String, Any?>(
                            "$SESSIONS_REF/$firebaseSessionId" to firebaseSession.toMap(),
                            "$CODES_REF/$code" to firebaseSessionId
                        )
                        
                        database.reference.updateChildren(updates).await()
                        Log.d(TAG, "Created Firebase session: $code")
                        firebaseSession
                    }
                    return Result.success(firebaseResult)
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Firebase timeout, using local session")
                    useLocalFallback = true
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase failed, using local session: ${e.message}")
                    useLocalFallback = true
                }
            }
            
            // Local fallback - session works on this device only
            Log.d(TAG, "Created local session: $code (Firebase unavailable)")
            Result.success(session)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Join an existing session as participant (multi-participant support)
     */
    suspend fun joinSession(
        code: String,
        guestName: String,
        guestAvatar: String? = null
    ): Result<JamSession> {
        return try {
            val userId = ensureUserId()
            
            if (useLocalFallback) {
                // Local mode - simulate a session with multi-participant support
                val participant = JamParticipant(
                    id = userId,
                    name = guestName,
                    avatar = guestAvatar,
                    joinedAt = System.currentTimeMillis(),
                    isOnline = true
                )
                val session = JamSession(
                    sessionId = "local_session_${System.currentTimeMillis()}",
                    sessionCode = code,
                    hostId = "remote_host",
                    hostName = "Host",
                    hostAvatar = null,
                    participants = mapOf(userId to participant),
                    isPlaying = true
                )
                Log.d(TAG, "Joined local session: $code")
                return Result.success(session)
            }
            
            // Try Firebase with timeout
            val result = withTimeout(TIMEOUT_MS) {
                val sessionIdSnapshot = codesRef.child(code).get().await()
                if (!sessionIdSnapshot.exists()) {
                    return@withTimeout Result.failure<JamSession>(Exception("Invalid session code"))
                }
                
                val sessionId = sessionIdSnapshot.getValue(String::class.java)
                    ?: return@withTimeout Result.failure<JamSession>(Exception("Session not found"))
                
                val sessionSnapshot = sessionsRef.child(sessionId).get().await()
                if (!sessionSnapshot.exists()) {
                    return@withTimeout Result.failure<JamSession>(Exception("Session expired"))
                }
                
                @Suppress("UNCHECKED_CAST")
                val sessionMap = sessionSnapshot.value as? Map<String, Any?>
                    ?: return@withTimeout Result.failure<JamSession>(Exception("Invalid session data"))
                
                val session = JamSession.fromMap(sessionMap)
                
                // Check if session is full (multi-participant check)
                if (session.isFull) {
                    return@withTimeout Result.failure<JamSession>(Exception("Session is full (${session.maxParticipants} participants max)"))
                }
                
                // Check if already a participant
                if (session.participants.containsKey(userId)) {
                    Log.d(TAG, "Already in session, returning existing")
                    return@withTimeout Result.success(session)
                }
                
                if (session.hostId == userId) {
                    return@withTimeout Result.failure<JamSession>(Exception("Cannot join your own session"))
                }
                
                // Create participant object
                val participant = JamParticipant(
                    id = userId,
                    name = guestName,
                    avatar = guestAvatar,
                    joinedAt = System.currentTimeMillis(),
                    isOnline = true
                )
                
                // Add to participants map (multi-participant style)
                val updates = mapOf(
                    "participants/$userId" to participant.toMap(),
                    "lastUpdated" to System.currentTimeMillis()
                )
                
                sessionsRef.child(sessionId).updateChildren(updates).await()
                
                val updatedParticipants = session.participants.toMutableMap()
                updatedParticipants[userId] = participant
                
                val updatedSession = session.copy(
                    participants = updatedParticipants
                )
                
                Log.d(TAG, "Joined Firebase session: $code (${updatedSession.participantCount} participants)")
                Result.success(updatedSession)
            }
            result
            
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Join timeout")
            Result.failure(Exception("Connection timeout. Please try again."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Observe real-time updates to a session
     */
    fun observeSession(sessionId: String): Flow<JamSession> = callbackFlow {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            // Local mode - no real-time updates
            awaitClose { }
            return@callbackFlow
        }
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val sessionMap = snapshot.value as? Map<String, Any?>
                    if (sessionMap != null) {
                        val session = JamSession.fromMap(sessionMap)
                        trySend(session)
                    }
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Session observation cancelled", error.toException())
            }
        }
        
        sessionsRef.child(sessionId).addValueEventListener(listener)
        
        awaitClose {
            sessionsRef.child(sessionId).removeEventListener(listener)
        }
    }
    
    /**
     * Update playback state (host only)
     */
    suspend fun updatePlaybackState(
        sessionId: String,
        isPlaying: Boolean,
        position: Long
    ) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            return // Local mode - no sync needed
        }
        
        try {
            withTimeout(5000L) {
                val updates = mapOf(
                    "isPlaying" to isPlaying,
                    "currentPosition" to position,
                    "lastUpdated" to System.currentTimeMillis()
                )
                sessionsRef.child(sessionId).updateChildren(updates).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update playback state", e)
        }
    }
    
    /**
     * Change the current track (host only)
     */
    suspend fun changeTrack(
        sessionId: String,
        track: Track
    ) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            return
        }
        
        try {
            withTimeout(5000L) {
                val updates = mapOf(
                    "currentTrackId" to track.id,
                    "currentTrackTitle" to track.title,
                    "currentTrackArtist" to track.artist,
                    "currentTrackArtwork" to track.remoteArtworkUrl,
                    "currentPosition" to 0L,
                    "isPlaying" to true,
                    "lastUpdated" to System.currentTimeMillis()
                )
                sessionsRef.child(sessionId).updateChildren(updates).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change track", e)
        }
    }
    
    /**
     * Leave the current session (multi-participant aware)
     */
    suspend fun leaveSession(sessionId: String) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            Log.d(TAG, "Left local session")
            return
        }
        
        try {
            val userId = auth.currentUser?.uid ?: return
            
            withTimeout(5000L) {
                val sessionSnapshot = sessionsRef.child(sessionId).get().await()
                if (!sessionSnapshot.exists()) return@withTimeout
                
                @Suppress("UNCHECKED_CAST")
                val sessionMap = sessionSnapshot.value as? Map<String, Any?> ?: return@withTimeout
                val session = JamSession.fromMap(sessionMap)
                
                if (session.hostId == userId) {
                    // Host leaves - delete entire session
                    val code = session.sessionCode
                    sessionsRef.child(sessionId).removeValue().await()
                    codesRef.child(code).removeValue().await()
                    Log.d(TAG, "Host left - deleted session")
                } else if (session.participants.containsKey(userId)) {
                    // Multi-participant: Remove from participants map
                    sessionsRef.child(sessionId).child("participants").child(userId).removeValue().await()
                    sessionsRef.child(sessionId).child("lastUpdated").setValue(System.currentTimeMillis()).await()
                    Log.d(TAG, "Participant left session (${session.participantCount - 1} remaining)")
                } else if (session.guestId == userId) {
                    // Legacy: Clear single guest fields
                    val updates = mapOf<String, Any?>(
                        "guestId" to null,
                        "guestName" to null,
                        "guestAvatar" to null,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                    sessionsRef.child(sessionId).updateChildren(updates).await()
                    Log.d(TAG, "Guest left session (legacy)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave session", e)
        }
    }
    
    /**
     * Check if a session code is valid
     */
    suspend fun isValidCode(code: String): Boolean {
        return try {
            withTimeout(5000L) {
                val snapshot = codesRef.child(code).get().await()
                snapshot.exists()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun getCurrentUserId(): String? = auth.currentUser?.uid
    
    /**
     * Check if we're in local fallback mode
     */
    fun isLocalMode(): Boolean = useLocalFallback
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // QUEUE MANAGEMENT FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Add a track to the shared queue
     */
    suspend fun addToQueue(
        sessionId: String,
        trackId: String,
        title: String,
        artist: String,
        artwork: String?,
        duration: Long,
        addedByName: String
    ): Result<JamQueueItem> {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            return Result.failure(Exception("Queue sync not available in offline mode"))
        }

        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))

            val queueItem = JamQueueItem(
                id = "q_${System.currentTimeMillis()}_${Random.nextInt(9999)}",
                trackId = trackId,
                title = title,
                artist = artist,
                artwork = artwork,
                duration = duration,
                addedBy = userId,
                addedByName = addedByName,
                addedAt = System.currentTimeMillis()
            )

            withTimeout(5000L) {
                val queueRef = sessionsRef.child(sessionId).child("queue")
                val newItemRef = queueRef.push()
                val firebaseId = newItemRef.key ?: queueItem.id
                val finalItem = queueItem.copy(id = firebaseId)
                
                newItemRef.setValue(finalItem.toMap()).await()
                sessionsRef.child(sessionId).child("lastUpdated").setValue(System.currentTimeMillis())
                
                Result.success(finalItem)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add to queue", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a track from the queue
     */
    suspend fun removeFromQueue(sessionId: String, itemId: String) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) return

        try {
            withTimeout(5000L) {
                sessionsRef.child(sessionId).child("queue").child(itemId).removeValue().await()
                sessionsRef.child(sessionId).child("lastUpdated").setValue(System.currentTimeMillis())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove from queue", e)
        }
    }
    
    /**
     * Send a live reaction
     */
    suspend fun sendReaction(sessionId: String, emoji: String) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) return

        try {
            val userId = auth.currentUser?.uid ?: return
            
            val reaction = JamReaction(
                id = "react_${System.currentTimeMillis()}_${Random.nextInt(999)}",
                emoji = emoji,
                senderId = userId,
                senderName = "User",
                timestamp = System.currentTimeMillis()
            )
            
            withTimeout(2000L) {
                val reactRef = sessionsRef.child(sessionId).child("reactions").push()
                val finalReaction = reaction.copy(id = reactRef.key ?: reaction.id)
                reactRef.setValue(finalReaction.toMap()).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reaction", e)
        }
    }

     /**
     * Observe live reactions
     */
    fun observeReactions(sessionId: String): Flow<List<JamReaction>> = callbackFlow {
        if (useLocalFallback || sessionId.startsWith("local_")) {
            trySend(emptyList())
            awaitClose {}
            return@callbackFlow
        }
        
        val reactionsRef = sessionsRef.child(sessionId).child("reactions")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val reactions = snapshot.children.mapNotNull { child -> 
                    child.value?.let { 
                        @Suppress("UNCHECKED_CAST")
                        JamReaction.fromMap(it as Map<String, Any?>) 
                    }
                }
                trySend(reactions)
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        
        reactionsRef.limitToLast(20).addValueEventListener(listener)
        awaitClose { reactionsRef.removeEventListener(listener) }
    }

    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CHAT FUNCTIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Send a chat message in a Jam session
     */
    suspend fun sendChatMessage(
        sessionId: String,
        senderName: String,
        senderAvatar: String?,
        message: String
    ): Result<JamChatMessage> {
        return try {
            val userId = auth.currentUser?.uid ?: return Result.failure(Exception("Not authenticated"))
            
            val chatMessage = JamChatMessage(
                id = "msg_${System.currentTimeMillis()}_${userId.take(6)}",
                sessionId = sessionId,
                senderId = userId,
                senderName = senderName,
                senderAvatar = senderAvatar,
                message = message,
                timestamp = System.currentTimeMillis(),
                isSystem = false
            )
            
            if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
                // Local mode - just return the message (won't sync)
                Log.d(TAG, "Sent local chat message")
                return Result.success(chatMessage)
            }
            
            withTimeout(5000L) {
                chatRef.child(sessionId).push().setValue(chatMessage.toMap()).await()
                Log.d(TAG, "Sent chat message: ${message.take(20)}...")
            }
            
            Result.success(chatMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send chat message", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send a system message (e.g., "User joined")
     */
    suspend fun sendSystemMessage(sessionId: String, message: String) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            return
        }
        
        try {
            val systemMessage = JamChatMessage.systemMessage(sessionId, message)
            withTimeout(5000L) {
                chatRef.child(sessionId).push().setValue(systemMessage.toMap()).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send system message", e)
        }
    }
    
    /**
     * Observe chat messages for a session in real-time
     */
    fun observeChat(sessionId: String): Flow<List<JamChatMessage>> = callbackFlow {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            // Local mode - emit empty list
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        
        val messages = mutableListOf<JamChatMessage>()
        
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                @Suppress("UNCHECKED_CAST")
                val messageMap = snapshot.value as? Map<String, Any?>
                if (messageMap != null) {
                    val message = JamChatMessage.fromMap(messageMap)
                    messages.add(message)
                    // Sort by timestamp and emit
                    trySend(messages.sortedBy { it.timestamp })
                }
            }
            
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                val messageMap = snapshot.value as? Map<String, Any?>
                if (messageMap != null) {
                    val messageId = messageMap["id"] as? String
                    messages.removeAll { it.id == messageId }
                    trySend(messages.sortedBy { it.timestamp })
                }
            }
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Chat observation cancelled", error.toException())
            }
        }
        
        // Limit to last 50 messages to save bandwidth and memory (Battery Optimization Point 4)
        chatRef.child(sessionId)
            .orderByChild("timestamp")
            .limitToLast(50)
            .addChildEventListener(listener)
        
        awaitClose {
            chatRef.child(sessionId).removeEventListener(listener)
        }
    }
    
    /**
     * Delete all chat messages for a session (called when session ends)
     */
    suspend fun clearSessionChat(sessionId: String) {
        if (useLocalFallback || sessionId.startsWith("local_") || sessionId.startsWith("session_")) {
            return
        }
        
        try {
            withTimeout(5000L) {
                chatRef.child(sessionId).removeValue().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chat", e)
        }
    }
}
