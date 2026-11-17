package com.music.vikify.data.personalization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Portable values only. Null is a durable deletion, never an absent download. */
@Serializable data class CuratedCloudValue(
    val time: Long, val writer: String, val value: String?, val epoch: Long = 0,
    val expiresAt: Long = 0,
)

/** Session IDs are unique and their evidence is useful for 90 days, unlike durable user choices. */
internal object CuratedSessionRetention {
    const val WINDOW_MS = ListeningEvidenceStore.RETENTION_MS + 7 * 24 * 60 * 60 * 1000L
    private const val PREFIX = "evidence/session:"
    fun isSession(key: String) = key.startsWith(PREFIX)
    fun updatedAt(payload: String): Long {
        val fields = Json.parseToJsonElement(payload).jsonObject
        return fields.getValue("updatedAt").jsonPrimitive.long.also {
            require(it > 0) { "Invalid cloud session timestamp" }
        }
    }

    fun expiry(key: String, value: String?, previousValue: String? = null,
        previousExpiry: Long = 0, fallbackTime: Long): Long {
        if (!isSession(key)) return 0
        val payload = value ?: previousValue
        if (payload != null) {
            return Math.addExact(updatedAt(payload), WINDOW_MS)
        }
        // Deletion carries the original event's lifetime; deleting it never restarts retention.
        return previousExpiry.takeIf { it > 0 } ?: Math.addExact(fallbackTime.coerceAtLeast(1), WINDOW_MS)
    }
}

@Serializable data class CuratedCloudManifest(
    val schemaVersion: Int = 1, val entries: Map<String, CuratedCloudValue> = emptyMap(),
) {
    fun validated(): CuratedCloudManifest = apply {
        require(schemaVersion == 1 && entries.size <= 30_000) { "Unsupported curated backup" }
        entries.forEach { (key, item) ->
            require(key == "reset" || PREFIXES.any { key.startsWith(it) }) { "Unknown curated backup entry" }
            require(key.length <= 256 && item.time >= 0 && item.epoch >= 0 && item.writer.length in 1..80)
            require(item.expiresAt >= 0 && (CuratedSessionRetention.isSession(key) || item.expiresAt == 0L))
            require((item.value?.length ?: 0) <= 4_000_000) { "Curated backup entry is too large" }
            if (CuratedSessionRetention.isSession(key) && item.value != null) {
                val expiry = CuratedSessionRetention.expiry(key, item.value, fallbackTime = item.time)
                require(item.expiresAt == 0L || item.expiresAt == expiry) { "Invalid cloud session expiry" }
            }
            if (key == "reset") {
                require(item.value != null && item.epoch > 0 &&
                    Json.parseToJsonElement(item.value).jsonObject.getValue("cutoff").jsonPrimitive.long == item.epoch) {
                    "Invalid curated reset generation"
                }
            }
        }
    }

    fun merge(other: CuratedCloudManifest): CuratedCloudManifest {
        validated(); other.validated()
        val merged = entries.toMutableMap()
        other.entries.forEach { (key, incoming) ->
            val old = merged[key]
            // Clearing learning is irreversible. A slow/offline device cannot roll it back.
            val compare = if (!key.startsWith("copies/") && old != null && old.epoch != incoming.epoch)
                incoming.epoch.compareTo(old.epoch) else if (key == "reset" && old != null && old.value != incoming.value)
                incoming.value.orEmpty().compareTo(old.value.orEmpty()) else old?.let {
                compareValuesBy(incoming, it, CuratedCloudValue::time, CuratedCloudValue::writer,
                    { value -> value.value.orEmpty() })
            } ?: 1
            if (compare > 0) merged[key] = incoming
        }
        return CuratedCloudManifest(entries = merged.toSortedMap()).validated()
    }

    /** Initial local caches are seeds, not fresh edits. Existing cloud values and deletions win. */
    fun mergeReplica(pending: CuratedCloudManifest, now: Long): CuratedCloudManifest {
        val remote = retainingSessions(now)
        val activePending = pending.retainingSessions(now)
        var time = maxOf(now.coerceAtLeast(1), (remote.entries.values.maxOfOrNull { it.time } ?: 0) + 1)
        val seeded = activePending.entries.mapNotNull { (key, item) ->
            when {
                item.time != 0L -> key to item
                key != "reset" && remote.entries[key]?.let { old ->
                    key.startsWith("copies/") || old.epoch >= item.epoch
                } == true -> null
                else -> key to item.copy(time = time++)
            }
        }.toMap()
        return remote.merge(CuratedCloudManifest(entries = seeded)).limitActiveSessions()
    }

    /** Old clients without expiry metadata are bounded by the same event timestamp. */
    internal fun retainingSessions(now: Long): CuratedCloudManifest {
        validated()
        return copy(entries = entries.mapNotNull { (key, item) ->
            if (!CuratedSessionRetention.isSession(key)) key to item else {
                val expiry = CuratedSessionRetention.expiry(key, item.value, previousExpiry = item.expiresAt,
                    fallbackTime = item.time)
                if (expiry <= now) null else key to item.copy(expiresAt = expiry)
            }
        }.toMap())
    }

    /** A union of several phones still fits the same newest-2,000 evidence window as each phone. */
    private fun limitActiveSessions(): CuratedCloudManifest {
        val sessions = entries.filter { (key, item) -> CuratedSessionRetention.isSession(key) && item.value != null }
        if (sessions.size <= ListeningEvidenceStore.MAX_SESSIONS) return this
        val retained = sessions.entries.sortedWith(compareByDescending<Map.Entry<String, CuratedCloudValue>> {
            CuratedSessionRetention.updatedAt(checkNotNull(it.value.value))
        }.thenBy { it.key }).take(ListeningEvidenceStore.MAX_SESSIONS).mapTo(HashSet()) { it.key }
        // Omitted events are older than every retained event. An offline candidate cannot displace
        // this window; by the time the retained window expires, omitted events have also expired.
        return copy(entries = entries.filter { (key, item) ->
            !CuratedSessionRetention.isSession(key) || item.value == null || key in retained
        })
    }

    val epoch: Long get() = entries["reset"]?.epoch ?: 0

    /** Old learning generations stay tombstoned; saved copies intentionally survive a taste reset. */
    fun values(): Map<String, String> = entries.mapNotNull { (key, item) ->
        item.value?.takeIf { key == "reset" || key.startsWith("copies/") || item.epoch == epoch }
            ?.let { key to it }
    }.toMap()

    companion object { private val PREFIXES = listOf("home/", "evidence/", "copies/", "mixes/") }
}

/** Persisted before network I/O. The restore intent also makes a crash during import idempotent. */
@Serializable data class CuratedCloudReplica(
    val manifest: CuratedCloudManifest = CuratedCloudManifest(),
    val observed: Map<String, String> = emptyMap(),
    val observedEpoch: Long = 0,
    val restoreTarget: Map<String, String>? = null,
) {
    fun observe(current: Map<String, String>, epoch: Long, writer: String, now: Long): CuratedCloudReplica {
        val next = manifest.retainingSessions(now).entries.toMutableMap()
        var time = maxOf(now.coerceAtLeast(0), (next.values.maxOfOrNull { it.time } ?: 0) + 1)
        (observed.keys + current.keys + restoreTarget.orEmpty().keys).sorted().forEach { key ->
            val value = current[key]
            // If an import reached disk before a crash, do not publish it as a fresh local edit.
            val restored = restoreTarget != null && value == restoreTarget[key]
            val changedGeneration = epoch != observedEpoch && key != "reset" && !key.startsWith("copies/")
            val changed = if (changedGeneration) value != null else observed[key] != value
            if (!restored && changed) {
                val expiresAt = CuratedSessionRetention.expiry(key, value,
                    previousValue = observed[key] ?: next[key]?.value, previousExpiry = next[key]?.expiresAt ?: 0,
                    fallbackTime = now)
                if (expiresAt == 0L || expiresAt > now) next[key] = CuratedCloudValue(time++, writer, value,
                    if (key.startsWith("copies/")) 0 else epoch, expiresAt)
                else next.remove(key)
            }
        }
        return CuratedCloudReplica(CuratedCloudManifest(entries = next).validated(), current.toSortedMap(), epoch)
    }

    companion object {
        fun bootstrap(current: Map<String, String>, epoch: Long, writer: String,
            now: Long = System.currentTimeMillis()) = CuratedCloudReplica(
            manifest = CuratedCloudManifest(entries = current.mapValues { (key, value) ->
                CuratedCloudValue(0, writer, value, if (key.startsWith("copies/")) 0 else epoch,
                    CuratedSessionRetention.expiry(key, value, fallbackTime = now))
            }).retainingSessions(now).validated(), observed = current.toSortedMap(), observedEpoch = epoch,
        )
    }
}
