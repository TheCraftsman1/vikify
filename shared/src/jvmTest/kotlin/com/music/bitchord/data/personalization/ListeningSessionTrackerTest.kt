package com.music.bitchord.data.personalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ListeningSessionTrackerTest {
    private class Harness {
        var scope = "a"
        var mono = 0L
        var wall = 1_000_000L
        var position = 0L
        val records = mutableListOf<Pair<String, ListeningSessionRecord>>()
        var track = SessionTrack("abcdefghijk", "entry-1", "mood:on_repeat")
        val tracker = ListeningSessionTracker({ scope }, { mono }, { wall }) { scope, record -> records += scope to record }
        init { tracker.transition(track, 0, 120_000, true) }
        fun advance(ms: Long = 5_000, mediaMs: Long = ms) {
            mono += ms; wall += ms; position += mediaMs
            tracker.sample(track, position, 120_000)
        }
        fun finish(reason: SessionEnd = SessionEnd.STOPPED): ListeningSessionRecord {
            tracker.finish(reason)
            return records.last().second
        }
    }

    @Test fun pauseResumeKeepsOneSessionAndExcludesPausedTime() {
        val h = Harness()
        repeat(2) { h.advance() }
        h.tracker.playing(h.track, false, h.position, 120_000)
        h.mono += 3_600_000; h.wall += 3_600_000
        h.tracker.playing(h.track, true, h.position, 120_000)
        h.advance()
        val finished = h.finish()
        assertEquals(15_000, finished.heardMs)
        assertEquals(15_000, finished.uniqueHeardMs)
        assertEquals(1, h.records.map { it.second.id }.distinct().size)
    }

    @Test fun forwardSeekDoesNotFillUnheardCoverage() {
        val h = Harness()
        h.advance()
        h.tracker.seek(h.position, 105_000); h.position = 105_000
        h.advance()
        val finished = h.finish(SessionEnd.AUTO_ADVANCE)
        assertEquals(10_000, finished.heardMs)
        assertEquals(10_000, finished.uniqueHeardMs)
    }

    @Test fun repeatedSegmentsDoNotDoubleCountUniqueCoverage() {
        val h = Harness()
        repeat(2) { h.advance() }
        h.tracker.seek(h.position, 0); h.position = 0
        repeat(2) { h.advance() }
        val finished = h.finish()
        assertEquals(20_000, finished.heardMs)
        assertEquals(10_000, finished.uniqueHeardMs)
        assertFalse(finished.intentionalReplay)
    }

    @Test fun explicitlyMarkedNextClosesAsManualSkip() {
        val h = Harness()
        h.advance()
        h.tracker.intent(ListeningIntent.SKIP)
        h.tracker.transition(SessionTrack("newnewnew12", "entry-2"), 0, 120_000, true)
        assertEquals(SessionEnd.MANUAL_SKIP, h.records.last().second.ended)
    }

    @Test fun automaticAdvanceDoesNotInheritAStaleUserIntent() {
        val h = Harness()
        h.advance(); h.tracker.intent(ListeningIntent.SKIP)
        h.mono += 3_000
        h.tracker.transition(SessionTrack("newnewnew12", "entry-2"), 0, 120_000, true, SessionTransition.AUTO_ADVANCE)
        assertEquals(SessionEnd.AUTO_ADVANCE, h.records.last().second.ended)
    }

    @Test fun unmarkedQueueChangesAndFailuresAreNeutral() {
        val h = Harness()
        h.advance()
        h.tracker.transition(SessionTrack("newnewnew12", "entry-2"), 0, 120_000, true)
        assertEquals(SessionEnd.REPLACED, h.records.last().second.ended)
        h.track = SessionTrack("newnewnew12", "entry-2"); h.position = 0
        h.advance()
        assertEquals(SessionEnd.ERROR, h.finish(SessionEnd.ERROR).ended)
    }

    @Test fun intentionalRestartCreatesANewSessionWithReplayAttribution() {
        val h = Harness()
        repeat(3) { h.advance() }
        h.tracker.intent(ListeningIntent.REPLAY)
        h.tracker.seek(h.position, 0); h.position = 0
        val old = h.records.last().second
        repeat(6) { h.advance() }
        val replay = h.finish()
        assertEquals(SessionEnd.MANUAL_REPLAY, old.ended)
        assertNotEquals(old.id, replay.id)
        assertTrue(replay.intentionalReplay)
        assertEquals(30_000, replay.heardMs)
    }

    @Test fun repeatModeDoesNotBecomeAnIntentionalReplay() {
        val h = Harness()
        repeat(6) { h.advance() }
        h.tracker.transition(h.track, 0, 120_000, true, SessionTransition.AUTO_REPEAT)
        assertEquals(SessionEnd.AUTO_REPEAT, h.records.last().second.ended)
        h.position = 0; h.advance()
        assertFalse(h.finish().intentionalReplay)
    }

    @Test fun aQualityReplacementPreservesTheLogicalSession() {
        val h = Harness()
        repeat(2) { h.advance() }
        h.tracker.transition(h.track, h.position, 120_000, false)
        h.advance()
        val finished = h.finish()
        assertEquals(15_000, finished.heardMs)
        assertEquals(1, h.records.map { it.second.id }.distinct().size)
    }

    @Test fun switchingAccountsClosesOldEvidenceInItsOriginalScope() {
        val h = Harness()
        h.advance(); h.scope = "b"; h.advance()
        assertEquals("a", h.records.first().first)
        assertEquals(SessionEnd.SCOPE_CHANGED, h.records.first().second.ended)
        assertEquals(5_000, h.records.first().second.heardMs)
        h.advance(); h.finish()
        assertEquals("b", h.records.last().first)
        assertNotEquals(h.records.first().second.id, h.records.last().second.id)
    }

    @Test fun wallClockChangesCannotInflateElapsedListening() {
        val h = Harness()
        h.advance(); h.wall += 86_400_000; h.advance()
        assertEquals(10_000, h.finish().heardMs)
    }

    @Test fun checkpointsReuseAnIdentityWithIncreasingRevisions() {
        val h = Harness()
        repeat(14) { h.advance() }
        h.finish()
        assertEquals(1, h.records.map { it.second.id }.distinct().size)
        assertEquals(listOf(1L, 2L, 3L), h.records.map { it.second.revision })
        assertEquals(70_000, h.records.last().second.heardMs)
    }
}
