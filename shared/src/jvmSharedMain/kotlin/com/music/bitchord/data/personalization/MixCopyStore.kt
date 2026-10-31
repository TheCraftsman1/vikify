package com.music.bitchord.data.personalization

import com.music.bitchord.data.model.Song
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SavedMixCopy(val id: String, val title: String, val kind: LocalMixKind, val sourceVersion: Long,
    val createdAt: Long, val songs: List<Song>) {
    val browseId: String get() = MixCopyStore.PREFIX + id
    val coverUrl: String get() = com.music.bitchord.data.covers.CoverUri.encode(
        com.music.bitchord.data.covers.CoverCatalog.dynamic("saved-copy:$id", title))
}
data class MixCopyDraft(val baseScope: String, val kind: LocalMixKind, val sourceVersion: Long, val songs: List<Song>)
@Serializable private data class StoredCopy(val id: String, val title: String, val kind: LocalMixKind,
    val sourceVersion: Long, val createdAt: Long, val tracks: List<StoredMixTrack>) {
    fun copy() = SavedMixCopy(id, title, kind, sourceVersion, createdAt, tracks.map { it.song() })
}
@Serializable private data class CopyFile(val key: String, val schemaVersion: Int = 1, val copies: List<StoredCopy> = emptyList())

/** Account library ownership is independent of a recommendation generation and its reset lifecycle. */
class MixCopyStore(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cache = ConcurrentHashMap<String, CopyFile>()
    suspend fun list(baseScope: String): List<SavedMixCopy> = update(baseScope) { it }.copies.map { it.copy() }
    suspend fun get(baseScope: String, browseId: String): SavedMixCopy? = list(baseScope).firstOrNull { it.browseId == browseId }

    suspend fun save(baseScope: String, title: String, kind: LocalMixKind, sourceVersion: Long, songs: List<Song>): SavedMixCopy {
        val name = title.trim()
        require(name.isNotEmpty() && name.length <= 120 && sourceVersion > 0)
        require(songs.isNotEmpty() && songs.size <= LocalMixGenerator.MAX_TRACKS && songs.all(LocalMixGenerator::eligible))
        val record = StoredCopy(UUID.randomUUID().toString(), name, kind, sourceVersion, clock(), songs.map(StoredMixTrack::from))
        update(baseScope) { old -> check(old.copies.size < 100) { "Saved copy limit reached" }; old.copy(copies = listOf(record) + old.copies) }
        return record.copy()
    }

    suspend fun rename(baseScope: String, browseId: String, title: String) {
        require(title.trim().isNotEmpty() && title.trim().length <= 120)
        update(baseScope) { old ->
            check(old.copies.any { PREFIX + it.id == browseId }) { "Saved copy missing" }
            old.copy(copies = old.copies.map { if (PREFIX + it.id == browseId) it.copy(title = title.trim()) else it })
        }
    }

    suspend fun delete(baseScope: String, browseId: String) {
        update(baseScope) { old -> old.copy(copies = old.copies.filterNot { PREFIX + it.id == browseId }) }
    }

    suspend fun removeTrack(baseScope: String, browseId: String, trackId: String) {
        update(baseScope) { old ->
            check(old.copies.any { PREFIX + it.id == browseId }) { "Saved copy missing" }
            old.copy(copies = old.copies.map { if (PREFIX + it.id == browseId) it.copy(tracks = it.tracks.filterNot { track -> track.id == trackId }) else it })
        }
    }

    private suspend fun update(base: String, transform: (CopyFile) -> CopyFile): CopyFile = withContext(Dispatchers.IO) {
        val key = MessageDigest.getInstance("SHA-256").digest(base.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        locks.getOrPut(key) { Mutex() }.withLock {
            val old = cache[key] ?: File(directory, "$key.json").let { file ->
                if (!file.isFile) CopyFile(key) else {
                    check(file.length() <= 8_000_000) { "Saved copies are too large" }
                    json.decodeFromString(CopyFile.serializer(), file.readText()).also { check(it.schemaVersion == 1 && it.key == key) }
                }
            }.also { cache[key] = it }
            val next = transform(old)
            if (next != old) {
                check(directory.isDirectory || directory.mkdirs()) { "Copy storage unavailable" }
                val temporary = File(directory, "$key.tmp")
                try {
                    temporary.outputStream().use { it.write(json.encodeToString(CopyFile.serializer(), next).toByteArray(Charsets.UTF_8)); it.fd.sync() }
                    Files.move(temporary.toPath(), File(directory, "$key.json").toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    cache[key] = next
                } finally { temporary.delete() }
            }
            next
        }
    }
    companion object { const val PREFIX = "personal-copy:" }
}
