package com.music.vikify.data.personalization

import com.music.vikify.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreMixRepositoryTest {
    private val f = CoreMixFixture()
    private val kinds = listOf(LocalMixKind.COMFORT_ZONE, LocalMixKind.ARTIST_ORBIT,
        LocalMixKind.FRESH_FINDS, LocalMixKind.BEYOND_THE_USUAL)
    private fun temp() = Files.createTempDirectory("vikify-core-mix-test").toFile()
    private fun repository(dir: File, history: suspend () -> List<TrackListeningEvidence> = { f.history },
        related: suspend (Song) -> List<Song> = { f.candidates.map { it.song } },
        feedback: suspend (String) -> MixFeedback = { MixFeedback() },
        artistTracks: (suspend (Song) -> List<Song>)? = null) =
        GeneratedMixRepository(dir, history, related, clock = { f.now }, zone = { f.zone },
            feedback = feedback, artistTracks = artistTracks)

    @Test fun allFourSnapshotsPreserveMembershipAndConfidenceAcrossRestart() = runBlocking {
        val dir = temp()
        try {
            val initial = repository(dir)
            val saved = kinds.associateWith { kind -> assertNotNull(initial.load("listener", kind).mix) }
            val restored = repository(dir, related = { error("Fresh snapshots need no catalog request") })
            saved.forEach { (kind, snapshot) ->
                assertEquals(PreferenceConfidence.ESTABLISHED, snapshot.confidence)
                assertEquals(snapshot, restored.load("listener", kind).mix)
            }
            assertEquals(listOf("Alpha", "Beta"), saved.getValue(LocalMixKind.ARTIST_ORBIT).basedOnArtists)
            assertFalse(dir.listFiles().orEmpty().any { it.extension == "tmp" })
        } finally { dir.deleteRecursively() }
    }

    @Test fun orbitUsesBoundedArtistAndRadioRequests() = runBlocking {
        val dir = temp()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val radio = AtomicInteger()
        val artists = AtomicInteger()
        suspend fun fetch(counter: AtomicInteger): List<Song> {
            counter.incrementAndGet()
            val concurrent = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, concurrent) }
            try { delay(25); return f.candidates.map { it.song } }
            finally { active.decrementAndGet() }
        }
        try {
            assertNotNull(repository(dir, related = { fetch(radio) }, artistTracks = { fetch(artists) })
                .load("listener", LocalMixKind.ARTIST_ORBIT, force = true).mix)
            assertEquals(2, radio.get())
            assertEquals(2, artists.get())
            assertTrue(peak.get() <= 2)
        } finally { dir.deleteRecursively() }
    }

    @Test fun coldStartsMakeNoCatalogRequestsOrInventedPersonalCollections() = runBlocking {
        val dir = temp()
        try {
            val repo = repository(dir, history = { emptyList() }, related = { error("No personal seed") })
            kinds.forEach { kind ->
                val result = repo.load("new listener", kind)
                assertNull(result.mix)
                assertEquals(MixFailure.INSUFFICIENT_EVIDENCE, result.failure)
            }
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun emptyCatalogAndUnavailableCatalogHaveDifferentDiscoveryStates() = runBlocking {
        val dir = temp()
        try {
            listOf(LocalMixKind.FRESH_FINDS, LocalMixKind.BEYOND_THE_USUAL).forEach { kind ->
                val empty = repository(dir, related = { emptyList() }).load("listener", kind)
                assertNull(empty.mix)
                assertEquals(MixFailure.NO_DISCOVERIES, empty.failure)
                val unavailable = repository(dir, related = { error("offline") }).load("listener", kind)
                assertNull(unavailable.mix)
                assertEquals(MixFailure.REFRESH_FAILED, unavailable.failure)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun freshlyHeardDiscoveriesAreRemovedFromUnexpiredAndRestoredSnapshots() = runBlocking {
        val dir = temp()
        var history = f.history
        try {
            val repo = repository(dir, history = { history })
            val initial = assertNotNull(repo.load("listener", LocalMixKind.FRESH_FINDS).mix)
            val heard = initial.songs.first()
            history = history + f.track(200).copy(song = heard)
            val cached = assertNotNull(repo.cached("listener", LocalMixKind.FRESH_FINDS))
            assertFalse(cached.songs.any { it.videoId == heard.videoId })
            assertEquals(initial.version, cached.version)
            val restored = repository(dir, history = { history }, related = { error("Fresh cache") })
                .load("listener", LocalMixKind.FRESH_FINDS)
            assertNull(restored.failure)
            assertFalse(restored.mix!!.songs.any { it.videoId == heard.videoId })
        } finally { dir.deleteRecursively() }
    }

    @Test fun beyondSnapshotDropsAnArtistThatBecomesDominant() = runBlocking {
        val dir = temp()
        var history = f.history
        try {
            val repo = repository(dir, history = { history })
            val initial = assertNotNull(repo.load("listener", LocalMixKind.BEYOND_THE_USUAL).mix)
            val artist = initial.songs.first().artist
            history = history + (200..210).map { f.track(it, artist, plays = 50) }
            val filtered = repo.cached("listener", LocalMixKind.BEYOND_THE_USUAL)
            assertFalse(filtered?.songs.orEmpty().any { it.artist == artist })
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedRefreshPreservesEachUsableSnapshotAndItsBytes() = runBlocking {
        val dir = temp()
        try {
            kinds.forEach { kind ->
                val initial = assertNotNull(repository(dir).load("listener", kind).mix)
                val files = dir.listFiles()!!.associate { it.name to it.readBytes().toList() }
                val result = repository(dir, related = { error("offline") }).load("listener", kind, force = true)
                assertEquals(MixFailure.REFRESH_FAILED, result.failure)
                assertEquals(initial, result.mix)
                assertEquals(files, dir.listFiles()!!.associate { it.name to it.readBytes().toList() })
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun feedbackWhileDiscoveryLoadsCannotResurfaceHiddenTracks() = runBlocking {
        val dir = temp()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var feedback = MixFeedback()
        try {
            val repo = repository(dir, related = {
                entered.complete(Unit); release.await(); f.candidates.map { it.song }
            }, feedback = { feedback })
            val loading = async { repo.load("listener", LocalMixKind.FRESH_FINDS) }
            entered.await()
            feedback = MixFeedback(1, mapOf(f.song(101).videoId to TrackPreference.HIDE))
            release.complete(Unit)
            val saved = assertNotNull(loading.await().mix)
            assertEquals(1, saved.feedbackRevision)
            assertFalse(saved.songs.any { it.videoId == f.song(101).videoId })
        } finally { release.complete(Unit); dir.deleteRecursively() }
    }
}
