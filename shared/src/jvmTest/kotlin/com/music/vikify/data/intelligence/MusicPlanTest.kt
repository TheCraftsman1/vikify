package com.music.vikify.data.intelligence

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MusicPlanTest {
    private val request = MusicPlanRequest("Only from my library, no repeats today", listOf(
        MusicInventoryTrack("a", "One", "First", true),
        MusicInventoryTrack("b", "Two", "Second", false),
        MusicInventoryTrack("c", "Three", "Third", true),
        MusicInventoryTrack("d", "Four", "Fourth", true, false),
    ), blockedIds = listOf("c"), playedTodayIds = listOf("a"))

    @Test fun unknownAndUnplayableTracksCannotEnterAQueue() {
        val plan = MusicPlan(1, MusicIntent(), listOf("invented"), "model", "")
        assertFalse(plan.validFor(request))
        assertFalse(plan.copy(trackIds = listOf("d")).validFor(request))
        assertFalse(plan.copy(trackIds = listOf("c")).validFor(request))
        assertFalse(plan.copy(trackIds = listOf("a", "a")).validFor(request))
    }

    @Test fun hardConstraintsAreCheckedAgainOnTheClient() {
        val plan = MusicPlan(1, MusicIntent(libraryOnly = true, excludePlayedToday = true), emptyList(), "model", "")
        assertTrue(plan.validFor(request))
        assertFalse(plan.copy(trackIds = listOf("a")).validFor(request))
        assertFalse(plan.copy(trackIds = listOf("b")).validFor(request))
        assertFalse(plan.copy(intent = MusicIntent(excludedArtists = listOf("FIRST")), trackIds = listOf("a")).validFor(request))
    }

    @Test fun unknownResponseFieldsAndPlaybackUrlsAreRejected() {
        val valid = MusicPlanCodec.json.encodeToString(MusicPlan(1, MusicIntent(), listOf("a"), "rules", "Actual library track"))
        assertTrue(MusicPlanCodec.decode(valid, request) != null)
        assertNull(MusicPlanCodec.decode(valid.dropLast(1) + ",\"audioUrl\":\"https://example.com\"}", request))
        val url = MusicPlanCodec.json.encodeToString(MusicPlan(1, MusicIntent(query = "https://example.com"), emptyList(), "model", ""))
        assertNull(MusicPlanCodec.decode(url, request))
        val hijack = MusicPlanCodec.json.encodeToString(MusicPlan(1, MusicIntent(action = MusicAction.PAUSE), emptyList(), "model", ""))
        assertNull(MusicPlanCodec.decode(hijack, request.copy(transcript = "Play Pause by An Artist")))
    }

    @Test fun offlineInterpreterRemovesOnlySupportedControlClauses() {
        assertEquals("mellow songs", LocalMusicIntent.parse("play mellow songs").query)
        val intent = LocalMusicIntent.parse("Play Telugu love songs, only from my library; no repeats today")
        assertEquals("Telugu love songs", intent.query)
        assertTrue(intent.libraryOnly)
        assertTrue(intent.excludePlayedToday)
        assertEquals(MusicAction.EXPLAIN, LocalMusicIntent.parse("Why this song?").action)
        assertEquals(MusicAction.PLAY, LocalMusicIntent.parse("Play Pause by An Artist").action)
    }

    @Test fun excludedArtistsApplyToCollaborationsButNotNameFragments() {
        assertTrue(MusicSelection.artistExcluded("Anirudh Ravichander, Shreya Ghoshal", listOf("Anirudh")))
        assertFalse(MusicSelection.artistExcluded("Anna", listOf("Ann")))
    }
}
