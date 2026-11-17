package com.music.vikify.data.personalization

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudPersonalizationStoreTest {
    private val f = CoreMixFixture()
    private val ids = LocalMixKind.entries.map { it.id }.toSet()

    @Test fun homeChoicesRestoreWithoutScopeOrDeviceUsageAndLocalEditsWinRaces() = runBlocking {
        val dir = Files.createTempDirectory("cloud-home").toFile()
        try {
            val store = HomeCollectionStore(dir, ids) { f.now }
            assertTrue(store.exportCloud("blank").isEmpty())
            store.pin("source", LocalMixKind.ON_REPEAT.id, true)
            store.hide("source", LocalMixKind.DAILY_MIX.id, true)
            store.follow("source", LocalMixKind.ARTIST_ORBIT.id, true)
            store.record("source", "visit-private", LocalMixKind.ON_REPEAT.id, true)
            val exported = store.exportCloud("source")
            assertFalse(exported.values.joinToString().contains("visit-private"))
            assertTrue(store.applyCloud("restored", emptyMap(), exported))
            assertEquals(exported, store.exportCloud("restored"))
            assertTrue(store.snapshot("restored").usage.isEmpty())
            val captured = store.exportCloud("restored")
            store.pin("restored", LocalMixKind.COMFORT_ZONE.id, true)
            assertFalse(store.applyCloud("restored", captured, emptyMap()))
            assertTrue(LocalMixKind.COMFORT_ZONE.id in store.snapshot("restored").preferences.pinned)
            assertTrue(store.exportCloud("unrelated").isEmpty())
            store.clearScope("restored")
            assertFalse(store.applyCloud("restored", emptyMap(), exported))
        } finally { dir.deleteRecursively() }
    }

    @Test fun evidenceRoundTripsAsPortableItemsWithDeletionAndCheckpointProtection() = runBlocking {
        val dir = Files.createTempDirectory("cloud-evidence").toFile()
        try {
            val store = ListeningEvidenceStore(dir) { f.now }
            val trackId = f.song(1).videoId
            val session = ListeningSessionRecord("session-1", trackId, "private-queue-entry", null,
                f.now - 30_000, f.now, 1, 30_000, 30_000, 120_000, true, SessionEnd.MANUAL_SKIP)
            store.record("a", session)
            store.setPreference("a", trackId, TrackPreference.MORE)
            val exported = store.exportCloud("a")
            assertFalse(exported.values.joinToString().contains("private-queue-entry"))
            assertTrue(store.applyCloud("b", emptyMap(), exported))
            assertEquals(exported, store.exportCloud("b"))
            assertEquals(TrackPreference.MORE, store.snapshot("b").preferences[trackId])
            val captured = store.exportCloud("b")
            store.setPreference("b", trackId, TrackPreference.HIDE)
            assertFalse(store.applyCloud("b", captured, emptyMap()))
            val current = store.exportCloud("b")
            val next = current.filterKeys { !it.startsWith("preference:") }
            assertTrue(store.applyCloud("b", current, next))
            assertEquals(next, store.exportCloud("b"))
            assertTrue(store.snapshot("b").preferences.isEmpty())
            val beforeCheckpoint = store.exportCloud("a")
            store.record("a", session.copy(revision = 2, heardMs = 35_000, uniqueHeardMs = 35_000))
            assertFalse(store.applyCloud("a", beforeCheckpoint, emptyMap()))
            assertEquals(2L, store.snapshot("a").sessions.single().revision)
            store.clearScope("b")
            assertFalse(store.applyCloud("b", emptyMap(), exported))
        } finally { dir.deleteRecursively() }
    }

    @Test fun copyRoundTripsPreserveOrderAndRejectConcurrentRenameOrDelete() = runBlocking {
        val dir = Files.createTempDirectory("cloud-copies").toFile()
        try {
            val store = MixCopyStore(dir) { f.now }
            val saved = store.save("a", "Keep this mix", LocalMixKind.ON_REPEAT, 12, listOf(f.song(3), f.song(1)))
            val exported = store.exportCloud("a")
            assertTrue(store.applyCloud("b", emptyMap(), exported))
            assertEquals(exported, store.exportCloud("b"))
            assertEquals(saved, MixCopyStore(dir).get("b", saved.browseId))
            store.rename("b", saved.browseId, "Renamed offline")
            assertFalse(store.applyCloud("b", exported, emptyMap()))
            val captured = store.exportCloud("b")
            store.delete("b", saved.browseId)
            assertFalse(store.applyCloud("b", captured, exported))
            assertTrue(store.list("b").isEmpty())
            assertTrue(store.applyCloud("a", exported, emptyMap()))
            assertTrue(MixCopyStore(dir).list("a").isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun localFilesStayOnTheDeviceAndMalformedImportsDoNotReplaceTheLibrary() = runBlocking {
        val dir = Files.createTempDirectory("cloud-copy-files").toFile()
        try {
            val store = MixCopyStore(dir) { f.now }
            val local = store.save("a", "My recording", LocalMixKind.ON_REPEAT, 1,
                listOf(f.song(1).copy(videoId = "recording", localUri = "content://private/recording", localPath = "/private/recording")))
            val catalog = store.save("a", "Downloaded catalog", LocalMixKind.ON_REPEAT, 1,
                listOf(f.song(2).copy(localUri = "content://private/download", localPath = "/private/download",
                    thumbnailUrl = "file:///private/cover.jpg")))
            val exported = store.exportCloud("a")
            assertEquals(setOf(catalog.id), exported.keys)
            assertFalse(exported.values.joinToString().contains("private"))
            assertFailsWith<Exception> { store.applyCloud("a", exported, mapOf("broken" to "{}")) }
            assertEquals(2, store.list("a").size)
            assertTrue(store.applyCloud("a", exported, exported))
            assertEquals("content://private/download", store.get("a", catalog.browseId)!!.songs.single().localUri)
            assertTrue(store.applyCloud("a", exported, emptyMap()))
            assertEquals(local.id, store.list("a").single().id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun generatedMixesRestoreToANewScopeAndCannotOverwriteAnOfflineRefresh() = runBlocking {
        val dir = Files.createTempDirectory("cloud-mixes").toFile()
        try {
            var history = f.history
            val repository = GeneratedMixRepository(dir, { history }, { emptyList() },
                clock = { f.now }, zone = { f.zone })
            val saved = assertNotNull(repository.load("a", LocalMixKind.ON_REPEAT).mix)
            val exported = repository.exportCloud("a")
            assertTrue(repository.applyCloud("b", emptyMap(), exported))
            assertEquals(exported, repository.exportCloud("b"))
            assertEquals(saved.songs, repository.cached("b", LocalMixKind.ON_REPEAT)!!.songs)
            history = listOf(f.track(28, plays = 30))
            repository.load("b", LocalMixKind.ON_REPEAT, force = true)
            assertFalse(repository.applyCloud("b", exported, emptyMap()))
            assertEquals(f.song(28), repository.cached("b", LocalMixKind.ON_REPEAT)!!.songs.single())
            repository.clearScope("a")
            assertFalse(repository.applyCloud("a", emptyMap(), exported))
        } finally { dir.deleteRecursively() }
    }

    @Test fun restoredEvidenceInvalidatesFreshnessWithoutLosingTheOfflineFallback() = runBlocking {
        val dir = Files.createTempDirectory("cloud-evidence-refresh").toFile()
        try {
            var history = listOf(f.track(1))
            var failEvidence = false
            var reads = 0
            val repository = GeneratedMixRepository(dir, {
                reads++
                check(!failEvidence) { "Temporarily unavailable" }
                history
            }, { emptyList() }, clock = { f.now }, zone = { f.zone })
            val first = assertNotNull(repository.load("a", LocalMixKind.ON_REPEAT).mix)
            val initialReads = reads
            repository.load("a", LocalMixKind.ON_REPEAT)
            assertEquals(initialReads, reads)
            repository.markEvidenceChanged("a")
            failEvidence = true
            assertEquals(first, repository.load("a", LocalMixKind.ON_REPEAT).mix)
            assertEquals(first, repository.cached("a", LocalMixKind.ON_REPEAT))
            failEvidence = false
            history = listOf(f.track(2))
            val refreshed = assertNotNull(repository.load("a", LocalMixKind.ON_REPEAT).mix)
            assertEquals(f.song(2), refreshed.songs.single())
            val afterRefresh = reads
            repository.load("a", LocalMixKind.ON_REPEAT)
            assertEquals(afterRefresh, reads)
        } finally { dir.deleteRecursively() }
    }
}
