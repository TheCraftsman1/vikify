package com.music.vikify.data.personalization

import com.music.vikify.data.model.Song
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMixGeneratorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = Instant.parse("2026-10-31T12:00:00Z").toEpochMilli()
    private val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    private val day = 86_400_000L
    private fun song(n: Int, artist: String = "Artist $n") =
        Song(n.toString().padStart(11, '0'), "Song $n", artist, null)
    private fun track(n: Int, plays: Int, ago: Int = 0, daily: Boolean = true) = TrackListeningEvidence(
        song(n), plays, plays * 120_000L, now - ago * day,
        if (daily) listOf(DailyListeningEvidence(today - ago, plays, plays * 120_000L)) else emptyList(),
    )
    private fun ids(kind: LocalMixKind, evidence: List<TrackListeningEvidence>) =
        LocalMixGenerator.generate(kind, evidence, now, zone).map { it.videoId }

    @Test fun repeatUsesFrequencyInsteadOfChronologicalRecents() {
        assertEquals(listOf(song(1).videoId, song(2).videoId), ids(LocalMixKind.ON_REPEAT,
            listOf(track(3, 1), track(2, 3), track(1, 8, 2))))
    }

    @Test fun repeatUsesDatedRecentPlaysRatherThanAllTimeTotals() {
        val historical = track(1, 200).copy(days = listOf(DailyListeningEvidence(today, 1, 120_000)))
        assertTrue(ids(LocalMixKind.ON_REPEAT, listOf(historical, track(2, 50, 35), track(3, 70, daily = false))).isEmpty())
    }

    @Test fun repeatIgnoresFutureDatedEvidence() {
        assertTrue(ids(LocalMixKind.ON_REPEAT, listOf(track(1, 10, -2))).isEmpty())
    }

    @Test fun rotationRequiresHistoricalAffectionAndTimeAway() {
        assertEquals(listOf(song(1).videoId), ids(LocalMixKind.BACK_IN_ROTATION,
            listOf(track(1, 12, 40), track(2, 1, 80), track(3, 50, 2), track(4, 20, -3))))
    }

    @Test fun legacyHistoryCanSupportRediscoveryWithoutInventingRecentListening() {
        assertEquals(listOf(song(1).videoId), ids(LocalMixKind.BACK_IN_ROTATION, listOf(track(1, 8, 30, daily = false))))
    }

    @Test fun dailyMixInterleavesUnheardCandidatesAndRemovesDuplicateRecordings() {
        val history = (1..8).map { track(it, 10 - it) }
        val related = listOf(song(1), song(20), song(20), song(21))
        val result = LocalMixGenerator.generate(LocalMixKind.DAILY_MIX, history, now, zone, related)
        assertEquals(song(20), result[2])
        assertEquals(10, result.size)
        assertEquals(result.size, result.distinctBy { it.videoId }.size)
    }

    @Test fun catalogTracksWithoutHistoryDoNotBecomeAPersonalDailyMix() {
        assertTrue(LocalMixGenerator.generate(LocalMixKind.DAILY_MIX, emptyList(), now, zone, listOf(song(20))).isEmpty())
    }

    @Test fun dailyMixRemainsUsefulWithoutANetworkConnection() {
        assertEquals(4, ids(LocalMixKind.DAILY_MIX, (1..4).map { track(it, 3) }).size)
    }

    @Test fun artistVarietyDoesNotDiscardASingleArtistLibrary() {
        val evidence = (1..12).map { track(it, 4).copy(song = song(it, "Same artist")) }
        assertEquals(12, ids(LocalMixKind.DAILY_MIX, evidence).size)
    }

    @Test fun shortAndLongSongsAreNotRejectedByAnArbitraryDurationFilter() {
        assertTrue(LocalMixGenerator.eligible(song(1).copy(durationText = "0:55")))
        assertTrue(LocalMixGenerator.eligible(song(2).copy(durationText = "12:30")))
        assertFalse(LocalMixGenerator.eligible(song(1).copy(videoId = "spotify:pending")))
        assertFalse(LocalMixGenerator.eligible(song(1).copy(isVideo = true)))
    }

    @Test fun selectionIsDeterministicAndBounded() {
        val evidence = (1..100).map { track(it, 5) }
        val first = ids(LocalMixKind.ON_REPEAT, evidence)
        assertEquals(30, first.size)
        assertEquals(first, ids(LocalMixKind.ON_REPEAT, evidence.reversed()))
    }

    @Test fun hiddenTracksAreExcludedEvenWhenTheyAreStrongFavoritesOrDiscoveries() {
        val history = listOf(track(1, 50), track(2, 3))
        val preferences = mapOf(song(1).videoId to TrackPreference.HIDE, song(20).videoId to TrackPreference.HIDE)
        val result = LocalMixGenerator.generate(LocalMixKind.DAILY_MIX, history, now, zone, listOf(song(20)), preferences)
        assertEquals(listOf(song(2)), result)
    }

    @Test fun explicitBoostAndDeliberateReplayInfluenceOtherwiseEqualTracks() {
        val history = listOf(track(1, 4), track(2, 4).copy(intentionalReplays = 2), track(3, 4))
        val result = LocalMixGenerator.generate(LocalMixKind.ON_REPEAT, history, now, zone,
            preferences = mapOf(song(3).videoId to TrackPreference.MORE))
        assertEquals(listOf(song(3), song(2), song(1)), result)
    }

    @Test fun repeatedEarlySkipsAreASoftPenaltyRatherThanAnExclusion() {
        val history = listOf(track(1, 4).copy(earlySkips = 2), track(2, 4))
        val result = LocalMixGenerator.generate(LocalMixKind.ON_REPEAT, history, now, zone)
        assertEquals(listOf(song(2), song(1)), result)
    }
}
