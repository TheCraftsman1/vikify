package com.music.vikify.data.covers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class CachedCover(val file: File, val cacheHit: Boolean, val keptPrevious: Boolean = false)

/** Serializes expensive work and atomically publishes bounded finished images, never partial output. */
class CoverDiskCache(private val directory: File, private val valid: (File, Int) -> Boolean,
    private val maxBytes: Long = 24 * 1024 * 1024, private val maxEntries: Int = 96,
    private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()

    suspend fun get(spec: CoverSpec, requestedSize: Int, backend: String,
        render: suspend (CoverSpec, Int) -> ByteArray): CachedCover = withContext(Dispatchers.IO) {
        lock.withLock {
            val size = CoverCatalog.sizeBucket(requestedSize)
            check(directory.isDirectory || directory.mkdirs()) { "Cover cache unavailable" }
            val target = File(directory, "${spec.cacheKey(size, backend)}.png")
            val previousIndex = File(directory, "${hash("${spec.collectionId}:$size")}.previous")
            if (target.isFile && valid(target, size)) {
                target.setLastModified(clock())
                remember(previousIndex, target)
                return@withLock CachedCover(target, true)
            }
            val temporary = File(directory, "${target.name}.tmp")
            try {
                val bytes = render(spec, size)
                currentCoroutineContext().ensureActive()
                check(bytes.size.toLong() <= maxBytes) { "Cover output is too large" }
                temporary.outputStream().use { it.write(bytes); it.fd.sync() }
                check(valid(temporary, size)) { "Cover renderer returned an invalid image" }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                target.setLastModified(clock())
                remember(previousIndex, target)
                prune(target)
                CachedCover(target, false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                val name = if (previousIndex.isFile) previousIndex.readText().takeIf { it.matches(Regex("[0-9a-f]{64}\\.png")) } else null
                val previous = name?.let { File(directory, it) }?.takeIf { it.isFile && valid(it, size) }
                if (previous != null) CachedCover(previous, true, keptPrevious = true) else throw failure
            } finally { temporary.delete() }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { lock.withLock {
        directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[0-9a-f]{64}\\.(png(?:\\.tmp)?|previous(?:\\.tmp)?)")) }
            .forEach { check(it.delete() || !it.exists()) { "Could not remove a cached cover" } }
    } }

    private fun remember(index: File, image: File) {
        val temporary = File(directory, "${index.name}.tmp")
        try {
            temporary.writeText(image.name)
            Files.move(temporary.toPath(), index.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (ignored: Exception) {
            // The completed image is still useful even if the previous-version pointer cannot be updated.
        } finally { temporary.delete() }
    }

    private fun prune(protected: File) {
        val images = directory.listFiles().orEmpty().filter { it.isFile && it.name.matches(Regex("[0-9a-f]{64}\\.png")) }.sortedBy { it.lastModified() }
        var bytes = images.sumOf { it.length() }
        var count = images.size
        for (file in images) {
            if (bytes <= maxBytes && count <= maxEntries) break
            if (file != protected) { val length = file.length(); if (file.delete()) { bytes -= length; count-- } }
        }
        directory.listFiles().orEmpty().filter { it.isFile && it.extension == "previous" }.forEach { index ->
            val image = index.readText().takeIf { it.matches(Regex("[0-9a-f]{64}\\.png")) }
            if (image == null || !File(directory, image).isFile) index.delete()
        }
    }
}
