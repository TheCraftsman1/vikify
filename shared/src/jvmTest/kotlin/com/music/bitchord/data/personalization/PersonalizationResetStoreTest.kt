package com.music.bitchord.data.personalization

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonalizationResetStoreTest {
    private val f = CoreMixFixture()
    private fun newPlay(track: TrackListeningEvidence, at: Long = f.now + 60_000) = track.copy(
        plays = track.plays + 1, heardMs = track.heardMs + 60_000, lastPlayedAt = at,
        days = track.days.map { if (it.epochDay == f.today) it.copy(plays = it.plays + 1, heardMs = it.heardMs + 60_000) else it })

    @Test fun existingProfilesKeepTheirScopeAndEvidenceUntilReset() {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            assertEquals("a", store.scope("a"))
            assertEquals(f.history, store.learningHistory("a", f.history))
        } finally { dir.deleteRecursively() }
    }

    @Test fun resetAndRestartExcludeEarlierListeningWithoutChangingReplayInput() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            val original = f.history.toList()
            val store = PersonalizationResetStore(dir)
            assertEquals("a:reset:1", store.reset("a", f.history, f.now, f.zone))
            val restored = PersonalizationResetStore(dir)
            assertEquals("a:reset:1", restored.scope("a"))
            assertTrue(restored.learningHistory("a", f.history).isEmpty())
            assertEquals(original, f.history)
            assertEquals("b", restored.scope("b"))
            assertEquals(original, restored.learningHistory("b", f.history))
        } finally { dir.deleteRecursively() }
    }

    @Test fun anotherPlayOfAnOldFavoriteAddsOnlyThatNewPlay() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            val before = f.track(1)
            store.reset("a", listOf(before), f.now, f.zone)
            val track = store.learningHistory("a", listOf(newPlay(before))).single()
            assertEquals(1, track.plays)
            assertEquals(60_000L, track.heardMs)
            assertEquals(1, track.days.single().plays)
            assertEquals(f.today, track.days.single().epochDay)
        } finally { dir.deleteRecursively() }
    }

    @Test fun laterCalendarDaysAndNewTracksCanTeachPreferencesAgain() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", f.history, f.now, f.zone)
            val later = f.track(1).copy(lastPlayedAt = f.now + 86_400_000,
                days = f.track(1).days + DailyListeningEvidence(f.today + 1, 2, 120_000))
            val new = f.track(100, days = 1).copy(lastPlayedAt = f.now + 60_000)
            val result = store.learningHistory("a", listOf(later, new))
            assertEquals(2, result.first().plays)
            assertEquals(new.plays, result.last().plays)
        } finally { dir.deleteRecursively() }
    }

    @Test fun undatedTotalsAndImportedOldDaysDoNotRebuildClearedAffinity() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", f.history, f.now, f.zone)
            val undated = f.track(100).copy(lastPlayedAt = f.now + 60_000, days = emptyList())
            val oldDays = f.track(101).copy(lastPlayedAt = f.now + 60_000,
                days = listOf(DailyListeningEvidence(f.today - 10, 500, 500_000)))
            assertTrue(store.learningHistory("a", listOf(undated, oldDays)).isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun repeatingResetMovesTheBoundaryWithoutAccumulatingEarlierProfiles() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", f.history, f.now, f.zone)
            val history = listOf(newPlay(f.track(1)))
            assertEquals("a:reset:2", store.reset("a", history, f.now + 60_000, f.zone))
            assertTrue(store.learningHistory("a", history).isEmpty())
            assertEquals(listOf("a", "a:reset:1"), store.pending("a"))
            store.acknowledgeCleanup("a", "a")
            assertEquals(listOf("a:reset:1"), PersonalizationResetStore(dir).pending("a"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun cutoffMetadataKeepsHashesAndCountersInsteadOfTrackMetadata() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            PersonalizationResetStore(dir).reset("a", f.history, f.now, f.zone)
            val raw = dir.listFiles()!!.single().readText()
            assertFalse(raw.contains(f.song(1).videoId))
            assertFalse(raw.contains(f.song(1).title))
            assertFalse(raw.contains("Alpha"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun corruptBoundaryFailsClosedAndIsNotOverwritten() = runBlocking {
        val dir = Files.createTempDirectory("reset-boundary-test").toFile()
        try {
            PersonalizationResetStore(dir).reset("a", f.history, f.now, f.zone)
            val file = dir.listFiles()!!.single()
            file.writeText("{broken")
            val restored = PersonalizationResetStore(dir)
            assertFailsWith<Exception> { restored.learningHistory("a", f.history) }
            assertFailsWith<Exception> { restored.reset("a", f.history, f.now, f.zone) }
            assertEquals("{broken", file.readText())
        } finally { dir.deleteRecursively() }
    }
}
