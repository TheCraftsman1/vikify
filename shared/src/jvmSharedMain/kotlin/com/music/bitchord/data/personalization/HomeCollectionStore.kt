package com.music.bitchord.data.personalization

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
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln

@Serializable enum class HomeMixAvailability { READY, LEARNING, NO_DISCOVERIES, UNAVAILABLE, EXCLUDED, NEEDS_REFRESH }

@Serializable data class HomeMixSummary(
    val id: String,
    val availability: HomeMixAvailability = HomeMixAvailability.LEARNING,
    val trackCount: Int = 0,
    val generatedAt: Long = 0,
    val confidence: PreferenceConfidence = PreferenceConfidence.NONE,
) {
    val ready: Boolean get() = availability == HomeMixAvailability.READY && trackCount > 0 && generatedAt > 0
}

@Serializable data class HomeCollectionPreferences(val pinned: List<String> = emptyList(), val hidden: Set<String> = emptySet(),
    val followed: Set<String> = emptySet())
@Serializable data class HomeUsageDay(val day: Long, val impressions: Int = 0, val opens: Int = 0)
@Serializable data class HomeVisitExposure(val id: String, val at: Long, val seen: Set<String> = emptySet(), val opened: Set<String> = emptySet())
@Serializable data class HomeCollectionSnapshot(
    val schemaVersion: Int = 1,
    val scopeKey: String,
    val preferences: HomeCollectionPreferences = HomeCollectionPreferences(),
    val summaries: List<HomeMixSummary> = emptyList(),
    val readyOrder: List<String> = emptyList(),
    val usage: Map<String, List<HomeUsageDay>> = emptyMap(),
    val visits: List<HomeVisitExposure> = emptyList(),
)

/** Ranks summaries only. After the initial ranking, at most one adjacent pair changes per preparation. */
object HomeFeedComposer {
    private val base = listOf(LocalMixKind.DAILY_MIX, LocalMixKind.ON_REPEAT, LocalMixKind.COMFORT_ZONE,
        LocalMixKind.BACK_IN_ROTATION, LocalMixKind.ARTIST_ORBIT, LocalMixKind.FRESH_FINDS, LocalMixKind.BEYOND_THE_USUAL)

    fun order(summaries: List<HomeMixSummary>, previous: List<String>, usage: Map<String, List<HomeUsageDay>>, now: Long): List<String> {
        val today = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        val desired = summaries.filter { it.ready }.distinctBy { it.id }.sortedWith(
            compareByDescending<HomeMixSummary> { summary ->
                val kind = LocalMixKind.fromId(summary.id)
                val history = usage[summary.id].orEmpty().filter { it.day in (today - 27)..today }
                val opens = history.sumOf { it.opens }
                val ignored = (history.sumOf { it.impressions } - opens).coerceAtLeast(0)
                val baseScore = 70.0 - base.indexOf(kind).takeIf { it >= 0 }.let { it ?: 7 } * 3
                baseScore + 6 * ln(1.0 + opens) - minOf(2.0, ignored * 0.15) +
                    summary.confidence.ordinal + if (kind != null && now >= summary.generatedAt &&
                    now - summary.generatedAt < kind.freshnessMs) 2 else 0
            }.thenBy { it.id },
        ).map { it.id }
        if (previous.isEmpty()) return desired
        val stable = (previous.filter { it in desired }.distinct() + desired.filterNot { it in previous }).toMutableList()
        val rank = desired.withIndex().associate { it.value to it.index }
        for (index in 1 until stable.size) {
            if (rank.getValue(stable[index]) < rank.getValue(stable[index - 1])) {
                val other = stable[index - 1]; stable[index - 1] = stable[index]; stable[index] = other
                break
            }
        }
        return stable
    }
}

/** Local controls and bounded quality summaries. No listening archive or player queue is edited. */
class HomeCollectionStore(private val directory: File, private val collectionIds: Set<String>,
    private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cache = ConcurrentHashMap<String, HomeCollectionSnapshot>()
    private val retired = ConcurrentHashMap.newKeySet<String>()

    suspend fun snapshot(scope: String): HomeCollectionSnapshot = update(scope) { it }

    suspend fun follow(scope: String, id: String, followed: Boolean): HomeCollectionSnapshot {
        require(LocalMixKind.fromId(id) != null && id in collectionIds)
        return update(scope) { old -> old.copy(preferences = old.preferences.copy(
            followed = if (followed) old.preferences.followed + id else old.preferences.followed - id)) }
    }

    suspend fun clearScope(scope: String) = withContext(Dispatchers.IO) {
        val key = key(scope)
        retired.add(key)
        locks.getOrPut(key) { Mutex() }.withLock {
            cache.remove(key)
            listOf("json", "tmp").forEach { suffix ->
                val file = File(directory, "$key.$suffix")
                check(!file.exists() || file.delete()) { "Could not remove Home personalization" }
            }
        }
    }

    suspend fun pin(scope: String, id: String, pinned: Boolean): HomeCollectionSnapshot {
        require(id in collectionIds)
        return update(scope) { old -> old.copy(preferences = old.preferences.let { prefs -> prefs.copy(
            pinned = if (pinned) (prefs.pinned + id).distinct() else prefs.pinned - id,
            hidden = if (pinned) prefs.hidden - id else prefs.hidden,
        ) }) }
    }

    suspend fun hide(scope: String, id: String, hidden: Boolean): HomeCollectionSnapshot {
        require(id in collectionIds)
        return update(scope) { old -> old.copy(preferences = old.preferences.let { prefs -> prefs.copy(
            pinned = if (hidden) prefs.pinned - id else prefs.pinned,
            hidden = if (hidden) prefs.hidden + id else prefs.hidden - id,
        ) }) }
    }

    suspend fun prepare(scope: String, summaries: List<HomeMixSummary>): HomeCollectionSnapshot = update(scope) { old ->
        val valid = summaries.filter { LocalMixKind.fromId(it.id) != null }.distinctBy { it.id }
        old.copy(summaries = valid, readyOrder = HomeFeedComposer.order(valid, old.readyOrder, old.usage, clock()))
    }

    /** A tap proves exposure too. Both impressions and selections are counted at most once per visit. */
    suspend fun record(scope: String, visitId: String, id: String, opened: Boolean = false): HomeCollectionSnapshot {
        require(id in collectionIds && visitId.isNotBlank() && visitId.length <= 100)
        return update(scope) { old ->
            val now = clock()
            val day = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            val visit = old.visits.firstOrNull { it.id == visitId } ?: HomeVisitExposure(visitId, now)
            if (id in visit.seen && (!opened || id in visit.opened)) return@update old
            val seenDelta = if (id in visit.seen) 0 else 1
            val openDelta = if (opened && id !in visit.opened) 1 else 0
            val days = old.usage[id].orEmpty().filter { it.day in (day - 89)..day }
            val bucket = days.firstOrNull { it.day == day } ?: HomeUsageDay(day)
            val nextVisit = visit.copy(seen = visit.seen + id, opened = if (opened) visit.opened + id else visit.opened)
            old.copy(
                usage = old.usage.mapValues { (_, values) -> values.filter { it.day in (day - 89)..day } }.filterValues { it.isNotEmpty() } +
                    (id to (days.filterNot { it.day == day } + bucket.copy(
                        impressions = (bucket.impressions + seenDelta).coerceAtMost(10_000),
                        opens = (bucket.opens + openDelta).coerceAtMost(10_000)))),
                visits = (old.visits.filterNot { it.id == visitId }.filter { it.at in (now - 7 * DAY_MS)..now } + nextVisit).takeLast(32),
            )
        }
    }

    private suspend fun update(scope: String, transform: (HomeCollectionSnapshot) -> HomeCollectionSnapshot): HomeCollectionSnapshot =
        withContext(Dispatchers.IO) {
            val key = key(scope)
            locks.getOrPut(key) { Mutex() }.withLock {
                check(key !in retired) { "Recommendation scope was reset" }
                val old = read(key)
                val next = transform(old)
                if (next != old) write(key, next)
                next
            }
        }

    private fun read(key: String): HomeCollectionSnapshot {
        cache[key]?.let { return it }
        val file = File(directory, "$key.json")
        val value = if (file.isFile) {
            check(file.length() <= 2_000_000) { "Home preferences are too large" }
            json.decodeFromString(HomeCollectionSnapshot.serializer(), file.readText()).also {
                check(it.schemaVersion == 1 && it.scopeKey == key) { "Unsupported Home preferences" }
            }
        } else HomeCollectionSnapshot(scopeKey = key)
        return value.also { cache[key] = it }
    }

    private fun write(key: String, value: HomeCollectionSnapshot) {
        check(directory.isDirectory || directory.mkdirs()) { "Home storage unavailable" }
        val temporary = File(directory, "$key.tmp")
        try {
            temporary.outputStream().use {
                it.write(json.encodeToString(HomeCollectionSnapshot.serializer(), value).toByteArray(Charsets.UTF_8)); it.fd.sync()
            }
            Files.move(temporary.toPath(), File(directory, "$key.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            cache[key] = value
        } finally { temporary.delete() }
    }

    private fun key(scope: String) = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    companion object { private const val DAY_MS = 86_400_000L }
}
