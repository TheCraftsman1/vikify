package com.music.vikify.data.personalization

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

@Serializable internal data class ResetDay(val day: Long, val plays: Int, val heardMs: Long)
@Serializable internal data class ResetTrack(val key: String, val days: List<ResetDay>)
@Serializable internal data class ResetBoundary(
    val schemaVersion: Int = 1, val key: String, val generation: Long = 0, val cutoff: Long = 0,
    val epochDay: Long = 0, val baseline: List<ResetTrack> = emptyList(), val cleanupScopes: List<String> = emptyList(),
)

/** Scope hashes, device generations and cleanup queues never leave this device. */
@Serializable private data class CloudResetMarker(
    val schemaVersion: Int = 1,
    val cutoff: Long,
    val epochDay: Long,
    val baseline: List<ResetTrack>,
)

/** A durable generation boundary prevents preserved Replay totals from rebuilding cleared preferences. */
class PersonalizationResetStore(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val cache = ConcurrentHashMap<String, ResetBoundary>()
    private val locks = ConcurrentHashMap<String, Any>()

    fun scope(base: String): String = synchronized(locks.getOrPut(base) { Any() }) {
        val generation = read(base).generation
        if (generation == 0L) base else "$base:reset:$generation"
    }

    fun pending(base: String): List<String> = synchronized(locks.getOrPut(base) { Any() }) { read(base).cleanupScopes }

    /** Portable, deterministic reset identity; null means this profile has never been reset. */
    fun cloudMarker(base: String): String? = synchronized(locks.getOrPut(base) { Any() }) {
        marker(read(base))
    }

    /**
     * Adopts a newer cloud reset without letting an old device resurrect discarded learning.
     * The caller must finish pending cleanup before importing evidence into the new [scope].
     * A false result means the caller must reread local state and merge again.
     */
    suspend fun adoptCloudMarker(base: String, expected: String?, desired: String?): Boolean = withContext(Dispatchers.IO) {
        synchronized(locks.getOrPut(base) { Any() }) {
            val old = read(base)
            val current = marker(old)
            if (current != expected) return@synchronized false
            if (current == desired) return@synchronized true
            // Reset is monotonic. Missing or stale cloud state cannot undo a local reset.
            if (desired == null) return@synchronized false
            require(desired.toByteArray(Charsets.UTF_8).size <= 8_000_000) { "Cloud reset marker is too large" }
            val incoming = json.decodeFromString(CloudResetMarker.serializer(), desired)
            validate(incoming)
            require(encode(incoming) == desired) { "Cloud reset marker is not canonical" }
            // Independent devices can reset in the same millisecond. Canonical max is the
            // deterministic tie-break used by the cloud manifest, so both replicas converge.
            if (incoming.cutoff < old.cutoff ||
                (incoming.cutoff == old.cutoff && desired <= current.orEmpty())) return@synchronized false
            val oldScope = if (old.generation == 0L) base else "$base:reset:${old.generation}"
            write(base, ResetBoundary(key = hash(base), generation = Math.addExact(old.generation, 1),
                cutoff = incoming.cutoff, epochDay = incoming.epochDay, baseline = incoming.baseline,
                cleanupScopes = (old.cleanupScopes + oldScope).distinct()))
            true
        }
    }

    suspend fun reset(base: String, history: List<TrackListeningEvidence>, cutoff: Long, zone: ZoneId): String = withContext(Dispatchers.IO) {
        synchronized(locks.getOrPut(base) { Any() }) {
            val old = read(base)
            val oldScope = if (old.generation == 0L) base else "$base:reset:${old.generation}"
            require(cutoff > 0) { "Invalid reset cutoff" }
            // Millisecond collisions and a clock moving backwards must not undo a reset.
            val effectiveCutoff = maxOf(cutoff, Math.addExact(old.cutoff, 1))
            val day = Instant.ofEpochMilli(effectiveCutoff).atZone(zone).toLocalDate().toEpochDay()
            val baseline = history.mapNotNull { track ->
                val days = track.days.filter { it.epochDay >= day }.map { ResetDay(it.epochDay, it.plays.coerceAtLeast(0), it.heardMs.coerceAtLeast(0)) }
                if (days.isEmpty()) null else ResetTrack(hash(track.song.videoId), days)
            }
            val next = ResetBoundary(key = hash(base), generation = Math.addExact(old.generation, 1), cutoff = effectiveCutoff,
                epochDay = day, baseline = baseline, cleanupScopes = (old.cleanupScopes + oldScope).distinct())
            write(base, next)
            "$base:reset:${next.generation}"
        }
    }

    suspend fun acknowledgeCleanup(base: String, scope: String) = withContext(Dispatchers.IO) {
        synchronized(locks.getOrPut(base) { Any() }) {
            val old = read(base)
            if (scope in old.cleanupScopes) write(base, old.copy(cleanupScopes = old.cleanupScopes - scope))
        }
    }

    fun learningHistory(base: String, history: List<TrackListeningEvidence>, includeResetDay: Boolean = true): List<TrackListeningEvidence> {
        val boundary = synchronized(locks.getOrPut(base) { Any() }) { read(base) }
        if (boundary.generation == 0L) return history
        val baseline = boundary.baseline.associate { it.key to it.days.associateBy { day -> day.day } }
        return history.mapNotNull { track ->
            if (track.lastPlayedAt <= boundary.cutoff) return@mapNotNull null
            val previous = baseline[hash(track.song.videoId)].orEmpty()
            // Cross-device day aggregates cannot separate late-arriving pre-reset plays from
            // new plays on the cutoff day. Cloud callers conservatively start the following day.
            val days = track.days.filter { it.epochDay > boundary.epochDay ||
                (includeResetDay && it.epochDay == boundary.epochDay) }.mapNotNull { day ->
                val old = previous[day.epochDay]
                val plays = (day.plays - (old?.plays ?: 0)).coerceAtLeast(0)
                val ms = (day.heardMs - (old?.heardMs ?: 0)).coerceAtLeast(0)
                if (plays == 0) null else day.copy(plays = plays, heardMs = ms)
            }
            if (days.isEmpty()) null else track.copy(plays = days.sumOf { it.plays }, heardMs = days.sumOf { it.heardMs }, days = days)
        }
    }

    private fun read(base: String): ResetBoundary {
        cache[base]?.let { return it }
        val key = hash(base)
        val file = File(directory, "$key.json")
        val value = if (file.isFile) {
            check(file.length() <= 8_000_000) { "Reset boundary is too large" }
            json.decodeFromString(ResetBoundary.serializer(), file.readText()).also {
                check(it.schemaVersion == 1 && it.key == key && it.generation >= 0) { "Unsupported reset boundary" }
            }
        } else ResetBoundary(key = key)
        return value.also { cache[base] = it }
    }

    private fun marker(boundary: ResetBoundary): String? = if (boundary.generation == 0L) null else
        encode(CloudResetMarker(cutoff = boundary.cutoff, epochDay = boundary.epochDay, baseline = boundary.baseline))

    private fun encode(marker: CloudResetMarker): String = json.encodeToString(CloudResetMarker.serializer(),
        marker.copy(baseline = marker.baseline.sortedBy { it.key }.map { it.copy(days = it.days.sortedBy { day -> day.day }) }))

    private fun validate(marker: CloudResetMarker) {
        require(marker.schemaVersion == 1 && marker.cutoff > 0) { "Unsupported cloud reset marker" }
        val utcDay = Instant.ofEpochMilli(marker.cutoff).atZone(ZoneId.of("UTC")).toLocalDate().toEpochDay()
        require(marker.epochDay in (utcDay - 1)..(utcDay + 1)) { "Invalid cloud reset day" }
        require(marker.baseline.size <= 50_000 && marker.baseline.map { it.key }.distinct().size == marker.baseline.size) {
            "Invalid cloud reset baseline"
        }
        marker.baseline.forEach { track ->
            require(track.key.matches(Regex("[a-f0-9]{64}")) && track.days.size <= 1_100 &&
                track.days.map { it.day }.distinct().size == track.days.size) { "Invalid cloud reset track" }
            require(track.days.all { it.day >= marker.epochDay && it.plays >= 0 && it.heardMs >= 0 }) {
                "Invalid cloud reset counters"
            }
        }
    }

    private fun write(base: String, boundary: ResetBoundary) {
        check(directory.isDirectory || directory.mkdirs()) { "Reset storage unavailable" }
        val file = File(directory, "${boundary.key}.json")
        val temporary = File(directory, "${boundary.key}.tmp")
        try {
            temporary.outputStream().use { it.write(json.encodeToString(ResetBoundary.serializer(), boundary).toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            cache[base] = boundary
        } finally { temporary.delete() }
    }

    companion object {
        private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}

data class PersonalizationResetResult(val scope: String, val cleanupComplete: Boolean)

class PersonalizationResetCoordinator(private val boundaries: PersonalizationResetStore,
    private val evidence: ListeningEvidenceStore, private val mixes: GeneratedMixRepository, private val home: HomeCollectionStore) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun reset(base: String, history: suspend () -> List<TrackListeningEvidence>, clock: () -> Long = System::currentTimeMillis,
        zone: ZoneId = ZoneId.systemDefault()): PersonalizationResetResult = locks.getOrPut(base) { Mutex() }.withLock {
        val cutoff = clock()
        val scope = boundaries.reset(base, history(), cutoff, zone)
        PersonalizationResetResult(scope, clean(base))
    }

    suspend fun resumeCleanup(base: String): Boolean = locks.getOrPut(base) { Mutex() }.withLock { clean(base) }

    private suspend fun clean(base: String): Boolean {
        var complete = true
        for (scope in boundaries.pending(base)) {
            try {
                evidence.clearScope(scope)
                home.clearScope(scope)
                mixes.clearScope(scope)
                boundaries.acknowledgeCleanup(base, scope)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { complete = false }
        }
        return complete
    }
}
