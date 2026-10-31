package com.music.vikify.data.covers

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoverDiskCacheTest {
    private val spec = CoverSpec("mix-identity", "Telugu Melodies")
    private fun cache(directory: File, entries: Int = 96, clock: () -> Long = System::currentTimeMillis) =
        CoverDiskCache(directory, { file, side -> file.readText().startsWith("$side:") }, maxEntries = entries, clock = clock)
    private fun bytes(spec: CoverSpec, side: Int) = "$side:${spec.title}".toByteArray()

    @Test fun completedCoverIsReusedAfterRestartWithoutRendering() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        try {
            val first = cache(dir).get(spec, 480, "test", ::bytes)
            val restored = cache(dir).get(spec, 480, "test") { _, _ -> error("Must reuse completed image") }
            assertEquals(first.file, restored.file)
            assertTrue(restored.cacheHit)
            assertFalse(restored.keptPrevious)
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun renamePublishesANewCoverWithoutMutatingTheOldFile() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        try {
            val cache = cache(dir)
            val old = cache.get(spec, 480, "test", ::bytes)
            val renamed = cache.get(spec.copy(title = "Renamed"), 480, "test", ::bytes)
            assertNotEquals(old.file, renamed.file)
            assertEquals("480:Telugu Melodies", old.file.readText())
            assertEquals("480:Renamed", renamed.file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedRenderingRetainsThePreviousCoverEvenAfterRestart() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        try {
            val old = cache(dir).get(spec, 720, "test", ::bytes)
            val retained = cache(dir).get(spec.copy(typographyVersion = spec.typographyVersion + 1), 720, "test") { _, _ -> error("Renderer unavailable") }
            assertEquals(old.file, retained.file)
            assertTrue(retained.keptPrevious)
            assertEquals("720:Telugu Melodies", old.file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidOutputDoesNotPublishAnImageOrTemporaryFile() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        try {
            assertFailsWith<IllegalStateException> { cache(dir).get(spec, 480, "test") { _, _ -> "broken".toByteArray() } }
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptFinishedFileIsReplacedByAValidatedRender() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        try {
            val cache = cache(dir)
            val first = cache.get(spec, 192, "test", ::bytes)
            first.file.writeText("corrupt")
            val repaired = cache.get(spec, 192, "test", ::bytes)
            assertFalse(repaired.cacheHit)
            assertEquals("192:Telugu Melodies", repaired.file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun simultaneousRequestsShareOneRender() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        try {
            val cache = cache(dir)
            val first = async { cache.get(spec, 480, "test") { s, side -> calls.incrementAndGet(); entered.complete(Unit); release.await(); bytes(s, side) } }
            entered.await()
            val second = async { cache.get(spec, 480, "test") { _, _ -> error("Duplicate render") } }
            release.complete(Unit)
            assertEquals(first.await().file, second.await().file)
            assertEquals(1, calls.get())
        } finally { release.complete(Unit); dir.deleteRecursively() }
    }

    @Test fun cancellationDoesNotPublishAPartialCover() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        val entered = CompletableDeferred<Unit>()
        try {
            val pending = async { cache(dir).get(spec, 480, "test") { _, _ -> entered.complete(Unit); CompletableDeferred<ByteArray>().await() } }
            entered.await(); pending.cancelAndJoin()
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun pruningAndClearStayWithinTheOwnedCache() = runBlocking {
        val dir = Files.createTempDirectory("cover-cache-test").toFile()
        var now = 100L
        try {
            val unrelated = dir.resolve("keep.txt").also { it.writeText("unrelated") }
            val cache = cache(dir, entries = 2, clock = { now++ })
            repeat(5) { cache.get(spec.copy(collectionId = "mix-$it"), 480, "test", ::bytes) }
            assertEquals(2, dir.listFiles()!!.count { it.extension == "png" })
            assertTrue(unrelated.isFile)
            cache.clear()
            assertEquals(listOf("keep.txt"), dir.listFiles()!!.map { it.name })
        } finally { dir.deleteRecursively() }
    }
}
