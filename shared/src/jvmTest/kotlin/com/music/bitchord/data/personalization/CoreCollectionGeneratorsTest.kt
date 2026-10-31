package com.music.bitchord.data.personalization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreCollectionGeneratorsTest {
    private val f = CoreMixFixture()

    @Test fun fourCollectionsHaveDistinctMembershipAndPurposeForOneListener() {
        val comfort = f.generate(LocalMixKind.COMFORT_ZONE)
        val orbit = f.generate(LocalMixKind.ARTIST_ORBIT)
        val fresh = f.generate(LocalMixKind.FRESH_FINDS)
        val beyond = f.generate(LocalMixKind.BEYOND_THE_USUAL)
        val heard = f.history.map { it.song.videoId }.toSet()
        assertTrue(comfort.count { it.videoId in heard } >= comfort.size * 0.8)
        assertTrue(orbit.first().videoId in setOf(f.song(101).videoId, f.song(102).videoId))
        assertTrue(fresh.none { it.videoId in heard })
        assertEquals(f.song(101), fresh.first())
        assertEquals(f.song(103, "New artist X"), beyond.first())
        assertTrue(beyond.none { it.artistId == "UC_Alpha" })
        assertEquals(4, listOf(comfort, orbit, fresh, beyond).map { songs -> songs.map { it.videoId }.toSet() }.distinct().size)
    }

    @Test fun sparseHistoryDoesNotTurnComfortOrDailyMixIntoMostlyDiscovery() {
        val history = listOf(f.track(1, plays = 3, days = 1))
        val offers = (100..110).map { MixCandidate(f.song(it), f.song(1).videoId) }
        assertEquals(listOf(f.song(1)), f.generate(LocalMixKind.COMFORT_ZONE, history, offers))
        assertEquals(listOf(f.song(1)), f.generate(LocalMixKind.DAILY_MIX, history, offers))
        assertTrue(f.generate(LocalMixKind.BEYOND_THE_USUAL, history, offers).isEmpty())
    }

    @Test fun candidatesNeedAKnownPersonalSeedAndCorrectSourceProvenance() {
        val inventedSeed = MixCandidate(f.song(120, "Unrelated"), "no_such_seed")
        val noArtistId = f.history.map { it.copy(song = it.song.copy(artistId = null)) }
        val unsupportedCatalog = MixCandidate(f.song(121, "Unrelated"), f.song(1).videoId, CandidateSource.ARTIST_CATALOG)
        assertTrue(f.generate(LocalMixKind.FRESH_FINDS, candidates = listOf(inventedSeed)).isEmpty())
        assertTrue(f.generate(LocalMixKind.FRESH_FINDS, history = noArtistId, candidates = listOf(unsupportedCatalog)).isEmpty())
    }

    @Test fun discoveryDiversityCapsAvoidAnEntireMixFromOneArtist() {
        val offers = (100..120).map { MixCandidate(f.song(it, "New artist"), f.song(1).videoId) }
        assertEquals(3, f.generate(LocalMixKind.FRESH_FINDS, candidates = offers).size)
        assertEquals(2, f.generate(LocalMixKind.BEYOND_THE_USUAL, candidates = offers).size)
    }

    @Test fun hiddenTracksStayOutOfEveryNewGenerator() {
        val hidden = setOf(f.song(1).videoId, f.song(101).videoId, f.song(103).videoId)
        val feedback = hidden.associateWith { TrackPreference.HIDE }
        listOf(LocalMixKind.COMFORT_ZONE, LocalMixKind.ARTIST_ORBIT, LocalMixKind.FRESH_FINDS, LocalMixKind.BEYOND_THE_USUAL).forEach {
            assertFalse(f.generate(it, preferences = feedback).any { song -> song.videoId in hidden })
        }
    }

    @Test fun discoveryRemovesEveryKnownPlayedIdentityIncludingUndatedHistory() {
        val heard = f.track(101).copy(lastPlayedAt = 0, days = emptyList())
        assertFalse(f.generate(LocalMixKind.FRESH_FINDS, history = f.history + heard).any { it.videoId == heard.song.videoId })
    }

    @Test fun beyondUsualRejectsUnknownArtistMetadataAndDominantCollaborations() {
        val unknown = f.song(140).copy(artist = "", artistId = null)
        val collab = f.song(141, "New artist").copy(artists = listOf(
            com.music.bitchord.data.model.ArtistRef("Alpha", "UC_Alpha"),
            com.music.bitchord.data.model.ArtistRef("New artist", "UC_New artist"),
        ))
        assertTrue(f.generate(LocalMixKind.BEYOND_THE_USUAL, candidates = listOf(
            MixCandidate(unknown, f.song(1).videoId), MixCandidate(collab, f.song(1).videoId),
        )).isEmpty())
    }
}
