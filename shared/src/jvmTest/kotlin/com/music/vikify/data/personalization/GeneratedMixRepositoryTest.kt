package com.music.vikify.data.personalization

import com.music.vikify.data.model.ArtistRef
import com.music.vikify.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneratedMixRepositoryTest {
    private val now = Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun history(n: Int = 1): List<TrackListeningEvidence> = listOf(TrackListeningEvidence(
        Song(n.toString().padStart(11, '0'), "Favorite $n", "Artist", "https://example.test/art",
            artistId = "UC_test", albumId = "album", albumName = "Album",
            artists = listOf(ArtistRef("Artist", "UC_test")), isExplicit = true),
        plays = 6, heardMs = 720_000, lastPlayedAt = now,
        days = listOf(DailyListeningEvidence(Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay(), 6, 720_000)),
    ))
    private fun repo(dir: File, evidence: suspend () -> List<TrackListeningEvidence> = { history() },
        related: suspend (Song) -> List<Song> = { emptyList() },
        scope: (String) -> Boolean = { true }, clock: () -> Long = { now }) =
        GeneratedMixRepository(dir, evidence, related, scope, clock, { zone })

    private fun temp() = Files.createTempDirectory("vikify-mix-test").toFile()

    @Test fun savedVersionSurvivesRestartWithItsMetadata() = runBlocking {
        val dir = temp()
        try {
            val original = repo(dir).load("listener-a", LocalMixKind.ON_REPEAT).mix
            val restored = repo(dir, evidence = { error("Fresh cache must not read history") })
                .load("listener-a", LocalMixKind.ON_REPEAT).mix
            assertNotNull(original)
            assertEquals(original, restored)
            assertEquals(history().single().song, restored?.songs?.single())
            assertFalse(dir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedRefreshKeepsPreviousVersionAndBytes() = runBlocking {
        val dir = temp()
        try {
            val original = repo(dir).load("a", LocalMixKind.ON_REPEAT).mix
            val bytes = dir.listFiles()!!.single().readBytes().toList()
            val failure = repo(dir, evidence = { error("disk temporarily unavailable") })
                .load("a", LocalMixKind.ON_REPEAT, force = true)
            assertEquals(original, failure.mix)
            assertEquals(MixFailure.REFRESH_FAILED, failure.failure)
            assertEquals(bytes, dir.listFiles()!!.single().readBytes().toList())
        } finally { dir.deleteRecursively() }
    }

    @Test fun listenerCachesDoNotLeakBetweenAccounts() = runBlocking {
        val dir = temp()
        try {
            repo(dir).load("a", LocalMixKind.ON_REPEAT)
            assertNull(repo(dir).cached("b", LocalMixKind.ON_REPEAT))
            val b = repo(dir, evidence = { history(2) }).load("b", LocalMixKind.ON_REPEAT).mix
            assertEquals("Favorite 2", b?.songs?.single()?.title)
            assertEquals("Favorite 1", repo(dir).cached("a", LocalMixKind.ON_REPEAT)?.songs?.single()?.title)
        } finally { dir.deleteRecursively() }
    }

    @Test fun scopeSwitchDuringRetrievalPreventsPublication() = runBlocking {
        val dir = temp()
        var active = "a"
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val repository = repo(dir, evidence = { entered.complete(Unit); release.await(); history() }, scope = { it == active })
            val pending = async { repository.load("a", LocalMixKind.ON_REPEAT) }
            entered.await(); active = "b"; release.complete(Unit)
            val result = pending.await()
            assertEquals(MixFailure.SCOPE_CHANGED, result.failure)
            assertNull(result.mix)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun forceRefreshReplacesSnapshotWithoutMutatingOldTrackList() = runBlocking {
        val dir = temp()
        var number = 1
        try {
            val repository = repo(dir, evidence = { history(number) })
            val old = repository.load("a", LocalMixKind.ON_REPEAT).mix!!
            number = 2
            val fresh = repository.load("a", LocalMixKind.ON_REPEAT, force = true).mix!!
            assertEquals("Favorite 1", old.songs.single().title)
            assertEquals("Favorite 2", fresh.songs.single().title)
            assertTrue(fresh.version > old.version)
        } finally { dir.deleteRecursively() }
    }

    @Test fun insufficientHistoryDoesNotOverwriteAUsableMix() = runBlocking {
        val dir = temp()
        try {
            val old = repo(dir).load("a", LocalMixKind.ON_REPEAT).mix
            val result = repo(dir, evidence = { emptyList() }).load("a", LocalMixKind.ON_REPEAT, force = true)
            assertEquals(old, result.mix)
            assertEquals(MixFailure.INSUFFICIENT_EVIDENCE, result.failure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun coldStartDoesNotWriteAnEmptyPersonalizedVersion() = runBlocking {
        val dir = temp()
        try {
            val result = repo(dir, evidence = { emptyList() }).load("a", LocalMixKind.DAILY_MIX)
            assertNull(result.mix)
            assertEquals(MixFailure.INSUFFICIENT_EVIDENCE, result.failure)
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptCacheCanBeRegenerated(): Unit = runBlocking {
        val dir = temp()
        try {
            repo(dir).load("a", LocalMixKind.ON_REPEAT)
            dir.listFiles()!!.single().writeText("{truncated")
            assertNotNull(repo(dir).load("a", LocalMixKind.ON_REPEAT).mix)
        } finally { dir.deleteRecursively() }
    }

    @Test fun unavailableRadioKeepsAnExistingDailyVersion() = runBlocking {
        val dir = temp()
        try {
            val old = repo(dir).load("a", LocalMixKind.DAILY_MIX).mix
            val result = repo(dir, related = { error("offline") }).load("a", LocalMixKind.DAILY_MIX, force = true)
            assertEquals(old, result.mix)
            assertEquals(MixFailure.REFRESH_FAILED, result.failure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun cancellationDoesNotPublishPartialDailyMix() = runBlocking {
        val dir = temp()
        val entered = CompletableDeferred<Unit>()
        try {
            val repository = repo(dir, related = { entered.complete(Unit); CompletableDeferred<List<Song>>().await() })
            val pending = async { repository.load("a", LocalMixKind.DAILY_MIX, force = true) }
            entered.await(); pending.cancelAndJoin()
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun expiredSnapshotRefreshesWithoutAnExplicitForce() = runBlocking {
        val dir = temp()
        var time = now
        var number = 1
        try {
            val repository = repo(dir, evidence = { history(number) }, clock = { time })
            val first = repository.load("a", LocalMixKind.ON_REPEAT).mix!!
            time += LocalMixKind.ON_REPEAT.freshnessMs + 1
            number = 2
            val next = repository.load("a", LocalMixKind.ON_REPEAT).mix!!
            assertTrue(next.version > first.version)
            assertEquals("Favorite 2", next.songs.single().title)
        } finally { dir.deleteRecursively() }
    }

    @Test fun simultaneousLoadsGenerateOnlyOneInitialSnapshot() = runBlocking {
        val dir = temp()
        val calls = AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val repository = repo(dir, evidence = {
                calls.incrementAndGet(); entered.complete(Unit); release.await(); history()
            })
            val first = async { repository.load("a", LocalMixKind.ON_REPEAT) }
            val second = async { repository.load("a", LocalMixKind.ON_REPEAT) }
            entered.await(); release.complete(Unit)
            assertEquals(first.await().mix, second.await().mix)
            assertEquals(1, calls.get())
        } finally { release.complete(Unit); dir.deleteRecursively() }
    }

    @Test fun previousSnapshotRemainsReadableWhileRefreshIsInProgress() = runBlocking {
        val dir = temp()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var refreshing = false
        try {
            val repository = repo(dir, evidence = {
                if (refreshing) { entered.complete(Unit); release.await() }
                history(if (refreshing) 2 else 1)
            })
            val original = repository.load("a", LocalMixKind.ON_REPEAT).mix
            refreshing = true
            val pending = async { repository.load("a", LocalMixKind.ON_REPEAT, force = true) }
            entered.await()
            assertEquals(original, withTimeout(1_000) { repository.cached("a", LocalMixKind.ON_REPEAT) })
            release.complete(Unit)
            assertEquals("Favorite 2", pending.await().mix?.songs?.single()?.title)
        } finally { release.complete(Unit); dir.deleteRecursively() }
    }

    @Test fun cachedMixHonorsNewExclusionsBeforeAnotherGeneration(): Unit = runBlocking {
        val dir = temp()
        var preferences = MixFeedback()
        try {
            val repository = GeneratedMixRepository(dir, { history() + history(2) }, { emptyList() },
                clock = { now }, zone = { zone }, feedback = { preferences })
            repository.load("a", LocalMixKind.ON_REPEAT)
            preferences = MixFeedback(1, mapOf(history().single().song.videoId to TrackPreference.HIDE))
            assertEquals("Favorite 2", repository.cached("a", LocalMixKind.ON_REPEAT)?.songs?.single()?.title)
            val fresh = repository.load("a", LocalMixKind.ON_REPEAT).mix
            assertEquals(1, fresh?.feedbackRevision)
            assertEquals("Favorite 2", fresh?.songs?.single()?.title)
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedRefreshCannotResurfaceHiddenCachedTracks(): Unit = runBlocking {
        val dir = temp()
        var preferences = MixFeedback()
        var offline = false
        try {
            val repository = GeneratedMixRepository(dir, { history() + history(2) },
                { if (offline) error("offline") else emptyList() }, clock = { now }, zone = { zone }, feedback = { preferences })
            repository.load("a", LocalMixKind.DAILY_MIX)
            preferences = MixFeedback(1, mapOf(history().single().song.videoId to TrackPreference.HIDE))
            offline = true
            val retained = repository.load("a", LocalMixKind.DAILY_MIX, force = true)
            assertEquals(MixFailure.REFRESH_FAILED, retained.failure)
            assertEquals("Favorite 2", retained.mix?.songs?.single()?.title)
        } finally { dir.deleteRecursively() }
    }

    @Test fun hidingEveryCandidateReturnsAnExplicitFeedbackState(): Unit = runBlocking {
        val dir = temp()
        var preferences = MixFeedback()
        try {
            val repository = GeneratedMixRepository(dir, { history() }, { emptyList() },
                clock = { now }, zone = { zone }, feedback = { preferences })
            repository.load("a", LocalMixKind.ON_REPEAT)
            preferences = MixFeedback(1, mapOf(history().single().song.videoId to TrackPreference.HIDE))
            assertNull(repository.cached("a", LocalMixKind.ON_REPEAT))
            val result = repository.load("a", LocalMixKind.ON_REPEAT)
            assertNull(result.mix)
            assertEquals(MixFailure.HIDDEN_BY_FEEDBACK, result.failure)
        } finally { dir.deleteRecursively() }
    }

    @Test fun feedbackArrivingDuringRetrievalFiltersThePublishedResult(): Unit = runBlocking {
        val dir = temp()
        var preferences = MixFeedback()
        try {
            val repository = GeneratedMixRepository(dir, { history() + history(2) }, {
                preferences = MixFeedback(1, mapOf(history().single().song.videoId to TrackPreference.HIDE))
                emptyList()
            }, clock = { now }, zone = { zone }, feedback = { preferences })
            val result = repository.load("a", LocalMixKind.DAILY_MIX, force = true)
            assertEquals("Favorite 2", result.mix?.songs?.single()?.title)
            assertEquals(1, result.mix?.feedbackRevision)
        } finally { dir.deleteRecursively() }
    }

    @Test fun familiarMixesOpenFromRecordedHistoryWithoutAnyCatalogRequests() = runBlocking {
        val dir = temp()
        val calls = AtomicInteger()
        try {
            val repository = repo(dir, related = { calls.incrementAndGet(); error("offline") })
            listOf(LocalMixKind.ON_REPEAT, LocalMixKind.DAILY_MIX, LocalMixKind.COMFORT_ZONE, LocalMixKind.ARTIST_ORBIT)
                .forEach { kind ->
                    val loaded = repository.load("a", kind)
                    val mix = assertNotNull(loaded.mix, kind.id)
                    assertNull(loaded.failure, kind.id)
                    assertEquals(history().map { it.song.videoId }, mix.songs.map { it.videoId })
                }
            assertEquals(0, calls.get())
        } finally { dir.deleteRecursively() }
    }
}
