package com.music.vikify.data.personalization

import com.music.vikify.data.mood.MoodGenreRepository
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HomeFeedComposerTest {
    private val f = CoreMixFixture()
    private val ready = LocalMixKind.entries.mapIndexed { index, kind -> HomeMixSummary(kind.id,
        HomeMixAvailability.READY, 10, f.now, PreferenceConfidence.ESTABLISHED, index.toString(16).padStart(64, '0')) }
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
        assertEquals(5, ids.size) // Two deliberate pins and at most three recommendations.
        assertTrue(categories.isEmpty())
    }

    @Test fun coldStartDoesNotFillHomeWithUnreadyMixesOrUnrequestedCatalogCards() {
        val snapshot = HomeCollectionSnapshot(scopeKey = "new listener", summaries = LocalMixKind.entries.map { HomeMixSummary(it.id) })
        val (leading, categories) = HomeCollectionShelves.build(snapshot)
        assertFalse(leading.any { it.title == "Made For You" })
        assertTrue(leading.isEmpty())
        assertTrue(categories.isEmpty())
    }

    @Test fun pinningAnUnreadyMixDoesNotInventAReadyClaim() {
        val summary = HomeMixSummary("on_repeat")
        val snapshot = HomeCollectionSnapshot(scopeKey = "scope", summaries = listOf(summary),
            preferences = HomeCollectionPreferences(pinned = listOf("on_repeat")))
        val leading = HomeCollectionShelves.build(snapshot).first
        assertEquals("Play a few favorites more than once", leading.first().items.single().subtitle)
        assertFalse(leading.any { it.title == "Made For You" })
    }

    @Test fun readyHomeMixesAreBoundedDistinctAndExplainTheirRealTrackCount() {
        val state = snapshot().copy(readyOrder = snapshot().readyOrder + snapshot().readyOrder)
        val shelves = HomeCollectionShelves.build(state).first
        val cards = shelves.single().items
        assertEquals(3, cards.size)
        assertEquals(3, cards.map { it.browseId }.distinct().size)
        assertTrue(cards.all { it.subtitle.startsWith("10 songs · ") })
        assertFalse(cards.any { it.subtitle.contains("Curated collection") })
    }

    @Test fun anExplicitCatalogPinKeepsItsSpecificDescription() {
        val state = HomeCollectionSnapshot(scopeKey = "scope")
            .copy(preferences = HomeCollectionPreferences(pinned = listOf("quiet_focus")))
        val card = HomeCollectionShelves.build(state).first.single().items.single()
        assertEquals(MoodGenreRepository.findMood("quiet_focus")!!.subtitle, card.subtitle)
    }

    @Test fun automaticCardsNeedFiveTracksAndARevalidatedTrackSet() {
        val summaries = ready.mapIndexed { index, summary -> when (index) {
            0 -> summary.copy(trackCount = 4)
            1 -> summary.copy(trackSetFingerprint = null) // A summary written before this field existed.
            2 -> summary.copy(trackSetFingerprint = "unknown")
            else -> summary
        } }
        val cards = HomeCollectionShelves.build(snapshot().copy(summaries = summaries)).first.flatMap { it.items }
        val rejected = summaries.take(3).map { "mood:${it.id}" }
        assertEquals(3, cards.size)
        assertTrue(cards.none { it.browseId in rejected })
    }

    @Test fun identicalTrackSetsArePromotedOnceWhileDeliberatePinsRemain() {
        val duplicate = ready[0].trackSetFingerprint
        val summaries = ready.mapIndexed { index, summary -> if (index < 3) summary.copy(trackSetFingerprint = duplicate) else summary }
        val state = snapshot().copy(summaries = summaries,
            preferences = HomeCollectionPreferences(pinned = listOf(ready[0].id, ready[1].id)))
        val shelves = HomeCollectionShelves.build(state).first
        assertEquals(listOf("mood:${ready[0].id}", "mood:${ready[1].id}"), shelves.first().items.map { it.browseId })
        val automatic = shelves.drop(1).flatMap { it.items }
        assertEquals(3, automatic.size)
        assertTrue(automatic.none { it.browseId == "mood:${ready[2].id}" })
        val identities = automatic.map { card -> summaries.single { "mood:${it.id}" == card.browseId }.trackSetFingerprint }
        assertEquals(identities.size, identities.distinct().size)
    }

    @Test fun trackSetIdentityIgnoresOrderAndDuplicateRowsButChangesWithMembership() {
        val first = f.song(1)
        val second = f.song(2)
        assertEquals(homeTrackSetFingerprint(listOf(first, second)), homeTrackSetFingerprint(listOf(second, first, first)))
        assertNotEquals(homeTrackSetFingerprint(listOf(first)), homeTrackSetFingerprint(listOf(first, second)))
    }
}
