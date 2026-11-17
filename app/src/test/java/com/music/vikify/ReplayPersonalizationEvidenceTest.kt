package com.music.vikify

import com.music.vikify.data.stats.DayStats
import com.music.vikify.data.stats.StoredBucket
import com.music.vikify.data.stats.TrackEntry
import com.music.vikify.data.stats.mergePersonalizationTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class ReplayPersonalizationEvidenceTest {
    @Test fun `a fresh installation can recommend from restored listening alone`() {
        val restored = track(plays = 3, ms = 60_000, last = 200)
        val result = mergePersonalizationTracks(
            emptyList(),
            listOf(StoredBucket(month = "2026-11", tracks = listOf(restored))),
        ).single()
        assertEquals(restored, result)
        assertNotSame(restored, result)
        assertNotSame(restored.days, result.days)
    }

    @Test fun `remote listening contributes to recommendations without changing original history`() {
        val local = track(plays = 2, ms = 40_000, last = 100)
        val restored = track(plays = 3, ms = 60_000, last = 200)
        val bucket = StoredBucket(month = "2026-11", tracks = listOf(restored))

        val combined = mergePersonalizationTracks(listOf(local), listOf(bucket)).single()

        assertEquals(5, combined.plays)
        assertEquals(100_000L, combined.ms)
        assertEquals(200L, combined.last)
        assertEquals(DayStats(ms = 100_000, plays = 5), combined.days["2026-11-11"])
        assertEquals(2, local.plays)
        assertEquals(3, restored.plays)
        assertEquals(DayStats(ms = 60_000, plays = 3), restored.days["2026-11-11"])
        assertNotSame(restored.days["2026-11-11"], combined.days["2026-11-11"])

        // Re-reading a cumulative cloud snapshot must not add it twice.
        val reread = mergePersonalizationTracks(listOf(local), listOf(bucket)).single()
        assertEquals(combined, reread)
    }

    @Test fun `months and installations add independently and leave returned counters detached`() {
        val january = StoredBucket(month = "2026-01", tracks = listOf(track(1, 20_000, 1)))
        val february = StoredBucket(month = "2026-02", tracks = listOf(track(4, 80_000, 2)))
        val otherTrack = TrackEntry(id = "other", plays = 6, ms = 120_000)

        val result = mergePersonalizationTracks(listOf(otherTrack), listOf(january, february))

        assertEquals(2, result.size)
        assertEquals(5, result.single { it.id == "dQw4w9WgXcQ" }.plays)
        result.single { it.id == "dQw4w9WgXcQ" }.days.getValue("2026-11-11").plays = 999
        result.single { it.id == "other" }.plays = 999
        assertEquals(1, january.tracks.single().days.getValue("2026-11-11").plays)
        assertEquals(6, otherTrack.plays)
    }

    @Test fun `disabling remote sync removes restored evidence from subsequent reads`() {
        val local = track(1, 20_000, 1)
        val remote = StoredBucket(month = "2026-11", tracks = listOf(track(4, 80_000, 2)))
        assertEquals(5, mergePersonalizationTracks(listOf(local), listOf(remote)).single().plays)
        assertEquals(1, mergePersonalizationTracks(listOf(local), emptyList()).single().plays)
    }

    private fun track(plays: Int, ms: Long, last: Long) = TrackEntry(
        id = "dQw4w9WgXcQ",
        title = "A track",
        plays = plays,
        ms = ms,
        last = last,
        days = mutableMapOf("2026-11-11" to DayStats(ms = ms, plays = plays)),
    )
}
