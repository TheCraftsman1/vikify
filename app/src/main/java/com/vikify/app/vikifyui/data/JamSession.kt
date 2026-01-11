package com.vikify.app.vikifyui.data

/**
 * JAM SESSION DATA MODELS
 * 
 * Core data structures for collaborative listening sessions.
 * Used for both local state and Firebase sync.
 * 
 * Supports multi-participant sessions (Spotify Jam style).
 */

/**
 * Represents a participant in a Jam session
 */
data class JamParticipant(
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "avatar" to avatar,
        "joinedAt" to joinedAt,
        "isOnline" to isOnline
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): JamParticipant {
            return JamParticipant(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                avatar = map["avatar"] as? String,
                joinedAt = (map["joinedAt"] as? Long) ?: System.currentTimeMillis(),
                isOnline = (map["isOnline"] as? Boolean) ?: true
            )
        }
    }
}

/**
 * Represents a Jam session with multiple participants (Spotify-style)
 */
data class JamSession(
    val sessionId: String = "",
    val sessionCode: String = "",  // 6-digit join code
    
    // Host info
    val hostId: String = "",
    val hostName: String = "",
    val hostAvatar: String? = null,
    
    // Multi-participant support (new)
    val participants: Map<String, JamParticipant> = emptyMap(),
    val maxParticipants: Int = 32,  // Spotify allows up to 32
    
    // Legacy guest fields (for backward compatibility)
    val guestId: String? = null,
    val guestName: String? = null,
    val guestAvatar: String? = null,
    
    // Playback state (synced in real-time)
    val currentTrackId: String? = null,
    val currentTrackTitle: String? = null,
    val currentTrackArtist: String? = null,
    val currentTrackArtwork: String? = null,
    val currentPosition: Long = 0L,        // Position in milliseconds
    val isPlaying: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    
    // Collaborative Queue (NEW)
    val queue: List<JamQueueItem> = emptyList(),
    
    // Live Reactions (NEW)
    val reactions: Map<String, JamReaction> = emptyMap(),
    
    // Session metadata
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (2 * 60 * 60 * 1000) // 2 hours
) {
    /**
     * Check if session has any participants (backward compatible)
     */
    val hasGuest: Boolean get() = participants.isNotEmpty() || guestId != null
    
    /**
     * Total participant count (excluding host)
     */
    val participantCount: Int get() = participants.size.coerceAtLeast(if (guestId != null) 1 else 0)
    
    /**
     * Check if session can accept more participants
     */
    val isFull: Boolean get() = participantCount >= maxParticipants
    
    /**
     * Get all participants as a list (for UI display)
     */
    val participantList: List<JamParticipant> get() {
        return if (participants.isNotEmpty()) {
            participants.values.toList().sortedBy { it.joinedAt }
        } else if (guestId != null) {
            // Legacy support: convert single guest to participant
            listOf(JamParticipant(
                id = guestId,
                name = guestName ?: "Guest",
                avatar = guestAvatar
            ))
        } else {
            emptyList()
        }
    }
    
    /**
     * Check if session is expired
     */
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    
    /**
     * Convert to Firebase-compatible map
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "sessionId" to sessionId,
        "sessionCode" to sessionCode,
        "hostId" to hostId,
        "hostName" to hostName,
        "hostAvatar" to hostAvatar,
        "participants" to participants.mapValues { it.value.toMap() },
        "maxParticipants" to maxParticipants,
        "guestId" to guestId,
        "guestName" to guestName,
        "guestAvatar" to guestAvatar,
        "currentTrackId" to currentTrackId,
        "currentTrackTitle" to currentTrackTitle,
        "currentTrackArtist" to currentTrackArtist,
        "currentTrackArtwork" to currentTrackArtwork,
        "currentPosition" to currentPosition,
        "isPlaying" to isPlaying,
        "lastUpdated" to lastUpdated,
        "createdAt" to createdAt,
        "expiresAt" to expiresAt
    )
    
    companion object {
        /**
         * Create from Firebase snapshot data
         */
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): JamSession {
            // Parse participants map
            val participantsRaw = map["participants"] as? Map<String, Any?> ?: emptyMap()
            val participants = participantsRaw.mapValues { (_, value) ->
                val participantMap = value as? Map<String, Any?> ?: emptyMap()
                JamParticipant.fromMap(participantMap)
            }
            
            // Parse queue (map to list)
            val queueRaw = map["queue"] as? Map<String, Any?> ?: emptyMap()
            val queue = queueRaw.values.mapNotNull { 
                val itemMap = it as? Map<String, Any?>
                if (itemMap != null) JamQueueItem.fromMap(itemMap) else null
            }.sortedBy { it.addedAt }
            
            // Parse reactions
            val reactionsRaw = map["reactions"] as? Map<String, Any?> ?: emptyMap()
            val reactions = reactionsRaw.mapValues { (_, value) ->
                val reactionMap = value as? Map<String, Any?> ?: emptyMap()
                JamReaction.fromMap(reactionMap)
            }
            
            return JamSession(
                sessionId = map["sessionId"] as? String ?: "",
                sessionCode = map["sessionCode"] as? String ?: "",
                hostId = map["hostId"] as? String ?: "",
                hostName = map["hostName"] as? String ?: "",
                hostAvatar = map["hostAvatar"] as? String,
                participants = participants,
                maxParticipants = (map["maxParticipants"] as? Number)?.toInt() ?: 32,
                guestId = map["guestId"] as? String,
                guestName = map["guestName"] as? String,
                guestAvatar = map["guestAvatar"] as? String,
                currentTrackId = map["currentTrackId"] as? String,
                currentTrackTitle = map["currentTrackTitle"] as? String,
                currentTrackArtist = map["currentTrackArtist"] as? String,
                currentTrackArtwork = map["currentTrackArtwork"] as? String,
                currentPosition = (map["currentPosition"] as? Long) ?: 0L,
                isPlaying = (map["isPlaying"] as? Boolean) ?: false,
                lastUpdated = (map["lastUpdated"] as? Long) ?: System.currentTimeMillis(),
                createdAt = (map["createdAt"] as? Long) ?: System.currentTimeMillis(),
                expiresAt = (map["expiresAt"] as? Long) ?: (System.currentTimeMillis() + 7200000),
                queue = queue,
                reactions = reactions
            )
        }
    }
}

/**
 * State machine for Jam session lifecycle
 */
sealed class JamSessionState {
    /**
     * No active session
     */
    object Idle : JamSessionState()
    
    /**
     * Creating a new session as host
     */
    object Creating : JamSessionState()
    
    /**
     * Session created, waiting for guest to join
     */
    data class WaitingForGuest(
        val sessionCode: String,
        val session: JamSession
    ) : JamSessionState()
    
    /**
     * Attempting to join a session with code
     */
    data class Joining(val sessionCode: String) : JamSessionState()
    
    /**
     * Active 2-person session
     */
    data class Active(
        val session: JamSession,
        val isHost: Boolean
    ) : JamSessionState()
    
    /**
     * Error state
     */
    data class Error(val message: String) : JamSessionState()
}

/**
 * Events that can occur during a Jam session
 */
sealed class JamSessionEvent {
    data class GuestJoined(val guestName: String) : JamSessionEvent()
    object GuestLeft : JamSessionEvent()
    object HostLeft : JamSessionEvent()
    data class TrackChanged(val trackTitle: String, val artist: String) : JamSessionEvent()
    data class PlaybackChanged(val isPlaying: Boolean) : JamSessionEvent()
    object SessionExpired : JamSessionEvent()
}

/**
 * Playback sync command from host
 */
data class PlaybackSync(
    val trackId: String?,
    val position: Long,
    val isPlaying: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "trackId" to trackId,
        "position" to position,
        "isPlaying" to isPlaying,
        "timestamp" to timestamp
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): PlaybackSync {
            return PlaybackSync(
                trackId = map["trackId"] as? String,
                position = (map["position"] as? Long) ?: 0L,
                isPlaying = (map["isPlaying"] as? Boolean) ?: false,
                timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis()
            )
        }
    }
}

/**
 * Chat message in a Jam session
 */
data class JamChatMessage(
    val id: String = "",
    val sessionId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderAvatar: String? = null,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false  // For system messages like "joined", "left"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "sessionId" to sessionId,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderAvatar" to senderAvatar,
        "message" to message,
        "timestamp" to timestamp,
        "isSystem" to isSystem
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): JamChatMessage {
            return JamChatMessage(
                id = map["id"] as? String ?: "",
                sessionId = map["sessionId"] as? String ?: "",
                senderId = map["senderId"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                senderAvatar = map["senderAvatar"] as? String,
                message = map["message"] as? String ?: "",
                timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                isSystem = (map["isSystem"] as? Boolean) ?: false
            )
        }
        
        /**
         * Create a system message (e.g., "User joined the session")
         */
        fun systemMessage(sessionId: String, message: String): JamChatMessage {
            return JamChatMessage(
                id = "sys_${System.currentTimeMillis()}",
                sessionId = sessionId,
                senderId = "system",
                senderName = "System",
                message = message,
                isSystem = true
            )
        }
    }
}

/**
 * Represents a track in the collaborative Jam queue
 */
data class JamQueueItem(
    val id: String = "",           // Unique queue item ID
    val trackId: String = "",      // Music track ID
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val duration: Long = 0L,
    val addedBy: String = "",      // User ID who added
    val addedByName: String = "",  // Display name
    val addedAt: Long = System.currentTimeMillis(),
    val votes: Int = 0             // Upvotes for queue ordering
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "trackId" to trackId,
        "title" to title,
        "artist" to artist,
        "artwork" to artwork,
        "duration" to duration,
        "addedBy" to addedBy,
        "addedByName" to addedByName,
        "addedAt" to addedAt,
        "votes" to votes
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): JamQueueItem {
            return JamQueueItem(
                id = map["id"] as? String ?: "",
                trackId = map["trackId"] as? String ?: "",
                title = map["title"] as? String ?: "",
                artist = map["artist"] as? String ?: "",
                artwork = map["artwork"] as? String,
                duration = (map["duration"] as? Long) ?: 0L,
                addedBy = map["addedBy"] as? String ?: "",
                addedByName = map["addedByName"] as? String ?: "",
                addedAt = (map["addedAt"] as? Long) ?: System.currentTimeMillis(),
                votes = (map["votes"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

/**
 * Represents a live reaction during a Jam session (emoji that floats across screen)
 */
data class JamReaction(
    val id: String = "",
    val emoji: String = "🎵",
    val senderId: String = "",
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "emoji" to emoji,
        "senderId" to senderId,
        "senderName" to senderName,
        "timestamp" to timestamp
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): JamReaction {
            return JamReaction(
                id = map["id"] as? String ?: "",
                emoji = map["emoji"] as? String ?: "🎵",
                senderId = map["senderId"] as? String ?: "",
                senderName = map["senderName"] as? String ?: "",
                timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis()
            )
        }
        
        val AVAILABLE_EMOJIS = listOf("🔥", "❤️", "🎵", "🎤", "👏", "💯", "🙌", "✨")
    }
}
