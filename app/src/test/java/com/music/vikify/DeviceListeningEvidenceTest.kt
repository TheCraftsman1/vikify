package com.music.vikify

import com.music.vikify.data.personalization.deviceListeningEvidence
import com.music.vikify.data.personalization.enrichSessionEvidence
import com.music.vikify.data.personalization.ListeningSessionRecord
import com.music.vikify.data.personalization.SessionEnd
import com.music.vikify.data.stats.DayStats
import com.music.vikify.data.stats.TrackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListeningEvidenceTest {
    @Test fun monthlyRecordsMergeWithoutDoubleCountingOrInventingDatedPlays() {
        val entries = listOf(
            TrackEntry("abcdefghijk", "Old title", "Artist", plays = 10, ms = 1_000, last = 1),
            TrackEntry("abcdefghijk", "New title", "Artist", plays = 2, ms = 200, last = 2,
                days = mutableMapOf("2026-10-31" to DayStats(200, 2), "invalid" to DayStats(999, 9))),
        )
        val result = deviceListeningEvidence(entries).single()
        assertEquals(12, result.plays)
        assertEquals(1_200L, result.heardMs)
        assertEquals("New title", result.song.title)
        assertEquals(2, result.days.single().plays)
    }

    @Test fun unresolvedLocalAndExternalIdentifiersAreNotTreatedAsYoutubeTracks() {
        assertTrue(deviceListeningEvidence(listOf(TrackEntry("local:12"), TrackEntry("spotify:123"))).isEmpty())
    }

    @Test fun sessionIntentDoesNotCountExistingReplayPlaysAgain() {
        val now = 1_800_000_000_000L
        val track = deviceListeningEvidence(listOf(TrackEntry("abcdefghijk", "Song", "Artist", plays = 8, ms = 900_000, last = now)))
        val sessions = listOf(
            ListeningSessionRecord("replay", "abcdefghijk", "one", null, now - 60_000, now, 1, 60_000, 60_000, 65_000, true, SessionEnd.AUTO_ADVANCE),
            ListeningSessionRecord("skip", "abcdefghijk", "two", null, now - 5_000, now, 1, 5_000, 5_000, 120_000, false, SessionEnd.MANUAL_SKIP),
            ListeningSessionRecord("error", "abcdefghijk", "three", null, now - 5_000, now, 1, 5_000, 5_000, 120_000, false, SessionEnd.ERROR),
        )
        val enriched = enrichSessionEvidence(track, sessions, now).single()
        assertEquals(8, enriched.plays)
        assertEquals(900_000L, enriched.heardMs)
        assertEquals(1, enriched.intentionalReplays)
        assertEquals(1, enriched.earlySkips)
        assertEquals(1, enriched.completedSessions)
    }

    @Test fun sessionDaysPreserveActualCalendarDatesAndIgnoreShortObservations() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val now = java.time.Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
        val track = deviceListeningEvidence(listOf(TrackEntry("abcdefghijk", "Song", "Artist", plays = 8, last = now)))
        fun session(id: String, start: Long, heard: Long) = ListeningSessionRecord(id, "abcdefghijk", id, null,
            start, start + heard, 1, heard, heard, 120_000, false, SessionEnd.STOPPED)
        val sessions = listOf(session("today", now - 60_000, 40_000), session("same day", now - 120_000, 40_000),
            session("yesterday", now - 86_400_000, 40_000), session("short", now - 172_800_000, 5_000))
        val enriched = enrichSessionEvidence(track, sessions, now, zone).single()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
        assertEquals(setOf(today, today - 1), enriched.sessionEpochDays)
        assertEquals(2, enriched.distinctSessionDays)
        assertEquals(8, enriched.plays)
    }
}
