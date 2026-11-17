package com.music.vikify.data.personalization

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalizationResetCloudTest {
    private val fixture = CoreMixFixture()

    @Test fun portableResetPreservesBoundaryAcrossScopesAndRestarts() = runBlocking {
        val first = Files.createTempDirectory("reset-cloud-source").toFile()
        val second = Files.createTempDirectory("reset-cloud-target").toFile()
        try {
            val source = PersonalizationResetStore(first)
            val target = PersonalizationResetStore(second)
            source.reset("device-cookie-a", fixture.history.reversed(), fixture.now, fixture.zone)
            val marker = source.cloudMarker("device-cookie-a")!!
            assertFalse(marker.contains("device-cookie-a"))
            assertFalse(marker.contains("generation"))
            assertFalse(marker.contains("cleanupScopes"))
            assertTrue(target.adoptCloudMarker("device-cookie-b", null, marker))
            assertEquals(marker, target.cloudMarker("device-cookie-b"))
            assertEquals(listOf("device-cookie-b"), target.pending("device-cookie-b"))
            assertTrue(target.learningHistory("device-cookie-b", fixture.history).isEmpty())

            val restarted = PersonalizationResetStore(second)
            assertEquals(marker, restarted.cloudMarker("device-cookie-b"))
            assertEquals("device-cookie-b:reset:1", restarted.scope("device-cookie-b"))
            assertNull(restarted.cloudMarker("different-account"))
            assertEquals(fixture.history, restarted.learningHistory("different-account", fixture.history))
            assertTrue(restarted.adoptCloudMarker("device-cookie-b", marker, marker))
            assertEquals("device-cookie-b:reset:1", restarted.scope("device-cookie-b"))
        } finally {
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }

    @Test fun olderMissingAndConcurrentlyChangedMarkersCannotUndoAReset() = runBlocking {
        val dir = Files.createTempDirectory("reset-cloud-monotonic").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", fixture.history, fixture.now, fixture.zone)
            val older = store.cloudMarker("a")!!
            store.reset("a", fixture.history, fixture.now + 60_000, fixture.zone)
            val newer = store.cloudMarker("a")!!
            assertFalse(store.adoptCloudMarker("a", older, null))
            assertFalse(store.adoptCloudMarker("a", newer, null))
            assertFalse(store.adoptCloudMarker("a", newer, older))
            assertEquals(newer, store.cloudMarker("a"))
            assertEquals("a:reset:2", store.scope("a"))
            assertEquals(listOf("a", "a:reset:1"), store.pending("a"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun adoptingANewerResetSchedulesOnlyLocalScopesForCleanup() = runBlocking {
        val dir = Files.createTempDirectory("reset-cloud-cleanup").toFile()
        try {
            val source = PersonalizationResetStore(dir.resolve("source"))
            val target = PersonalizationResetStore(dir.resolve("target"))
            source.reset("remote", fixture.history, fixture.now + 60_000, fixture.zone)
            target.reset("local", fixture.history, fixture.now, fixture.zone)
            val marker = source.cloudMarker("remote")!!
            assertTrue(target.adoptCloudMarker("local", target.cloudMarker("local"), marker))
            assertEquals(listOf("local", "local:reset:1"), target.pending("local"))
            assertEquals("local:reset:2", target.scope("local"))
            assertEquals(marker, target.cloudMarker("local"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidPayloadFailsBeforeChangingLocalBoundary() = runBlocking {
        val dir = Files.createTempDirectory("reset-cloud-invalid").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("source", fixture.history, fixture.now, fixture.zone)
            val valid = store.cloudMarker("source")!!
            val invalidCounter = valid.replaceFirst("\"plays\":", "\"plays\":-")
            assertFailsWith<IllegalArgumentException> { store.adoptCloudMarker("target", null, invalidCounter) }
            assertFailsWith<IllegalArgumentException> { store.adoptCloudMarker("target", null, valid.replace("\"schemaVersion\":1", "\"schemaVersion\":9")) }
            assertFailsWith<IllegalArgumentException> { store.adoptCloudMarker("target", null, "$valid ") }
            assertNull(store.cloudMarker("target"))
            assertEquals("target", store.scope("target"))
            assertTrue(store.pending("target").isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun repeatedLocalResetWithSlowClockStillAdvancesCloudEpoch() = runBlocking {
        val dir = Files.createTempDirectory("reset-cloud-clock").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", fixture.history, fixture.now, fixture.zone)
            val old = store.cloudMarker("a")!!
            store.reset("a", fixture.history, fixture.now - 1_000, fixture.zone)
            val next = store.cloudMarker("a")!!
            assertTrue(next.contains("\"cutoff\":${fixture.now + 1}"))
            assertFalse(store.adoptCloudMarker("a", next, old))
        } finally { dir.deleteRecursively() }
    }

    @Test fun simultaneousRemoteResetsConvergeOnCanonicalMarker() = runBlocking {
        val dir = Files.createTempDirectory("reset-cloud-tie").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", fixture.history, fixture.now, fixture.zone)
            store.reset("b", fixture.history.take(1), fixture.now, fixture.zone)
            val first = store.cloudMarker("a")!!
            val second = store.cloudMarker("b")!!
            val winner = maxOf(first, second)
            val loser = minOf(first, second)
            assertTrue(first != second)
            val losingBase = if (first == loser) "a" else "b"
            val winningBase = if (first == winner) "a" else "b"
            assertTrue(store.adoptCloudMarker(losingBase, loser, winner))
            assertFalse(store.adoptCloudMarker(winningBase, winner, loser))
            assertEquals(winner, store.cloudMarker(losingBase))
            assertEquals(winner, store.cloudMarker(winningBase))
        } finally { dir.deleteRecursively() }
    }

    @Test fun cloudLearningExcludesLateCutoffDayHistoryButLearnsFollowingDays() = runBlocking {
        val dir = Files.createTempDirectory("reset-cloud-late-history").toFile()
        try {
            val store = PersonalizationResetStore(dir)
            store.reset("a", fixture.history, fixture.now, fixture.zone)
            // This remote track was unknown when the reset baseline was captured. Its old
            // same-day plays arrive together with one new play and must not revive affinity.
            val late = fixture.track(100).copy(plays = 103, heardMs = 10_300_000,
                lastPlayedAt = fixture.now + 60_000,
                days = listOf(DailyListeningEvidence(fixture.today, 103, 10_300_000)))
            assertTrue(store.learningHistory("a", listOf(late), includeResetDay = false).isEmpty())
            val nextDay = late.copy(plays = 105, heardMs = 10_420_000, lastPlayedAt = fixture.now + 86_400_000,
                days = late.days + DailyListeningEvidence(fixture.today + 1, 2, 120_000))
            val learned = store.learningHistory("a", listOf(nextDay), includeResetDay = false).single()
            assertEquals(2, learned.plays)
            assertEquals(120_000L, learned.heardMs)
            assertEquals(listOf(DailyListeningEvidence(fixture.today + 1, 2, 120_000)), learned.days)
        } finally { dir.deleteRecursively() }
    }
}
