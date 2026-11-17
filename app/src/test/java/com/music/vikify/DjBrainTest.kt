package com.music.vikify

import com.music.vikify.dj.*
import kotlin.random.Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DjBrainTest {
    @Test fun `bundled catalog decodes into the real Android selector`() {
        val source = java.io.File("src/main/assets/dj/catalog.json").readText()
        val tracks = Json { ignoreUnknownKeys = true }.decodeFromString<List<DjTrack>>(source)
        assertEquals(90, tracks.size)
        assertEquals(90, tracks.map { it.id }.toSet().size)
        repeat(30) { seed ->
            val brain = DjBrain(random = Random(seed))
            val selected = brain.select(tracks, emptySet())
            assertEquals(4, selected.size)
            assertEquals(4, selected.map { it.id }.toSet().size)
            assertTrue(selected.all { t -> t.vector.values().all { it in 0.0..1.0 } })
        }
    }
    private fun track(id: Int, genre: String = "pop", artist: String = "Artist $id") =
        DjTrack("track_$id", "Title $id", artist, genre, listOf("dance-pop"), listOf("calm"),
            "2010s", 100.0, "8A", 200_000, DjVector())

    @Test fun `actual dwell decides completion credit and seeks cannot fake it`() {
        assertEquals(0.0, DjBrain.reward("complete", 1000, 200_000), 0.0)
        assertEquals(.7, DjBrain.reward("skip", 160_000, 200_000), 0.0)
        assertEquals(-1.0, DjBrain.reward("skip", 7999, 200_000), 0.0)
        assertEquals(-.25, DjBrain.reward("skip", 40_000, 200_000), 0.0)
        assertEquals(1.5, DjBrain.reward("like", 0, 200_000), 0.0)
    }

    @Test fun `likes learn without adding a second copy of listening time`() {
        val brain = DjBrain().apply { bucket = "evening" }
        val t = track(1)
        brain.learn(t, "like", 0, t.durationMs)
        brain.learn(t, "complete", 170_000, t.durationMs)
        assertEquals(170_000L, brain.taste.listenedMs)
        assertEquals(2, brain.taste.events)
        assertEquals(3.2, brain.taste.global.getValue("0:pop").alpha, 1e-10)
    }

    @Test fun `late skips penalize genre but preserve artist and era priors`() {
        val brain = DjBrain()
        brain.learn(track(1), "skip", 40_000, 200_000)
        assertEquals(1.25, brain.taste.global.getValue("0:pop").beta, 0.0)
        assertFalse(brain.taste.global.containsKey("1:artist 1"))
        assertFalse(brain.taste.global.containsKey("2:2010s"))
    }

    @Test fun `sets never repeat committed tracks or relax hard exclusions`() {
        repeat(30) { seed ->
            val brain = DjBrain(random = Random(seed))
            brain.excludedGenres += "rock"
            brain.excludedArtists += "artist 5"
            val candidates = (1..8).map { track(it, if (it <= 2) "rock" else "pop") }
            val selected = brain.select(candidates, setOf("track_3", "track_4"), size = 10)
            assertEquals(setOf("track_6", "track_7", "track_8"), selected.map { it.id }.toSet())
            assertEquals(selected.size, selected.distinctBy { it.id }.size)
        }
    }

    @Test fun `fresh tracks precede repeats and tiny inventory can replay after exhaustion`() {
        val brain = DjBrain(random = Random(1))
        val old = track(1)
        brain.remember(old.id)
        assertEquals("track_2", brain.select(listOf(old, track(2)), emptySet(), 1).single().id)
        assertEquals(old, brain.select(listOf(old), emptySet(), 1).single())
        assertTrue(brain.select(listOf(old), setOf(old.id), 4).isEmpty())
    }

    @Test fun `artist diversity takes precedence within freshness tier`() {
        val tracks = listOf(track(1, artist = "Same"), track(2, artist = "Same"), track(3, artist = "Other"))
        val selected = DjBrain(random = Random(42)).select(tracks, emptySet(), 2)
        assertEquals(2, selected.map { it.artist }.toSet().size)
    }

    @Test fun `learned preferences survive JSON persistence and history remains bounded`() {
        val brain = DjBrain()
        repeat(100) { brain.learn(track(it), "skip", 1000, 200_000) }
        val saved = Json.encodeToString(brain.taste)
        val loaded = Json.decodeFromString<DjTaste>(saved)
        assertEquals(40, loaded.recent.size)
        assertEquals(100, loaded.events)
        assertEquals(brain.taste.global, loaded.global)
    }

    @Test fun `time buckets and four position arc match the server blueprint`() {
        assertEquals("morning", DjBrain.bucketForHour(5))
        assertEquals("late_night", DjBrain.bucketForHour(23))
        val target = DjVector()
        assertEquals(.6, DjBrain.arc(target, 1).energy, 1e-10)
        assertEquals(.54, DjBrain.arc(target, 2).energy, 1e-10)
        assertEquals(.45, DjBrain.arc(target, 3).energy, 1e-10)
        assertEquals(target, DjBrain.arc(target, 4))
    }

    @Test fun `tempo and key compatibility handle wheel wrap and half double time`() {
        val base = track(1).copy(key = "12A")
        assertEquals(.935, DjBrain.harmonic(base, track(2).copy(key = "1A")), 1e-10)
        assertEquals(1.0, DjBrain.harmonic(base, base.copy(bpm = 50.0)), 1e-10)
        assertEquals(1.0, DjBrain.harmonic(base, base.copy(bpm = 200.0)), 1e-10)
        assertEquals(1.0, DjBrain.cosine(DjVector(), DjVector()), 1e-10)
    }
}
