package com.music.bitchord.data.personalization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

@Serializable enum class TrackPreference { MORE, HIDE }
data class MixFeedback(val revision: Long = 0, val preferences: Map<String, TrackPreference> = emptyMap())

@Serializable
data class ListeningEvidenceSnapshot(
    val schemaVersion: Int = 1,
    val scopeKey: String,
    val feedbackRevision: Long = 0,
    val preferences: Map<String, TrackPreference> = emptyMap(),
    val sessions: List<ListeningSessionRecord> = emptyList(),
) {
    fun feedback() = MixFeedback(feedbackRevision, preferences)
}

/** Bounded, account-scoped local evidence with atomic updates and idempotent checkpoints. */
class ListeningEvidenceStore(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cache = ConcurrentHashMap<String, ListeningEvidenceSnapshot>()
    private val retired = ConcurrentHashMap.newKeySet<String>()
    private val _feedbackChanges = MutableStateFlow(0L)
    val feedbackChanges: StateFlow<Long> = _feedbackChanges.asStateFlow()

    /** A nonblocking view of already loaded preferences for final UI publication guards. */
    fun knownFeedback(scope: String): MixFeedback? = cache[key(scope)]?.feedback()

    suspend fun snapshot(scope: String): ListeningEvidenceSnapshot = withContext(Dispatchers.IO) {
        val key = key(scope)
        locks.getOrPut(key) { Mutex() }.withLock { read(key) }
    }

    suspend fun record(scope: String, session: ListeningSessionRecord) = withContext(Dispatchers.IO) {
        val key = key(scope)
        locks.getOrPut(key) { Mutex() }.withLock {
            if (key in retired) return@withLock
            val old = read(key)
            val existing = old.sessions.firstOrNull { it.id == session.id }
            if (existing != null && (existing.revision >= session.revision ||
                (existing.ended != null && session.ended == null))) return@withLock
            val sessions = (old.sessions.filterNot { it.id == session.id } + session)
                .filter { it.updatedAt in (clock() - RETENTION_MS)..clock() }
                .sortedByDescending { it.updatedAt }.take(MAX_SESSIONS)
            write(key, old.copy(sessions = sessions))
        }
    }

    suspend fun setPreference(scope: String, trackId: String, preference: TrackPreference?): MixFeedback =
        withContext(Dispatchers.IO) {
            require(trackId.isNotBlank())
            val key = key(scope)
            locks.getOrPut(key) { Mutex() }.withLock {
                check(key !in retired) { "Recommendation scope was reset" }
                val old = read(key)
                if (old.preferences[trackId] == preference) return@withLock old.feedback()
                val preferences = old.preferences.toMutableMap().also {
                    if (preference == null) it.remove(trackId) else it[trackId] = preference
                }
                check(preferences.size <= 10_000) { "Recommendation feedback limit reached" }
                val next = old.copy(preferences = preferences, feedbackRevision = old.feedbackRevision + 1)
                write(key, next)
                _feedbackChanges.update { it + 1 }
                next.feedback()
            }
        }

    /** Retires queued checkpoints before deletion, including writes already waiting on this lock. */
    suspend fun clearScope(scope: String) = withContext(Dispatchers.IO) {
        val key = key(scope)
        retired.add(key)
        locks.getOrPut(key) { Mutex() }.withLock {
            cache.remove(key)
            listOf("json", "tmp").forEach { suffix ->
                val file = File(directory, "$key.$suffix")
                check(!file.exists() || file.delete()) { "Could not remove listening evidence" }
            }
            _feedbackChanges.update { it + 1 }
        }
    }

    private fun read(key: String): ListeningEvidenceSnapshot {
        cache[key]?.let { return it }
        val file = File(directory, "$key.json")
        val stored = if (file.isFile) {
            // Never overwrite unreadable evidence with an empty profile.
            check(file.length() <= 8_000_000L) { "Listening evidence is too large" }
            json.decodeFromString(ListeningEvidenceSnapshot.serializer(), file.readText()).also {
                check(it.schemaVersion == 1 && it.scopeKey == key) { "Unsupported listening evidence" }
            }
        } else ListeningEvidenceSnapshot(scopeKey = key)
        return stored.also { cache[key] = it }
    }

    private fun write(key: String, value: ListeningEvidenceSnapshot) {
        check(directory.isDirectory || directory.mkdirs()) { "Listening storage unavailable" }
        val target = File(directory, "$key.json")
        val temporary = File(directory, "$key.tmp")
        try {
            temporary.outputStream().use {
                it.write(json.encodeToString(ListeningEvidenceSnapshot.serializer(), value).toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            cache[key] = value
        } finally { temporary.delete() }
    }

    private fun key(scope: String) = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_SESSIONS = 2_000
        const val RETENTION_MS = 90 * 24 * 60 * 60 * 1000L
    }
}
