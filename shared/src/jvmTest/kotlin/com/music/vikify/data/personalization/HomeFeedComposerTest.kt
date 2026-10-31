package com.music.vikify.data.personalization

import com.music.vikify.data.mood.MoodGenreRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedComposerTest {
    private val f = CoreMixFixture()
    private val ready = LocalMixKind.entries.map { HomeMixSummary(it.id, HomeMixAvailability.READY, 10, f.now, PreferenceConfidence.ESTABLISHED) }
    private fun snapshot() = HomeCollectionSnapshot(scopeKey = "scope", summaries = ready,
        readyOrder = HomeFeedComposer.order(ready, emptyList(), emptyMap(), f.now))

    @Test fun onlyUsableVersionsEnterTheReadyOrder() {
        val summaries = ready.mapIndexed { index, summary -> when (index) {
            0 -> summary.copy(trackCount = 0)
            1 -> summary.copy(availability = HomeMixAvailability.UNAVAILABLE)
            2 -> summary.copy(generatedAt = 0)
            else -> summary
        } }
        assertEquals(summaries.filter { it.ready }.map { it.id }.toSet(),
            HomeFeedComposer.order(summaries, emptyList(), emptyMap(), f.now).toSet())
    }

    @Test fun strongNewEngagementCanOnlySwapOneAdjacentPairPerPreparation() {
        val previous = snapshot().readyOrder
        val day = Instant.ofEpochMilli(f.now).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        val usage = mapOf(previous.last() to listOf(HomeUsageDay(day, 100, 100)))
        val next = HomeFeedComposer.order(ready, previous, usage, f.now)
        assertEquals(previous.toSet(), next.toSet())
        assertEquals(2, previous.indices.count { previous[it] != next[it] })
        val moved = previous.indices.filter { previous[it] != next[it] }
        assertEquals(1, moved.last() - moved.first())
    }

    @Test fun offscreenAndOldExposureDoNotSupplyAnEngagementPenalty() {
        val previous = snapshot().readyOrder
        val day = Instant.ofEpochMilli(f.now).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        val obsolete = previous.associateWith { listOf(HomeUsageDay(day - 90, 10_000, 0)) }
        assertEquals(previous, HomeFeedComposer.order(ready, previous, obsolete, f.now))
        assertEquals(previous, HomeFeedComposer.order(ready, previous, emptyMap(), f.now))
    }

    @Test fun pinsHaveExplicitOrderAndNoCardAppearsTwiceOrWhileHidden() {
        val snapshot = snapshot().copy(preferences = HomeCollectionPreferences(
            pinned = listOf("artist_orbit", "quiet_focus"), hidden = setOf("on_repeat", "heavy_heart")))
        val (leading, categories) = HomeCollectionShelves.build(snapshot)
        assertEquals(listOf("mood:artist_orbit", "mood:quiet_focus"), leading.first().items.map { it.browseId })
        val ids = (leading + categories).flatMap { it.items }.map { it.browseId }
        assertEquals(ids.distinct(), ids)
        assertFalse("mood:on_repeat" in ids)
        assertFalse("mood:heavy_heart" in ids)
        assertEquals(MoodGenreRepository.allMoods.size - 2, ids.size)
    }

    @Test fun coldStartOffersGuidanceAndCuratedCollectionsWithoutClaimingReadyMixes() {
        val snapshot = HomeCollectionSnapshot(scopeKey = "new listener", summaries = LocalMixKind.entries.map { HomeMixSummary(it.id) })
        val (leading, categories) = HomeCollectionShelves.build(snapshot)
        assertFalse(leading.any { it.title == "Made For You" })
        assertEquals(7, leading.single().items.size)
        assertTrue(leading.single().items.all { it.subtitle.isNotBlank() && !it.subtitle.contains("Curated") })
        assertTrue(categories.all { it.subtitle == "Curated collections" })
        assertFalse(categories.flatMap { it.items }.any { LocalMixKind.fromId(it.browseId!!.removePrefix("mood:")) != null })
    }

    @Test fun pinningAnUnreadyMixDoesNotInventAReadyClaim() {
        val summary = HomeMixSummary("on_repeat")
        val snapshot = HomeCollectionSnapshot(scopeKey = "scope", summaries = listOf(summary),
            preferences = HomeCollectionPreferences(pinned = listOf("on_repeat")))
        val leading = HomeCollectionShelves.build(snapshot).first
        assertEquals("Play a few favorites more than once", leading.first().items.single().subtitle)
        assertFalse(leading.any { it.title == "Made For You" })
    }
}
