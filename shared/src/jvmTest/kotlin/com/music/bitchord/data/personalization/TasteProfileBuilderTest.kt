package com.music.bitchord.data.personalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TasteProfileBuilderTest {
    private val f = CoreMixFixture()
    private fun build(tracks: List<TrackListeningEvidence>) = TasteProfileBuilder.build(tracks, f.now, f.zone)

    @Test fun emptyHistoryHasNoClaimOfPreferenceConfidence() {
        assertEquals(PreferenceConfidence.NONE, build(emptyList()).confidence.level)
    }

    @Test fun oneLoopedSongCannotEstablishABroadProfile() {
        val profile = build(listOf(f.track(1, plays = 1_000, days = 1)))
        assertEquals(PreferenceConfidence.LEARNING, profile.confidence.level)
        assertEquals(PreferenceConfidence.LEARNING, profile.artists.single().confidence.level)
    }

    @Test fun manySongsOnOneDayRemainLearning() {
        val profile = build((1..20).map { f.track(it, days = 1) })
        assertEquals(PreferenceConfidence.LEARNING, profile.confidence.level)
        assertEquals(1, profile.confidence.distinctDays)
    }

    @Test fun independentTracksAndDaysEstablishConfidence() {
        val profile = build(f.history)
        assertEquals(PreferenceConfidence.ESTABLISHED, profile.confidence.level)
        assertEquals(3, profile.confidence.distinctDays)
        assertTrue(profile.artists.all { it.longTermAffinity in 0.0..1.0 && it.recentAffinity in 0.0..1.0 })
        assertEquals("Alpha", profile.artists.first().name)
    }

    @Test fun smallerConsistentHistoryIsDeveloping() {
        assertEquals(PreferenceConfidence.DEVELOPING, build(listOf(f.track(1, days = 2), f.track(2, days = 2))).confidence.level)
    }

    @Test fun undatedTotalsSupportAffinityWithoutInventingDatedConfidence() {
        val profile = build(f.history.map { it.copy(days = emptyList()) })
        assertEquals(PreferenceConfidence.LEARNING, profile.confidence.level)
        assertEquals(0, profile.confidence.distinctDays)
        assertTrue(profile.artists.isNotEmpty())
    }

    @Test fun sessionDaysAreUnionedAcrossTracksInsteadOfAddedTogether() {
        val days = setOf(f.today, f.today - 1, f.today - 2)
        val profile = build(listOf(f.track(1).copy(days = emptyList(), sessionEpochDays = days),
            f.track(2).copy(days = emptyList(), sessionEpochDays = days)))
        assertEquals(3, profile.confidence.distinctDays)
    }

    @Test fun stableArtistIdentityUnifiesAnUnambiguousNameAlias() {
        val tracks = listOf(f.track(1), f.track(2).let {
            it.copy(song = it.song.copy(artistId = null, artist = "  ALPHA  "))
        })
        assertEquals(1, build(tracks).artists.size)
        val ambiguous = tracks + f.track(3).let { it.copy(song = it.song.copy(artistId = "UC_another_Alpha")) }
        assertEquals(3, build(ambiguous).artists.size)
    }

    @Test fun hiddenEvidenceDoesNotShapeArtistAffinity() {
        val profile = TasteProfileBuilder.build(f.history, f.now, f.zone,
            (1..4).associate { f.song(it).videoId to TrackPreference.HIDE })
        assertFalse(profile.artists.any { it.key == "id:UC_Alpha" })
    }

    @Test fun legacyNameOnlyHistoryMatchesCatalogCreditsWithoutMergingConflictingIds() {
        val legacy = f.history.map { it.copy(song = it.song.copy(artistId = null)) }
        val profile = build(legacy)
        assertEquals(setOf("name:alpha"), profile.artistKeys(f.song(101)))
        assertTrue(profile.artistAffinity(f.song(101)) > 0.0)
        val withKnownId = build(f.history)
        assertEquals(setOf("id:UC_another_Alpha"), withKnownId.artistKeys(f.song(101).copy(artistId = "UC_another_Alpha")))
        assertEquals(0.0, withKnownId.artistAffinity(f.song(101).copy(artistId = "UC_another_Alpha")))
    }

    @Test fun futureDatedPlaysAreStillKnownHeardButDoNotTeachATaste() {
        val future = f.track(1).copy(lastPlayedAt = f.now + 86_400_000)
        val profile = build(listOf(future))
        assertEquals(PreferenceConfidence.NONE, profile.confidence.level)
        assertTrue(future.song.videoId in profile.heardTrackIds)
    }

    @Test fun boostingALesserPlayedArtistDoesNotRedefineEveryArtistAsDominant() {
        val profile = TasteProfileBuilder.build(f.history, f.now, f.zone,
            mapOf(f.song(8).videoId to TrackPreference.MORE))
        assertEquals("Gamma", profile.artists.first().name)
        assertEquals(setOf("id:UC_Alpha"), profile.dominantArtistKeys)
    }
}
