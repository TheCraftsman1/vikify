package com.music.vikify.data.personalization

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveHomeRepositoryTest {
    private val f = CoreMixFixture()
    private val ids = LocalMixKind.entries.mapTo(HashSet()) { it.id }

    @Test fun coldStartHasGuidanceWithoutCatalogRequestsOrReadyVersions() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { emptyList() }, { error("No listening seed") },
                clock = { f.now }, zone = { f.zone })
            val repo = AdaptiveHomeRepository(store, mixes)
            assertTrue(repo.cached("a").readyOrder.isEmpty())
            val prepared = repo.prepare("a")
            assertEquals(7, prepared.summaries.size)
            assertTrue(prepared.summaries.all { it.availability == HomeMixAvailability.LEARNING })
            assertTrue(prepared.readyOrder.isEmpty())
            assertTrue(dir.resolve("mixes").listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun restartUsesRealSavedMixesAndMakesNoCatalogRequests() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { f.history }, { f.candidates.map { it.song } },
                clock = { f.now }, zone = { f.zone })
            val generated = AdaptiveHomeRepository(store, mixes).prepare("a")
            val restarted = AdaptiveHomeRepository(HomeCollectionStore(dir.resolve("home"), ids),
                GeneratedMixRepository(dir.resolve("mixes"), { f.history }, { error("No network on cache restore") },
                    clock = { f.now }, zone = { f.zone })).cached("a")
            assertEquals(generated.summaries, restarted.summaries)
            assertEquals(generated.readyOrder, restarted.readyOrder)
            assertTrue(restarted.readyOrder.isNotEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun summaryWithoutAnActualSavedVersionIsNeverPromotedAsReady() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            store.prepare("a", listOf(HomeMixSummary("on_repeat", HomeMixAvailability.READY, 10, f.now)))
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { emptyList() }, { error("Cache only") })
            val cached = AdaptiveHomeRepository(store, mixes).cached("a")
            assertTrue(cached.readyOrder.isEmpty())
            assertEquals(HomeMixAvailability.NEEDS_REFRESH, cached.summaries.first { it.id == "on_repeat" }.availability)
        } finally { dir.deleteRecursively() }
    }

    @Test fun heardDiscoveriesLoseTheirReadyClaimBeforeNormalExpiry() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        var history = f.history
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { history }, { f.candidates.map { it.song } },
                clock = { f.now }, zone = { f.zone })
            val repo = AdaptiveHomeRepository(store, mixes)
            repo.prepare("a")
            val discoveries = mixes.cached("a", LocalMixKind.FRESH_FINDS)!!.songs
            history = history + discoveries.mapIndexed { index, song -> f.track(200 + index).copy(song = song) }
            val cached = repo.cached("a")
            assertFalse("fresh_finds" in cached.readyOrder)
            assertFalse(cached.summaries.first { it.id == "fresh_finds" }.ready)
        } finally { dir.deleteRecursively() }
    }

    @Test fun preferenceChangesDuringGenerationSurvivePreparedSnapshotPublication() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { f.history }, {
                entered.complete(Unit); release.await(); f.candidates.map { it.song }
            }, clock = { f.now }, zone = { f.zone })
            val repo = AdaptiveHomeRepository(store, mixes)
            val preparing = async { repo.prepare("a") }
            entered.await()
            store.hide("a", "on_repeat", true)
            store.pin("a", "your_daily_mix", true)
            release.complete(Unit)
            val prepared = preparing.await()
            assertTrue("on_repeat" in prepared.preferences.hidden)
            assertEquals(listOf("your_daily_mix"), prepared.preferences.pinned)
        } finally { release.complete(Unit); dir.deleteRecursively() }
    }

    @Test fun cancellingPreparationDoesNotPublishAPartialHomeSnapshot() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        val entered = CompletableDeferred<Unit>()
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { f.history }, {
                entered.complete(Unit); CompletableDeferred<List<com.music.vikify.data.model.Song>>().await()
            }, clock = { f.now }, zone = { f.zone })
            val pending = async { AdaptiveHomeRepository(store, mixes).prepare("a") }
            entered.await(); pending.cancelAndJoin()
            assertTrue(store.snapshot("a").summaries.isEmpty())
            assertTrue(dir.resolve("home").listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun accountChangeDiscardsPreparedResults() = runBlocking {
        val dir = Files.createTempDirectory("adaptive-home-test").toFile()
        var scope = "a"
        try {
            val store = HomeCollectionStore(dir.resolve("home"), ids, { f.now })
            val mixes = GeneratedMixRepository(dir.resolve("mixes"), { scope = "b"; f.history }, { emptyList() },
                isCurrentScope = { it == scope }, clock = { f.now }, zone = { f.zone })
            val repo = AdaptiveHomeRepository(store, mixes, { it == scope })
            assertFailsWith<IllegalStateException> { repo.prepare("a") }
            assertTrue(store.snapshot("a").summaries.isEmpty())
            assertTrue(store.snapshot("b").preferences.pinned.isEmpty())
        } finally { dir.deleteRecursively() }
    }
}
