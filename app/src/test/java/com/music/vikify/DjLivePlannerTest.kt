package com.music.vikify

import com.music.vikify.data.model.Song
import com.music.vikify.dj.*
import org.junit.Assert.*
import org.junit.Test

class DjLivePlannerTest {
    private fun candidate(n: Int, artist: String = "Artist $n", origin: DjOrigin = DjOrigin.FOR_YOU, rank: Int = n) =
        DjCandidate(Song("song_$n", "Song $n", artist, null), origin, rank)

    @Test fun `a cold start uses ranked live recommendations without needing a profile`() {
        val choices = (0..30).map { candidate(it) }
        val result = DjLivePlanner.choose(choices, DjTaste(), emptySet(), emptyList(), emptySet(), 0)
        assertEquals(6, result.size)
        assertEquals("song_0", result.first().song.videoId)
        assertEquals(6, result.map { it.song.videoId }.toSet().size)
    }

    @Test fun `opening blends a recent seed with related music instead of replaying history`() {
        val choices = (0..12).map { candidate(it, origin = DjOrigin.HISTORY) } +
            (20..32).map { candidate(it, origin = DjOrigin.RADIO) } +
            (40..50).map { candidate(it) }
        val result = DjLivePlanner.choose(choices, DjTaste(), emptySet(), emptyList(), emptySet(), 0)
        assertEquals(DjOrigin.HISTORY, result[0].origin)
        assertEquals(DjOrigin.RADIO, result[1].origin)
        assertEquals(DjOrigin.RADIO, result[2].origin)
        assertTrue(result.count { it.origin == DjOrigin.HISTORY } <= 2)
        assertEquals(6, result.map { it.song.videoId }.toSet().size)
    }

    @Test fun `chapters change source emphasis after six songs`() {
        val candidates = listOf(candidate(1, origin = DjOrigin.FOR_YOU, rank = 0), candidate(2, origin = DjOrigin.RADIO, rank = 0),
            candidate(3, origin = DjOrigin.DISCOVERY, rank = 0))
        val first = (0..2).map { chapter -> DjLivePlanner.choose(candidates, DjTaste(), emptySet(), emptyList(), emptySet(), chapter, size = 1).first().song.videoId }
        assertEquals(listOf("song_1", "song_2", "song_3"), first)
        assertEquals(6, DjLivePlanner.CHAPTER_SIZE)
    }

    @Test fun `queued played hidden and excluded artists do not leak into a fresh set`() {
        val candidates = (1..12).map { candidate(it) }
        val result = DjLivePlanner.choose(candidates, DjTaste(), setOf("song_1"), listOf("song_2"), emptySet(), 0,
            excludedArtists = setOf("artist 3"), hidden = setOf("song_4"))
        assertTrue(result.none { it.song.videoId in setOf("song_1", "song_2", "song_3", "song_4") })
        assertEquals(6, result.size)
    }

    @Test fun `one artist cannot occupy an entire six song set when alternatives exist`() {
        val candidates = (0..15).map { candidate(it, if (it < 8) "Same" else "Artist $it") }
        val result = DjLivePlanner.choose(candidates, DjTaste(), emptySet(), emptyList(), emptySet(), 0)
        assertTrue(result.count { it.song.artist == "Same" } <= 2)
        assertTrue(result.zipWithNext().none { it.first.song.artist == it.second.song.artist })
    }

    @Test fun `explicit request provenance wins over unrelated recommendations`() {
        val candidates = (0..12).map { candidate(it) } + candidate(99, origin = DjOrigin.REQUEST, rank = 0)
        val result = DjLivePlanner.choose(candidates, DjTaste(), emptySet(), emptyList(), emptySet(), 0)
        assertEquals("song_99", result.first().song.videoId)
    }

    @Test fun `artist learning affects equally ranked recommendations`() {
        val taste = DjTaste(global = mutableMapOf("1:artist 1" to DjPrior(15.0, 1.0), "1:artist 2" to DjPrior(1.0, 15.0)))
        val result = DjLivePlanner.choose(listOf(candidate(1, rank = 0), candidate(2, rank = 0)), taste, emptySet(), emptyList(), emptySet(), 0, size = 1)
        assertEquals("song_1", result.first().song.videoId)
    }

    @Test fun `unknown live audio features are not learned as measured preferences`() {
        val brain = DjBrain()
        val before = brain.target()
        val track = DjTrack("live", "Live song", "Live artist", "unknown", era = "unknown", bpm = 0.0, key = "",
            durationMs = 180_000, vector = DjVector(), hasAcoustics = false)
        brain.learn(track, "like", 0, 180_000)
        assertEquals(before, brain.target())
        assertFalse(brain.taste.global.containsKey("0:unknown"))
        assertTrue(brain.taste.global.containsKey("1:live artist"))
    }

    @Test fun `empty inventory and explicit exclusions never fabricate songs`() {
        assertTrue(DjLivePlanner.choose(emptyList(), DjTaste(), emptySet(), emptyList(), emptySet(), 0).isEmpty())
        assertTrue(DjLivePlanner.choose(listOf(candidate(1)), DjTaste(), emptySet(), emptyList(), emptySet(), 0, hidden = setOf("song_1")).isEmpty())
    }

    @Test fun `chart music videos remain playable through the existing audio resolver`() {
        val chart = (1..10).map { candidate(it, origin = DjOrigin.DISCOVERY).let { c -> c.copy(song = c.song.copy(isVideo = true)) } }
        assertEquals(6, DjLivePlanner.choose(chart, DjTaste(), emptySet(), emptyList(), emptySet(), 0).size)
    }
    @Test fun `an explicit request may replay a heard song while keeping current tracks excluded`() {
        val taste = DjTaste(recent = mutableListOf("song_1"))
        val candidates = listOf(candidate(1, origin = DjOrigin.REQUEST, rank = 0), candidate(2, origin = DjOrigin.REQUEST, rank = 20))
        assertEquals("song_1", DjLivePlanner.choose(candidates, taste, emptySet(), listOf("song_1"), emptySet(), 0,
            size = 1, explicitRequest = true).single().song.videoId)
        assertEquals("song_2", DjLivePlanner.choose(candidates, taste, setOf("song_1"), listOf("song_1"), emptySet(), 0,
            size = 1, explicitRequest = true).single().song.videoId)
    }
}
