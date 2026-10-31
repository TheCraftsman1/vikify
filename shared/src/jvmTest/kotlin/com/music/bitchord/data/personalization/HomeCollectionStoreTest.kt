package com.music.bitchord.data.personalization

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCollectionStoreTest {
    private val f = CoreMixFixture()
    private val ids = setOf("your_daily_mix", "on_repeat", "comfort_zone")

    @Test fun followingSurvivesRestartAndIsIndependentOfPinsAndVisibility() = runBlocking {
        val dir = Files.createTempDirectory("home-follow-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids)
            store.follow("a", "on_repeat", true)
            store.hide("a", "on_repeat", true)
            val restored = HomeCollectionStore(dir, ids).snapshot("a").preferences
            assertEquals(setOf("on_repeat"), restored.followed)
            assertTrue(restored.pinned.isEmpty())
            assertEquals(setOf("on_repeat"), restored.hidden)
            store.follow("a", "on_repeat", false)
            assertTrue(store.snapshot("a").preferences.followed.isEmpty())
            assertTrue(store.snapshot("b").preferences.followed.isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun pinsAndHiddenChoicesSurviveRestartAndStayAccountScoped() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids)
            store.pin("a", "on_repeat", true)
            store.pin("a", "your_daily_mix", true)
            store.pin("a", "on_repeat", true) // An idempotent pin must preserve the selected order.
            store.hide("a", "comfort_zone", true)
            val restored = HomeCollectionStore(dir, ids).snapshot("a").preferences
            assertEquals(listOf("on_repeat", "your_daily_mix"), restored.pinned)
            assertEquals(setOf("comfort_zone"), restored.hidden)
            assertEquals(HomeCollectionPreferences(), store.snapshot("b").preferences)
            assertFalse(dir.listFiles().orEmpty().any { it.extension == "tmp" })
            assertFalse(dir.listFiles()!!.single().name.contains("a.json"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun hidingUnpinsAndPinningRestoresWhileShowingDoesNotRepin() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids)
            store.pin("a", "on_repeat", true)
            val hidden = store.hide("a", "on_repeat", true).preferences
            assertTrue(hidden.pinned.isEmpty())
            assertTrue("on_repeat" in hidden.hidden)
            assertEquals(HomeCollectionPreferences(), store.hide("a", "on_repeat", false).preferences)
            store.hide("a", "on_repeat", true)
            assertEquals(HomeCollectionPreferences(listOf("on_repeat")), store.pin("a", "on_repeat", true).preferences)
        } finally { dir.deleteRecursively() }
    }

    @Test fun repeatedRecompositionAndRepeatedTapsCountOncePerVisit() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids, { f.now })
            repeat(5) { store.record("a", "visit-1", "on_repeat") }
            repeat(5) { store.record("a", "visit-1", "on_repeat", opened = true) }
            var bucket = store.snapshot("a").usage.getValue("on_repeat").single()
            assertEquals(1, bucket.impressions)
            assertEquals(1, bucket.opens)
            store.record("a", "visit-2", "on_repeat", opened = true)
            bucket = store.snapshot("a").usage.getValue("on_repeat").single()
            assertEquals(2, bucket.impressions)
            assertEquals(2, bucket.opens)
        } finally { dir.deleteRecursively() }
    }

    @Test fun tapWithoutTimerStillSuppliesItsOwnImpression() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids, { f.now })
            store.record("a", "visit", "on_repeat", opened = true)
            store.record("a", "visit", "on_repeat")
            val day = store.snapshot("a").usage.getValue("on_repeat").single()
            assertEquals(1, day.opens)
            assertEquals(day.opens, day.impressions)
        } finally { dir.deleteRecursively() }
    }

    @Test fun concurrentPreparationMetricsAndControlsDoNotLoseEachOther() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids, { f.now })
            (listOf(async { store.pin("a", "on_repeat", true) }, async {
                store.prepare("a", listOf(HomeMixSummary("on_repeat", HomeMixAvailability.READY, 3, f.now)))
            }) + (1..12).map { visit -> async { store.record("a", "visit-$visit", "on_repeat") } }).awaitAll()
            val saved = HomeCollectionStore(dir, ids).snapshot("a")
            assertEquals(listOf("on_repeat"), saved.preferences.pinned)
            assertEquals(listOf("on_repeat"), saved.readyOrder)
            assertEquals(12, saved.usage.getValue("on_repeat").single().impressions)
        } finally { dir.deleteRecursively() }
    }

    @Test fun oldDaysAndVisitDeduplicationRecordsAreBounded() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        var time = f.now
        try {
            val store = HomeCollectionStore(dir, ids, { time })
            repeat(100) { day ->
                time = f.now + day * 86_400_000L
                store.record("a", "day-$day", "on_repeat")
            }
            var saved = store.snapshot("a")
            assertEquals(90, saved.usage.getValue("on_repeat").size)
            assertTrue(saved.visits.size <= 8)
            repeat(50) { store.record("a", "today-$it", "on_repeat") }
            saved = store.snapshot("a")
            assertEquals(32, saved.visits.size)
            val today = Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            assertTrue(saved.usage.values.flatten().all { it.day in (today - 89)..today })
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptPreferencesArePreservedAndCannotBeSilentlyReset() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            HomeCollectionStore(dir, ids).pin("a", "on_repeat", true)
            val file = dir.listFiles()!!.single()
            file.writeText("{unreadable")
            val restored = HomeCollectionStore(dir, ids)
            assertFailsWith<Exception> { restored.hide("a", "on_repeat", true) }
            assertEquals("{unreadable", file.readText())
        } finally { dir.deleteRecursively() }
    }

    @Test fun unsupportedCollectionIdsDoNotCreatePreferenceFiles() = runBlocking {
        val dir = Files.createTempDirectory("home-controls-test").toFile()
        try {
            val store = HomeCollectionStore(dir, ids)
            assertFailsWith<IllegalArgumentException> { store.pin("a", "unknown", true) }
            assertFailsWith<IllegalArgumentException> { store.record("a", "visit", "unknown") }
            assertTrue(dir.listFiles().isNullOrEmpty())
        } finally { dir.deleteRecursively() }
    }
}
